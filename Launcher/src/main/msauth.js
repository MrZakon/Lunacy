/**
 * Авторизация по лицензии Minecraft (Microsoft / Xbox Live).
 *
 * Схема device code — пользователь открывает microsoft.com/link, вводит код,
 * лаунчер опрашивает токен. Дальше цепочка:
 *   MSA access_token -> Xbox Live (XBL) -> XSTS -> Minecraft services -> profile
 *
 * Итог: { nickname, uuid, accessToken, refreshToken, expiresAt } — этого
 * достаточно, чтобы зайти на лицензионные сервера.
 */

/*
 * Два режима входа:
 *   live  — классический client id Minecraft через login.live.com.
 *           Работает без регистрации приложения в Azure (по умолчанию).
 *   msal  — своё приложение Azure, если задан LUNACY_MS_CLIENT_ID.
 *
 * Прежняя ошибка AADSTS700016 была именно из-за того, что классический
 * client id отправлялся на эндпоинты login.microsoftonline.com, где его нет.
 */

const CUSTOM_CLIENT_ID = (process.env.LUNACY_MS_CLIENT_ID || '').trim()
const LIVE_CLIENT_ID = '00000000402B5328'

export const AUTH_FLAVOR = CUSTOM_CLIENT_ID ? 'msal' : 'live'
const CLIENT_ID = CUSTOM_CLIENT_ID || LIVE_CLIENT_ID
const SCOPE =
	AUTH_FLAVOR === 'msal'
		? 'XboxLive.signin offline_access'
		: 'service::user.auth.xboxlive.com::MBI_SSL'
/** login.live.com выдаёт RPS-билет (t=), Azure — обычный токен (d=). */
const TICKET_PREFIX = AUTH_FLAVOR === 'msal' ? 'd=' : 't='

const ENDPOINTS = {
	deviceCode:
		AUTH_FLAVOR === 'msal'
			? 'https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode'
			: 'https://login.live.com/oauth20_connect.srf',
	token:
		AUTH_FLAVOR === 'msal'
			? 'https://login.microsoftonline.com/consumers/oauth2/v2.0/token'
			: 'https://login.live.com/oauth20_token.srf',
	xbl: 'https://user.auth.xboxlive.com/user/authenticate',
	xsts: 'https://xsts.auth.xboxlive.com/xsts/authorize',
	mcLogin: 'https://api.minecraftservices.com/authentication/login_with_xbox',
	entitlements: 'https://api.minecraftservices.com/entitlements/mcstore',
	profile: 'https://api.minecraftservices.com/minecraft/profile',
	link: 'https://www.microsoft.com/link',
}

const UA = 'LunacyLauncher/1.0 (lunacyvisual.fun)'

export class MicrosoftAuthError extends Error {
	constructor(message, { code = 'ms_error', status = 0, help = '' } = {}) {
		super(message)
		this.name = 'MicrosoftAuthError'
		this.code = code
		this.status = status
		this.help = help
	}
}

async function postJson(url, body, { headers = {}, label = 'Microsoft', timeoutMs = 20000 } = {}) {
	const controller = new AbortController()
	const timer = setTimeout(() => controller.abort(), timeoutMs)
	try {
		const response = await fetch(url, {
			method: 'POST',
			signal: controller.signal,
			headers: {
				'Content-Type': 'application/json',
				Accept: 'application/json',
				'User-Agent': UA,
				...headers,
			},
			body: JSON.stringify(body),
		})
		const text = await response.text()
		let data = null
		try {
			data = text ? JSON.parse(text) : null
		} catch {
			data = null
		}
		return { response, data, text }
	} catch (error) {
		if (error.name === 'AbortError') {
			throw new MicrosoftAuthError(`${label} не отвечает — проверь интернет`, {
				code: 'timeout',
			})
		}
		throw new MicrosoftAuthError(`${label}: ${error.message}`, { code: 'network' })
	} finally {
		clearTimeout(timer)
	}
}

