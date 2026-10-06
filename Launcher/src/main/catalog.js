import fs from 'node:fs'
import path from 'node:path'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'

/**
 * Lunacy Launcher 2.1.0 — Мастерская контента.
 * Официальный источник: Modrinth v2 API (https://api.modrinth.com/v2).
 * Без ключей, без сторонних зеркал, быстрая загрузка напрямую.
 */

const UA = 'LunacyLauncher/2.1.0 (+https://lunacyvisual.fun)'
const MODRINTH_API = 'https://api.modrinth.com/v2'
const MODRINTH_SITE = 'https://modrinth.com'

export const CATALOG_KINDS = {
	mod: { projectType: 'mod', dir: 'mods', title: 'Моды' },
	resourcepack: { projectType: 'resourcepack', dir: 'resourcepacks', title: 'Ресурспаки' },
	shader: { projectType: 'shader', dir: 'shaderpacks', title: 'Шейдеры' },
}

export const CATALOG_CATEGORIES = [
	{ id: 'all', title: 'Все' },
	{ id: 'optimization', title: 'Оптимизация' },
	{ id: 'decoration', title: 'Графика и визуал' },
	{ id: 'utility', title: 'Утилиты' },
	{ id: 'equipment', title: 'Интерфейс и HUD' },
	{ id: 'adventure', title: 'Приключения' },
	{ id: 'technology', title: 'Технологии' },
	{ id: 'library', title: 'Библиотеки' },
]

export function listProviders() {
	return [
		{
			id: 'modrinth',
			title: 'Modrinth',
			needsKey: false,
			mirror: false,
		},
	]
}

async function getJson(url, { timeoutMs = 20000, headers = {}, label = 'Modrinth' } = {}) {
	const controller = new AbortController()
	const timer = setTimeout(() => controller.abort(), timeoutMs)
	try {
		const response = await fetch(url, {
			signal: controller.signal,
			headers: { Accept: 'application/json', 'User-Agent': UA, ...headers },
		})
		if (!response.ok) throw new Error(`${label}: HTTP ${response.status}`)
		return await response.json()
	} catch (error) {
		if (error.name === 'AbortError') throw new Error(`${label} не отвечает — проверь интернет`)
		throw error
	} finally {
		clearTimeout(timer)
	}
}

/* ------------------------------------------------------------------ поиск */

export async function searchCatalog({
	kind = 'mod',
	query = '',
	mcVersion = null,
	loader = null,
	category = null,
	sort = 'downloads', // downloads | relevance | updated | newest
	limit = 24,
	offset = 0,
} = {}) {
	const meta = CATALOG_KINDS[kind] || CATALOG_KINDS.mod
	const facets = [[`project_type:${meta.projectType}`]]

	if (mcVersion) facets.push([`versions:${mcVersion}`])
	if (loader && meta.projectType === 'mod') {
		const l = String(loader).toLowerCase()
		if (l !== 'auto' && l !== 'any') facets.push([`categories:${l}`])
	}
	if (category && category !== 'all') {
		facets.push([`categories:${category}`])
	}

	const indexMap = {
		downloads: 'downloads',
		relevance: 'relevance',
		updated: 'updated',
		newest: 'newest',
	}
	const index = query ? 'relevance' : (indexMap[sort] || 'downloads')

	const params = new URLSearchParams({
		limit: String(limit),
		offset: String(offset),
		index,
		facets: JSON.stringify(facets),
	})
	if (query) params.set('query', query)

	const data = await getJson(`${MODRINTH_API}/search?${params.toString()}`, {
		label: 'Мастерская (Modrinth)',
	})

	return {
		total: data.total_hits || 0,
		kind,
		provider: 'modrinth',
		items: (data.hits || []).map((hit) => ({
			id: hit.project_id,
			slug: hit.slug,
			title: hit.title,
			description: hit.description,
			author: hit.author,
			downloads: hit.downloads || 0,
			follows: hit.follows || 0,
			icon: hit.icon_url || null,
			categories: hit.categories || [],
			versions: hit.versions || [],
			clientSide: hit.client_side || 'optional',
			serverSide: hit.server_side || 'optional',
			latestVersion: hit.latest_version || null,
			dateModified: hit.date_modified || null,
			provider: 'modrinth',
			pageUrl: `${MODRINTH_SITE}/${meta.projectType}/${hit.slug}`,
		})),
	}
}

