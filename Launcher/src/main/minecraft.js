import fs from 'node:fs'
import fsp from 'node:fs/promises'
import path from 'node:path'
import crypto from 'node:crypto'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import { spawn } from 'node:child_process'
import { extractZip } from './unzip.js'

/**
 * Установщик Minecraft: Vanilla, Fabric и Forge.
 * Метаданные берутся с официальных источников Mojang / FabricMC / Forge.
 */

export const META = {
	vanillaManifest: 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',
	assets: 'https://resources.download.minecraft.net',
	fabricGame: 'https://meta.fabricmc.net/v2/versions/game',
	fabricLoader: 'https://meta.fabricmc.net/v2/versions/loader',
	forgeMeta:
		'https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml',
	forgeInstaller: 'https://maven.minecraftforge.net/net/minecraftforge/forge',
}

const osName =
	process.platform === 'win32'
		? 'windows'
		: process.platform === 'darwin'
			? 'osx'
			: 'linux'
const osArch = process.arch === 'ia32' ? 'x86' : process.arch === 'arm64' ? 'arm64' : 'x64'

/* -------------------------------------------------------------- утилиты */

async function getJson(url, timeoutMs = 20000) {
	const controller = new AbortController()
	const timer = setTimeout(() => controller.abort(), timeoutMs)
	try {
		const response = await fetch(url, {
			signal: controller.signal,
			headers: { Accept: 'application/json', 'User-Agent': 'LunacyLauncher/1.0' },
		})
		if (!response.ok) throw new Error(`${url} — HTTP ${response.status}`)
		return await response.json()
	} finally {
		clearTimeout(timer)
	}
}

async function getText(url, timeoutMs = 20000) {
	const controller = new AbortController()
	const timer = setTimeout(() => controller.abort(), timeoutMs)
	try {
		const response = await fetch(url, {
			signal: controller.signal,
			headers: { 'User-Agent': 'LunacyLauncher/1.0' },
		})
		if (!response.ok) throw new Error(`${url} — HTTP ${response.status}`)
		return await response.text()
	} finally {
		clearTimeout(timer)
	}
}

function sha1File(file) {
	return new Promise((resolve, reject) => {
		const hash = crypto.createHash('sha1')
		const stream = fs.createReadStream(file)
		stream.on('error', reject)
		stream.on('data', (chunk) => hash.update(chunk))
		stream.on('end', () => resolve(hash.digest('hex')))
	})
}

async function fileOk(file, sha1, size) {
	try {
		const stat = await fsp.stat(file)
		if (!stat.isFile() || stat.size === 0) return false
		if (size && stat.size !== size) return false
		if (sha1) return (await sha1File(file)) === sha1
		return true
	} catch {
		return false
	}
}

async function downloadFile(url, dest, { sha1, size, timeoutMs = 120000 } = {}) {
	if (await fileOk(dest, sha1, size)) return { skipped: true, dest }
	await fsp.mkdir(path.dirname(dest), { recursive: true })
	const controller = new AbortController()
	const timer = setTimeout(() => controller.abort(), timeoutMs)
	try {
		const response = await fetch(url, {
			signal: controller.signal,
			headers: { 'User-Agent': 'LunacyLauncher/1.0' },
		})
		if (!response.ok) throw new Error(`${url} — HTTP ${response.status}`)
		const tmp = `${dest}.part`
		await pipeline(Readable.fromWeb(response.body), fs.createWriteStream(tmp))
		if (sha1 || size) {
			const valid = await fileOk(tmp, sha1, size)
			if (!valid) {
				await fsp.rm(tmp, { force: true }).catch(() => {})
				throw new Error(`Хеш файла не совпадает (${path.basename(dest)})`)
			}
		}
		await fsp.rm(dest, { force: true })
		await fsp.rename(tmp, dest)
		return { skipped: false, dest }
	} finally {
		clearTimeout(timer)
	}
}

/** Скачивание с перебором зеркал. */
async function downloadWithMirrors(task) {
	const urls = (task.urls && task.urls.length ? task.urls : [task.url]).filter(Boolean)
	if (!urls.length) {
		if (fs.existsSync(task.dest)) return { skipped: true, dest: task.dest }
		throw new Error('нет ссылки для скачивания')
	}
	let lastError = null
	for (const url of urls) {
		try {
			return await downloadFile(url, task.dest, task)
		} catch (error) {
			lastError = error
		}
	}
	throw lastError || new Error('не удалось скачать')
}

/** Параллельная загрузка с ограничением потоков. */
async function downloadAll(tasks, { concurrency = 12, onTick } = {}) {
	let index = 0
	let done = 0
	const errors = []
	const workers = Array.from({ length: Math.min(concurrency, tasks.length || 1) }, async () => {
		while (index < tasks.length) {
			const task = tasks[index++]
			try {
				await downloadWithMirrors(task)
			} catch (error) {
				// optional — файл создаёт установщик Forge локально, ссылки на него нет
				if (!task.optional || !fs.existsSync(task.dest)) {
					errors.push(`${task.name || path.basename(task.dest)}: ${error.message}`)
				}
			}
			done += 1
			onTick?.(done, tasks.length)
		}
	})
	await Promise.all(workers)
	return errors
}

