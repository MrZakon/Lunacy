import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import crypto from 'node:crypto'

/**
 * Простое JSON-хранилище с атомарной записью.
 */
export class JsonStore {
	constructor(filePath, defaults = {}) {
		this.filePath = filePath
		this.defaults = defaults
		this.data = this.#read()
	}

	#read() {
		try {
			const raw = fs.readFileSync(this.filePath, 'utf8')
			const parsed = JSON.parse(raw)
			return { ...structuredClone(this.defaults), ...parsed }
		} catch {
			return structuredClone(this.defaults)
		}
	}

	save() {
		fs.mkdirSync(path.dirname(this.filePath), { recursive: true })
		const tmp = `${this.filePath}.tmp`
		fs.writeFileSync(tmp, JSON.stringify(this.data, null, 2), 'utf8')
		fs.renameSync(tmp, this.filePath)
		return this.data
	}

	get all() {
		return this.data
	}

	get(key, fallback = undefined) {
		return this.data[key] ?? fallback
	}

	set(patch) {
		this.data = { ...this.data, ...patch }
		return this.save()
	}

	reset() {
		this.data = structuredClone(this.defaults)
		return this.save()
	}
}

export function defaultGameDir(userDataDir) {
	if (process.platform === 'win32') {
		const appData = process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming')
		return path.join(appData, '.lunacy')
	}
	if (process.platform === 'darwin') {
		return path.join(os.homedir(), 'Library', 'Application Support', 'lunacy')
	}
	return path.join(os.homedir(), '.lunacy')
}

export function defaultSettings(userDataDir) {
	const totalGb = Math.max(2, Math.round(os.totalmem() / 1024 ** 3))
	const recommended = Math.min(8, Math.max(2, Math.floor(totalGb / 2)))
	return {
		gameDir: defaultGameDir(userDataDir),
		javaPath: '',
		memoryMin: 1,
		memoryMax: recommended,
		jvmArgs: '-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -Dfile.encoding=UTF-8',
		width: 1280,
		height: 720,
		fullscreen: false,
		afterLaunch: 'hide', // hide | close | keep
		autoUpdateMod: true,
		checkUpdatesOnStart: true,
		keepLoggedIn: true,
		activeAccountId: null,
		// Выбранная установленная сборка (versions/<id>/<id>.json)
		selectedVersionId: null,
		// Активная сборка-папка (instances/Fabric 1.21.11)
		activeInstanceId: null,
		// Куда по умолчанию ставить моды/ресурспаки/шейдеры из Мастерской
		contentTargetId: null,
		// Версия MC для мода Lunacy (загрузчик Fabric)
		modMcVersion: '',
		// Выбранная видеокарта (если их несколько)
		gpuId: null,
		// Сборка с визуалом (Fabric + мод Lunacy)
		lunacyVersionId: null,
		// Настройки экрана и разрешения
		resolutionPreset: '1280x720',
		// Оформление и интерфейс
		themeAccent: 'emerald', // emerald | mint | violet | cyan | amber
		uiScale: '100', // 90 | 100 | 110 | 125
		bgGlow: true,
		soundEffects: true,
		// Производительность и JVM
		jvmPreset: 'g1gc', // g1gc | aikar | zgc | minimal | custom
		// Сеть и зеркала
		downloadThreads: 3,
		assetsMirror: 'mojang', // mojang | bmclapi
		// Интеграции
		discordRpc: true,
		discordClientId: '',
		// Установленная версия мода — уходит в ?current= при проверке обновлений
		modVersion: '0.0.0',
		modEtag: null,
		modSha256: null,
	}
}

export function offlineUuid(nickname) {
	// Оффлайн-UUID как в Minecraft: MD5 от "OfflinePlayer:<nick>" (версия 3).
	const hash = crypto.createHash('md5').update(`OfflinePlayer:${nickname}`).digest()
	hash[6] = (hash[6] & 0x0f) | 0x30
	hash[8] = (hash[8] & 0x3f) | 0x80
	const hex = hash.toString('hex')
	return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
