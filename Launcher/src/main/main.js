import { app, BrowserWindow, ipcMain, dialog, shell } from 'electron'
import path from 'node:path'
import fs from 'node:fs'
import fsp from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

import { APP_NAME, CONFIG } from './config.js'
import { ApiError, ping, apiRequest } from './http.js'
import { JsonStore, defaultSettings, offlineUuid } from './store.js'
import { LunacyAuth, planLabel } from './lunacy-auth.js'
import { downloadMod, downloadFreeBuild, downloadLauncher } from './downloads.js'
import {
	checkLauncherVersion,
	checkModVersion,
	fetchClientInfo,
	installLauncherUpdate,
} from './updater.js'
import { readZipJson } from './unzip.js'
import { launchGame, ensureGameDirs, javaMajorOf } from './game.js'
import {
	detectGpus,
	detectJavaInstalls,
	memoryInfo,
	pickJavaFor,
	systemInfo,
} from './system.js'
import {
	installVanilla,
	installFabric,
	installForge,
	listFabricVersions,
	listForgeVersions,
	listInstalled,
	listVanillaVersions,
	markVersionSource,
	removeVersion,
	repairVersion,
	resolveVersion,
	verifyVersion,
} from './minecraft.js'
import { InstanceManager, ensureInstanceDirs, suggestName } from './instances.js'
import {
	CATALOG_KINDS,
	CATALOG_CATEGORIES,
	downloadCatalogItem,
	fetchProjectDetails,
	importContentFile,
	listInstalledContent,
	listProviders,
	removeContentFile,
	searchCatalog,
	toggleModEnabled,
} from './catalog.js'
import { discordRpc } from './discord.js'
import { pingServer } from './server-ping.js'
import {
	MS_LINK_URL,
	loginWithMsaToken,
	pollDeviceToken,
	refreshGameAccount,
	startDeviceCode,
	tokenAlive,
} from './msauth.js'

const launcherStartTime = Date.now()


const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '..', '..')

/* ------------------------------------------------------------------ состояние */

let win = null
let child = null
let instancesStore = null
let instances = null
let settings = null
let accounts = null
let auth = null
let cacheDir = ''
let javaCache = []
let gpuCache = []
let clientInfoCache = null
let pendingUpdate = null // { file, version }
let msFlow = null // активный вход по лицензии Microsoft
let logDir = ''
let logFile = ''
let discordStatusKey = ''
const logBuffer = [] // кольцевой буфер для вкладки «Консоль»
const LOG_LIMIT = 3000

const FALLBACK_MC_VERSIONS = ['1.21.11', '26.1.2', '26.2']

/* ------------------------------------------------------------------ хелперы */

function send(channel, payload) {
	if (win && !win.isDestroyed()) win.webContents.send(channel, payload)
}

function log(line) {
	const entry = { time: new Date().toISOString(), line: String(line) }
	logBuffer.push(entry)
	if (logBuffer.length > LOG_LIMIT) logBuffer.shift()
	if (logFile) {
		try {
			fs.appendFileSync(logFile, `[${entry.time}] ${entry.line}\n`, 'utf8')
		} catch {
			/* файл лога не критичен */
		}
	}
	// чтобы `npm run start:debug` показывал всё в терминале
	if (process.env.LUNACY_DEBUG === '1') console.log(`[lunacy] ${entry.line}`)
	send('launcher:log', entry)
}

function progress(target, data) {
	send('download:progress', { target, ...data })
}

function fail(error) {
	if (error instanceof ApiError) {
		return {
			ok: false,
			error: error.message,
			code: error.code || null,
			status: error.status || null,
			fields: error.fields || null,
			retryAfter: error.retryAfter || null,
			buyUrl: error.buyUrl || null,
			needsLogin: error.status === 401,
			needsSubscription: Boolean(error.isSubscriptionError),
		}
	}
	return { ok: false, error: error?.message || 'Неизвестная ошибка' }
}

function handle(channel, fn) {
	ipcMain.handle(channel, async (_event, payload) => {
		try {
			const result = await fn(payload)
			return result && result.ok === false ? result : { ok: true, ...(result || {}) }
		} catch (error) {
			log(`Ошибка [${channel}]: ${error?.message || error}`)
			return fail(error)
		}
	})
}

/** Гейт по подписке: без подписки доступна только FREE-сборка. */
function requireSubscription() {
	if (!auth.isAuthorized) {
		throw new ApiError('Войди в аккаунт', { status: 401 })
	}
	if (auth.session?.subscription?.isFrozen) {
		throw new ApiError('Ваша подписка заморожена. Зайдите в личный кабинет, чтобы разморозить её перед игрой с визуалами.', {
			status: 403,
			buyUrl: CONFIG.web.buy,
		})
	}
	if (!auth.hasSubscription) {
		throw new ApiError('Нужна подписка — доступна только FREE-сборка', {
			status: 403,
			buyUrl: CONFIG.web.buy,
		})
	}
	return auth.token
}

/** Проверка, является ли файл мода платным Lunacy Visuals */
function isPaidLunacyMod(filePath) {
	try {
		if (!fs.existsSync(filePath)) return false
		const filename = path.basename(filePath)
		let lower = filename.toLowerCase()
		if (lower.endsWith('.disabled')) lower = lower.slice(0, -9)
		if (!lower.endsWith('.jar')) return false

		// Сторонние и системные библиотеки не трогаем
		if (
			lower.startsWith('fabric-api') ||
			lower.startsWith('fabric-loader') ||
			lower.includes('sodium') ||
			lower.includes('iris') ||
			lower.includes('indium') ||
			lower.includes('lithium')
		) {
			return false
		}

		// Если имя содержит free — это точно бесплатная версия
		if (lower.includes('free')) return false

		// Если имя файла явно указывает на платный Lunacy
		if (lower.includes('lunacy') || lower.includes('visual')) {
			return true
		}

		// Проверка содержимого fabric.mod.json на случай переименования
		const modJson = readZipJson(filePath, 'fabric.mod.json')
		if (modJson && (modJson.id === 'lunacyvisuals' || modJson.id === 'lunacyvisual')) {
			const isFree =
				String(modJson.version || '').toLowerCase().includes('free') ||
				String(modJson.name || '').toLowerCase().includes('free')
			return !isFree
		}
	} catch {}
	return false
}

/** Проверка, является ли файл мода бесплатным Lunacy Visuals */
function isFreeLunacyMod(filePath) {
	try {
		if (!fs.existsSync(filePath)) return false
		const filename = path.basename(filePath)
		let lower = filename.toLowerCase()
		if (lower.endsWith('.disabled')) lower = lower.slice(0, -9)
		if (!lower.endsWith('.jar')) return false

		if (lower.includes('free') && (lower.includes('lunacy') || lower.includes('visual'))) {
			return true
		}

		const modJson = readZipJson(filePath, 'fabric.mod.json')
		if (modJson && (modJson.id === 'lunacyvisuals' || modJson.id === 'lunacyvisual')) {
			return (
				String(modJson.version || '').toLowerCase().includes('free') ||
				String(modJson.name || '').toLowerCase().includes('free')
			)
		}
	} catch {}
	return false
}

/** Возвращает версию реально установленного платного визуала в инстансе. */
function installedLunacyModVersion(instanceDir) {
	try {
		if (!instanceDir) return null
		const modsDir = path.join(instanceDir, 'mods')
		if (!fs.existsSync(modsDir)) return null
		for (const file of fs.readdirSync(modsDir)) {
			if (!file.toLowerCase().endsWith('.jar') || isFreeLunacyMod(path.join(modsDir, file))) continue
			const fullPath = path.join(modsDir, file)
			const modJson = readZipJson(fullPath, 'fabric.mod.json')
			if (modJson && (modJson.id === 'lunacyvisuals' || modJson.id === 'lunacyvisual')) {
				const version = String(modJson.version || '').trim()
				if (version && !version.toLowerCase().includes('free')) return version
			}
		}
	} catch {}
	return null
}

/**
 * Проверка лицензии: если у пользователя нет лицензии (подписки):
 * 1) Ищет во всех папках сборок (instances) и корневой папке игры любые jar-файлы платного мода и удаляет их.
 * 2) Удаляет любые сборки Lunacy для версий кроме 1.21.11 (26.1.2, 26.2 и т.д.),
 *    так как бесплатная версия существует ТОЛЬКО для 1.21.11.
 * 3) Удаляет профили версий LunacyVisual-26.* из versions/
 * 4) Очищает настройки settings.json: сбрасывает modMcVersion на 1.21.11, modVersion на 1.1.0-free,
 *    а если были выбраны версии 26.x — переключает на оставшуюся 1.21.11.
 * 5) Удаляет дубликаты сборок Fabric (например, Fabric 26.2), созданные загрузчиком.
 */
