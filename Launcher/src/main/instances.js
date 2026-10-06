import fs from 'node:fs'
import path from 'node:path'

/**
 * Инстансы (сборки) — у каждой версии своя папка с модами, ресурспаками,
 * шейдерами, сейвами и конфигами:
 *
 *   <gameDir>/instances/Fabric 1.21.11/mods
 *   <gameDir>/instances/Fabric 1.21.11/resourcepacks
 *   <gameDir>/instances/Fabric 1.21.11/shaderpacks
 *   <gameDir>/instances/Fabric 1.21.11/saves
 *
 * А «пакеты» (тяжёлые общие файлы) остаются едиными для всех сборок:
 *
 *   <gameDir>/versions    — профили версий
 *   <gameDir>/libraries   — библиотеки
 *   <gameDir>/assets      — ресурсы игры
 *   <gameDir>/cache       — кэш загрузок
 *
 * Так одна и та же библиотека или ассет не качается по второму разу.
 */

/** Общие папки — одни на все сборки. */
export const SHARED_DIRS = ['versions', 'libraries', 'assets', 'cache', 'instances']

/** Личные папки каждой сборки. */
export const INSTANCE_DIRS = [
	'mods',
	'resourcepacks',
	'shaderpacks',
	'saves',
	'config',
	'screenshots',
	'logs',
]