/* ------------------------------------------------------------- списки версий */

let vanillaCache = null
const OFFLINE_MC_VERSIONS = ['26.2', '26.1.2', '1.21.11', '1.21.10', '1.21.9', '1.21.8', '1.21.7', '1.21.6', '1.21.5', '1.21.4', '1.21.3', '1.21.2', '1.21.1', '1.21', '1.20.6', '1.20.4', '1.20.1']
const OFFLINE_FABRIC_LOADERS = ['0.19.5', '0.18.4', '0.17.3', '0.16.14']

export async function vanillaManifest() {
	if (vanillaCache && Date.now() - vanillaCache.at < 10 * 60 * 1000) return vanillaCache.data
	const data = await getJson(META.vanillaManifest)
	vanillaCache = { at: Date.now(), data }
	return data
}

export async function listVanillaVersions({ includeSnapshots = false } = {}) {
	try {
		const manifest = await vanillaManifest()
		return manifest.versions
			.filter((item) => includeSnapshots || item.type === 'release')
			.map((item) => ({ id: item.id, type: item.type, releaseTime: item.releaseTime, url: item.url }))
	} catch {
		return OFFLINE_MC_VERSIONS.map((id) => ({ id, type: 'release', releaseTime: null, offline: true }))
	}
}

export async function listFabricVersions() {
	try {
		const [games, loaders] = await Promise.all([
			getJson(META.fabricGame),
			getJson(`${META.fabricLoader}`),
		])
		return {
			games: games.map((item) => ({ id: item.version, stable: item.stable })),
			loaders: loaders.map((item) => ({ id: item.version, stable: item.stable })),
		}
	} catch {
		return {
			games: OFFLINE_MC_VERSIONS.map((id) => ({ id, stable: true, offline: true })),
			loaders: OFFLINE_FABRIC_LOADERS.map((id) => ({ id, stable: true, offline: true })),
		}
	}
}

export async function listForgeVersions(mcVersion) {
	try {
		const xml = await getText(META.forgeMeta)
		const all = [...xml.matchAll(/<version>([^<]+)<\/version>/g)].map((m) => m[1])
		const filtered = mcVersion ? all.filter((v) => v.startsWith(`${mcVersion}-`)) : all
		return filtered.reverse().map((id) => ({ id, mc: id.split('-')[0], forge: id.split('-')[1] }))
	} catch {
		return []
	}
}

/* ------------------------------------------------- служебные версии-зависимости */

/**
 * Vanilla-профиль нужен Fabric/Forge как основа (client.jar, ассеты, библиотеки),
 * но пользователь его отдельно не ставил. Помечаем такие версии файлом-меткой,
 * чтобы они не показывались в менеджере как отдельная установленная сборка.
 */
const DEPENDENCY_MARKER = '.lunacy-dependency'

export function isDependencyVersion(gameDir, id) {
	return fs.existsSync(path.join(gameDir, 'versions', id, DEPENDENCY_MARKER))
}

/** dependency=true — служебная основа, false — версия, установленная пользователем. */
export function markVersionSource(gameDir, id, dependency) {
	const marker = path.join(gameDir, 'versions', id, DEPENDENCY_MARKER)
	try {
		if (dependency) {
			// Профиль-основа может уже иметь JSON после установки Fabric,
			// поэтому метку ставим независимо от наличия этого JSON.
			if (!fs.existsSync(marker)) {
				fs.mkdirSync(path.dirname(marker), { recursive: true })
				fs.writeFileSync(marker, 'base for a loader profile\n', 'utf8')
			}
		} else {
			fs.rmSync(marker, { force: true })
		}
	} catch {
		/* метка не критична */
	}
}

/** Кто из установленных версий опирается на данную (Fabric/Forge → Vanilla). */
export function dependentsOf(gameDir, baseId) {
	return listInstalled(gameDir, { includeDependencies: true })
		.filter((item) => item.id !== baseId && item.mc === baseId)
		.map((item) => item.id)
}

