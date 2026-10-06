import { getHwid } from './hwid.js'
import { CONFIG, url } from './config.js'

/** Ошибка API. Сайт всегда отвечает { success:false, error:"текст" }. */
export class ApiError extends Error {
	constructor(message, options = {}) {
		super(message)
		this.name = 'ApiError'
		this.status = options.status ?? 0
		this.code = options.code ?? null
		this.fields = options.fields ?? null
		this.retryAfter = options.retryAfter ?? null
		this.network = Boolean(options.network)
		this.buyUrl = options.buyUrl ?? null
		this.payload = options.payload ?? null
	}

	get isAuthError() {
		return this.status === 401
	}

	get isSubscriptionError() {
		return this.status === 403
	}
}

const RAW_MESSAGES = {
	Unauthorized: 'Сессия истекла — войди снова',
	'Invalid token': 'Токен недействителен — войди снова',
	'Validation failed': 'Проверь поля формы',
	'Too many attempts': 'Слишком много попыток — подожди немного',
	'Too many requests': 'Слишком много запросов — подожди немного',
	'User not found': 'Аккаунт не найден',
	'Not found': 'Метод не найден на сервере',
	'Download failed': 'Сервер не смог отдать файл',
	'Admin only': 'Нужны права администратора',
}

/** Превращает ответ сервера в понятный текст для UI. */
export function describeError(status, raw, fallback) {
	const text = String(raw || '').trim()
	if (RAW_MESSAGES[text]) return RAW_MESSAGES[text]
	if (/database unavailable/i.test(text)) return 'Сервер на техработах — база недоступна'
	// сервер часто отвечает уже по-русски — показываем как есть
	if (text && /[а-яё]/i.test(text)) return text
	if (text) return text

	switch (status) {
		case 400:
			return 'Неверные данные запроса'
		case 401:
			return 'Нужна авторизация'
		case 403:
			return 'Нет активной подписки'
		case 404:
			return 'Файл или метод не найден на сервере'
		case 409:
			return 'Ник или e-mail уже заняты'
		case 429:
			return 'Слишком много запросов — подожди немного'
		case 500:
			return 'Ошибка на сервере'
		case 502:
		case 503:
			return 'Сервер на техработах'
		default:
			return fallback || 'Неизвестная ошибка'
	}
}

function parseRetryAfter(response) {
	const header =
		response.headers.get('retry-after') ||
		response.headers.get('ratelimit-reset') ||
		response.headers.get('x-ratelimit-reset')
	if (!header) return null
	const seconds = Number(header)
	return Number.isFinite(seconds) ? Math.max(1, Math.round(seconds)) : null
}

/**
 * Запрос к API. Распаковывает { success, data, token, ... }.
 */
export async function apiRequest(pathname, options = {}) {
	const {
		method = 'GET',
		token = null,
		body = null,
		query = null,
		timeoutMs = CONFIG.network.timeoutMs,
		raw = false,
	} = options

	const controller = new AbortController()
	const timer = setTimeout(() => controller.abort(), timeoutMs)

	const headers = {
		Accept: 'application/json',
		'User-Agent': CONFIG.network.userAgent,
		'X-HWID': getHwid(),
	}
	if (token) headers.Authorization = `Bearer ${token}`
	if (body !== null) headers['Content-Type'] = 'application/json'

	let response
	try {
		response = await fetch(url(pathname, query), {
			method,
			headers,
			body: body === null ? undefined : JSON.stringify(body),
			signal: controller.signal,
		})
	} catch (error) {
		clearTimeout(timer)
		throw new ApiError(
			error.name === 'AbortError'
				? 'Сервер не отвечает — превышено время ожидания'
				: 'Нет связи с сервером lunacyvisual.fun',
			{ network: true },
		)
	}
	clearTimeout(timer)

	const text = await response.text()
	let payload = null
	if (text) {
		try {
			payload = JSON.parse(text)
		} catch {
			payload = null
		}
	}

	if (!response.ok || payload?.success === false) {
		const rawError = payload?.error || payload?.message || null
		throw new ApiError(describeError(response.status, rawError), {
			status: response.status,
			code: rawError,
			fields: Array.isArray(payload?.errors) ? payload.errors : null,
			retryAfter: response.status === 429 ? parseRetryAfter(response) : null,
			buyUrl: payload?.funpayUrl || null,
			payload,
		})
	}

	if (raw) return payload ?? {}
	if (payload && typeof payload === 'object' && 'data' in payload) {
		return { ...payload, data: payload.data }
	}
	return payload ?? {}
}

/** То же самое, но с повторами при сетевых сбоях и 503. */
export async function apiRequestRetry(pathname, options = {}) {
	const attempts = Math.max(1, (options.retries ?? CONFIG.network.retries) + 1)
	let lastError = null
	for (let attempt = 1; attempt <= attempts; attempt += 1) {
		try {
			return await apiRequest(pathname, options)
		} catch (error) {
			lastError = error
			const retryable =
				error instanceof ApiError &&
				(error.network || error.status === 502 || error.status === 503)
			if (!retryable || attempt === attempts) throw error
			await new Promise((resolve) => setTimeout(resolve, 700 * attempt))
		}
	}
	throw lastError
}

/** Проверка доступности сайта — /api/status. */
export async function ping() {
	const started = Date.now()
	const payload = await apiRequest(CONFIG.system.status, {
		timeoutMs: 8000,
		raw: true,
	})
	return {
		ok: true,
		ms: Date.now() - started,
		db: payload?.db || 'unknown',
		product: payload?.product || null,
	}
}