/** Получение полных сведений о проекте (описание, скриншоты, галерея). */
export async function fetchProjectDetails(slugOrId) {
	const data = await getJson(`${MODRINTH_API}/project/${slugOrId}`, {
		label: 'Информация о моде',
	})
	return {
		id: data.id,
		slug: data.slug,
		title: data.title,
		description: data.description,
		body: data.body || '',
		iconUrl: data.icon_url || null,
		downloads: data.downloads || 0,
		followers: data.followers || 0,
		categories: data.categories || [],
		loaders: data.loaders || [],
		gameVersions: data.game_versions || [],
		license: data.license?.name || data.license?.id || 'Не указана',
		clientSide: data.client_side || 'optional',
		serverSide: data.server_side || 'optional',
		gallery: (data.gallery || []).map((item) => ({
			url: item.url,
			rawUrl: item.raw_url || item.url,
			title: item.title || '',
			description: item.description || '',
		})),
		issuesUrl: data.issues_url || null,
		sourceUrl: data.source_url || null,
		wikiUrl: data.wiki_url || null,
		pageUrl: `${MODRINTH_SITE}/mod/${data.slug}`,
	}
}

/* ------------------------------------------------------------------ выбор файла */

function noFileError(mcVersion, loader, kind) {
	const withLoader = loader && kind === 'mod' ? ` (${loader})` : ''
	return mcVersion
		? `Нет файлов под Minecraft ${mcVersion}${withLoader} — попробуй другую версию`
		: 'Нет подходящих файлов для этой версии игры'
}

function modrinthMatches(version, { mcVersion, loader, kind }) {
	if (mcVersion) {
		const games = (version.game_versions || []).map((item) => String(item))
		if (!games.includes(String(mcVersion))) return false
	}
	if (loader && kind === 'mod') {
		const want = String(loader).toLowerCase()
		if (want !== 'auto' && want !== 'any') {
			const loaders = (version.loaders || []).map((item) => String(item).toLowerCase())
			if (loaders.length && !loaders.includes(want)) return false
		}
	}
	return true
}

function sortModrinthVersions(versions) {
	const rank = (item) => (String(item.version_type || 'release') === 'release' ? 0 : 1)
	return [...versions].sort((a, b) => {
		if (rank(a) !== rank(b)) return rank(a) - rank(b)
		return new Date(b.date_published || 0) - new Date(a.date_published || 0)
	})
}

export async function resolveCatalogFile({ projectId, mcVersion, loader, kind = 'mod' }) {
	const params = new URLSearchParams()
	if (mcVersion) params.set('game_versions', JSON.stringify([String(mcVersion)]))
	if (loader && kind === 'mod' && loader !== 'auto' && loader !== 'any') {
		params.set('loaders', JSON.stringify([String(loader).toLowerCase()]))
	}

	const query = params.toString()
	const filter = { mcVersion, loader, kind }

	const filtered = await getJson(
		`${MODRINTH_API}/project/${projectId}/version${query ? `?${query}` : ''}`,
		{ label: 'Modrinth' },
	)
	let matching = (filtered || []).filter((item) => modrinthMatches(item, filter))

	if (!matching.length && query) {
		const all = await getJson(`${MODRINTH_API}/project/${projectId}/version`, {
			label: 'Modrinth',
		})
		matching = (all || []).filter((item) => modrinthMatches(item, filter))
	}

	if (!matching.length) throw new Error(noFileError(mcVersion, loader, kind))

	const version = sortModrinthVersions(matching)[0]
	const file = version.files.find((item) => item.primary) || version.files[0]
	if (!file) throw new Error('В релизе нет файлов')

	return {
		versionId: version.id,
		versionNumber: version.version_number,
		gameVersions: version.game_versions,
		loaders: version.loaders,
		fileName: file.filename,
		url: file.url,
		size: file.size,
		datePublished: version.date_published,
	}
}

/* ------------------------------------------------------------------ скачивание */