/** Всё, что уже установлено в папке игры. */
export function listInstalled(gameDir, { includeDependencies = false } = {}) {
	const versionsDir = path.join(gameDir, 'versions')
	if (!fs.existsSync(versionsDir)) return []
	const result = []
	for (const entry of fs.readdirSync(versionsDir)) {
		const jsonFile = path.join(versionsDir, entry, `${entry}.json`)
		if (!fs.existsSync(jsonFile)) continue
		let dependency = isDependencyVersion(gameDir, entry)
		if (!dependency && entry.startsWith('fabric-loader-')) {
			try {
				for (const other of fs.readdirSync(versionsDir)) {
					if (/lunacy/i.test(other)) {
						const otherJson = path.join(versionsDir, other, `${other}.json`)
						if (fs.existsSync(otherJson)) {
							const oj = JSON.parse(fs.readFileSync(otherJson, 'utf8'))
							if (oj.inheritsFrom === entry) {
								dependency = true
								break
							}
						}
					}
				}
			} catch {}
		}
		if (dependency && !includeDependencies) continue
		try {
			const json = JSON.parse(fs.readFileSync(jsonFile, 'utf8'))
			const loader = /lunacy/i.test(entry)
				? 'LunacyVisual'
				: /fabric/i.test(entry)
					? 'Fabric'
					: /forge/i.test(entry)
						? 'Forge'
						: 'Vanilla'
			const mc = (json.inheritsFrom && !/lunacy/i.test(json.inheritsFrom))
				? (json.inheritsFrom.startsWith('fabric-loader-') ? json.inheritsFrom.split('-').pop() : json.inheritsFrom)
				: (json.mc || String(json.id || '').replace(/^LunacyVisual-/, ''))
			result.push({
				id: entry,
				loader,
				mc,
				title: loader === 'LunacyVisual' ? `LunacyVisual ${mc}` : `${loader} ${mc}`,
				type: json.type || 'release',
				javaMajor: json.javaVersion?.majorVersion || null,
				loaderVersion: json.id !== mc ? String(json.id || '').replace(/^fabric-loader-/, '') : null,
				dependency,
				dir: path.join(versionsDir, entry),
			})
		} catch {
			/* битый json — пропускаем */
		}
	}
	return result.sort((a, b) => a.id.localeCompare(b.id))
}

/** Удаление профиля версии из общей папки versions. */
export function removeVersion(gameDir, versionId) {
	const dir = path.join(gameDir, 'versions', versionId)
	if (!fs.existsSync(dir)) throw new Error(`Версия ${versionId} не найдена`)
	const used = dependentsOf(gameDir, versionId)
	if (used.length) {
		throw new Error(
			`Версия ${versionId} — основа для: ${used.join(', ')}. Сначала удали их.`,
		)
	}
	fs.rmSync(dir, { recursive: true, force: true })
	return { removed: versionId, dir }
}

/* ---------------------------------------------------------- чтение профилей */

function readVersionJson(gameDir, id) {
	const file = path.join(gameDir, 'versions', id, `${id}.json`)
	if (!fs.existsSync(file)) throw new Error(`Не найден профиль версии ${id}`)
	return JSON.parse(fs.readFileSync(file, 'utf8'))
}

/** Слияние профиля с родительским (inheritsFrom у Fabric/Forge). */
export function resolveVersion(gameDir, id, depth = 0) {
	if (depth > 5) throw new Error('Слишком глубокая цепочка inheritsFrom')
	const json = readVersionJson(gameDir, id)
	if (!json.inheritsFrom) return json
	const parent = resolveVersion(gameDir, json.inheritsFrom, depth + 1)
	return {
		...parent,
		...json,
		id: json.id,
		inheritsFrom: parent.inheritsFrom || parent.id || json.inheritsFrom,
		libraries: [...(json.libraries || []), ...(parent.libraries || [])],
		arguments: {
			game: [...(parent.arguments?.game || []), ...(json.arguments?.game || [])],
			jvm: [...(parent.arguments?.jvm || []), ...(json.arguments?.jvm || [])],
		},
		minecraftArguments: json.minecraftArguments || parent.minecraftArguments,
		assetIndex: json.assetIndex || parent.assetIndex,
		assets: json.assets || parent.assets,
		downloads: parent.downloads || json.downloads,
		javaVersion: json.javaVersion || parent.javaVersion,
		mainClass: json.mainClass || parent.mainClass,
	}
}

/* ------------------------------------------------------- правила библиотек */

export function rulesAllow(rules, features = {}) {
	if (!rules || !rules.length) return true
	let allowed = false
	for (const rule of rules) {
		let matches = true
		if (rule.os) {
			if (rule.os.name && rule.os.name !== osName) matches = false
			if (rule.os.arch && rule.os.arch !== osArch) matches = false
		}
		if (rule.features) {
			for (const [key, value] of Object.entries(rule.features)) {
				if (Boolean(features[key]) !== Boolean(value)) matches = false
			}
		}
		if (matches) allowed = rule.action === 'allow'
	}
	return allowed
}

function mavenToPath(name) {
	const [group, artifact, versionPart, classifier] = name.split(':')
	const version = versionPart
	const file = classifier
		? `${artifact}-${version}-${classifier}.jar`
		: `${artifact}-${version}.jar`
	return path.join(...group.split('.'), artifact, version, file)
}

function nativeClassifier(library) {
	const natives = library.natives
	if (!natives) return null
	const key = natives[osName]
	if (!key) return null
	return key.replace('${arch}', process.arch === 'ia32' ? '32' : '64')
}

/** Зеркала maven на случай, если в профиле нет прямой ссылки. */
export const MAVEN_MIRRORS = [
	'https://libraries.minecraft.net/',
	'https://maven.minecraftforge.net/',
	'https://maven.neoforged.net/releases/',
	'https://maven.fabricmc.net/',
	'https://maven.quiltmc.org/repository/release/',
	'https://repo1.maven.org/maven2/',
]

/** group:artifact[:classifier] — ключ для дедупликации версий одной и той же библиотеки. */
function libraryKey(name) {
	const parts = String(name || '').split(':')
	if (parts.length < 2) return null
	const classifier = parts[3]
	return `${parts[0]}:${parts[1]}${classifier ? `:${classifier}` : ''}`
}