function cleanupPaidModsIfNoSubscription(specificDir = null) {
	if (auth?.hasSubscription) return false
	let removedAny = false
	const gameDir = settings?.get('gameDir')
	if (!gameDir || !fs.existsSync(gameDir)) return false

	const instancesRootDir = path.join(gameDir, 'instances')

	// 1. Проверяем все папки mods: root + все инстансы на диске
	const dirsToCheck = new Set()
	if (specificDir) dirsToCheck.add(path.join(specificDir, 'mods'))
	dirsToCheck.add(path.join(gameDir, 'mods'))
	if (fs.existsSync(instancesRootDir)) {
		try {
			for (const d of fs.readdirSync(instancesRootDir)) {
				const candidate = path.join(instancesRootDir, d, 'mods')
				if (fs.existsSync(candidate)) dirsToCheck.add(candidate)
			}
		} catch {}
	}
	if (instances?.items) {
		for (const it of instances.items) {
			const resDir = instances.resolveDir(it)
			if (resDir) dirsToCheck.add(path.join(resDir, 'mods'))
		}
	}

	for (const dir of dirsToCheck) {
		if (!fs.existsSync(dir)) continue
		try {
			const files = fs.readdirSync(dir)
			for (const f of files) {
				const full = path.join(dir, f)
				if (isPaidLunacyMod(full)) {
					log(`[Безопасность] Обнаружен платный мод «${f}» без активной лицензии. Сношу...`)
					try {
						fs.unlinkSync(full)
						removedAny = true
						log(`[Безопасность] Платный мод «${f}» успешно удалён.`)
					} catch (e) {
						log(`[Безопасность] Ошибка при удалении ${f}: ${e.message}`)
					}
				}
			}
		} catch {}
	}

	// 2. Сносим инстансы Lunacy для версий MC, отличных от 1.21.11 (например, 26.1.2, 26.2),
	// а также любые дубликаты Fabric 26.x
	if (instances?.items) {
		const toRemove = instances.items.filter((it) => {
			const nameLow = (it.name || '').toLowerCase()
			const isLun = Boolean(it.isLunacy || nameLow.includes('lunacy'))
			const isDuplicateFabric = nameLow.startsWith('fabric ') && !it.isLunacy
			if (isLun && it.mc && it.mc !== '1.21.11') return true
			if (isDuplicateFabric && it.mc && it.mc !== '1.21.11') return true
			if (it.versionId && (it.versionId.includes('26.1.2') || it.versionId.includes('26.2'))) return true
			return false
		})
		for (const inst of toRemove) {
			log(`[Безопасность] Удаляю сборку «${inst.name}» (без подписки доступна только 1.21.11)...`)
			try {
				instances.remove(inst.id, { deleteFiles: true })
				removedAny = true
			} catch {}
		}
	}

	// 3. Удаляем профили версий LunacyVisual-26.* из versions/
	const versionsDir = path.join(gameDir, 'versions')
	if (fs.existsSync(versionsDir)) {
		try {
			for (const verName of fs.readdirSync(versionsDir)) {
				if (verName.toLowerCase().startsWith('lunacyvisual-') && !verName.includes('1.21.11')) {
					const targetDir = path.join(versionsDir, verName)
					try {
						fs.rmSync(targetDir, { recursive: true, force: true })
						log(`[Безопасность] Удалён профиль версии «${verName}».`)
					} catch {}
				}
			}
		} catch {}
	}

	// 4. Очищаем настройки (settings.json)
	const currentModMc = settings.get('modMcVersion')
	const currentSel = settings.get('selectedVersionId')
	const currentLunId = settings.get('lunacyVersionId')

	const patch = {}
	if (currentModMc && currentModMc !== '1.21.11') {
		patch.modMcVersion = '1.21.11'
	}
	if (currentSel && (currentSel.includes('26.') || currentSel.includes('26_') || currentSel.includes('26.1') || currentSel.includes('26.2'))) {
		const freeInst = instances?.items?.find((it) => it.mc === '1.21.11')
		patch.selectedVersionId = freeInst?.versionId || 'fabric-loader-0.19.5-1.21.11'
		patch.activeInstanceId = freeInst?.id || null
		patch.contentTargetId = freeInst?.id || null
	}
	if (currentLunId && (currentLunId.includes('26.') || currentLunId.includes('26_') || currentLunId.includes('26.1') || currentLunId.includes('26.2'))) {
		patch.lunacyVersionId = 'LunacyVisual-1.21.11'
	}
	if (settings.get('modVersion') !== '1.1.0-free') {
		patch.modVersion = '1.1.0-free'
	}
	if (Object.keys(patch).length > 0) {
		settings.set(patch)
	}

	return removedAny
}

function accountList() {
	const list = accounts.get('items', [])
	const activeId = settings.get('activeAccountId')
	// Токены наружу не отдаём — только то, что нужно интерфейсу.
	return list.map((item) => ({
		id: item.id,
		nickname: item.nickname,
		type: item.type === 'online' ? 'online' : 'offline',
		auth: item.auth || 'offline',
		uuid: item.uuid,
		addedAt: item.addedAt,
		expiresAt: item.expiresAt || null,
		licensed: item.auth === 'microsoft',
		active: item.id === activeId,
	}))
}

/** Версия Minecraft сборки: поле mc, иначе достаём из id профиля. */
function instanceMcVersion(instance) {
	if (!instance) return null
	if (instance.mc) return String(instance.mc)
	const match = String(instance.versionId || '').match(/(\d+\.\d+(?:\.\d+)?)\s*$/)
	return match ? match[1] : null
}

/** Ключ загрузчика сборки для мастерской: fabric / forge / neoforge / quilt. */
function instanceLoaderKey(instance) {
	const key = String(instance?.loader || '').toLowerCase()
	if (key.includes('fabric')) return 'fabric'
	if (key.includes('quilt')) return 'quilt'
	if (key.includes('neoforge')) return 'neoforge'
	if (key.includes('forge')) return 'forge'
	return null
}

/* ------------------------------------------------------------------ сборки */

/** Список сборок + автоподхват версий, установленных раньше. */
function instanceList() {
	try {
		return instances.sync(listInstalled(settings.get('gameDir')))
	} catch {
		return instances?.list() || []
	}
}

/**
 * Куда ставить контент: явно выбранная сборка → сохранённая цель →
 * активная сборка → сборка выбранной версии → первая в списке.
 */
function resolveInstance(instanceId) {
	const list = instanceList()
	if (!list.length) return null
	const byId = (id) => (id ? list.find((item) => item.id === id) : null)
	return (
		byId(instanceId) ||
		byId(settings.get('contentTargetId')) ||
		byId(settings.get('activeInstanceId')) ||
		list.find((item) => item.versionId === settings.get('selectedVersionId')) ||
		list[0]
	)
}

/** Личная папка версии для запуска игры. */
function instanceDirFor(versionId) {
	const gameDir = settings.get('gameDir')
	const version = listInstalled(gameDir, { includeDependencies: true }).find(
		(item) => item.id === versionId,
	)
	if (!version) return null
	const isLunacy = Boolean(
		version.loader === 'LunacyVisual' ||
		versionId.toLowerCase().includes('lunacy') ||
		settings.get('lunacyVersionId') === versionId
	)
	const instance = instances.ensureFor({
		versionId,
		loader: version.loader,
		mc: version.mc,
		isLunacy,
	})
	instances.touch(instance.id)
	return instances.resolveDir(instance)
}

/** Перед запуском продлеваем токен лицензии, если он протух. */
async function ensureAccountToken(account) {
	if (!account || account.auth !== 'microsoft') return account
	if (tokenAlive(account)) return account
	const items = accounts.get('items', [])
	const stored = items.find((item) => item.id === account.id) || account
	const fresh = await refreshGameAccount(stored, { onLog: log })
	accounts.set({ items: items.map((item) => (item.id === fresh.id ? fresh : item)) })
	return fresh
}

function activeAccount() {
	const list = accounts.get('items', [])
	if (!list.length) return null
	const found = list.find((item) => item.id === settings.get('activeAccountId'))
	const account = found || list[0]
	return {
		...account,
		uuid: account.uuid || offlineUuid(account.nickname),
		accessToken: account.type === 'online' ? account.accessToken || '0' : '0',
	}
}

async function ensureJava() {
	if (!javaCache.length) javaCache = await detectJavaInstalls()
	return javaCache
}

async function ensureGpus() {
	if (!gpuCache.length) gpuCache = await detectGpus()
	return gpuCache
}

function selectedGpu() {
	const id = settings.get('gpuId')
	if (!id) return gpuCache.find((item) => item.primary) || null
	return gpuCache.find((item) => item.id === id) || null
}

async function javaForVersion(versionId) {
	let required = 17
	try {
		const version = resolveVersion(settings.get('gameDir'), versionId)
		required = version?.javaVersion?.majorVersion || 17
	} catch {
		/* версия ещё не установлена */
	}

	const manual = settings.get('javaPath')
	if (manual && fs.existsSync(manual)) {
		const detected = javaMajorOf(manual)
		if (!detected.major || detected.major >= required) return manual
		log(
			`Выбранная Java ${detected.major} не подходит для ${versionId} (нужна ${required}) — ищу другую`,
		)
	}

	const list = await ensureJava()
	const picked = pickJavaFor(list, required)
	if (picked?.path) return picked.path
	if (manual && fs.existsSync(manual)) return manual
	return process.platform === 'win32' ? 'java.exe' : 'java'
}

/* ------------------------------------------------------------------ окно */

function createWindow() {
	win = new BrowserWindow({
		width: 1180,
		height: 720,
		minWidth: 1020,
		minHeight: 640,
		show: false,
		frame: false,
		backgroundColor: '#0a0e0b',
		title: APP_NAME,
		icon: path.join(ROOT, 'assets', 'icon.ico'),
		webPreferences: {
			preload: path.join(ROOT, 'src', 'preload.cjs'),
			contextIsolation: true,
			nodeIntegration: false,
			sandbox: false,
		},
	})

	win.loadFile(path.join(ROOT, 'src', 'renderer', 'index.html'))
	win.once('ready-to-show', () => win.show())
	win.on('closed', () => {
		win = null
	})

	win.webContents.setWindowOpenHandler(({ url }) => {
		shell.openExternal(url)
		return { action: 'deny' }
	})
}

/* ------------------------------------------------------------------ Discord Presence */