async function postForm(url, params, { label = 'Microsoft', timeoutMs = 20000 } = {}) {
	const controller = new AbortController()
	const timer = setTimeout(() => controller.abort(), timeoutMs)
	try {
		const response = await fetch(url, {
			method: 'POST',
			signal: controller.signal,
			headers: {
				'Content-Type': 'application/x-www-form-urlencoded',
				Accept: 'application/json',
				'User-Agent': UA,
			},
			body: new URLSearchParams(params).toString(),
		})
		const text = await response.text()
		let data = null
		try {
			data = text ? JSON.parse(text) : null
		} catch {
			data = null
		}
		return { response, data, text }
	} catch (error) {
		if (error.name === 'AbortError') {
			throw new MicrosoftAuthError(`${label} не отвечает — проверь интернет`, {
				code: 'timeout',
			})
		}
		throw new MicrosoftAuthError(`${label}: ${error.message}`, { code: 'network' })
	} finally {
		clearTimeout(timer)
	}
}

/** Человеческий текст ошибки Microsoft. */
function describeAuthError(data) {
	const raw = String(data?.error_description || data?.error || '')
	if (raw.includes('AADSTS700016') || raw.includes('was not found in the directory')) {
		return (
			'Microsoft не знает этого приложения. Задай свой client id в переменной ' +
			'LUNACY_MS_CLIENT_ID или оставь пустым для классического входа Minecraft'
		)
	}
	if (raw.includes('unauthorized_client')) {
		return 'Приложению Microsoft не разрешён вход по коду (device code flow)'
	}
	return raw || 'Microsoft не выдал код для входа'
}

/** Шаг 1: получить код для microsoft.com/link. */
export async function startDeviceCode() {
	const params = { client_id: CLIENT_ID, scope: SCOPE }
	if (AUTH_FLAVOR === 'live') params.response_type = 'device_code'
	const { response, data } = await postForm(ENDPOINTS.deviceCode, params, {
		label: 'Microsoft',
	})
	if (!response.ok || !data?.device_code) {
		throw new MicrosoftAuthError(describeAuthError(data), {
			code: data?.error || 'device_code_failed',
			status: response.status,
		})
	}
	return {
		deviceCode: data.device_code,
		userCode: data.user_code,
		verificationUrl: data.verification_uri || ENDPOINTS.link,
		interval: Math.max(3, Number(data.interval || 5)),
		expiresIn: Number(data.expires_in || 900),
		message: data.message || '',
	}
}

/**
 * Шаг 2: один опрос токена.
 * Возвращает { status: 'pending' | 'ready' | 'declined' | 'expired' }.
 */
export async function pollDeviceToken(deviceCode) {
	const { response, data } = await postForm(
		ENDPOINTS.token,
		{
			client_id: CLIENT_ID,
			grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
			device_code: deviceCode,
		},
		{ label: 'Microsoft' },
	)

	if (response.ok && data?.access_token) {
		return {
			status: 'ready',
			accessToken: data.access_token,
			refreshToken: data.refresh_token || '',
			expiresIn: Number(data.expires_in || 3600),
		}
	}

	const error = data?.error || ''
	if (error === 'authorization_pending') return { status: 'pending' }
	if (error === 'slow_down') return { status: 'pending', slowDown: true }
	if (error === 'authorization_declined') {
		return { status: 'declined', error: 'Вход отменён в браузере' }
	}
	if (error === 'expired_token' || error === 'code_expired') {
		return { status: 'expired', error: 'Код устарел — начни вход заново' }
	}
	throw new MicrosoftAuthError(describeAuthError(data), {
		code: error || 'token_failed',
		status: response.status,
	})
}