/**
 * Список файлов библиотек: что качать и что в classpath.
 * Дубликаты (одна библиотека разных версий у Vanilla и Fabric/Forge) убираются —
 * побеждает первая, то есть версия загрузчика. Иначе игра падает с кодом 1.
 */
export function collectLibraries(version, gameDir) {
	const librariesDir = path.join(gameDir, 'libraries')
	const downloads = []
	const classpath = []
	const natives = []
	const seenKey = new Set()
	const seenFile = new Set()

	for (const library of version.libraries || []) {
		if (!rulesAllow(library.rules)) continue

		const classifier = nativeClassifier(library)
		const key = libraryKey(library.name)

		if (!library.natives && !(key && seenKey.has(key))) {
			if (key) seenKey.add(key)
			const artifact = library.downloads?.artifact
			const relative = artifact?.path || (library.name ? mavenToPath(library.name) : null)
			if (relative) {
				const dest = path.join(librariesDir, relative)
				const rel = relative.split(path.sep).join('/')
				if (!seenFile.has(dest)) {
					seenFile.add(dest)
					const urls = []
					if (artifact?.url) urls.push(artifact.url)
					if (library.url) urls.push(`${String(library.url).replace(/\/?$/, '/')}${rel}`)
					for (const base of MAVEN_MIRRORS) urls.push(base + rel)
					downloads.push({
						url: urls[0],
						urls,
						dest,
						sha1: artifact?.url ? artifact?.sha1 : null,
						size: artifact?.url ? artifact?.size : null,
						name: library.name || rel,
						// Forge кладёт часть библиотек локально (пустой url в профиле)
						optional: Boolean(artifact) && !artifact.url,
					})
					classpath.push(dest)
				}
			}
		}

		if (classifier) {
			const native = library.downloads?.classifiers?.[classifier]
			if (native?.path) {
				const dest = path.join(librariesDir, native.path)
				if (!seenFile.has(dest)) {
					seenFile.add(dest)
					downloads.push({
						url: native.url,
						urls: [native.url],
						dest,
						sha1: native.sha1,
						size: native.size,
						name: `${library.name}:${classifier}`,
					})
				}
				natives.push({ file: dest, exclude: library.extract?.exclude || [] })
			}
		} else if (/natives-(windows|linux|macos|osx)/.test(library.name || '')) {
			// Новый формат: нативы приходят обычной artifact-библиотекой
			const relative = library.downloads?.artifact?.path || (library.name ? mavenToPath(library.name) : null)
			if (relative) natives.push({ file: path.join(librariesDir, relative), exclude: [] })
		}
	}

	return { downloads, classpath, natives }
}

/* ---------------------------------------------------------------- установка */

async function installVanillaCore(mcVersion, gameDir, report, { dependency = false } = {}) {
	const versionDir = path.join(gameDir, 'versions', mcVersion)
	const jsonFile = path.join(versionDir, `${mcVersion}.json`)
	let entry = null
	try {
		const manifest = await vanillaManifest()
		entry = manifest.versions.find((item) => item.id === mcVersion) || null
	} catch (error) {
		if (!fs.existsSync(jsonFile)) {
			throw new Error(`Нет связи с Mojang и версия ${mcVersion} ещё не загружена: ${error.message}`)
		}
		report(`Mojang недоступен — использую локальный профиль ${mcVersion}`, 2)
	}
	if (!entry && !fs.existsSync(jsonFile)) {
		throw new Error(`Версии ${mcVersion} нет в официальном списке Mojang — проверь номер версии`)
	}

	// метку ставим до записи профиля: так видно, что базу тянет загрузчик, а не пользователь
	if (dependency) markVersionSource(gameDir, mcVersion, true)
	await fsp.mkdir(versionDir, { recursive: true })

	if (entry) {
		report(`Скачиваю профиль ${mcVersion}…`, 2)
		try {
			await downloadFile(entry.url, jsonFile, { sha1: entry.sha1 })
		} catch (error) {
			if (!fs.existsSync(jsonFile)) throw error
			report(`Сеть недоступна — продолжаю с локальным профилем ${mcVersion}`, 2)
		}
	}
	const version = JSON.parse(await fsp.readFile(jsonFile, 'utf8'))

	// client.jar
	const clientJar = path.join(versionDir, `${mcVersion}.jar`)
	if (version.downloads?.client) {
		report('Скачиваю client.jar…', 6)
		await downloadFile(version.downloads.client.url, clientJar, {
			sha1: version.downloads.client.sha1,
			size: version.downloads.client.size,
			timeoutMs: 300000,
		})
	}

	// библиотеки
	const { downloads } = collectLibraries(version, gameDir)
	report(`Библиотеки: ${downloads.length} файлов`, 10)
	const libErrors = await downloadAll(downloads, {
		onTick: (done, total) => report(`Библиотеки ${done}/${total}`, 10 + Math.round((done / total) * 30)),
	})

	// ассеты
	if (version.assetIndex?.url) {
		const indexFile = path.join(gameDir, 'assets', 'indexes', `${version.assetIndex.id}.json`)
		await downloadFile(version.assetIndex.url, indexFile, { sha1: version.assetIndex.sha1 })
		const index = JSON.parse(await fsp.readFile(indexFile, 'utf8'))
		const objects = Object.values(index.objects || {})
		const tasks = objects.map((object) => ({
			url: `${META.assets}/${object.hash.slice(0, 2)}/${object.hash}`,
			dest: path.join(gameDir, 'assets', 'objects', object.hash.slice(0, 2), object.hash),
			sha1: object.hash,
			size: object.size,
		}))
		report(`Ресурсы: ${tasks.length} файлов`, 42)
		const assetErrors = await downloadAll(tasks, {
			concurrency: 16,
			onTick: (done, total) =>
				report(`Ресурсы ${done}/${total}`, 42 + Math.round((done / total) * 45)),
		})
		if (assetErrors.length) report(`Не скачалось ресурсов: ${assetErrors.length}`, 87)
	}

	if (libErrors.length) {
		report(`Ошибки библиотек: ${libErrors.slice(0, 3).join('; ')}`, 88)
	}

	return { version, versionDir, clientJar }
}

