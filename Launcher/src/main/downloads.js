import { getHwid } from './hwid.js'
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { Readable, Transform } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import { CONFIG, url } from './config.js'
import { ApiError, describeError } from './http.js'
import { extractZip, isValidZip } from './unzip.js'

export function sha256File(file) {
	return new Promise((resolve, reject) => {
		const hash = crypto.createHash('sha256')
		const stream = fs.createReadStream(file)
		stream.on('error', reject)
		stream.on('data', (chunk) => hash.update(chunk))
		stream.on('end', () => resolve(hash.digest('hex')))
	})
}

export function contentDispositionName(header) {
	if (!header) return null
	const star = /filename\*=UTF-8''([^;]+)/i.exec(header)
	if (star) {
		try {
			return decodeURIComponent(star[1])
		} catch {
			/* ignore */
		}
	}
	const plain = /filename="?([^";]+)"?/i.exec(header)
	return plain ? plain[1] : null
}

export function isZip(file) {
	try {
		const fd = fs.openSync(file, 'r')
		const buffer = Buffer.alloc(2)
		fs.readSync(fd, buffer, 0, 2, 0)
		fs.closeSync(fd)
		return buffer[0] === 0x50 && buffer[1] === 0x4b
	} catch {
		return false
	}
}

/**
 * Загрузка файла с сайта.
 * Express отдаёт файлы через res.download() — поддерживаются Range, ETag,
 * Last-Modified и Content-Disposition. Сервер не присылает sha256, поэтому
 * контрольная сумма сверяется только если заголовок всё же пришёл.
 */