function updateDiscordPresence(view = 'play') {
	if (child) return // если запущена игра, не перебиваем статус
	let state = 'В лаунчере • Кастомные визуалы'
	const details = 'Lunacy Launcher 2.1 • lunacyvisual.fun'

	if (view === 'play') {
		state = 'Готов к запуску • Визуалы Lunacy'
	} else if (view === 'workshop') {
		state = 'В каталоге модов, шейдеров и визуалов'
	} else if (view === 'versions') {
		state = 'Настройка сборок LunacyVisual'
	} else if (view === 'accounts') {
		state = 'Управление аккаунтами и лицензией'
	} else if (view === 'screenshots') {
		state = 'Галерея снимков с визуалами'
	} else if (view === 'settings') {
		state = 'Оптимизация графики и FPS Boost'
	} else if (view === 'console') {
		state = 'Консоль отладки и мониторинг'
	}

	try {
		discordRpc.setActivity({
			state,
			details,
			startTimestamp: launcherStartTime,
			largeImage: 'lunacy_logo',
			largeText: 'Lunacy Visuals 2.1 • lunacyvisual.fun',
			smallImage: 'lunacy_logo',
			smallText: 'Визуалы активны • lunacyvisual.fun',
			buttons: [
				{ label: '🌐 Перейти на сайт', url: 'https://lunacyvisual.fun' },
				{ label: '✨ Скачать визуалы', url: 'https://lunacyvisual.fun' },
			],
		})
	} catch {}
}

/* ------------------------------------------------------------------ IPC */