/** Распаковка нативов в versions/<id>/natives. */
export function extractNatives(gameDir, versionId, natives) {
	const nativesDir = path.join(gameDir, 'versions', versionId, 'natives')
	fs.mkdirSync(nativesDir, { recursive: true })
	for (const native of natives) {
		if (!fs.existsSync(native.file)) continue
		try {
			extractZip(native.file, nativesDir, {
				onEntry: (name) =>
					!name.startsWith('META-INF/') &&
					!native.exclude.some((prefix) => name.startsWith(prefix)),
			})
		} catch {
			/* не все jar-ы распаковываются — не критично */
		}
	}
	return nativesDir
}

/** Vanilla */
export async function installVanilla(mcVersion, { gameDir, onProgress, onLog }) {
	const report = (line, percent) => {
		onLog?.(line)
		onProgress?.({ percent, label: line })
	}
	report(`Установка Minecraft ${mcVersion}`, 1)
	const { version } = await installVanillaCore(mcVersion, gameDir, report)
	// пользователь поставил ванилу явно — снимаем служебную метку
	markVersionSource(gameDir, mcVersion, false)
	const { natives } = collectLibraries(version, gameDir)
	extractNatives(gameDir, mcVersion, natives)
	report(`Minecraft ${mcVersion} готов`, 100)
	return { id: mcVersion, versionId: mcVersion, loader: 'Vanilla', mc: mcVersion }
}

/** Fabric — загрузчик визуалов Lunacy. */
export async function installFabric(mcVersion, { gameDir, loaderVersion, onProgress, onLog }) {
	const report = (line, percent) => {
		onLog?.(line)
		onProgress?.({ percent, label: line })
	}

	report(`Установка Fabric для ${mcVersion}`, 1)
	// Fabric запускается поверх файлов ваниллы (client.jar + ассеты + библиотеки),
	// поэтому база докачивается молча и остаётся служебной — отдельной сборкой
	// "Vanilla" в менеджере версий она не появляется.
	await installVanillaCore(
		mcVersion,
		gameDir,
		(line, percent) => report(line, Math.round((percent || 0) * 0.85)),
		{ dependency: true },
	)

	let loader = loaderVersion
	if (!loader) {
		try {
			const loaders = await getJson(`${META.fabricLoader}/${encodeURIComponent(mcVersion)}`)
			if (!loaders.length) throw new Error(`Fabric пока не поддерживает ${mcVersion}`)
			loader = (loaders.find((item) => item.loader?.stable) || loaders[0]).loader.version
		} catch (error) {
			const versionsDir = path.join(gameDir, 'versions')
			const suffix = `-${mcVersion}`
			const local = fs.existsSync(versionsDir)
				? fs.readdirSync(versionsDir).find((id) => id.startsWith('fabric-loader-') && id.endsWith(suffix))
				: null
			if (!local) throw new Error(`Нет связи с Fabric Meta и локальный Fabric для ${mcVersion} не найден: ${error.message}`)
			loader = local.slice('fabric-loader-'.length, -suffix.length)
			report(`Fabric Meta недоступен — использую локальный loader ${loader}`, 88)
		}
	}

	report(`Fabric loader ${loader}`, 88)
	const fallbackId = `fabric-loader-${loader}-${mcVersion}`
	const fallbackProfile = path.join(gameDir, 'versions', fallbackId, `${fallbackId}.json`)
	let profile
	try {
		profile = await getJson(
			`${META.fabricLoader}/${encodeURIComponent(mcVersion)}/${encodeURIComponent(loader)}/profile/json`,
		)
	} catch (error) {
		if (!fs.existsSync(fallbackProfile)) throw error
		profile = JSON.parse(await fsp.readFile(fallbackProfile, 'utf8'))
		report(`Использую локальный профиль Fabric ${loader}`, 89)
	}
	const id = profile.id || fallbackId
	const versionDir = path.join(gameDir, 'versions', id)
	await fsp.mkdir(versionDir, { recursive: true })
	await fsp.writeFile(path.join(versionDir, `${id}.json`), JSON.stringify(profile, null, 2), 'utf8')

	const merged = resolveVersion(gameDir, id)
	const { downloads, natives } = collectLibraries(merged, gameDir)
	report(`Библиотеки Fabric: ${downloads.length}`, 90)
	const errors = await downloadAll(downloads, {
		onTick: (done, total) => report(`Fabric ${done}/${total}`, 90 + Math.round((done / total) * 9)),
	})
	if (errors.length) {
		throw new Error(`Не скачались библиотеки Fabric: ${errors.slice(0, 3).join('; ')}`)
	}
	extractNatives(gameDir, mcVersion, natives)

	report(`Fabric ${loader} для ${mcVersion} готов`, 100)
	return { id, versionId: id, loader: 'Fabric', mc: mcVersion, loaderVersion: loader }
}