export async function downloadArtifact({
	pathname,
	query = null,
	token = null,
	destFile,
	etag = null,
	onProgress = null,
	onLog = null,
	label = 'файл',
	resume = false,
	retries = CONFIG.network.retries,
	chunkSize = 1024 * 1024,
}) {
	fs.mkdirSync(path.dirname(destFile), { recursive: true })
	const partFile = `${destFile}.part`
	if (!resume && fs.existsSync(partFile)) {
		fs.rmSync(partFile, { force: true })
	}
	let startAt = 0
	if (resume && fs.existsSync(partFile)) {
		startAt = fs.statSync(partFile).size
	}

	const headers = {
		Accept: 'application/octet-stream, application/json',
		'User-Agent': CONFIG.network.userAgent,
		'X-HWID': getHwid(),
	}
	if (token) headers.Authorization = `Bearer ${token}`
	if (etag) headers['If-None-Match'] = etag
	if (chunkSize > 0) headers.Range = `bytes=${startAt}-${startAt + chunkSize - 1}`
	else if (startAt > 0) headers.Range = `bytes=${startAt}-`

	const controller = new AbortController()
	const timer = setTimeout(
		() => controller.abort(),
		CONFIG.network.downloadTimeoutMs,
	)

	let response
	try {
		response = await fetch(url(pathname, query), {
			headers,
			signal: controller.signal,
		})
	} catch (error) {
		clearTimeout(timer)
		if (retries > 0) {
			onLog?.(`${label}: соединение оборвалось, продолжаю загрузку…`)
			return downloadArtifact({
				pathname, query, token, destFile, etag: null, onProgress, onLog, label,
				resume: true, retries: retries - 1, chunkSize,
			})
		}
		throw new ApiError(
			error.name === 'AbortError'
				? `Загрузка (${label}) прервана по таймауту`
				: 'Нет связи с сервером',
			{ network: true },
		)
	}

	if (response.status === 304) {
		clearTimeout(timer)
		const isZipFile = label === 'мод' || destFile.endsWith('.zip') || destFile.endsWith('.jar')
		if (!fs.existsSync(destFile) || (isZipFile && !isValidZip(destFile))) {
			fs.rmSync(destFile, { force: true })
			return downloadArtifact({
				pathname,
				query,
				token,
				destFile,
				etag: null,
				onProgress,
				onLog,
				label,
				resume: false,
				retries,
				chunkSize,
			})
		}
		onLog?.(`${label}: уже актуальный (304)`)
		return { file: destFile, skipped: true, etag }
	}


	if (!response.ok) {
		clearTimeout(timer)
		let payload = null
		try {
			payload = await response.json()
		} catch {
			/* не JSON */
		}
		throw new ApiError(
			describeError(response.status, payload?.error || payload?.message),
			{
				status: response.status,
				code: payload?.error || null,
				buyUrl: payload?.funpayUrl || null,
				payload,
			},
		)
	}

	const partial = response.status === 206
	if (!partial) startAt = 0

	const headerSize = Number(
		response.headers.get('x-artifact-size') ||
			response.headers.get('content-length') ||
			0,
	)
	const contentRange = response.headers.get('content-range')
	const rangeTotal = contentRange ? Number(/\/(\d+)$/.exec(contentRange)?.[1] || 0) : 0
	const total = rangeTotal || (partial && headerSize ? startAt + headerSize : headerSize || null)
	const serverSha = response.headers.get('x-checksum-sha256')
	const newEtag = response.headers.get('etag')
	const fileName = contentDispositionName(
		response.headers.get('content-disposition'),
	)

	let received = startAt
	const writeStream = fs.createWriteStream(partFile, {
		flags: partial && startAt > 0 ? 'a' : 'w',
	})
	const progressStream = new Transform({
		transform(chunk, encoding, callback) {
			received += chunk.length
			onProgress?.({
				received,
				total,
				percent: total ? Math.min(100, Math.round((received / total) * 100)) : null,
			})
			callback(null, chunk)
		},
	})

	try {
		await pipeline(Readable.fromWeb(response.body), progressStream, writeStream)
	} catch (error) {
		if (retries > 0) {
			onLog?.(`${label}: поток прерван, продолжаю с ${received} байт…`)
			return downloadArtifact({
				pathname, query, token, destFile, etag: null, onProgress, onLog, label,
				resume: true, retries: retries - 1, chunkSize,
			})
		}
		throw new ApiError(`Загрузка (${label}) оборвалась: ${error?.message || 'ошибка сети'}`, {
			code: 'download_interrupted',
			network: true,
		})
	} finally {
		clearTimeout(timer)
	}

	if (total && received < total) {
		onLog?.(`${label}: загружено ${received}/${total}, продолжаю…`)
		return downloadArtifact({
			pathname, query, token, destFile, etag: null, onProgress, onLog, label,
			resume: true, retries: CONFIG.network.retries, chunkSize,
		})
	}

	if (serverSha) {
		const actual = await sha256File(partFile)
		if (actual.toLowerCase() !== serverSha.toLowerCase()) {
			fs.rmSync(partFile, { force: true })
			throw new ApiError(`Контрольная сумма (${label}) не совпала`, {
				code: 'checksum_mismatch',
			})
		}
		onLog?.(`${label}: sha256 совпал`)
	}

	let targetFile = destFile
	if (fileName) {
		const ext = path.extname(fileName).toLowerCase()
		if (ext && ext !== path.extname(destFile).toLowerCase()) {
			targetFile = path.join(
				path.dirname(destFile),
				`${path.basename(destFile, path.extname(destFile))}${ext}`,
			)
		}
	}

	const ext = path.extname(targetFile).toLowerCase()
	const isExe = ext === '.exe' || ext === '.msi'
	const isZipFile = !isExe && (label === 'мод' || ext === '.zip' || ext === '.jar')
	if (isZipFile && !isValidZip(partFile)) {
		fs.rmSync(partFile, { force: true })
		throw new ApiError(`Архив (${label}) повреждён при скачивании, попробуй ещё раз`, {
			code: 'corrupt_zip',
		})
	}

	fs.rmSync(targetFile, { force: true })
	fs.renameSync(partFile, targetFile)

	return {
		file: targetFile,
		skipped: false,
		size: received,
		etag: newEtag,
		sha256: serverSha || null,
		fileName,
	}
}