function registerIpc() {
	/* окно */
	ipcMain.handle('window:minimize', () => win?.minimize())
	ipcMain.handle('window:maximize', () =>
		win?.isMaximized() ? win.unmaximize() : win?.maximize(),
	)
	ipcMain.handle('window:close', () => win?.close())

	/* общее */
	handle('app:info', async () => ({
		name: APP_NAME,
		version: app.getVersion(),
		baseUrl: CONFIG.baseUrl,
		web: CONFIG.web,
		platform: process.platform,
		system: systemInfo(),
		memory: memoryInfo(),
	}))
	handle('app:ping', async () => ({ online: await ping() }))
	handle('app:openExternal', async (url) => {
		await shell.openExternal(String(url))
		return {}
	})
	handle('app:openPath', async (target) => {
		await shell.openPath(String(target || settings.get('gameDir')))
		return {}
	})
	handle('app:openFolder', async ({ folder = 'game', instanceId = null } = {}) => {
		const gameDir = settings.get('gameDir')
		let targetPath = gameDir
		if (instanceId) {
			const inst = instances.find(instanceId)
			if (inst) targetPath = instances.resolveDir(inst)
		} else {
			const activeId = settings.get('activeInstanceId') || settings.get('contentTargetId')
			const inst = activeId ? instances.find(activeId) : null
			if (inst) targetPath = instances.resolveDir(inst)
		}

		if (folder === 'mods') targetPath = path.join(targetPath, 'mods')
		else if (folder === 'screenshots') targetPath = path.join(targetPath, 'screenshots')
		else if (folder === 'resourcepacks') targetPath = path.join(targetPath, 'resourcepacks')
		else if (folder === 'shaderpacks') targetPath = path.join(targetPath, 'shaderpacks')
		else if (folder === 'logs') targetPath = path.join(gameDir, 'logs')
		else if (folder === 'instances') targetPath = path.join(gameDir, 'instances')

		fs.mkdirSync(targetPath, { recursive: true })
		await shell.openPath(targetPath)
		return { path: targetPath }
	})

	handle('storage:clean', async ({ target = 'all' } = {}) => {
		let freed = 0
		const gameDir = settings.get('gameDir')

		if (target === 'all' || target === 'cache') {
			if (fs.existsSync(cacheDir)) {
				try {
					for (const f of fs.readdirSync(cacheDir)) {
						const fp = path.join(cacheDir, f)
						try {
							const s = fs.statSync(fp).size
							fs.rmSync(fp, { recursive: true, force: true })
							freed += s
						} catch {}
					}
				} catch {}
			}
			try {
				const scanParts = (dir) => {
					for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
						const p = path.join(dir, item.name)
						if (item.isDirectory() && !['saves'].includes(item.name)) scanParts(p)
						else if (item.isFile() && item.name.endsWith('.part')) {
							freed += fs.statSync(p).size
							fs.rmSync(p, { force: true })
						}
					}
				}
				scanParts(gameDir)
			} catch {}
		}

		if (target === 'all' || target === 'logs') {
			if (fs.existsSync(logDir)) {
				try {
					for (const f of fs.readdirSync(logDir)) {
						if (f === 'launcher-latest.log' || f === 'game-latest.log') continue
						const fp = path.join(logDir, f)
						try {
							freed += fs.statSync(fp).size
							fs.rmSync(fp, { force: true })
						} catch {}
					}
				} catch {}
			}
		}

		log(`Очистка хранилища (${target}): освобождено ${(freed / 1024 / 1024).toFixed(1)} МБ`)
		return { freedBytes: freed, freedFormatted: `${(freed / 1024 / 1024).toFixed(1)} МБ` }
	})

	handle('system:diagnose', async () => {
		const checks = []

		try {
			const t0 = Date.now()
			const res = await fetch(`${CONFIG.baseUrl}/api/status`, {
				headers: { 'User-Agent': 'LunacyLauncher/2.1.0' },
				signal: AbortSignal.timeout(5000),
			})
			const ms = Date.now() - t0
			checks.push({ name: 'Сервер Lunacy Visuals', ok: res.ok, latency: `${ms} мс`, status: res.status })
		} catch {
			checks.push({ name: 'Сервер Lunacy Visuals', ok: false, error: 'Сайт недоступен' })
		}

		try {
			const t0 = Date.now()
			const res = await fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', {
				method: 'HEAD',
				signal: AbortSignal.timeout(5000),
			})
			const ms = Date.now() - t0
			checks.push({ name: 'Серверы Mojang (Версии)', ok: res.ok, latency: `${ms} мс` })
		} catch {
			checks.push({ name: 'Серверы Mojang (Версии)', ok: false, error: 'Не отвечает' })
		}

		try {
			const t0 = Date.now()
			const res = await fetch('https://meta.fabricmc.net/v2/versions/game', {
				method: 'HEAD',
				signal: AbortSignal.timeout(5000),
			})
			const ms = Date.now() - t0
			checks.push({ name: 'Fabric Meta (Загрузчики)', ok: res.ok, latency: `${ms} мс` })
		} catch {
			checks.push({ name: 'Fabric Meta (Загрузчики)', ok: false, error: 'Не отвечает' })
		}

		try {
			const t0 = Date.now()
			const res = await fetch('https://api.modrinth.com/v2', {
				method: 'HEAD',
				headers: { 'User-Agent': 'LunacyLauncher/2.1.0' },
				signal: AbortSignal.timeout(5000),
			})
			const ms = Date.now() - t0
			checks.push({ name: 'Мастерская (Modrinth)', ok: res.ok, latency: `${ms} мс` })
		} catch {
			checks.push({ name: 'Мастерская (Modrinth)', ok: false, error: 'Не отвечает' })
		}

		const gameDir = settings.get('gameDir')
		try {
			const testFile = path.join(gameDir, `.lunacy_test_${Date.now()}`)
			fs.writeFileSync(testFile, 'test')
			fs.rmSync(testFile)
			checks.push({ name: 'Папка игры (.lunacy)', ok: true, latency: 'OK' })
		} catch {
			checks.push({ name: 'Папка игры (.lunacy)', ok: false, error: 'Нет доступа на запись' })
		}

		return { checks, allOk: checks.every((c) => c.ok) }
	})

	/* авторизация */
	handle('auth:login', async ({ login, password }) => {
		const session = await auth.login(login, password)
		send('auth:session', session)
		if (!session?.hasSubscription) cleanupPaidModsIfNoSubscription()
		return { session, planLabel: planLabel(session.subscription) }
	})
	handle('auth:register', async (payload) => {
		const session = await auth.register(payload || {})
		send('auth:session', session)
		if (!session?.hasSubscription) cleanupPaidModsIfNoSubscription()
		return { session, planLabel: planLabel(session.subscription) }
	})
	handle('auth:restore', async () => {
		const session = await auth.restore()
		if (session) send('auth:session', session)
		if (!session?.hasSubscription) cleanupPaidModsIfNoSubscription()
		return { session, planLabel: session ? planLabel(session.subscription) : null }
	})
	handle('auth:refresh', async () => {
		const session = await auth.refresh()
		send('auth:session', session)
		if (!session?.hasSubscription) cleanupPaidModsIfNoSubscription()
		return { session, planLabel: planLabel(session.subscription) }
	})
	handle('auth:redeem', async (code) => {
		const session = await auth.redeem(code)
		send('auth:session', session)
		if (!session?.hasSubscription) cleanupPaidModsIfNoSubscription()
		return { session, planLabel: planLabel(session.subscription) }
	})
	handle('auth:logout', async () => {
		await auth.logout()
		send('auth:session', null)
		cleanupPaidModsIfNoSubscription()
		return {}
	})

	/* подписка — проверка при каждом запуске */
	handle('subscription:get', async () => {
		if (!auth.isAuthorized) {
			cleanupPaidModsIfNoSubscription()
			return { hasSubscription: false, tier: 'free', session: null }
		}
		const session = await auth.refresh()
		send('auth:session', session)
		if (!session?.hasSubscription) cleanupPaidModsIfNoSubscription()
		return {
			session,
			hasSubscription: Boolean(session.hasSubscription),
			tier: session.hasSubscription ? session.plan : 'free',
			planLabel: planLabel(session.subscription),
			subscription: session.subscription,
		}
	})

	/* менеджер аккаунтов (игровые ники) */
	handle('accounts:list', async () => ({ items: accountList() }))
	handle('accounts:add', async ({ nickname, type = 'offline' }) => {
		const nick = String(nickname || '').trim()
		if (!/^[A-Za-z0-9_]{3,16}$/.test(nick)) {
			throw new Error('Ник: 3–16 символов, латиница, цифры, _')
		}
		const items = accounts.get('items', [])
		if (items.some((item) => item.nickname.toLowerCase() === nick.toLowerCase())) {
			throw new Error('Такой ник уже добавлен')
		}
		if (type === 'online') {
			throw new Error('Лицензионный ник добавляется кнопкой «Войти по лицензии Microsoft»')
		}
		const account = {
			id: `acc-${Date.now().toString(36)}`,
			nickname: nick,
			// offline = пиратка (ник свободный), online = лицензия Microsoft
			type: 'offline',
			auth: 'offline',
			uuid: offlineUuid(nick),
			addedAt: new Date().toISOString(),
		}
		accounts.set({ items: [...items, account] })
		if (!settings.get('activeAccountId')) settings.set({ activeAccountId: account.id })
		return { items: accountList(), account }
	})
	handle('accounts:remove', async (id) => {
		const items = accounts.get('items', []).filter((item) => item.id !== id)
		accounts.set({ items })
		if (settings.get('activeAccountId') === id) {
			settings.set({ activeAccountId: items[0]?.id || null })
		}
		return { items: accountList() }
	})
	handle('accounts:select', async (id) => {
		settings.set({ activeAccountId: id })
		return { items: accountList() }
	})

	/* вход по лицензии Minecraft — Microsoft / Xbox Live */
	handle('accounts:msStart', async () => {
		const flow = await startDeviceCode()
		msFlow = { ...flow, startedAt: Date.now() }
		log(`Microsoft: открой ${flow.verificationUrl} и введи код ${flow.userCode}`)
		return {
			code: flow.userCode,
			url: flow.verificationUrl,
			link: MS_LINK_URL,
			interval: flow.interval,
			expiresIn: flow.expiresIn,
		}
	})

	handle('accounts:msPoll', async () => {
		if (!msFlow) throw new Error('Вход Microsoft не запущен')
		const state = await pollDeviceToken(msFlow.deviceCode)
		if (state.status !== 'ready') {
			if (state.status !== 'pending') msFlow = null
			return { status: state.status, error: state.error || null }
		}

		const profile = await loginWithMsaToken({
			accessToken: state.accessToken,
			refreshToken: state.refreshToken,
			onLog: log,
		})
		msFlow = null

		const items = accounts.get('items', [])
		const existing = items.find(
			(item) =>
				item.uuid === profile.uuid ||
				String(item.nickname).toLowerCase() === profile.nickname.toLowerCase(),
		)
		const account = {
			id: existing?.id || `acc-${Date.now().toString(36)}`,
			addedAt: existing?.addedAt || new Date().toISOString(),
			...profile,
		}
		accounts.set({
			items: existing
				? items.map((item) => (item.id === account.id ? account : item))
				: [...items, account],
		})
		settings.set({ activeAccountId: account.id })
		return { status: 'ready', nickname: account.nickname, items: accountList() }
	})

	handle('accounts:msCancel', async () => {
		msFlow = null
		return {}
	})

	handle('accounts:refresh', async (id) => {
		const items = accounts.get('items', [])
		const account = items.find((item) => item.id === id)
		if (!account) throw new Error('Аккаунт не найден')
		if (account.auth !== 'microsoft') return { items: accountList() }
		const fresh = await refreshGameAccount(account, { onLog: log })
		accounts.set({ items: items.map((item) => (item.id === id ? fresh : item)) })
		return { items: accountList(), nickname: fresh.nickname }
	})

	/* настройки + железо */
	handle('settings:get', async () => ({
		settings: settings.all,
		memory: memoryInfo(),
		system: systemInfo(),
	}))
	handle('settings:set', async (patch) => {
		const clean = { ...(patch || {}) }
		if (clean.memoryMax) {
			const { maxGb } = memoryInfo()
			clean.memoryMax = Math.min(Math.max(1, Number(clean.memoryMax)), maxGb)
		}
		return { settings: settings.set(clean) }
	})
	handle('settings:reset', async () => ({ settings: settings.reset() }))
	handle('settings:pickFolder', async () => {
		const result = await dialog.showOpenDialog(win, {
			properties: ['openDirectory', 'createDirectory'],
			defaultPath: settings.get('gameDir'),
		})
		if (result.canceled || !result.filePaths[0]) return { canceled: true }
		const gameDir = result.filePaths[0]
		ensureGameDirs(gameDir)
		return { settings: settings.set({ gameDir }), gameDir }
	})
	handle('settings:pickJava', async () => {
		const result = await dialog.showOpenDialog(win, {
			properties: ['openFile'],
			filters:
				process.platform === 'win32'
					? [{ name: 'Java', extensions: ['exe'] }]
					: [{ name: 'Java', extensions: ['*'] }],
		})
		if (result.canceled || !result.filePaths[0]) return { canceled: true }
		return { settings: settings.set({ javaPath: result.filePaths[0] }) }
	})

	handle('system:info', async () => ({ system: systemInfo(), memory: memoryInfo() }))
	handle('java:list', async (force) => {
		if (force) javaCache = []
		const items = await ensureJava()
		return { items, selected: settings.get('javaPath') || null }
	})
	handle('gpu:list', async (force) => {
		if (force) gpuCache = []
		const items = await ensureGpus()
		return { items, selected: settings.get('gpuId') || null, multiple: items.length > 1 }
	})

	/* версии Minecraft: Vanilla / Fabric / Forge */
	handle('versions:installed', async () => {
		if (!auth.hasSubscription) {
			cleanupPaidModsIfNoSubscription()
			let changed = false
			for (const inst of instances.items) {
				if (inst.name && inst.name.toLowerCase().startsWith('lunacyvisual') && inst.mc === '1.21.11') {
					inst.name = inst.name.replace(/LunacyVisual/i, 'Lunacy Free')
					changed = true
				}
			}
			if (changed) instances.save(instances.items)
		}
		const items = listInstalled(settings.get('gameDir'))
		const list = instances.sync(items)
		return {
			items: items.map((item) => {
				const instance = list.find((entry) => entry.versionId === item.id) || null
				return { ...item, instance }
			}),
			instances: list,
			selected: settings.get('selectedVersionId'),
			gameDir: settings.get('gameDir'),
		}
	})

	/* удаление версии: профиль из общей папки + (по желанию) папка сборки */
	handle('versions:remove', async ({ versionId, withInstance = true } = {}) => {
		const gameDir = settings.get('gameDir')
		if (!versionId) throw new Error('Не указана версия')
		if (child) throw new Error('Сначала закрой игру')
		const instance = instances.findByVersion(versionId)
		removeVersion(gameDir, versionId)
		log(`Версия ${versionId} удалена`)
		if (instance) {
			instances.remove(instance.id, { deleteFiles: Boolean(withInstance) })
			if (withInstance) log(`Папка сборки «${instance.name}» удалена`)
		}
		const items = listInstalled(gameDir)
		if (settings.get('selectedVersionId') === versionId) {
			settings.set({ selectedVersionId: items[0]?.id || null })
		}
		return {
			items,
			instances: instances.sync(items),
			selected: settings.get('selectedVersionId'),
		}
	})

	/* сборки-папки: у каждой версии свои моды, ресурспаки, шейдеры и сейвы */
	handle('instances:list', async () => {
		if (!auth.hasSubscription) {
			cleanupPaidModsIfNoSubscription()
		}
		return {
			items: instanceList(),
			active: settings.get('activeInstanceId'),
			target: settings.get('contentTargetId') || settings.get('activeInstanceId'),
			root: path.join(settings.get('gameDir'), 'instances'),
		}
	})
	handle('instances:create', async ({ versionId, name } = {}) => {
		const gameDir = ensureGameDirs(settings.get('gameDir'))
		const version = listInstalled(gameDir, { includeDependencies: true }).find(
			(item) => item.id === versionId,
		)
		if (!version) throw new Error('Сначала установи версию')
		const instance = instances.ensureFor({
			versionId,
			loader: version.loader,
			mc: version.mc,
			name: name || suggestName(version),
		})
		log(`Создана сборка «${instance.name}» → ${instance.dir}`)
		return { instance, items: instanceList() }
	})
	handle('instances:rename', async ({ id, name } = {}) => {
		if (!name?.trim()) throw new Error('Введи новое имя сборки')
		const instance = instances.rename(id, name.trim())
		return { instance, items: instanceList() }
	})
	handle('instances:clone', async ({ id, name } = {}) => {
		const cloned = instances.clone(id, name)
		log(`Сборка клонирована: «${cloned.name}»`)
		return { instance: cloned, items: instanceList() }
	})
	handle('instances:remove', async ({ id, deleteFiles = true } = {}) => {
		const result = instances.remove(id, { deleteFiles })
		if (settings.get('activeInstanceId') === id) settings.set({ activeInstanceId: null })
		if (settings.get('contentTargetId') === id) settings.set({ contentTargetId: null })
		return { ...result, items: instanceList() }
	})
	handle('instances:open', async ({ id, sub } = {}) => {
		const instance = instances.find(id)
		if (!instance) throw new Error('Сборка не найдена')
		const dir = instances.resolveDir(instance)
		ensureInstanceDirs(dir)
		await shell.openPath(sub ? path.join(dir, sub) : dir)
		return { dir }
	})
	handle('instances:select', async ({ id, target = false } = {}) => {
		if (target) settings.set({ contentTargetId: id })
		else settings.set({ activeInstanceId: id })
		return { settings: settings.all, items: instanceList() }
	})
	handle('versions:remote', async ({ kind = 'vanilla', mcVersion = null, snapshots = false } = {}) => {
		if (kind === 'fabric') {
			const data = await listFabricVersions()
			const games = (data.games || []).filter((item) => snapshots || item.stable !== false)
			return {
				kind,
				items: games.map((item) => ({ id: item.id, mc: item.id, stable: item.stable !== false })),
				builds: (data.loaders || [])
					.filter((item) => item.stable !== false)
					.map((item) => ({ id: item.id, label: `Fabric Loader ${item.id}` })),
			}
		}
		if (kind === 'forge') {
			const vanilla = await listVanillaVersions({ includeSnapshots: snapshots })
			const items = vanilla.map((item) => ({ id: item.id, mc: item.id, type: item.type }))
			if (!mcVersion) return { kind, items, builds: [] }
			const builds = await listForgeVersions(mcVersion)
			return {
				kind,
				items,
				builds: builds.map((item) => ({ id: item.id, label: `Forge ${item.forge}` })),
			}
		}
		const vanilla = await listVanillaVersions({ includeSnapshots: snapshots })
		return {
			kind: 'vanilla',
			items: vanilla.map((item) => ({
				id: item.id,
				mc: item.id,
				type: item.type,
				releaseTime: item.releaseTime,
			})),
			builds: [],
		}
	})

	handle('versions:builds', async ({ kind = 'fabric', mcVersion = null } = {}) => {
		if (kind === 'forge') {
			if (!mcVersion) throw new Error('Сначала выбери версию Minecraft')
			const builds = await listForgeVersions(mcVersion)
			return {
				kind,
				mcVersion,
				items: builds.map((item) => ({ id: item.id, label: `Forge ${item.forge || item.id}` })),
			}
		}
		if (kind === 'fabric') {
			const data = await listFabricVersions()
			return {
				kind,
				mcVersion,
				items: (data.loaders || [])
					.filter((item) => item.stable !== false)
					.map((item) => ({ id: item.id, label: `Fabric Loader ${item.id}` })),
			}
		}
		return { kind, mcVersion, items: [] }
	})
	handle('versions:install', async ({ kind = 'vanilla', mcVersion, loaderVersion, forgeId } = {}) => {
		const gameDir = ensureGameDirs(settings.get('gameDir'))
		const onProgress = (data) => progress('version', data)
		let result
		if (kind === 'fabric') {
			result = await installFabric(mcVersion, { gameDir, loaderVersion, onProgress, onLog: log })
		} else if (kind === 'forge') {
			const javaPath = await javaForVersion(mcVersion)
			result = await installForge(forgeId, { gameDir, javaPath, onProgress, onLog: log })
		} else {
			result = await installVanilla(mcVersion, { gameDir, onProgress, onLog: log })
		}
		const installedId = result.versionId || result.id
		// у каждой версии своя папка: <gameDir>/instances/Fabric 1.21.11
		const installedInfo = listInstalled(gameDir, { includeDependencies: true }).find(
			(item) => item.id === installedId,
		)
		const cleanLoader = installedInfo?.loader || (kind === 'vanilla' ? 'Vanilla' : kind)
		const cleanMc = installedInfo?.mc || mcVersion || installedId
		const instanceName = suggestName({ loader: cleanLoader, mc: cleanMc, versionId: installedId })
		const instance = instances.ensureFor({
			versionId: installedId,
			loader: cleanLoader,
			mc: cleanMc,
			name: instanceName,
			isLunacy: false,
		})
		log(`Папка сборки: ${instance.dir}`)
		settings.set({
			selectedVersionId: installedId,
			activeInstanceId: instance.id,
			contentTargetId: settings.get('contentTargetId') || instance.id,
		})
		const state = verifyVersion(gameDir, installedId)
		if (!state.ok) {
			log(`Версия ${installedId} собрана не полностью, докачиваю недостающее…`)
			await repairVersion(installedId, {
				gameDir,
				onProgress,
				onLog: log,
			})
		}
		progress('version', { percent: 100, done: true })
		return {
			version: { ...result, versionId: installedId },
			items: listInstalled(gameDir),
			instances: instanceList(),
			instance,
			selected: installedId,
			state: verifyVersion(gameDir, installedId),
		}
	})
	handle('versions:verify', async ({ versionId } = {}) => {
		const gameDir = settings.get('gameDir')
		const target = versionId || settings.get('selectedVersionId')
		if (!target) throw new Error('Сначала выбери версию')
		const state = verifyVersion(gameDir, target)
		const java = await javaForVersion(target)
		const detected = javaMajorOf(java)
		return { state, java, javaMajor: detected.major, javaLine: detected.output }
	})
	handle('versions:repair', async ({ versionId } = {}) => {
		const gameDir = ensureGameDirs(settings.get('gameDir'))
		const target = versionId || settings.get('selectedVersionId')
		if (!target) throw new Error('Сначала выбери версию')
		log(`Починка версии ${target}…`)
		const result = await repairVersion(target, {
			gameDir,
			onProgress: (data) => progress('version', data),
			onLog: log,
		})
		progress('version', { percent: 100, done: true })
		return { state: result.state, errors: result.errors, items: listInstalled(gameDir) }
	})

	/* консоль лаунчера */
	handle('logs:get', async () => ({ items: logBuffer.slice(-800), file: logFile }))
	handle('logs:clear', async () => {
		logBuffer.length = 0
		return { items: [] }
	})
	handle('logs:open', async () => {
		if (!logFile || !fs.existsSync(logFile)) throw new Error('Файл логов ещё не создан')
		shell.showItemInFolder(logFile)
		return { file: logFile }
	})
	handle('logs:file', async () => ({ file: logFile, dir: logDir }))

	handle('versions:select', async (versionId) => ({
		settings: settings.set({ selectedVersionId: versionId }),
	}))

	/* инфо с сайта: версии MC для мода + загрузчик */
	handle('client:info', async () => {
		try {
			clientInfoCache = await fetchClientInfo(auth.token)
		} catch (error) {
			log(`Не удалось получить данные с сайта: ${error.message}`)
		}
		if (!auth.hasSubscription) {
			cleanupPaidModsIfNoSubscription()
		}
		const mcVersions = auth.hasSubscription
			? (clientInfoCache?.mcVersions?.length ? clientInfoCache.mcVersions : FALLBACK_MC_VERSIONS)
			: ['1.21.11']
		return {
			info: clientInfoCache,
			mcVersions,
			loader: clientInfoCache?.loader || 'Fabric',
			modVersion: auth.hasSubscription
				? (clientInfoCache?.version || '1.1.2')
				: (clientInfoCache?.freeVersion || '1.1.0-free'),
			hasSubscription: Boolean(auth.hasSubscription),
			offlineInfo: !clientInfoCache,
		}
	})

	/* загрузки Lunacy */
	handle('download:mod', async ({ mcVersion } = {}) => {
		const token = requireSubscription()
		const gameDir = ensureGameDirs(settings.get('gameDir'))
		const version = mcVersion || settings.get('modMcVersion') || FALLBACK_MC_VERSIONS[0]
		const result = await downloadMod({
			token,
			gameDir,
			cacheDir,
			mcVersion: version,
			etag: settings.get('modEtag'),
			onProgress: (data) => progress('mod', data),
			onLog: log,
		})
		settings.set({
			modMcVersion: version,
			modEtag: result.etag || settings.get('modEtag'),
			modVersion: clientInfoCache?.version || settings.get('modVersion'),
		})
		progress('mod', { percent: 100, done: true })
		return { manifest: result, skipped: Boolean(result.skipped), version }
	})

	handle('download:free', async ({ mcVersion } = {}) => {
		const gameDir = ensureGameDirs(settings.get('gameDir'))
		const result = await downloadFreeBuild({
			gameDir,
			cacheDir,
			mcVersion: mcVersion || settings.get('modMcVersion') || null,
			onProgress: (data) => progress('free', data),
			onLog: log,
		})
		progress('free', { percent: 100, done: true })
		return { manifest: result }
	})

	/* каталог модов / ресурспаков (Modrinth v2) */
	handle('catalog:kinds', async () => ({
		kinds: CATALOG_KINDS,
		categories: CATALOG_CATEGORIES,
		providers: listProviders(),
		provider: 'modrinth',
	}))
	handle('catalog:providers', async () => ({
		providers: listProviders(),
		provider: 'modrinth',
	}))
	handle('catalog:search', async (payload = {}) => {
		return searchCatalog(payload)
	})
	handle('catalog:details', async ({ slugOrId } = {}) => {
		if (!slugOrId) throw new Error('Не указан ID мода')
		return fetchProjectDetails(slugOrId)
	})
	handle('catalog:install', async (payload = {}) => {
		const gameDir = ensureGameDirs(settings.get('gameDir'))
		// instanceId — в какую сборку класть мод / ресурспак / шейдер
		const instance = resolveInstance(payload.instanceId)
		if (!instance) {
			throw new Error('Сначала установи версию — в неё и поставим файл')
		}
		ensureInstanceDirs(instance.dir)
		if (payload.instanceId) settings.set({ contentTargetId: instance.id })
		// Версия игры и загрузчик берутся из выбранной сборки, если их не передали
		const kind = payload.kind || 'mod'
		const resolved = payload.resolved === true
		const mcVersion = payload.mcVersion || (resolved ? null : instanceMcVersion(instance)) || null
		const loader =
			kind === 'mod'
				? payload.loader || (resolved ? null : instanceLoaderKey(instance)) || null
				: null
		log(
			`Мастерская: файл под Minecraft ${mcVersion || 'любой'}${loader ? ` (${loader})` : ''}`,
		)
		const result = await downloadCatalogItem({
			...payload,
			kind,
			mcVersion,
			loader,
			gameDir,
			targetDir: instance.dir,
			onProgress: (data) => progress('catalog', data),
			onLog: log,
		})
		progress('catalog', { percent: 100, done: true })
		log(`Файл установлен в сборку «${instance.name}»`)
		return { file: result, instance }
	})
	handle('catalog:installed', async ({ instanceId } = {}) => {
		const instance = resolveInstance(instanceId)
		return {
			items: instance ? listInstalledContent(instance.dir) : {},
			instance,
			instances: instanceList(),
		}
	})
	handle('catalog:remove', async ({ kind, name, instanceId } = {}) => {
		const instance = resolveInstance(instanceId)
		if (!instance) throw new Error('Сборка не найдена')
		removeContentFile(instance.dir, kind, name)
		return { items: listInstalledContent(instance.dir), instance }
	})
	handle('catalog:toggleMod', async ({ filename, instanceId } = {}) => {
		const instance = resolveInstance(instanceId)
		if (!instance) throw new Error('Сборка не найдена')
		const res = toggleModEnabled(instance.dir, filename)
		return { ...res, items: listInstalledContent(instance.dir), instance }
	})
	handle('catalog:importFile', async ({ filePath, kind, instanceId } = {}) => {
		const instance = resolveInstance(instanceId)
		if (!instance) throw new Error('Сборка не найдена')
		const res = importContentFile(instance.dir, filePath, kind)
		return { ...res, items: listInstalledContent(instance.dir), instance }
	})


	/* сборка с визуалом: Fabric нужной версии + мод (или FREE без подписки) */
	handle('lunacy:install', async ({ mcVersion } = {}) => {
		if (auth.isAuthorized) {
			try {
				send('auth:session', await auth.refresh())
			} catch {}
		}
		const premium = Boolean(auth.hasSubscription)
		const version = premium
			? (mcVersion || settings.get('modMcVersion') || FALLBACK_MC_VERSIONS[0])
			: '1.21.11'
		const gameDir = ensureGameDirs(settings.get('gameDir'))

		log(`Сборка Lunacy ${version}: ставим загрузчик Fabric:`)
		const fabric = await installFabric(version, {
			gameDir,
			onProgress: (data) => progress('version', data),
			onLog: log,
		})
		markVersionSource(gameDir, fabric.versionId, true)

		// Удаляем случайный дубликат сборки Fabric, если он успел создаться
		const dupInst = instances.items.find(
			(it) => it.name === `Fabric ${version}` && it.versionId === fabric.versionId
		)
		if (dupInst) {
			instances.remove(dupInst.id, { deleteFiles: true })
		}

		const lunacyVersionId = `LunacyVisual-${version}`
		const lunacyVersionDir = path.join(gameDir, 'versions', lunacyVersionId)
		await fsp.mkdir(lunacyVersionDir, { recursive: true })
		const fabricProfilePath = path.join(gameDir, 'versions', fabric.versionId, `${fabric.versionId}.json`)
		if (fs.existsSync(fabricProfilePath)) {
			try {
				const fabricProfile = JSON.parse(await fsp.readFile(fabricProfilePath, 'utf8'))
				const lunacyProfile = {
					...fabricProfile,
					id: lunacyVersionId,
					inheritsFrom: fabricProfile.inheritsFrom || fabric.versionId,
				}
				await fsp.writeFile(
					path.join(lunacyVersionDir, `${lunacyVersionId}.json`),
					JSON.stringify(lunacyProfile, null, 2),
					'utf8',
				)
			} catch {}
		}

		// Создаём инстанс LunacyVisual или Lunacy Free в зависимости от подписки (отдельная изолированная папка)
		const instanceName = premium ? `LunacyVisual ${version}` : `Lunacy Free ${version}`
		const instance = instances.ensureFor({
			versionId: lunacyVersionId,
			loader: 'Fabric',
			mc: version,
			name: instanceName,
			isLunacy: true,
		})
		
		// Очищаем папку mods инстанса перед скачиванием, чтобы не было дублей старых модов
		const instanceModsDir = path.join(instance.dir, 'mods')
		if (fs.existsSync(instanceModsDir)) {
			fs.rmSync(instanceModsDir, { recursive: true, force: true })
		}

		let content = null
		if (premium) {
			content = await downloadMod({
				token: auth.token,
				gameDir: instance.dir, // <- ТЕПЕРЬ ПУТЬ К ИНСТАНСУ!
				cacheDir,
				mcVersion: version,
				etag: settings.get('modEtag'),
				onProgress: (data) => progress('mod', data),
				onLog: log,
			})
			progress('mod', { percent: 100, done: true })
		} else {
			content = await downloadFreeBuild({
				gameDir: instance.dir, // <- ТЕПЕРЬ ПУТЬ К ИНСТАНСУ!
				cacheDir,
				mcVersion: version,
				onProgress: (data) => progress('free', data),
				onLog: log,
			})
			progress('free', { percent: 100, done: true })
		}

		settings.set({
			modMcVersion: version,
			selectedVersionId: lunacyVersionId,
			lunacyVersionId: lunacyVersionId,
			activeInstanceId: instance.id, // Делаем его активным
			contentTargetId: instance.id,
			modEtag: content?.etag || settings.get('modEtag'),
			modVersion: premium ? (clientInfoCache?.version || '1.1.2') : '1.1.0-free',
		})

		return {
			versionId: lunacyVersionId,
			instanceName,
			tier: premium ? 'premium' : 'free',
			selected: lunacyVersionId,
			installed: true,
			instance,
			instances: instanceList(),
			versions: listInstalled(gameDir),
		}
	})

	/* Восстановление визуала: проверяет loader по Fabric API и заново скачивает мод */
	handle('lunacy:restore', async ({ mcVersion } = {}) => {
		if (auth.isAuthorized) {
			try { send('auth:session', await auth.refresh()) } catch {}
		}
		const premium = Boolean(auth.hasSubscription)
		const version = premium
			? (mcVersion || settings.get('modMcVersion') || FALLBACK_MC_VERSIONS[0])
			: '1.21.11'
		const gameDir = ensureGameDirs(settings.get('gameDir'))

		log(`[Восстановление] Проверяю Fabric loader для Minecraft ${version}...`)
		const fabric = await installFabric(version, {
			gameDir,
			onProgress: (data) => progress('version', data),
			onLog: log,
		})
		markVersionSource(gameDir, fabric.versionId, true)

		const dupInst = instances.items.find(
			(it) => it.name === `Fabric ${version}` && it.versionId === fabric.versionId
		)
		if (dupInst) {
			instances.remove(dupInst.id, { deleteFiles: true })
		}

		const lunacyVersionId = `LunacyVisual-${version}`
		const lunacyVersionDir = path.join(gameDir, 'versions', lunacyVersionId)
		await fsp.mkdir(lunacyVersionDir, { recursive: true })
		const fabricProfilePath = path.join(gameDir, 'versions', fabric.versionId, `${fabric.versionId}.json`)
		if (fs.existsSync(fabricProfilePath)) {
			try {
				const fabricProfile = JSON.parse(await fsp.readFile(fabricProfilePath, 'utf8'))
				const lunacyProfile = {
					...fabricProfile,
					id: lunacyVersionId,
					inheritsFrom: fabricProfile.inheritsFrom || fabric.versionId,
				}
				await fsp.writeFile(
					path.join(lunacyVersionDir, `${lunacyVersionId}.json`),
					JSON.stringify(lunacyProfile, null, 2),
					'utf8',
				)
			} catch {}
		}

		const instanceName = premium ? `LunacyVisual ${version}` : `Lunacy Free ${version}`
		const instance = instances.ensureFor({
			versionId: lunacyVersionId,
			loader: 'Fabric',
			mc: version,
			name: instanceName,
			isLunacy: true,
		})

		log(`[Восстановление] Скачиваю файл визуала в ${instance.dir}...`)
		const instanceModsDir = path.join(instance.dir, 'mods')
		await fsp.mkdir(instanceModsDir, { recursive: true })

		// Если без подписки — проверяем и сносим любой платный мод из папки
		if (!premium) {
			cleanupPaidModsIfNoSubscription(instance.dir)
		} else {
			// Если с подпиской — удаляем старую фришку, если осталась
			if (fs.existsSync(instanceModsDir)) {
				for (const f of fs.readdirSync(instanceModsDir)) {
					const fp = path.join(instanceModsDir, f)
					if (isFreeLunacyMod(fp)) {
						try { fs.unlinkSync(fp) } catch {}
					}
				}
			}
		}

		let content = null
		if (premium) {
			content = await downloadMod({
				token: auth.token,
				gameDir: instance.dir,
				cacheDir,
				mcVersion: version,
				onProgress: (data) => progress('mod', data),
				onLog: log,
			})
			progress('mod', { percent: 100, done: true })
		} else {
			content = await downloadFreeBuild({
				gameDir: instance.dir,
				cacheDir,
				mcVersion: version,
				onProgress: (data) => progress('free', data),
				onLog: log,
			})
			progress('free', { percent: 100, done: true })
		}

		settings.set({
			modMcVersion: version,
			selectedVersionId: lunacyVersionId,
			lunacyVersionId: lunacyVersionId,
			activeInstanceId: instance.id,
			contentTargetId: instance.id,
			modEtag: content?.etag || settings.get('modEtag'),
			modVersion: premium ? (clientInfoCache?.version || '1.1.2') : '1.1.0-free',
		})

		log(`[Восстановление] Визуал Lunacy ${version} успешно восстановлен!`)
		return {
			ok: true,
			versionId: lunacyVersionId,
			instanceName,
			tier: premium ? 'premium' : 'free',
			selected: lunacyVersionId,
			installed: true,
			instance,
			instances: instanceList(),
			versions: listInstalled(gameDir),
		}
	})

	/* обновление лаунчера — ОДНА попытка проверки, без автозагрузки */
	handle('update:check', async () => {
		const current = app.getVersion()
		try {
			const launcher = await checkLauncherVersion(current)
			return {
				launcher,
				current,
				checked: true,
				ready: Boolean(pendingUpdate && pendingUpdate.version === launcher.version),
			}
		} catch (error) {
			// Не получилось проверить — останавливаемся, никаких повторов.
			return { ...fail(error), checked: false, current, stopped: true }
		}
	})

	handle('update:mod', async () => {
		const mod = await checkModVersion(settings.get('modVersion'), auth.token)
		return { mod }
	})

	/* скачивание обновления — только по кнопке пользователя */
	handle('update:download', async ({ version } = {}) => {
		const result = await downloadLauncher({
			cacheDir,
			onProgress: (data) => progress('update', data),
			onLog: log,
		})
		pendingUpdate = { file: result.file, version: version || null }
		progress('update', { percent: 100, done: true })
		return { file: result.file, version: version || null, ready: true }
	})

	handle('update:install', async () => {
		if (!pendingUpdate?.file) throw new Error('Сначала скачай обновление')
		const exePath = app.getPath('exe')
		const result = await installLauncherUpdate({
			file: pendingUpdate.file,
			appDir: path.dirname(exePath),
			exePath,
			workDir: cacheDir,
			isPackaged: app.isPackaged,
			onLog: log,
		})
		if (result.restart) {
			setTimeout(() => app.quit(), 600)
		} else if (result.stageDir) {
			shell.openPath(result.stageDir)
		}
		return { ...result }
	})

	/* игра */
	handle('game:launch', async ({ versionId } = {}) => {
		if (child) throw new Error('Игра уже запущена')

		// Сначала определяем профиль: обычные версии запускаются без аккаунта и HWID.
		const gameDir = ensureGameDirs(settings.get('gameDir'))
		const installedVersions = listInstalled(gameDir)
		let target = versionId || settings.get('selectedVersionId')
		if (!target || !installedVersions.some((v) => v.id === target)) {
			const lunacyInst = instances.items.find((item) =>
				item.name?.toLowerCase().includes('lunacy') && installedVersions.some((v) => v.id === item.versionId)
			)
			target = settings.get('lunacyVersionId') || lunacyInst?.versionId || null
		}
		if (!target) {
			const lunacyVer = installedVersions.find((v) => instances.findByVersion(v.id)?.name?.toLowerCase().includes('lunacy'))
			target = lunacyVer?.id || installedVersions[0]?.id || null
		}
		if (!target) throw new Error('Сначала установи сборку во вкладке «Менеджер версий»')
		settings.set({ selectedVersionId: target })
		const instanceDir = instanceDirFor(target)
		const isTargetLunacy = Boolean(
			target.toLowerCase().includes('lunacy') ||
			(instanceDir && instanceDir.toLowerCase().includes('lunacy')) ||
			(settings.get('lunacyVersionId') === target)
		)

		if (isTargetLunacy) {
			if (!auth.isAuthorized || !auth.token) throw new Error('Для запуска LunacyVisuals необходимо войти в аккаунт')
			log('Проверка HWID и лицензии перед запуском LunacyVisuals...')
			try {
				const hwidRes = await apiRequest('/api/client/verify-hwid', { method: 'POST', token: auth.token })
				if (!hwidRes || hwidRes.success === false) throw new Error(hwidRes?.error || 'Проверка HWID не пройдена')
				log('HWID и лицензия успешно подтверждены сервером.')
			} catch (error) {
				log(`Отказ в запуске LunacyVisuals: ${error.message}`)
				throw new Error(error.message || 'HWID не совпадает с привязанным к аккаунту! Запуск заблокирован.')
			}
		}

		// 1. Автоматическая проверка и установка обновления лаунчера
		try {
			const currentLauncher = app.getVersion()
			log('Проверка обновлений лаунчера...')
			const launcherInfo = await checkLauncherVersion(currentLauncher)
			if (launcherInfo.updateAvailable && launcherInfo.canDownload) {
				log(`Найдено обновление лаунчера: v${launcherInfo.version}. Загрузка...`)
				const updateResult = await downloadLauncher({
					cacheDir,
					onProgress: (data) => progress('update', data),
					onLog: log,
				})
				log('Установка обновления лаунчера...')
				const exePath = app.getPath('exe')
				const installResult = await installLauncherUpdate({
					file: updateResult.file,
					appDir: path.dirname(exePath),
					exePath,
					workDir: cacheDir,
					isPackaged: app.isPackaged,
					onLog: log,
				})
				if (installResult.restart) {
					setTimeout(() => app.quit(), 600)
					return { running: false, updateRestart: true }
				}
			}
		} catch (error) {
			log(`Не удалось проверить/установить обновление лаунчера: ${error.message}`)
		}

		// 2. Автоматическое обновление мода (только для пользователей с активной подпиской)
		const installedModVersion = installedLunacyModVersion(instanceDir)
		const currentModVersion = installedModVersion || settings.get('modVersion')
		if (isTargetLunacy && installedModVersion && installedModVersion !== settings.get('modVersion')) {
			settings.set({ modVersion: installedModVersion })
		}
		if (isTargetLunacy && currentModVersion && auth.hasSubscription && settings.get('autoUpdateMod')) {
			try {
				log('Проверка обновлений мода...')
				const modInfo = await checkModVersion(currentModVersion, auth.token)
				if (modInfo.updateAvailable && modInfo.canDownload) {
					log(`Найдено обновление мода (до v${modInfo.version}). Скачиваю...`)
					const modMcVersion = settings.get('modMcVersion') || FALLBACK_MC_VERSIONS[0]
					const targetInst = instances.findByVersion(target)
					const targetDir = targetInst ? instances.resolveDir(targetInst) : instanceDir
					const content = await downloadMod({
						token: auth.token,
						gameDir: targetDir,
						cacheDir,
						mcVersion: modMcVersion,
						etag: settings.get('modEtag'),
						onProgress: (data) => progress('mod', data),
						onLog: log,
					})
					progress('mod', { percent: 100, done: true })
					settings.set({
						modEtag: content?.etag || settings.get('modEtag'),
						modVersion: modInfo.version,
					})
					log('Мод успешно обновлён.')
				} else {
					log('Мод не требует обновления.')
				}
			} catch (error) {
				log(`Не удалось обновить мод: ${error.message}`)
			}
		}

		let account = activeAccount()
		if (!account) throw new Error('Добавь игровой ник во вкладке «Аккаунты»')
		account = await ensureAccountToken(account)

		const javaPath = await javaForVersion(target)
		await ensureGpus()

		// Играем в личной папке версии: свои моды, ресурспаки, шейдеры, сейвы.

		// Всегда освежаем статус лицензии / подписки перед запуском
		if (auth.isAuthorized) {
			try {
				const freshSession = await auth.refresh()
				send('auth:session', freshSession)
			} catch (e) {
				log(`Не удалось обновить статус сессии: ${e.message}`)
			}
		}

		const hasLicense = Boolean(auth.hasSubscription)
		// 1. Проверяем наличие лицензии у пользователя:
		// Если лицензии НЕТ — проверяем, установлена ли платная версия мода.
		// Если да — сносим её и больше ничего не делаем!
		let paidModRemoved = false
		if (isTargetLunacy && !hasLicense) {
			paidModRemoved = cleanupPaidModsIfNoSubscription(instanceDir)
			if (paidModRemoved) {
				log('[Безопасность] Платная версия мода удалена из-за отсутствия лицензии. Запуск без платной версии.')
			}
		}

		// 2. Если это сборка Lunacy и платный мод НЕ был только что удалён:
		// Проверяем наличие нужного визуала перед запуском (платникам — платка, фришникам — фришка)
		if (isTargetLunacy && instanceDir && !paidModRemoved) {
			const modsDir = path.join(instanceDir, 'mods')
			await fsp.mkdir(modsDir, { recursive: true })
			const installedMods = fs.existsSync(modsDir)
				? fs.readdirSync(modsDir).filter((f) => f.endsWith('.jar') && !f.endsWith('.disabled'))
				: []

			const hasPaidMod = installedMods.some((f) => isPaidLunacyMod(path.join(modsDir, f)))
			const hasFreeMod = installedMods.some((f) => isFreeLunacyMod(path.join(modsDir, f)))

			// Если у пользователя есть лицензия, но осталась старая free-версия — удаляем free
			if (hasLicense && hasFreeMod) {
				for (const f of installedMods) {
					const fp = path.join(modsDir, f)
					if (isFreeLunacyMod(fp)) {
						try { fs.unlinkSync(fp) } catch {}
					}
				}
			}

			const modMcVer = settings.get('modMcVersion') || target.replace(/^LunacyVisual-/, '') || '1.21.11'

			if (hasLicense) {
				// Платник: должен стоять платный мод
				if (!hasPaidMod) {
					log('[Lunacy] Платный визуал отсутствует в папке mods. Автоматически скачиваю с сервера перед запуском...')
					try {
						await downloadMod({
							token: auth.token,
							gameDir: instanceDir,
							cacheDir,
							mcVersion: modMcVer,
							onLog: log,
						})
						log('[Lunacy] Платный визуал успешно установлен.')
					} catch (err) {
						log(`[Lunacy] Не удалось восстановить платный визуал: ${err.message}`)
					}
				}
			} else {
				// Фришник: отдаём ТОЛЬКО фришную версию!
				if (!hasFreeMod) {
					log('[Lunacy] Бесплатный визуал отсутствует в папке mods. Скачиваю бесплатную версию...')
					try {
						await downloadFreeBuild({
							gameDir: instanceDir,
							cacheDir,
							mcVersion: modMcVer,
							onLog: log,
						})
						log('[Lunacy] Бесплатная версия мода успешно установлена.')
					} catch (err) {
						log(`[Lunacy] Не удалось скачать бесплатный мод: ${err.message}`)
					}
				}
			}

			// Проверка целостности Fabric loader библиотек
			const verState = verifyVersion(settings.get('gameDir'), target)
			if (!verState.ok) {
				log(`[Lunacy] Обнаружены повреждённые библиотеки Fabric (${verState.missing?.length || 0} шт.). Восстанавливаю...`)
				await repairVersion(target, { gameDir: settings.get('gameDir'), onLog: log })
				log('[Lunacy] Библиотеки Fabric проверены и восстановлены.')
			}
		}

		child = launchGame({
			settings: settings.all,
			account,
			versionId: target,
			instanceDir,
			javaPath,
			gpu: selectedGpu(),
			logFile: path.join(logDir, 'game-latest.log'),
			onLog: log,
			onExit: (code, reason) => {
				child = null
				updateDiscordPresence('play')
				send('game:state', { running: false, code, reason: reason || null })
				if (settings.get('afterLaunch') === 'hide' && win && !win.isDestroyed()) win.show()
			},
		})

		try {
			discordRpc.setActivity({
				state: `В игре: ${account.nickname} • Визуалы Lunacy`,
				details: `Сборка: ${target} • lunacyvisual.fun`,
				startTimestamp: Date.now(),
				largeImage: 'lunacy_logo',
				largeText: 'Lunacy Visuals • lunacyvisual.fun',
				smallImage: 'lunacy_logo',
				smallText: 'FPS Boost & Визуалы включены',
				buttons: [
					{ label: '🌐 Играть с визуалом', url: 'https://lunacyvisual.fun' },
					{ label: '✨ Сайт проекта', url: 'https://lunacyvisual.fun' },
				],
			})
		} catch {}

		send('game:state', { running: true, nickname: account.nickname })
		const after = settings.get('afterLaunch')
		if (after === 'hide') win?.hide()
		if (after === 'close') setTimeout(() => app.quit(), 4000)
		return { running: true, versionId: target, nickname: account.nickname }
	})

	handle('game:stop', async () => {
		if (!child) return { running: false }
		child.kill()
		child = null
		updateDiscordPresence('play')
		send('game:state', { running: false, code: 0 })
		return { running: false }
	})

	/* мониторинг серверов Minecraft */
	handle('server:ping', async ({ host, port } = {}) => {
		return await pingServer(host || 'mc.hypixel.net', port || 25565)
	})

	/* управление интеграцией Discord RPC */
	handle('discord:toggle', async ({ enabled } = {}) => {
		discordRpc.enabled = Boolean(enabled)
		if (discordRpc.enabled) {
			discordRpc.connect()
			updateDiscordPresence('play')
		} else {
			discordRpc.destroy()
		}
		return { ok: true, enabled: discordRpc.enabled }
	})

	handle('discord:view', async ({ view } = {}) => {
		updateDiscordPresence(view)
		return { ok: true }
	})

	handle('discord:setClientId', async ({ clientId } = {}) => {
		const cleanId = String(clientId || '').trim()
		settings.set({ discordClientId: cleanId })
		discordRpc.setClientId(cleanId)
		if (discordRpc.enabled) {
			updateDiscordPresence('play')
		}
		return { ok: true, clientId: discordRpc.clientId }
	})

	/* галерея скриншотов сборок */
	handle('screenshots:list', async ({ instanceId } = {}) => {
		const instance = resolveInstance(instanceId)
		if (!instance || !instance.dir) return []
		try {
			const gameDir = settings.get('gameDir')
			const dirs = [
				path.join(instance.dir, 'screenshots'),
				// Совместимость со снимками, сделанными до запуска через отдельные инстансы.
				path.join(gameDir, 'screenshots'),
				path.join(gameDir, 'versions', instance.versionId || '', 'screenshots'),
			]
			const seen = new Set()
			const files = dirs.flatMap((scDir) => {
				if (!fs.existsSync(scDir)) return []
				return fs.readdirSync(scDir)
				.filter((f) => /\.(png|jpg|jpeg|webp)$/i.test(f))
				.map((name) => {
					const full = path.join(scDir, name)
					if (seen.has(full.toLowerCase())) return null
					seen.add(full.toLowerCase())
					let size = 0
					let mtime = 0
					try {
						const st = fs.statSync(full)
						size = st.size
						mtime = st.mtimeMs
					} catch {}
					return {
						name,
						fullPath: full,
						size,
						 time: mtime,
					}
				})
				.filter(Boolean)
			})
				.sort((a, b) => b.time - a.time)
			return files.slice(0, 60)
		} catch {
			return []
		}
	})
	handle('screenshots:read', async ({ fullPath } = {}) => {
		if (!fullPath || !fs.existsSync(fullPath)) return null
		try {
			const buf = fs.readFileSync(fullPath)
			const ext = path.extname(fullPath).toLowerCase()
			const mime = ext === '.jpg' || ext === '.jpeg' ? 'image/jpeg' : ext === '.webp' ? 'image/webp' : 'image/png'
			return `data:${mime};base64,${buf.toString('base64')}`
		} catch {
			return null
		}
	})
	handle('screenshots:openFolder', async ({ instanceId } = {}) => {
		const instance = resolveInstance(instanceId)
		if (!instance || !instance.dir) return false
		const scDir = path.join(instance.dir, 'screenshots')
		fs.mkdirSync(scDir, { recursive: true })
		await shell.openPath(scDir)
		return true
	})
	handle('screenshots:openFile', async ({ fullPath } = {}) => {
		if (!fullPath || !fs.existsSync(fullPath)) return false
		await shell.openPath(fullPath)
		return true
	})
	handle('screenshots:delete', async ({ fullPath } = {}) => {
		if (!fullPath || !fs.existsSync(fullPath)) return false
		try {
			fs.unlinkSync(fullPath)
			return true
		} catch {
			return false
		}
	})
}