/** Forge — через официальный installer (требует Java). */
export async function installForge(forgeId, { gameDir, javaPath, onProgress, onLog }) {
	const report = (line, percent) => {
		onLog?.(line)
		onProgress?.({ percent, label: line })
	}
	const mcVersion = forgeId.split('-')[0]
	report(`Установка Forge ${forgeId}`, 1)
	await installVanillaCore(
		mcVersion,
		gameDir,
		(line, percent) => report(line, Math.round((percent || 0) * 0.7)),
		{ dependency: true },
	)

	const installerUrl = `${META.forgeInstaller}/${forgeId}/forge-${forgeId}-installer.jar`
	const installerFile = path.join(gameDir, 'cache', `forge-${forgeId}-installer.jar`)
	report('Скачиваю установщик Forge…', 72)
	await downloadFile(installerUrl, installerFile, { timeoutMs: 300000 })

	if (!javaPath) throw new Error('Для Forge нужна Java — выбери её в настройках')

	// Файл launcher_profiles.json обязателен для установщика Forge.
	const profilesFile = path.join(gameDir, 'launcher_profiles.json')
	if (!fs.existsSync(profilesFile)) {
		await fsp.writeFile(
			profilesFile,
			JSON.stringify({ profiles: {}, settings: {}, version: 3 }, null, 2),
			'utf8',
		)
	}

	report('Запускаю установщик Forge…', 80)
	await new Promise((resolve, reject) => {
		const child = spawn(javaPath, ['-jar', installerFile, '--installClient', gameDir], {
			cwd: gameDir,
			windowsHide: true,
		})
		child.stdout.on('data', (chunk) => onLog?.(`[forge] ${String(chunk).trim()}`))
		child.stderr.on('data', (chunk) => onLog?.(`[forge] ${String(chunk).trim()}`))
		child.on('error', reject)
		child.on('close', (code) =>
			code === 0 ? resolve() : reject(new Error(`Установщик Forge вернул код ${code}`)),
		)
	})

	const forgeBuild = forgeId.split('-').slice(1).join('-')
	const installedList = listInstalled(gameDir, { includeDependencies: true }).filter(
		(item) => item.loader === 'Forge',
	)
	const installed =
		installedList.find((item) => item.id.includes(forgeBuild) && item.id.includes(mcVersion)) ||
		installedList.find((item) => item.id.includes(forgeBuild)) ||
		installedList.find((item) => item.mc === mcVersion)
	if (!installed) {
		throw new Error(
			`Установщик Forge отработал, но профиль версии не появился. Проверь, что папка игры доступна на запись: ${gameDir}`,
		)
	}

	// Установщик не всегда докачивает все библиотеки — добираем сами, иначе игра падает с кодом 1.
	report('Проверяю библиотеки Forge…', 92)
	const merged = resolveVersion(gameDir, installed.id)
	const { downloads, natives } = collectLibraries(merged, gameDir)
	const errors = await downloadAll(downloads, {
		onTick: (done, total) => report(`Forge ${done}/${total}`, 92 + Math.round((done / total) * 7)),
	})
	if (errors.length) report(`Не скачалось библиотек: ${errors.length} (${errors[0]})`, 99)
	extractNatives(gameDir, merged.inheritsFrom || merged.id, natives)

	report(`Forge ${forgeId} готов — профиль ${installed.id}`, 100)
	return { ...installed, versionId: installed.id, loaderVersion: forgeBuild }
}

/* ------------------------------------------------------- проверка и починка */

/** Что именно не хватает для запуска версии. */
export function verifyVersion(gameDir, versionId) {
	const version = resolveVersion(gameDir, versionId)
	const { classpath } = collectLibraries(version, gameDir)
	const baseId = version.inheritsFrom || version.id
	const clientJar = path.join(gameDir, 'versions', baseId, `${baseId}.jar`)
	const missing = classpath.filter((file) => !fs.existsSync(file))
	const assetIndexId = version.assetIndex?.id || version.assets || null
	const assetIndexFile = assetIndexId
		? path.join(gameDir, 'assets', 'indexes', `${assetIndexId}.json`)
		: null
	const assetIndexOk = assetIndexFile ? fs.existsSync(assetIndexFile) : true
	const clientJarOk = fs.existsSync(clientJar)
	return {
		versionId: version.id,
		baseId,
		mainClass: version.mainClass || null,
		javaMajor: version.javaVersion?.majorVersion || null,
		libraries: classpath.length,
		missing: missing.map((file) => path.basename(file)),
		missingCount: missing.length,
		clientJarOk,
		assetIndexOk,
		ok: clientJarOk && assetIndexOk && missing.length === 0 && Boolean(version.mainClass),
	}
}