export async function downloadFabricApi(mcVersion, targetDir, onLog) {
	try {
		onLog?.(`Поиск Fabric API для версии ${mcVersion} на Modrinth...`)
		const res = await fetch(`https://api.modrinth.com/v2/project/fabric-api/version?loaders=["fabric"]&game_versions=["${mcVersion}"]`)
		if (!res.ok) return false
		const versions = await res.json()
		if (versions && versions.length > 0) {
			const file = versions[0].files.find(f => f.primary) || versions[0].files[0]
			if (file) {
				const dest = path.join(targetDir, file.filename)

				// 1. Если этот конкретный файл уже есть и целый — не качаем заново
				if (fs.existsSync(dest) && fs.statSync(dest).size > 0) {
					onLog?.(`Fabric API уже установлен: ${file.filename}`)
					return dest
				}

				// 2. Удаляем старые или другие версии Fabric API, чтобы не вызвать DuplicateModsException
				if (fs.existsSync(targetDir)) {
					try {
						for (const f of fs.readdirSync(targetDir)) {
							const lower = f.toLowerCase()
							if (lower.startsWith('fabric-api') && (lower.endsWith('.jar') || lower.endsWith('.disabled')) && f !== file.filename) {
								try {
									fs.unlinkSync(path.join(targetDir, f))
									onLog?.(`Удалена старая версия Fabric API: ${f}`)
								} catch {}
							}
						}
					} catch {}
				}

				onLog?.(`Скачиваю ${file.filename}...`)
				await downloadArtifact({
					pathname: file.url,
					destFile: dest,
					onLog,
					label: 'Fabric API',
					resume: true,
				})
				onLog?.(`Fabric API установлен: ${file.filename}`)
				return dest
			}
		}
		onLog?.(`Fabric API для версии ${mcVersion} не найден на Modrinth.`)
	} catch (e) {
		onLog?.(`Ошибка загрузки Fabric API: ${e.message}`)
	}
	return null
}

/** Мод — GET /api/client/download/mod/file (Bearer + подписка) → распаковка в mods. */
export async function downloadMod({
	token,
	gameDir,
	cacheDir,
	mcVersion = null,
	etag = null,
	onProgress,
	onLog,
}) {
	const targetDir = path.join(gameDir, 'mods')
	const temp = path.join(cacheDir, `lunacy-mod${mcVersion ? `-${mcVersion}` : ''}.zip`)
	
	fs.mkdirSync(targetDir, { recursive: true })
	if (mcVersion) await downloadFabricApi(mcVersion, targetDir, onLog)

	onLog?.(`Скачиваю мод Lunacy Visuals${mcVersion ? ` для ${mcVersion}` : ''}…`)
	const result = await downloadArtifact({
		pathname: CONFIG.client.modFile,
		query: mcVersion ? { version: mcVersion } : null,
		token,
		destFile: temp,
		etag,
		onProgress,
		onLog,
		label: 'мод',
		resume: true,
	})

	const target = path.join(targetDir, 'lunacy-visuals.jar')
	if (!result.skipped || !fs.existsSync(target) || !isValidZip(target)) {
		fs.copyFileSync(result.file, target)
	}


	return { ...result, installed: true, dir: targetDir }
}

/** Бесплатная сборка — GET /api/client/download/free/file (без авторизации). */
export async function downloadFreeBuild({
	gameDir,
	cacheDir,
	mcVersion = null,
	onProgress,
	onLog,
}) {
	const targetDir = path.join(gameDir, 'mods')
	const temp = path.join(cacheDir, 'lunacy-free.zip')
	
	fs.mkdirSync(targetDir, { recursive: true })

	onLog?.('Скачиваю бесплатную сборку…')
	const result = await downloadArtifact({
		pathname: CONFIG.client.freeFile,
		query: mcVersion ? { version: mcVersion } : null,
		destFile: temp,
		onProgress,
		onLog,
		label: 'free-сборка',
	})

	

	if (!result.skipped) {
		const target = path.join(targetDir, (result.fileName && result.fileName.endsWith('.jar')) ? result.fileName : 'lunacy-visuals-free.jar');
		fs.copyFileSync(result.file, target);
	}
	
	if (mcVersion) {
		const fapiUrl = await downloadFabricApi(mcVersion, targetDir, onLog)
		if (fapiUrl) {}
	}

	

	return { ...result, installed: true, dir: targetDir }
}

/** Обновление самого лаунчера — без авторизации и подписки. */
export async function downloadLauncher({ cacheDir, onProgress, onLog }) {
	const temp = path.join(cacheDir, 'lunacy-launcher-update.exe')
	onLog?.('Скачиваю обновление лаунчера…')
	return downloadArtifact({
		pathname: CONFIG.client.launcherFile,
		destFile: temp,
		onProgress,
		onLog,
		label: 'лаунчер',
	})
}