/* ------------------------------------------------------------------ старт */

const singleInstance = app.requestSingleInstanceLock()
if (!singleInstance) {
	app.quit()
} else {
	app.on('second-instance', () => {
		if (win) {
			if (win.isMinimized()) win.restore()
			win.focus()
		}
	})

	app.whenReady().then(async () => {
		const userData = app.getPath('userData')
		cacheDir = path.join(userData, 'cache')
		fs.mkdirSync(cacheDir, { recursive: true })

		logDir = path.join(userData, 'logs')
		fs.mkdirSync(logDir, { recursive: true })
		logFile = path.join(logDir, 'launcher-latest.log')
		try {
			fs.writeFileSync(logFile, `# Lunacy Launcher — старт ${new Date().toISOString()}\n`, 'utf8')
		} catch {
			/* лог-файл не критичен */
		}

		settings = new JsonStore(path.join(userData, 'settings.json'), defaultSettings(userData))
		accounts = new JsonStore(path.join(userData, 'accounts.json'), { items: [] })
		instancesStore = new JsonStore(path.join(userData, 'instances.json'), { items: [] })
		instances = new InstanceManager(instancesStore, () => settings.get('gameDir'))
		auth = new LunacyAuth({
			baseUrl: CONFIG.baseUrl,
			sessionFile: path.join(userData, 'lunacy-session.json'),
		})

		ensureGameDirs(settings.get('gameDir'))
		if (!auth.hasSubscription) {
			try {
				cleanupPaidModsIfNoSubscription()
			} catch {}
		}
		// Подхватываем версии, установленные до появления отдельных папок.
		try {
			instances.sync(listInstalled(settings.get('gameDir')))
		} catch {
			/* реестр сборок не критичен для старта */
		}

		// Ограничиваем ОЗУ реальным объёмом памяти ПК.
		const { maxGb } = memoryInfo()
		if (Number(settings.get('memoryMax')) > maxGb) settings.set({ memoryMax: maxGb })

		registerIpc()
		createWindow()

		// Discord Rich Presence инициализация
		try {
			discordRpc.onStatus = (status) => {
				if (status.activitySent) return
				const key = status.ready ? 'ready' : status.error && status.pipeId === 9 ? 'unavailable' : ''
				if (!key || key === discordStatusKey) return
				discordStatusKey = key
				if (status.ready) log(`Discord Rich Presence подключён (Application ${discordRpc.clientId})`)
				else log('Discord Rich Presence ожидает запуска настольного приложения Discord')
			}
			if (settings.get('discordRpc') !== false) {
				const customId = settings.get('discordClientId')
				if (customId) discordRpc.setClientId(customId)
				discordRpc.connect()
				updateDiscordPresence('play')
			}
		} catch {
			/* не критично */
		}

		// Фоново прогреваем списки Java и видеокарт.
		ensureJava().catch(() => {})
		ensureGpus().catch(() => {})

		app.on('activate', () => {
			if (BrowserWindow.getAllWindows().length === 0) createWindow()
		})
	})

	app.on('window-all-closed', () => {
		if (process.platform !== 'darwin') app.quit()
	})

	app.on('before-quit', () => {
		try {
			discordRpc.destroy()
		} catch {}
		if (child) {
			try {
				child.kill()
			} catch {
				/* ignore */
			}
		}
	})
}