/** Повторно докачивает всё, чего не хватает, и заново распаковывает нативы. */
export async function repairVersion(versionId, { gameDir, onProgress, onLog } = {}) {
	const report = (line, percent) => {
		onLog?.(line)
		onProgress?.({ percent, label: line })
	}
	const version = resolveVersion(gameDir, versionId)
	const baseId = version.inheritsFrom || version.id
	report(`Проверяю ${versionId}…`, 2)
	await installVanillaCore(baseId, gameDir, (line, percent) => report(line, Math.round((percent || 0) * 0.8)))

	const merged = resolveVersion(gameDir, versionId)
	const { downloads, natives } = collectLibraries(merged, gameDir)
	report(`Библиотеки: ${downloads.length}`, 82)
	const errors = await downloadAll(downloads, {
		onTick: (done, total) => report(`Библиотеки ${done}/${total}`, 82 + Math.round((done / total) * 16)),
	})
	extractNatives(gameDir, baseId, natives)
	const state = verifyVersion(gameDir, versionId)
	report(
		state.ok
			? `Версия ${versionId} в порядке`
			: `Остались проблемы: нет ${state.missingCount} файлов`,
		100,
	)
	return { state, errors }
}

/* ----------------------------------------------------------- аргументы запуска */

function flattenArguments(list, features) {
	const result = []
	for (const item of list || []) {
		if (typeof item === 'string') {
			result.push(item)
		} else if (item && rulesAllow(item.rules, features)) {
			const value = item.value
			if (Array.isArray(value)) result.push(...value)
			else if (value) result.push(value)
		}
	}
	return result
}

function applyPlaceholders(args, map) {
	return args.map((arg) =>
		String(arg).replace(/\$\{([^}]+)\}/g, (match, key) => (key in map ? map[key] : match)),
	)
}

export const LUNACY_GUARD_SALT = 'LUNACY_SEC_AUTH_x8F2D_KERNEL_GUARD'

export function getLunacyGuardToken() {
	const window5Min = Math.floor(Date.now() / (1000 * 60 * 5))
	return crypto
		.createHash('sha256')
		.update(`${LUNACY_GUARD_SALT}:${window5Min}`)
		.digest('hex')
}

/**
 * Собирает команду запуска для любой установленной версии (Vanilla/Fabric/Forge).
 */
