import fs from 'node:fs'
import path from 'node:path'
import { CONFIG } from './config.js'
import { ApiError, apiRequest, apiRequestRetry } from './http.js'

const USER_RE = /^[a-zA-Z0-9_]{3,32}$/
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const PLAN_TITLES = {
	free: 'Free',
	premium: 'Premium',
	lifetime: 'Lifetime',
}

function daysBetween(endsAt) {
	if (!endsAt) return null
	const end = new Date(endsAt).getTime()
	if (!Number.isFinite(end)) return null
	return Math.max(0, Math.ceil((end - Date.now()) / 86400000))
}

/**
 * Сайт отдаёт publicUser():
 *   { id, username, email, role, createdAt, plan, hasSubscription,
 *     subscription: { plan, status, startsAt, endsAt } | null }
 * Приводим к единому виду для UI.
 */
export function normalizeSubscription(user) {
	const raw = user?.subscription || null
	const active = Boolean(user?.hasSubscription)
	const plan = active ? raw?.plan || user?.plan || 'premium' : 'free'
	const endsAt = raw?.endsAt ?? null
	const lifetime = plan === 'lifetime' || (active && !endsAt)

	return {
		active,
		plan,
		status: active ? raw?.status || 'active' : 'none',
		startsAt: raw?.startsAt ?? null,
		endsAt,
		daysLeft: lifetime ? null : daysBetween(endsAt),
		lifetime,
		canDownloadMod: active,
		serverTime: null,
	}
}

export function planLabel(subscription) {
	if (!subscription || !subscription.active) return 'Free • без подписки'
	const title = PLAN_TITLES[subscription.plan] || subscription.plan
	if (subscription.lifetime) return `${title} • навсегда`
	if (typeof subscription.daysLeft === 'number') {
		return `${title} • ${subscription.daysLeft} дн.`
	}
	return title
}

export class LunacyAuth {
	#file
	#session = null

	constructor({ baseUrl = CONFIG.baseUrl, sessionFile } = {}) {
		this.baseUrl = baseUrl
		this.#file = sessionFile || path.join(process.cwd(), 'lunacy-session.json')
		this.#session = this.#readFile()
	}

	get session() {
		return this.#session
	}

	get token() {
		return this.#session?.token || null
	}

	get isAuthorized() {
		return Boolean(this.#session?.token)
	}

	get hasSubscription() {
		return Boolean(this.#session?.hasSubscription)
	}

	/** POST /api/auth/login — login принимает и ник, и e-mail. */
	async login(login, password) {
		const value = String(login || '').trim()
		if (!value || !password) {
			throw new ApiError('Введи логин и пароль', { status: 400 })
		}
		const payload = await apiRequestRetry(CONFIG.auth.login, {
			method: 'POST',
			body: { login: value, password: String(password) },
		})
		return this.#persist(payload.token, payload.data)
	}

	/** POST /api/auth/register — сразу возвращает токен (7 дней). */
	async register({ username, email, password }) {
		const nick = String(username || '').trim()
		const mail = String(email || '').trim().toLowerCase()
		const pass = String(password || '')

		const fields = []
		if (!USER_RE.test(nick)) {
			fields.push({ field: 'username', message: 'Ник 3–32, латиница/цифры/_' })
		}
		if (!EMAIL_RE.test(mail)) {
			fields.push({ field: 'email', message: 'Некорректный email' })
		}
		if (pass.length < 6) {
			fields.push({ field: 'password', message: 'Пароль минимум 6 символов' })
		}
		if (fields.length) {
			throw new ApiError('Проверь поля формы', { status: 400, fields })
		}

		const payload = await apiRequestRetry(CONFIG.auth.register, {
			method: 'POST',
			body: { username: nick, email: mail, password: pass },
		})

		// Токен выдаётся сразу, но на всякий случай подстрахуемся входом.
		if (payload?.token) return this.#persist(payload.token, payload.data)
		return this.login(nick, pass)
	}

	/** GET /api/auth/me — свежий профиль + подписка. */
	async me() {
		if (!this.token) throw new ApiError('Нужна авторизация', { status: 401 })
		const payload = await apiRequestRetry(CONFIG.auth.me, { token: this.token })
		return this.#persist(this.token, payload.data)
	}

	/** Алиас: рефреш-эндпоинта у сайта нет, просто тянем /me. */
	async refresh() {
		return this.me()
	}

	/** Подписка отдаётся вместе с профилем. */
	async subscription() {
		const session = await this.me()
		return session.subscription
	}

	/** POST /api/auth/redeem — активация ключа. */
	async redeem(code) {
		const value = String(code || '').trim().toUpperCase()
		if (!value) throw new ApiError('Введи ключ', { status: 400 })
		if (!this.token) throw new ApiError('Нужна авторизация', { status: 401 })
		const payload = await apiRequest(CONFIG.auth.redeem, {
			method: 'POST',
			token: this.token,
			body: { code: value },
		})
		return this.#persist(this.token, payload.data)
	}

	/** POST /api/auth/password — смена пароля. */
	async changePassword(currentPassword, newPassword) {
		if (!this.token) throw new ApiError('Нужна авторизация', { status: 401 })
		await apiRequest(CONFIG.auth.password, {
			method: 'POST',
			token: this.token,
			body: {
				currentPassword: String(currentPassword || ''),
				newPassword: String(newPassword || ''),
			},
		})
		return true
	}

	/** При старте лаунчера: подтянуть ник и подписку с сервера. */
	async restore() {
		if (!this.#session?.token) return null
		try {
			return await this.me()
		} catch (error) {
			if (error instanceof ApiError && error.isAuthError) {
				this.logoutLocal()
				return null
			}
			// нет сети — работаем на кеше
			return { ...this.#session, offline: true }
		}
	}

	/** Выход — эндпоинта на сервере нет, удаляем токен локально. */
	async logout() {
		this.logoutLocal()
		return true
	}

	logoutLocal() {
		this.#session = null
		try {
			if (fs.existsSync(this.#file)) fs.rmSync(this.#file)
		} catch {
			/* ignore */
		}
	}

	#normalize(token, user) {
		const subscription = normalizeSubscription(user)
		return {
			token,
			userId: user?.id ?? null,
			username: user?.username || '',
			email: user?.email || '',
			role: user?.role || 'user',
			createdAt: user?.createdAt || null,
			plan: subscription.plan,
			planLabel: planLabel(subscription),
			subscription,
			hasSubscription: subscription.active,
			offline: false,
			updatedAt: new Date().toISOString(),
		}
	}

	#persist(token, user) {
		const session = this.#normalize(token, user)
		this.#session = session
		try {
			fs.mkdirSync(path.dirname(this.#file), { recursive: true })
			fs.writeFileSync(this.#file, JSON.stringify(session, null, 2), {
				mode: 0o600,
			})
		} catch (error) {
			console.warn('[auth] не смог сохранить сессию:', error.message)
		}
		return session
	}

	#readFile() {
		try {
			if (!fs.existsSync(this.#file)) return null
			const parsed = JSON.parse(fs.readFileSync(this.#file, 'utf8'))
			return parsed?.token ? parsed : null
		} catch {
			return null
		}
	}
}

export default LunacyAuth