/** Обновить MSA-токен по refresh_token. */
export async function refreshMsaToken(refreshToken) {
	const { response, data } = await postForm(
		ENDPOINTS.token,
		{
			client_id: CLIENT_ID,
			grant_type: 'refresh_token',
			refresh_token: refreshToken,
			scope: SCOPE,
		},
		{ label: 'Microsoft' },
	)
	if (!response.ok || !data?.access_token) {
		throw new MicrosoftAuthError('Сессия Microsoft истекла — войди заново', {
			code: 'refresh_failed',
			status: response.status,
		})
	}
	return {
		accessToken: data.access_token,
		refreshToken: data.refresh_token || refreshToken,
		expiresIn: Number(data.expires_in || 3600),
	}
}

async function xboxLive(msaToken) {
	const { response, data } = await postJson(
		ENDPOINTS.xbl,
		{
			Properties: {
				AuthMethod: 'RPS',
				SiteName: 'user.auth.xboxlive.com',
				RpsTicket: `${TICKET_PREFIX}${msaToken}`,
			},
			RelyingParty: 'http://auth.xboxlive.com',
			TokenType: 'JWT',
		},
		{ label: 'Xbox Live' },
	)
	if (!response.ok || !data?.Token) {
		throw new MicrosoftAuthError('Xbox Live не принял аккаунт Microsoft', {
			code: 'xbl_failed',
			status: response.status,
		})
	}
	return {
		token: data.Token,
		uhs: data.DisplayClaims?.xui?.[0]?.uhs || '',
	}
}

async function xsts(xblToken) {
	const { response, data } = await postJson(
		ENDPOINTS.xsts,
		{
			Properties: { SandboxId: 'RETAIL', UserTokens: [xblToken] },
			RelyingParty: 'rp://api.minecraftservices.com/',
			TokenType: 'JWT',
		},
		{ label: 'Xbox Live' },
	)

	if (response.status === 401) {
		const xerr = String(data?.XErr || '')
		const messages = {
			'2148916233': 'У этого аккаунта Microsoft нет профиля Xbox — создай его на xbox.com и повтори',
			'2148916235': 'Xbox Live недоступен в стране аккаунта',
			'2148916236': 'Нужна проверка возраста в аккаунте Microsoft',
			'2148916237': 'Нужна проверка возраста в аккаунте Microsoft',
			'2148916238': 'Детский аккаунт — добавь его в семью взрослого в настройках Microsoft',
		}
		throw new MicrosoftAuthError(messages[xerr] || 'Xbox Live отклонил вход', {
			code: 'xsts_denied',
			status: 401,
			help: data?.Redirect || '',
		})
	}
	if (!response.ok || !data?.Token) {
		throw new MicrosoftAuthError('Не удалось получить XSTS-токен', {
			code: 'xsts_failed',
			status: response.status,
		})
	}
	return {
		token: data.Token,
		uhs: data.DisplayClaims?.xui?.[0]?.uhs || '',
	}
}

async function minecraftLogin(uhs, xstsToken) {
	const { response, data } = await postJson(
		ENDPOINTS.mcLogin,
		{ identityToken: `XBL3.0 x=${uhs};${xstsToken}` },
		{ label: 'Minecraft services' },
	)
	if (!response.ok || !data?.access_token) {
		throw new MicrosoftAuthError('Minecraft не принял токен Xbox', {
			code: 'mc_login_failed',
			status: response.status,
		})
	}
	return { accessToken: data.access_token, expiresIn: Number(data.expires_in || 86400) }
}

async function getJson(url, mcToken, { label = 'Minecraft', timeoutMs = 20000 } = {}) {
	const controller = new AbortController()
	const timer = setTimeout(() => controller.abort(), timeoutMs)
	try {
		const response = await fetch(url, {
			signal: controller.signal,
			headers: {
				Authorization: `Bearer ${mcToken}`,
				Accept: 'application/json',
				'User-Agent': UA,
			},
		})
		const text = await response.text()
		let data = null
		try {
			data = text ? JSON.parse(text) : null
		} catch {
			data = null
		}
		return { response, data }
	} catch (error) {
		if (error.name === 'AbortError') {
			throw new MicrosoftAuthError(`${label} не отвечает — проверь интернет`, { code: 'timeout' })
		}
		throw new MicrosoftAuthError(`${label}: ${error.message}`, { code: 'network' })
	} finally {
		clearTimeout(timer)
	}
}