export function buildLaunchCommand({
	gameDir,
	instanceDir,
	versionId,
	account,
	settings,
	javaPath,
	strict = true,
}) {
	// gameDir — общие пакеты (versions/libraries/assets),
	// runDir — личная папка сборки (mods/resourcepacks/shaderpacks/saves).
	const runDir = instanceDir || gameDir
	const version = resolveVersion(gameDir, versionId)
	if (!version.mainClass) {
		throw new Error(`В профиле ${versionId} нет mainClass — переустанови версию`)
	}

	const { classpath, natives } = collectLibraries(version, gameDir)
	const baseId = version.inheritsFrom || version.id
	const clientJar = path.join(gameDir, 'versions', baseId, `${baseId}.jar`)
	if (fs.existsSync(clientJar)) classpath.push(clientJar)
	else if (strict) {
		throw new Error(`Нет файла игры ${baseId}.jar — нажми «Проверить и починить» во вкладке «Менеджер версий»`)
	}

	const existing = classpath.filter((file) => fs.existsSync(file))
	const missing = classpath.filter((file) => !fs.existsSync(file))
	if (strict && missing.length) {
		const names = missing.slice(0, 4).map((file) => path.basename(file)).join(', ')
		throw new Error(
			`Не хватает библиотек (${missing.length}): ${names}. Нажми «Проверить и починить» во вкладке «Менеджер версий»`,
		)
	}

	const nativesDir = extractNatives(gameDir, baseId, natives)
	const separator = process.platform === 'win32' ? ';' : ':'
	const assetsDir = path.join(gameDir, 'assets')
	const assetIndexName = version.assetIndex?.id || version.assets || 'legacy'

	const placeholders = {
		auth_player_name: account.nickname,
		version_name: version.id,
		game_directory: runDir,
		assets_root: assetsDir,
		game_assets: assetsDir,
		assets_index_name: assetIndexName,
		auth_uuid: account.uuid,
		auth_access_token: account.accessToken || '0',
		auth_session: account.accessToken || '0',
		clientid: account.clientId || '0',
		auth_xuid: account.xuid || '0',
		user_type: account.type === 'online' ? 'msa' : 'legacy',
		version_type: version.type || 'release',
		natives_directory: nativesDir,
		launcher_name: 'LunacyLauncher',
		launcher_version: '2.1.0',
		classpath: existing.join(separator),
		user_properties: '{}',
		resolution_width: String(settings.width || 1280),
		resolution_height: String(settings.height || 720),
		library_directory: path.join(gameDir, 'libraries'),
		classpath_separator: separator,
	}

	const features = {
		is_demo_user: false,
		has_custom_resolution: !settings.fullscreen,
		has_quick_plays_support: false,
		is_quick_play_singleplayer: false,
		is_quick_play_multiplayer: false,
		is_quick_play_realms: false,
	}

	let jvmFromProfile = version.arguments?.jvm?.length
		? applyPlaceholders(flattenArguments(version.arguments.jvm, features), placeholders)
		: []
	jvmFromProfile = jvmFromProfile.filter((arg) => arg !== '' && arg != null)

	// Гарантируем наличие classpath и пути к нативам — без них JVM падает сразу (код 1).
	if (!jvmFromProfile.includes('-cp') && !jvmFromProfile.includes('-classpath')) {
		jvmFromProfile.push('-cp', placeholders.classpath)
	}
	if (!jvmFromProfile.some((arg) => String(arg).startsWith('-Djava.library.path='))) {
		jvmFromProfile.unshift(`-Djava.library.path=${nativesDir}`)
	}

	const maxMem = Math.max(2, Number(settings.memoryMax) || 4)
	const minMem = Math.min(maxMem, Math.max(1, Number(settings.memoryMin) || 1))
	const memory = [
		`-Xms${minMem}G`,
		`-Xmx${maxMem}G`,
	]
	const guardToken = getLunacyGuardToken()
	const common = [
		'-Dlog4j2.formatMsgNoLookups=true',
		'-Dminecraft.launcher.brand=LunacyLauncher',
		'-Dminecraft.launcher.version=2.1.0',
		'-Dfile.encoding=UTF-8',
		`-Dlunacy.guard.token=${guardToken}`,
	]
	let extraJvm = String(settings.jvmArgs || '')
		.split(/\s+/)
		.filter(Boolean)

	if (!extraJvm.length && settings.jvmPreset) {
		const preset = String(settings.jvmPreset).toLowerCase()
		if (preset === 'aikar') {
			extraJvm = [
				'-XX:+UseG1GC',
				'-XX:+ParallelRefProcEnabled',
				'-XX:MaxGCPauseMillis=200',
				'-XX:+UnlockExperimentalVMOptions',
				'-XX:+DisableExplicitGC',
				'-XX:+AlwaysPreTouch',
				'-XX:G1NewSizePercent=30',
				'-XX:G1MaxNewSizePercent=40',
				'-XX:G1ReservePercent=20',
				'-XX:G1HeapWastePercent=5',
				'-XX:G1MixedGCCountTarget=4',
				'-XX:InitiatingHeapOccupancyPercent=15',
				'-XX:G1MixedGCLiveThresholdPercent=90',
				'-XX:G1RSetUpdatingPauseTimePercent=5',
				'-XX:SurvivorRatio=32',
				'-XX:+PerfDisableSharedMem',
				'-XX:MaxTenuringThreshold=1',
			]
		} else if (preset === 'zgc') {
			extraJvm = ['-XX:+UseZGC', '-XX:+UnlockExperimentalVMOptions']
		} else if (preset === 'shenandoah') {
			extraJvm = ['-XX:+UseShenandoahGC', '-XX:+UnlockExperimentalVMOptions']
		} else if (preset === 'g1gc') {
			extraJvm = ['-XX:+UseG1GC', '-XX:+UnlockExperimentalVMOptions']
		}
	}

	const gameArgs = version.arguments?.game?.length
		? applyPlaceholders(flattenArguments(version.arguments.game, features), placeholders)
		: applyPlaceholders(
				String(version.minecraftArguments || '')
					.split(/\s+/)
					.filter(Boolean),
				placeholders,
			)

	if (!settings.fullscreen) {
		if (!gameArgs.includes('--width')) gameArgs.push('--width', String(settings.width || 1280))
		if (!gameArgs.includes('--height')) gameArgs.push('--height', String(settings.height || 720))
	} else if (!gameArgs.includes('--fullscreen')) {
		gameArgs.push('--fullscreen')
	}

	if (settings.serverAutoConnect && settings.serverAutoConnect.trim()) {
		const parts = settings.serverAutoConnect.trim().split(':')
		if (parts[0] && !gameArgs.includes('--server')) {
			gameArgs.push('--server', parts[0])
			if (parts[1] && !gameArgs.includes('--port')) {
				gameArgs.push('--port', parts[1])
			}
		}
	}

	const args = [
		...memory,
		...common,
		...extraJvm,
		...jvmFromProfile,
		version.mainClass,
		...gameArgs.filter((arg) => arg !== '' && arg != null),
	]

	return {
		java: javaPath || 'java',
		args,
		cwd: runDir,
		instanceDir: runDir,
		mainClass: version.mainClass,
		javaMajor: version.javaVersion?.majorVersion || null,
		versionId: version.id,
		baseId,
		classpathCount: existing.length,
		missing: missing.map((file) => path.basename(file)),
		nativesDir,
		assetIndexName,
	}
}
