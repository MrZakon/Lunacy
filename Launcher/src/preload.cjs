const { contextBridge, ipcRenderer } = require('electron')

/**
 * Безопасный мост renderer <-> main.
 * В renderer доступно только то, что перечислено ниже. Токен наружу не отдаётся.
 *
 *   const { session } = await window.lunacy.login(nick, pass)
 */
const api = {
	// окно
	window: {
		minimize: () => ipcRenderer.invoke('window:minimize'),
		maximize: () => ipcRenderer.invoke('window:maximize'),
		close: () => ipcRenderer.invoke('window:close'),
	},

	// общее
	info: () => ipcRenderer.invoke('app:info'),
	ping: () => ipcRenderer.invoke('app:ping'),
	openExternal: (url) => ipcRenderer.invoke('app:openExternal', url),
	openPath: (target) => ipcRenderer.invoke('app:openPath', target),
	openFolder: (folder, instanceId) => ipcRenderer.invoke('app:openFolder', { folder, instanceId }),

	// авторизация на сайте
	login: (login, password) => ipcRenderer.invoke('auth:login', { login, password }),
	register: (payload) => ipcRenderer.invoke('auth:register', payload),
	restore: () => ipcRenderer.invoke('auth:restore'),
	refresh: () => ipcRenderer.invoke('auth:refresh'),
	redeem: (code) => ipcRenderer.invoke('auth:redeem', code),
	logout: () => ipcRenderer.invoke('auth:logout'),
	subscription: () => ipcRenderer.invoke('subscription:get'),

	// менеджер игровых аккаунтов (offline = пиратка, online = лицензия)
	accounts: {
		list: () => ipcRenderer.invoke('accounts:list'),
		add: (nickname, type = 'offline') => ipcRenderer.invoke('accounts:add', { nickname, type }),
		remove: (id) => ipcRenderer.invoke('accounts:remove', id),
		select: (id) => ipcRenderer.invoke('accounts:select', id),
		refresh: (id) => ipcRenderer.invoke('accounts:refresh', id),
		// лицензия Minecraft через Microsoft / Xbox Live
		microsoft: {
			start: () => ipcRenderer.invoke('accounts:msStart'),
			poll: () => ipcRenderer.invoke('accounts:msPoll'),
			cancel: () => ipcRenderer.invoke('accounts:msCancel'),
		},
	},

	// настройки и железо
	settings: {
		get: () => ipcRenderer.invoke('settings:get'),
		set: (patch) => ipcRenderer.invoke('settings:set', patch),
		reset: () => ipcRenderer.invoke('settings:reset'),
		pickFolder: () => ipcRenderer.invoke('settings:pickFolder'),
		pickJava: () => ipcRenderer.invoke('settings:pickJava'),
	},
	storage: {
		clean: (target) => ipcRenderer.invoke('storage:clean', { target }),
	},
	system: {
		info: () => ipcRenderer.invoke('system:info'),
		diagnose: () => ipcRenderer.invoke('system:diagnose'),
		java: (force = false) => ipcRenderer.invoke('java:list', force),
		gpu: (force = false) => ipcRenderer.invoke('gpu:list', force),
	},

	// версии Minecraft: Vanilla / Fabric / Forge
	versions: {
		installed: () => ipcRenderer.invoke('versions:installed'),
		remote: (payload) => ipcRenderer.invoke('versions:remote', payload),
		builds: (payload) => ipcRenderer.invoke('versions:builds', payload),
		install: (payload) => ipcRenderer.invoke('versions:install', payload),
		select: (versionId) => ipcRenderer.invoke('versions:select', versionId),
		verify: (versionId) => ipcRenderer.invoke('versions:verify', { versionId }),
		repair: (versionId) => ipcRenderer.invoke('versions:repair', { versionId }),
		remove: (versionId, withInstance = true) =>
			ipcRenderer.invoke('versions:remove', { versionId, withInstance }),
		// сборка с визуалом: Fabric + мод Lunacy
		lunacy: (mcVersion) => ipcRenderer.invoke('lunacy:install', { mcVersion }),
		restoreLunacy: (mcVersion) => ipcRenderer.invoke('lunacy:restore', { mcVersion }),
	},

	// сборки-папки: у каждой версии свои моды, ресурспаки, шейдеры, сейвы
	instances: {
		list: () => ipcRenderer.invoke('instances:list'),
		create: (versionId, name) => ipcRenderer.invoke('instances:create', { versionId, name }),
		clone: (id, name) => ipcRenderer.invoke('instances:clone', { id, name }),
		rename: (id, name) => ipcRenderer.invoke('instances:rename', { id, name }),
		remove: (id, deleteFiles = true) => ipcRenderer.invoke('instances:remove', { id, deleteFiles }),
		open: (id, sub) => ipcRenderer.invoke('instances:open', { id, sub }),
		select: (id, target = false) => ipcRenderer.invoke('instances:select', { id, target }),
	},

	// данные с сайта (версии мода, загрузчик, подписка)
	clientInfo: () => ipcRenderer.invoke('client:info'),

	// загрузки Lunacy
	download: {
		mod: (mcVersion) => ipcRenderer.invoke('download:mod', { mcVersion }),
		free: (mcVersion) => ipcRenderer.invoke('download:free', { mcVersion }),
	},

	// каталог модов и ресурспаков
	catalog: {
		kinds: () => ipcRenderer.invoke('catalog:kinds'),
		providers: () => ipcRenderer.invoke('catalog:providers'),
		search: (payload) => ipcRenderer.invoke('catalog:search', payload),
		details: (slugOrId) => ipcRenderer.invoke('catalog:details', { slugOrId }),
		// payload.instanceId — в какую сборку скачивать файл
		install: (payload) => ipcRenderer.invoke('catalog:install', payload),
		installed: (instanceId) => ipcRenderer.invoke('catalog:installed', { instanceId }),
		toggleMod: (payload) => ipcRenderer.invoke('catalog:toggleMod', payload),
		importFile: (payload) => ipcRenderer.invoke('catalog:importFile', payload),
		remove: (kind, name, instanceId) =>
			ipcRenderer.invoke('catalog:remove', { kind, name, instanceId }),
	},

	// мониторинг Minecraft серверов
	server: {
		ping: (host, port) => ipcRenderer.invoke('server:ping', { host, port }),
	},

	// Discord RPC
	discord: {
		toggle: (enabled) => ipcRenderer.invoke('discord:toggle', { enabled }),
		setView: (view) => ipcRenderer.invoke('discord:view', { view }),
		setClientId: (clientId) => ipcRenderer.invoke('discord:setClientId', { clientId }),
	},

	// скриншоты сборок
	screenshots: {
		list: (instanceId) => ipcRenderer.invoke('screenshots:list', { instanceId }),
		read: (fullPath) => ipcRenderer.invoke('screenshots:read', { fullPath }),
		openFolder: (instanceId) => ipcRenderer.invoke('screenshots:openFolder', { instanceId }),
		openFile: (fullPath) => ipcRenderer.invoke('screenshots:openFile', { fullPath }),
		delete: (fullPath) => ipcRenderer.invoke('screenshots:delete', { fullPath }),
	},

	// обновление лаунчера
	update: {
		check: () => ipcRenderer.invoke('update:check'),
		mod: () => ipcRenderer.invoke('update:mod'),
		download: (version) => ipcRenderer.invoke('update:download', { version }),
		install: () => ipcRenderer.invoke('update:install'),
	},

	// консоль лаунчера
	logs: {
		get: () => ipcRenderer.invoke('logs:get'),
		clear: () => ipcRenderer.invoke('logs:clear'),
		open: () => ipcRenderer.invoke('logs:open'),
		file: () => ipcRenderer.invoke('logs:file'),
	},

	// игра
	game: {
		launch: (versionId) => ipcRenderer.invoke('game:launch', { versionId }),
		stop: () => ipcRenderer.invoke('game:stop'),
	},


	// события из main
	onLog: (cb) => subscribe('launcher:log', cb),
	onProgress: (cb) => subscribe('download:progress', cb),
	onGameState: (cb) => subscribe('game:state', cb),
	onSession: (cb) => subscribe('auth:session', cb),
}

function subscribe(channel, cb) {
	const handler = (_event, payload) => cb(payload)
	ipcRenderer.on(channel, handler)
	return () => ipcRenderer.removeListener(channel, handler)
}

contextBridge.exposeInMainWorld('lunacy', api)