/** Есть ли купленная Java Edition. */
async function hasGameLicense(mcToken) {
	const { response, data } = await getJson(ENDPOINTS.entitlements, mcToken)
	if (!response.ok) return true
	const items = data?.items || []
	if (!items.length) return false
	return items.some((item) =>
		['product_minecraft', 'game_minecraft', 'product_minecraft_bedrock'].includes(item.name) ||
		String(item.name || '').includes('minecraft'),
	)
}

/** Профиль игрока: ник и UUID. */
async function getProfile(mcToken) {
	const { response, data } = await getJson(ENDPOINTS.profile, mcToken)
	if (response.status === 404) {
		throw new MicrosoftAuthError(
			'На аккаунте нет профиля Minecraft — купи Java Edition или задай ник на minecraft.net',
			{ code: 'no_profile', status: 404 },
		)
	}
	if (!response.ok || !data?.id) {
		throw new MicrosoftAuthError('Не удалось получить профиль Minecraft', {
			code: 'profile_failed',
			status: response.status,
		})
	}
	const raw = String(data.id).replace(/-/g, '')
	const uuid = [
		raw.slice(0, 8),
		raw.slice(8, 12),
		raw.slice(12, 16),
		raw.slice(16, 20),
		raw.slice(20, 32),
	].join('-')
	return {
		nickname: data.name,
		uuid,
		skins: data.skins || [],
	}
}

/** MSA access_token -> готовый игровой аккаунт. */
export async function loginWithMsaToken({ accessToken, refreshToken = '', onLog }) {
	onLog?.('Xbox Live: проверяю аккаунт…')
	const xbl = await xboxLive(accessToken)
	onLog?.('Xbox Live: получаю XSTS…')
	const secure = await xsts(xbl.token)
	onLog?.('Minecraft services: вхожу…')
	const mc = await minecraftLogin(secure.uhs || xbl.uhs, secure.token)

	onLog?.('Проверяю лицензию Minecraft…')
	const licensed = await hasGameLicense(mc.accessToken)
	if (!licensed) {
		throw new MicrosoftAuthError(
			'На этом аккаунте Microsoft нет купленной Minecraft: Java Edition',
			{ code: 'no_license' },
		)
	}

	const profile = await getProfile(mc.accessToken)
	onLog?.(`Лицензия подтверждена: ${profile.nickname}`)

	return {
		type: 'online',
		auth: 'microsoft',
		nickname: profile.nickname,
		uuid: profile.uuid,
		accessToken: mc.accessToken,
		msaRefreshToken: refreshToken,
		expiresAt: Date.now() + mc.expiresIn * 1000,
		skins: profile.skins,
	}
}

/** Продлить игровой токен по сохранённому refresh_token. */
export async function refreshGameAccount(account, { onLog } = {}) {
	if (!account?.msaRefreshToken) {
		throw new MicrosoftAuthError('Нет сохранённого доступа Microsoft — войди заново', {
			code: 'no_refresh_token',
		})
	}
	onLog?.('Обновляю лицензию Microsoft…')
	const msa = await refreshMsaToken(account.msaRefreshToken)
	const fresh = await loginWithMsaToken({
		accessToken: msa.accessToken,
		refreshToken: msa.refreshToken,
		onLog,
	})
	return { ...account, ...fresh, id: account.id }
}

/** Токен ещё живой (с запасом 5 минут). */
export function tokenAlive(account) {
	if (!account?.accessToken) return false
	if (!account.expiresAt) return false
	return Number(account.expiresAt) - Date.now() > 5 * 60 * 1000
}

export const MS_LINK_URL = ENDPOINTS.link