export async function downloadCatalogItem({
	projectId,
	kind = 'mod',
	mcVersion,
	loader,
	gameDir,
	targetDir,
	onProgress,
	onLog,
}) {
	const meta = CATALOG_KINDS[kind] || CATALOG_KINDS.mod
	const target = await resolveCatalogFile({
		projectId,
		mcVersion,
		loader,
		kind,
	})
	const destDir = path.join(targetDir || gameDir, meta.dir)
	fs.mkdirSync(destDir, { recursive: true })
	const dest = path.join(destDir, path.basename(target.fileName))

	onLog?.(
		`Скачиваю ${target.fileName}` +
			`${mcVersion ? ` для Minecraft ${mcVersion}` : ''}` +
			`${loader && kind === 'mod' ? ` (${loader})` : ''}…`,
	)
	const response = await fetch(target.url, { headers: { 'User-Agent': UA } })
	if (!response.ok) throw new Error(`Не удалось скачать файл (HTTP ${response.status})`)

	const total = Number(response.headers.get('content-length') || target.size || 0)
	let received = 0
	const body = Readable.fromWeb(response.body)
	body.on('data', (chunk) => {
		received += chunk.length
		onProgress?.({
			received,
			total: total || null,
			percent: total ? Math.round((received / total) * 100) : null,
		})
	})

	const tmp = `${dest}.part`
	await pipeline(body, fs.createWriteStream(tmp))
	fs.rmSync(dest, { force: true })
	fs.renameSync(tmp, dest)

	onLog?.(`Готово: ${meta.dir}/${target.fileName}`)
	return { file: dest, dir: destDir, provider: 'modrinth', ...target }
}

/* ----------------------------------------------------------- установленный контент */

export function listInstalledContent(contentDir) {
	const result = {}
	for (const [kind, meta] of Object.entries(CATALOG_KINDS)) {
		const dir = path.join(contentDir, meta.dir)
		try {
			result[kind] = fs
				.readdirSync(dir)
				.filter((name) => !name.startsWith('.') && !name.endsWith('.part'))
				.map((name) => {
					const full = path.join(dir, name)
					let size = 0
					try {
						size = fs.statSync(full).size
					} catch {
						/* ignore */
					}
					const isMod = kind === 'mod'
					const isExplicitDisabled = name.endsWith('.disabled')
					return {
						name,
						displayName: isExplicitDisabled ? name.replace(/\.disabled$/, '') : name,
						enabled: isMod ? !isExplicitDisabled : true,
						size,
						dir: meta.dir,
						path: full,
					}
				})
		} catch {
			result[kind] = []
		}
	}
	return result
}

export function toggleModEnabled(contentDir, filename) {
	const safeName = path.basename(String(filename))
	const modsDir = path.join(contentDir, 'mods')
	const src = path.join(modsDir, safeName)
	if (!fs.existsSync(src)) throw new Error(`Файл ${safeName} не найден`)

	let targetName
	let enabled
	if (safeName.endsWith('.disabled')) {
		targetName = safeName.replace(/\.disabled$/, '')
		enabled = true
	} else {
		targetName = `${safeName}.disabled`
		enabled = false
	}
	const dst = path.join(modsDir, targetName)
	fs.renameSync(src, dst)
	return { oldName: safeName, newName: targetName, enabled }
}

export function importContentFile(contentDir, sourcePath, explicitKind) {
	if (!fs.existsSync(sourcePath)) {
		throw new Error('Указанный файл не существует')
	}
	const filename = path.basename(sourcePath)
	let targetKind = explicitKind
	if (!targetKind) {
		const lower = filename.toLowerCase()
		if (lower.endsWith('.jar')) {
			targetKind = 'mod'
		} else if (lower.includes('shader') || lower.endsWith('.zip')) {
			targetKind = lower.includes('shader') ? 'shader' : 'resourcepack'
		} else {
			targetKind = 'mod'
		}
	}

	const meta = CATALOG_KINDS[targetKind] || CATALOG_KINDS.mod
	const targetDir = path.join(contentDir, meta.dir)
	fs.mkdirSync(targetDir, { recursive: true })
	const targetPath = path.join(targetDir, filename)
	fs.copyFileSync(sourcePath, targetPath)

	return {
		name: filename,
		kind: targetKind,
		path: targetPath,
		size: fs.statSync(targetPath).size,
	}
}

export function removeContentFile(contentDir, kind, name) {
	const meta = CATALOG_KINDS[kind] || CATALOG_KINDS.mod
	const safeName = path.basename(String(name))
	const file = path.join(contentDir, meta.dir, safeName)
	fs.rmSync(file, { force: true })
	return { removed: safeName }
}
