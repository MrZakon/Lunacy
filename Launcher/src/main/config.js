/**
 * Все эндпоинты взяты из исходников сайта (server/routes/*.js):
 *   /api/auth/{register,login,me,redeem,password}
 *   /api/client/{info,download/...}
 *   /api/{status,health,mod,changelog}
 * Никаких /api/launcher/* на сервере нет.
 */

export const APP_NAME = 'Lunacy Launcher'

export const BASE_URL = (
	process.env.LUNACY_BASE_URL || 'https://lunacyvisual.fun'
).replace(/\/+$/, '')

export const CONFIG = {
	baseUrl: BASE_URL,

	web: {
		site: BASE_URL,
		register: `${BASE_URL}/register`,
		login: `${BASE_URL}/login`,
		account: `${BASE_URL}/account`,
		buy: `${BASE_URL}/account`,
		support: 'mailto:lunacyvisual@mail.ru',
	},

	// Авторизация — server/routes/auth.js
	auth: {
		register: '/api/auth/register', // { username, email, password } -> 201 { token, data }
		login: '/api/auth/login', // { login, password }        -> 200 { token, data }
		me: '/api/auth/me', // Bearer                     -> { data }
		redeem: '/api/auth/redeem', // Bearer { code }              -> { data }
		password: '/api/auth/password', // Bearer { currentPassword, newPassword }
	},

	// Клиент/загрузки — server/routes/client.js
	client: {
		info: '/api/client/info', // auth опционально
		modMeta: '/api/client/download/mod', // Bearer + подписка
		modFile: '/api/client/download/mod/file', // Bearer + подписка
		launcherMeta: '/api/client/download/launcher', // без авторизации
		launcherFile: '/api/client/download/launcher/file', // без авторизации
		freeMeta: '/api/client/download/free', // бесплатная сборка
		freeFile: '/api/client/download/free/file',
	},

	// Служебное — server/routes/api.js + app.js
	system: {
		status: '/api/status',
		health: '/api/health',
		mod: '/api/mod',
		changelog: '/api/changelog',
	},

	network: {
		timeoutMs: 20000,
		downloadTimeoutMs: 900000,
		userAgent: 'LunacyLauncher/2.1.0 (+https://lunacyvisual.fun)',
		retries: 2,
	},
}

export function url(pathname, query) {
	const full = new URL(
		pathname.startsWith('http') ? pathname : `${BASE_URL}${pathname}`,
	)
	if (query) {
		for (const [key, value] of Object.entries(query)) {
			if (value !== undefined && value !== null && value !== '') {
				full.searchParams.set(key, String(value))
			}
		}
	}
	return full.toString()
}

export default CONFIG