/** Имя папки без запрещённых символов. */
export function safeFolderName(name) {
	const clean = String(name || '')
		.replace(/[<>:"/\\|?*\u0000-\u001f]/g, ' ')
		.replace(/\s+/g, ' ')
		.trim()
		.replace(/[. ]+$/, '')
	return clean || 'Instance'
}

/** Красивое имя сборки: «Fabric 1.21.11», «Vanilla 1.21.4», «Forge 1.20.1». */
export function suggestName({ loader = 'Vanilla', mc = '', versionId = '' } = {}) {
	const version = String(mc || versionId || '').trim()
	return safeFolderName(`${loader} ${version}`.trim())
}

export function instancesRoot(gameDir) {
	return path.join(gameDir, 'instances')
}

export function ensureSharedDirs(gameDir) {
	for (const sub of SHARED_DIRS) fs.mkdirSync(path.join(gameDir, sub), { recursive: true })
	return gameDir
}

export function ensureInstanceDirs(dir) {
	for (const sub of INSTANCE_DIRS) fs.mkdirSync(path.join(dir, sub), { recursive: true })
	return dir
}

function dirSize(dir) {
	let total = 0
	try {
		for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
			const full = path.join(dir, entry.name)
			if (entry.isDirectory()) total += dirSize(full)
			else {
				try {
					total += fs.statSync(full).size
				} catch {
					/* файл пропал — не страшно */
				}
			}
		}
	} catch {
		/* папки нет */
	}
	return total
}

function countFiles(dir) {
	try {
		return fs
			.readdirSync(dir)
			.filter((name) => !name.startsWith('.') && !name.endsWith('.part')).length
	} catch {
		return 0
	}
}

/**
 * Менеджер сборок поверх JsonStore ({ items: [...] }).
 * Каждый элемент: { id, name, dir, versionId, loader, mc, createdAt, lastPlayedAt }
 */
export class InstanceManager {
	constructor(store, getGameDir) {
		this.store = store
		this.getGameDir = getGameDir
	}

	get items() {
		return this.store.get('items', [])
	}

	save(items) {
		this.store.set({ items })
		return items
	}

	/** Абсолютный путь сборки (относительные пути считаются от instances/). */
	resolveDir(instance) {
		if (!instance) return null
		if (instance.dir && path.isAbsolute(instance.dir)) return instance.dir
		return path.join(instancesRoot(this.getGameDir()), safeFolderName(instance.dir || instance.name))
	}

	find(id) {
		return this.items.find((item) => item.id === id) || null
	}

	findByVersion(versionId, isLunacy = null) {
		return this.items.find((item) => {
			if (item.versionId !== versionId) return false
			if (isLunacy !== null) {
				const itemIsLun = Boolean(item.isLunacy || item.name?.toLowerCase().includes('lunacy'))
				return itemIsLun === isLunacy
			}
			return true
		}) || null
	}

	findByName(name) {
		const key = safeFolderName(name).toLowerCase()
		return this.items.find((item) => safeFolderName(item.name).toLowerCase() === key) || null
	}

	/** Свободное имя папки: «Fabric 1.21.11», «Fabric 1.21.11 (2)»… */
	uniqueName(name) {
		const base = safeFolderName(name)
		const root = instancesRoot(this.getGameDir())
		let candidate = base
		let index = 2
		while (this.findByName(candidate) || fs.existsSync(path.join(root, candidate))) {
			candidate = `${base} (${index})`
			index += 1
		}
		return candidate
	}

	/**
	 * Сборка для версии. Если её ещё нет — создаём папку вида
	 * <gameDir>/instances/Fabric 1.21.11 или <gameDir>/instances/LunacyVisual 1.21.11 и запоминаем.
	 */
	ensureFor({ versionId, loader = 'Vanilla', mc = '', name = '', isLunacy = null }) {
		const lunacyFlag = isLunacy !== null ? isLunacy : Boolean(name && name.toLowerCase().includes('lunacy'))

		// 1. Сначала ищем по точному имени (например, «LunacyVisual 1.21.11» или «Fabric 1.21.11»)
		let existing = name ? this.findByName(name) : null

		// 2. Если по имени не нашли, ищем по versionId строго с учётом типа (Lunacy vs чистый Fabric/Vanilla)
		if (!existing) {
			existing = this.items.find((item) => {
				if (item.versionId !== versionId) return false
				const itemIsLun = Boolean(item.isLunacy || item.name?.toLowerCase().includes('lunacy'))
				return itemIsLun === lunacyFlag
			}) || null
		}

		if (existing) {
			if (name && existing.name !== name) {
				const oldDir = this.resolveDir(existing)
				const newTitle = safeFolderName(name)
				const newDir = path.join(instancesRoot(this.getGameDir()), newTitle)
				if (fs.existsSync(oldDir) && oldDir !== newDir && !fs.existsSync(newDir)) {
					try {
						fs.renameSync(oldDir, newDir)
						existing.dir = newTitle
					} catch {}
				}
				existing.name = name
				existing.isLunacy = lunacyFlag
				this.save(this.items)
			}
			const dir = this.resolveDir(existing)
			ensureInstanceDirs(dir)
			return { ...existing, dir }
		}

		const title = this.uniqueName(name || suggestName({ loader, mc, versionId }))
		const dir = path.join(instancesRoot(this.getGameDir()), title)
		ensureInstanceDirs(dir)
		const instance = {
			id: `inst-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
			name: title,
			dir: title,
			versionId,
			loader,
			mc,
			isLunacy: lunacyFlag,
			createdAt: new Date().toISOString(),
			lastPlayedAt: null,
		}
		this.save([...this.items, instance])
		return { ...instance, dir }
	}

	/** Переименование меняет и папку на диске. */
	rename(id, nextName) {
		const instance = this.find(id)
		if (!instance) throw new Error('Сборка не найдена')
		const title = this.uniqueName(nextName)
		const from = this.resolveDir(instance)
		const to = path.join(instancesRoot(this.getGameDir()), title)
		if (fs.existsSync(from) && from !== to) fs.renameSync(from, to)
		else ensureInstanceDirs(to)
		const items = this.items.map((item) =>
			item.id === id ? { ...item, name: title, dir: title } : item,
		)
		this.save(items)
		return { ...this.find(id), dir: to }
	}

	/** Клонирование сборки (копирование всех модов, конфигов и параметров). */
	clone(id, newName) {
		const source = this.find(id)
		if (!source) throw new Error('Исходная сборка не найдена')
		const sourceDir = this.resolveDir(source)
		const title = this.uniqueName(newName || `${source.name} (Копия)`)
		const targetDir = path.join(instancesRoot(this.getGameDir()), title)
		
		ensureInstanceDirs(targetDir)
		if (fs.existsSync(sourceDir)) {
			try {
				fs.cpSync(sourceDir, targetDir, { recursive: true })
			} catch (err) {
				// Если fs.cpSync выдал ошибку на заблокированных файлах, продолжаем
			}
		}
		
		const cloned = {
			id: `inst-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
			name: title,
			dir: title,
			versionId: source.versionId,
			loader: source.loader,
			mc: source.mc,
			createdAt: new Date().toISOString(),
			lastPlayedAt: null,
		}
		this.save([...this.items, cloned])
		return { ...cloned, dir: targetDir }
	}

	/** Удаление сборки. deleteFiles — стереть папку с модами и сейвами. */
	remove(id, { deleteFiles = false } = {}) {
		const instance = this.find(id)
		if (!instance) return { removed: false }
		const dir = this.resolveDir(instance)
		if (deleteFiles && dir && fs.existsSync(dir)) fs.rmSync(dir, { recursive: true, force: true })
		this.save(this.items.filter((item) => item.id !== id))
		return { removed: true, dir, deletedFiles: deleteFiles }
	}

	touch(id) {
		const items = this.items.map((item) =>
			item.id === id ? { ...item, lastPlayedAt: new Date().toISOString() } : item,
		)
		this.save(items)
	}

	/** Список для интерфейса — с путями и счётчиками содержимого. */
	list() {
		return this.items.map((item) => {
			const dir = this.resolveDir(item)
			return {
				...item,
				dir,
				exists: fs.existsSync(dir),
				mods: countFiles(path.join(dir, 'mods')),
				resourcepacks: countFiles(path.join(dir, 'resourcepacks')),
				shaderpacks: countFiles(path.join(dir, 'shaderpacks')),
				sizeBytes: dirSize(dir),
			}
		})
	}

	/**
	 * Подхватывает сборки, установленные до появления инстансов,
	 * и чинит записи с пропавшими версиями.
	 */
	sync(installed = []) {
		for (const version of installed) {
			if (version.dependency) continue
			if (!this.findByVersion(version.id)) {
				this.ensureFor({
					versionId: version.id,
					loader: version.loader,
					mc: version.mc,
				})
			}
		}
		return this.list()
	}
}
