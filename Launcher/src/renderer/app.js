/* Lunacy Launcher 2.1.0 — Renderer Application */

const api = window.lunacy
const $ = (sel) => document.querySelector(sel)
const $$ = (sel) => Array.from(document.querySelectorAll(sel))

const ART_CLASS = {
	vanilla: 'art--vanilla',
	fabric: 'art--fabric',
	forge: 'art--forge',
	lunacy: 'art--lunacy',
	snapshot: 'art--snapshot',
}

const state = {
	session: null,
	hasSubscription: false,
	mcVersions: ['1.21.11', '26.1.2', '26.2'],
	loader: 'Fabric',
	accounts: [],
	versions: [],
	instances: [],
	contentTarget: null,
	selectedVersion: null,
	lunacyVersionId: null,
	lunMc: null,
	verKind: 'vanilla',
	verMc: null,
	remoteCache: {},
	// Мастерская (Modrinth)
	catKind: 'mod',
	catCategory: 'all',
	catSort: 'downloads',
	catMc: null,
	catLoader: 'auto',
	catPinnedInstance: undefined,
	catalogItems: [],
	catalogLoaded: false,
	activeModalMod: null,
	// Настройки
	settings: {},
	memory: { maxGb: 8, totalGb: 8, freeGb: 0, recommendedGb: 4 },
	web: {},
	update: { version: null, downloaded: false },
	lunacyBusy: false,
	msTimer: null,
	picker: { items: [], onPick: null, active: null },
	// Консоль и логи
	logs: [],
	logsLoaded: false,
	logFilter: '',
	logMode: 'all', // all | game | err
	logAuto: true,
	lastCrashReason: null,
	// Аудио контекст
	audioCtx: null,
}

/* ------------------------------------------------------------------ Аудио */

function playSound(type = 'click') {
	if (state.settings.soundEffects === false) return
	try {
		if (!state.audioCtx) {
			const AudioContext = window.AudioContext || window.webkitAudioContext
			if (AudioContext) state.audioCtx = new AudioContext()
		}
		if (!state.audioCtx) return
		if (state.audioCtx.state === 'suspended') state.audioCtx.resume()

		const ctx = state.audioCtx
		const osc = ctx.createOscillator()
		const gain = ctx.createGain()
		osc.connect(gain)
		gain.connect(ctx.destination)

		if (type === 'click') {
			osc.type = 'sine'
			osc.frequency.setValueAtTime(800, ctx.currentTime)
			osc.frequency.exponentialRampToValueAtTime(350, ctx.currentTime + 0.04)
			gain.gain.setValueAtTime(0.04, ctx.currentTime)
			gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.04)
			osc.start()
			osc.stop(ctx.currentTime + 0.04)
		} else if (type === 'launch') {
			osc.type = 'triangle'
			osc.frequency.setValueAtTime(220, ctx.currentTime)
			osc.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.25)
			gain.gain.setValueAtTime(0.08, ctx.currentTime)
			gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.25)
			osc.start()
			osc.stop(ctx.currentTime + 0.25)
		}
	} catch {
		/* звуки не критичны */
	}
}

/* ------------------------------------------------------------------ Утилиты */

function toast(message, kind = '') {
	const node = $('#toast')
	node.textContent = message
	node.className = kind ? `toast toast--${kind}` : 'toast'
	node.hidden = false
	clearTimeout(toast.timer)
	toast.timer = setTimeout(() => {
		node.hidden = true
	}, 4200)
}

function showError(node, message) {
	if (!node) return
	node.textContent = message || ''
	node.hidden = !message
}

function uiPrompt(title, defaultValue = '', hint = '') {
	return new Promise((resolve) => {
		const modal = $('#input-modal')
		if (!modal) {
			return resolve(window.prompt(title, defaultValue))
		}
		const titleEl = $('#input-modal-title')
		const hintEl = $('#input-modal-hint')
		const inputEl = $('#input-modal-value')
		const form = $('#input-modal-form')

		titleEl.textContent = title
		hintEl.textContent = hint || ''
		inputEl.value = defaultValue || ''
		modal.hidden = false
		inputEl.focus()
		inputEl.select()

		let finished = false
		const done = (val) => {
			if (finished) return
			finished = true
			modal.hidden = true
			resolve(val)
		}

		form.onsubmit = (e) => {
			e.preventDefault()
			done(inputEl.value.trim())
		}

		modal.querySelectorAll('[data-inputmodal-close]').forEach((el) => {
			el.onclick = () => done(null)
		})
	})
}

function setProgress(selector, percent) {
	const wrap = $(selector)
	if (!wrap) return
	const bar = wrap.querySelector('i')
	if (!bar) return
	if (percent === null || percent === undefined) {
		wrap.classList.add('progress--idle')
		bar.style.width = '0%'
		return
	}
	wrap.classList.remove('progress--idle')
	bar.style.width = `${Math.max(0, Math.min(100, percent))}%`
}

function fmtSize(bytes) {
	if (!bytes) return '0 КБ'
	const mb = bytes / 1024 / 1024
	if (mb >= 1024) return `${(mb / 1024).toFixed(2)} ГБ`
	return mb >= 1 ? `${mb.toFixed(1)} МБ` : `${Math.round(bytes / 1024)} КБ`
}

function fmtCount(value) {
	const num = Number(value || 0)
	if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
	if (num >= 1000) return `${Math.round(num / 1000)}k`
	return String(num)
}

function escapeHtml(value) {
	return String(value ?? '')
		.replaceAll('&', '&amp;')
		.replaceAll('<', '&lt;')
		.replaceAll('>', '&gt;')
		.replaceAll('"', '&quot;')
}

function getPlayerAvatar(nickname, uuid) {
	if (uuid && uuid.length > 20) {
		return `https://mc-heads.net/avatar/${uuid}/64`
	}
	if (nickname && nickname !== '—') {
		return `https://mc-heads.net/avatar/${encodeURIComponent(nickname)}/64`
	}
	return "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'><rect width='32' height='32' fill='%231a261c'/><circle cx='16' cy='16' r='10' fill='%2335d06d'/></svg>"
}

/* ------------------------------------------------------------------ Окно и Titlebar */

$('#btn-min').addEventListener('click', () => { playSound(); api.window.minimize(); })
$('#btn-max').addEventListener('click', () => { playSound(); api.window.maximize(); })
$('#btn-close').addEventListener('click', () => { playSound(); api.window.close(); })

/* ------------------------------------------------------------------ Навигация */

function showApp(show) {
	$('#screen-auth').hidden = show
	$('#screen-app').hidden = !show
}

$$('.nav').forEach((button) => {
	button.addEventListener('click', () => {
		playSound()
		$$('.nav').forEach((item) => item.classList.remove('is-active'))
		button.classList.add('is-active')
		const view = button.dataset.view
		$$('.view').forEach((section) =>
			section.classList.toggle('is-active', section.dataset.view === view),
		)
		if (view === 'play') refreshPlayScreen()
		if (view === 'versions') refreshVersions()
		if (view === 'accounts') loadAccounts()
		if (view === 'workshop') openWorkshop()
		if (view === 'screenshots') openScreenshots()
		if (view === 'console') openConsole()
		if (view === 'settings') loadSettings()
		try { api.discord?.setView?.(view) } catch {}
	})
})

function switchView(viewName) {
	const btn = $(`.nav[data-view="${viewName}"]`)
	if (btn) btn.click()
}

// Быстрый переход к управлению версиями
$('#btn-quick-manage-ver')?.addEventListener('click', () => switchView('versions'))

// Быстрый переход в кабинет на сайте
$('#btn-cabinet')?.addEventListener('click', () => {
	playSound()
	api.openExternal(state.web.account || `${state.web.site}/account`)
})

// Переход в Telegram-канал
$('#tg-banner')?.addEventListener('click', () => {
	playSound()
	api.openExternal('https://t.me/LunacyVisual')
})

/* ------------------------------------------------------------------ Авторизация */

$$('[data-authtab]').forEach((tab) => {
	tab.addEventListener('click', () => {
		playSound()
		$$('[data-authtab]').forEach((item) => item.classList.remove('is-active'))
		tab.classList.add('is-active')
		const mode = tab.dataset.authtab
		$('#form-login').hidden = mode !== 'login'
		$('#form-register').hidden = mode !== 'register'
	})
})

$$('[data-open]').forEach((link) => {
	link.addEventListener('click', (event) => {
		event.preventDefault()
		playSound()
		const target = state.web[link.dataset.open]
		if (target) api.openExternal(target)
	})
})

$('#form-login').addEventListener('submit', async (event) => {
	event.preventDefault()
	playSound()
	const button = $('#btn-login')
	showError($('#login-error'), '')
	button.disabled = true
	button.textContent = 'Авторизация…'
	try {
		const result = await api.login($('#login-user').value.trim(), $('#login-pass').value)
		if (!result.ok) {
			showError($('#login-error'), result.error)
			return
		}
		await onAuthorized(result.session)
	} finally {
		button.disabled = false
		button.textContent = 'Войти в лаунчер'
	}
})

$('#form-register').addEventListener('submit', async (event) => {
	event.preventDefault()
	playSound()
	showError($('#reg-error'), '')
	const payload = {
		username: $('#reg-user').value.trim(),
		email: $('#reg-mail').value.trim(),
		password: $('#reg-pass').value,
	}
	if (!/^[a-zA-Z0-9_]{3,32}$/.test(payload.username)) {
		showError($('#reg-error'), 'Ник: 3–32 символа (латиница, цифры, _)')
		return
	}
	if (payload.password.length < 6) {
		showError($('#reg-error'), 'Пароль должен быть не менее 6 символов')
		return
	}
	const result = await api.register(payload)
	if (!result.ok) {
		showError($('#reg-error'), result.error)
		return
	}
	await onAuthorized(result.session)
})

$('#btn-logout').addEventListener('click', async () => {
	playSound()
	await api.logout()
	state.session = null
	showApp(false)
})

function applySession(session) {
	state.session = session
	state.hasSubscription = Boolean(session?.hasSubscription)
	const name = session?.username || '—'
	$('#nick').textContent = name
	$('#user-initial').textContent = (name || 'L').slice(0, 1).toUpperCase()
	
	const planText = session?.planLabel || (state.hasSubscription ? 'Подписка активна' : 'Без подписки')
	$('#plan').textContent = planText
	
	const tierBadge = $('#tier-badge')
	const tierBadgeSide = $('#tier-badge-side')
	if (state.hasSubscription) {
		tierBadge.textContent = 'PREMIUM VISUALS'
		tierBadge.className = 'badge badge--glow'
		tierBadgeSide.textContent = 'PREMIUM'
		tierBadgeSide.className = 'badge badge--tier badge--glow'
	} else {
		tierBadge.textContent = 'FREE СБОРКА'
		tierBadge.className = 'badge badge--soft'
		tierBadgeSide.textContent = 'FREE'
		tierBadgeSide.className = 'badge badge--tier badge--soft'
		state.mcVersions = ['1.21.11']
		if (state.lunMc && state.lunMc !== '1.21.11') {
			setLunacyVersion('1.21.11')
		}
	}

	$('#free-banner').hidden = state.hasSubscription
	$('#lun-locked').hidden = state.hasSubscription
	updateLunacyUI()
}

async function onAuthorized(session) {
	applySession(session)
	showApp(true)
	await checkSubscription()
	await Promise.all([loadClientInfo(), loadAccounts(), refreshVersions(), loadSettings()])
	loadRemoteVersions(true).catch(() => {})
	if (state.settings.checkUpdatesOnStart !== false) checkUpdate()
}

async function checkSubscription() {
	const result = await api.subscription()
	if (!result.ok) return
	if (result.session) applySession(result.session)
}

$('#btn-buy')?.addEventListener('click', () => { playSound(); api.openExternal(state.web.buy || `${state.web.site}/account`); })
$('#btn-buy-2')?.addEventListener('click', () => { playSound(); api.openExternal(state.web.buy || `${state.web.site}/account`); })

$('#form-redeem')?.addEventListener('submit', async (event) => {
	event.preventDefault()
	playSound()
	const code = $('#redeem-code').value.trim()
	if (!code) return
	const result = await api.redeem(code)
	if (!result.ok) {
		toast(result.error, 'error')
		return
	}
	applySession(result.session)
	$('#redeem-code').value = ''
	toast('Подписка успешно активирована!')
})

/* ------------------------------------------------------------------ Данные клиента */

async function loadClientInfo() {
	const result = await api.clientInfo()
	if (result.ok) {
		state.mcVersions = result.mcVersions?.length ? result.mcVersions : (state.hasSubscription ? state.mcVersions : ['1.21.11'])
		state.loader = result.loader || 'Fabric'
	}
	if (!state.hasSubscription) {
		state.mcVersions = ['1.21.11']
	}
	$('#hero-sub').textContent = `Загрузчик ${state.loader} · Доступные версии: ${state.mcVersions.join(', ')}`
	$('#lun-loader').textContent = `Загрузчик ${state.loader} · Версии с сервера: ${state.mcVersions.join(', ')}`
	const chosenMc = state.mcVersions.includes(state.settings.modMcVersion)
		? state.settings.modMcVersion
		: state.mcVersions[0]
	setLunacyVersion(chosenMc)
	if (state.settings.modMcVersion !== chosenMc) {
		api.settings.set({ modMcVersion: chosenMc })
	}
	syncCatalogVersions()
}

/* ------------------------------------------------------------------ Аккаунты */

async function loadAccounts() {
	const result = await api.accounts.list()
	if (!result.ok) return
	state.accounts = result.items || []
	renderAccounts()
	renderPlayAccounts()
}

function renderAccounts() {
	const list = $('#account-list')
	list.innerHTML = ''
	$('#account-count').textContent = `${state.accounts.length} ${state.accounts.length === 1 ? 'аккаунт' : 'аккаунтов'}`

	if (!state.accounts.length) {
		list.innerHTML = '<p class="empty">Пока нет добавленных ников</p>'
		return
	}

	for (const account of state.accounts) {
		const card = document.createElement('div')
		card.className = `account-card${account.active ? ' is-active' : ''}`
		const avatarUrl = getPlayerAvatar(account.nickname, account.uuid)
		const tag = account.type === 'online' ? 'Лицензия Microsoft' : 'Offline'

		card.innerHTML = `
			<img class="account-card__avatar" src="${avatarUrl}" alt="" />
			<div class="account-card__info">
				<div class="account-card__name">${escapeHtml(account.nickname)}</div>
				<div class="account-card__tags">
					<span class="pill ${account.type === 'online' ? 'pill--green' : ''}">${tag}</span>
					${account.active ? '<span class="pill pill--green">Активен</span>' : ''}
				</div>
			</div>
			<div class="account-card__actions"></div>`

		const actions = card.querySelector('.account-card__actions')
		if (!account.active) {
			const selectBtn = document.createElement('button')
			selectBtn.className = 'btn btn--tiny btn--primary'
			selectBtn.textContent = 'Выбрать'
			selectBtn.addEventListener('click', async () => {
				playSound()
				await api.accounts.select(account.id)
				await loadAccounts()
				toast(`Выбран аккаунт ${account.nickname}`)
			})
			actions.appendChild(selectBtn)
		}

		if (account.type === 'online') {
			const refreshBtn = document.createElement('button')
			refreshBtn.className = 'btn btn--tiny btn--ghost'
			refreshBtn.title = 'Обновить токен лицензии'
			refreshBtn.textContent = 'Обновить'
			refreshBtn.addEventListener('click', async () => {
				playSound()
				refreshBtn.disabled = true
				const res = await api.accounts.refresh(account.id)
				refreshBtn.disabled = false
				toast(res.ok ? 'Лицензия обновлена' : res.error, res.ok ? '' : 'error')
				loadAccounts()
			})
			actions.appendChild(refreshBtn)
		}

		const removeBtn = document.createElement('button')
		removeBtn.className = 'btn btn--tiny btn--danger'
		removeBtn.textContent = '✕'
		removeBtn.title = 'Удалить аккаунт'
		removeBtn.addEventListener('click', async () => {
			playSound()
			if (confirm(`Удалить аккаунт ${account.nickname}?`)) {
				await api.accounts.remove(account.id)
				await loadAccounts()
			}
		})
		actions.appendChild(removeBtn)

		list.appendChild(card)
	}
}

function renderPlayAccounts() {
	const select = $('#play-account')
	if (!select) return
	select.innerHTML = ''
	for (const account of state.accounts) {
		const option = document.createElement('option')
		option.value = account.id
		option.textContent = `${account.nickname} (${account.type === 'online' ? 'Лицензия' : 'Офлайн'})`
		if (account.active) option.selected = true
		select.appendChild(option)
	}

	const active = state.accounts.find((item) => item.active) || state.accounts[0]
	const skinImg = $('#skin-full-body')
	const nickBadge = $('#skin-nick-badge')

	if (active) {
		$('#play-avatar').src = getPlayerAvatar(active.nickname, active.uuid)
		$('#play-account-hint').textContent = active.type === 'online'
			? 'Лицензия Microsoft (Xbox Live) — все серверы доступны'
			: 'Офлайн-ник (пиратка) — вход на открытые серверы'
		$('#play-account-badge').textContent = active.type === 'online' ? 'Microsoft' : 'Offline'

		if (skinImg) {
			const fallbackSvg = "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 128'><rect width='64' height='128' rx='8' fill='%23112015'/><rect x='16' y='16' width='32' height='32' rx='4' fill='%2334d399'/><rect x='16' y='52' width='32' height='40' rx='4' fill='%2310b981'/><rect x='4' y='52' width='10' height='36' rx='3' fill='%23059669'/><rect x='50' y='52' width='10' height='36' rx='3' fill='%23059669'/><rect x='18' y='94' width='12' height='32' rx='3' fill='%23047857'/><rect x='34' y='94' width='12' height='32' rx='3' fill='%23047857'/></svg>"
			skinImg.src = `https://mc-heads.net/body/${encodeURIComponent(active.nickname)}/right`
			skinImg.onerror = () => { skinImg.src = fallbackSvg }
		}
		if (nickBadge) nickBadge.textContent = active.nickname
	} else {
		$('#play-avatar').src = getPlayerAvatar('', '')
		$('#play-account-hint').textContent = 'Добавь ник во вкладке «Аккаунты»'
		$('#play-account-badge').textContent = 'Нет аккаунта'
		if (nickBadge) nickBadge.textContent = 'Lunacy Player'
	}
}

$('#play-account')?.addEventListener('change', async (event) => {
	playSound()
	await api.accounts.select(event.target.value)
	await loadAccounts()
})

$('#form-account')?.addEventListener('submit', async (event) => {
	event.preventDefault()
	playSound()
	showError($('#account-error'), '')
	const nick = $('#account-nick').value.trim()
	if (!/^[A-Za-z0-9_]{3,16}$/.test(nick)) {
		showError($('#account-error'), 'Ник: 3–16 символов (латиница, цифры, _)')
		return
	}
	const result = await api.accounts.add(nick, 'offline')
	if (!result.ok) {
		showError($('#account-error'), result.error)
		return
	}
	$('#account-nick').value = ''
	await loadAccounts()
	toast(`Аккаунт ${nick} добавлен`)
})

/* Microsoft Device Code Flow */

$('#btn-ms-login')?.addEventListener('click', async () => {
	playSound()
	const btn = $('#btn-ms-login')
	btn.disabled = true
	const result = await api.accounts.microsoft.start()
	btn.disabled = false
	if (!result.ok) {
		$('#ms-box').hidden = false
		$('#ms-status').textContent = result.error
		toast(result.error, 'error')
		return
	}
	$('#ms-box').hidden = false
	$('#ms-code').textContent = result.code
	$('#ms-link').textContent = result.url
	$('#ms-link').dataset.url = result.url
	$('#ms-status').textContent = 'Ожидаю подтверждения входа через браузер…'
	api.openExternal(result.url)
	startMsPolling(result.interval || 5)
})

$('#ms-link')?.addEventListener('click', (event) => {
	event.preventDefault()
	playSound()
	api.openExternal($('#ms-link').dataset.url || 'https://www.microsoft.com/link')
})

$('#btn-ms-copy')?.addEventListener('click', async () => {
	playSound()
	try {
		await navigator.clipboard.writeText($('#ms-code').textContent.trim())
		toast('Код скопирован в буфер')
	} catch {
		toast('Скопируй код вручную', 'error')
	}
})

$('#btn-ms-cancel')?.addEventListener('click', async () => {
	playSound()
	stopMsPolling()
	await api.accounts.microsoft.cancel()
	$('#ms-box').hidden = true
})

function stopMsPolling() {
	if (state.msTimer) clearInterval(state.msTimer)
	state.msTimer = null
}

function startMsPolling(interval) {
	stopMsPolling()
	state.msTimer = setInterval(async () => {
		const result = await api.accounts.microsoft.poll()
		if (!result.ok) {
			stopMsPolling()
			$('#ms-status').textContent = result.error
			return
		}
		if (result.status === 'pending') return
		stopMsPolling()
		if (result.status === 'ready') {
			$('#ms-box').hidden = true
			toast(`Лицензия подтверждена: ${result.nickname}!`)
			await loadAccounts()
			return
		}
		$('#ms-status').textContent = result.error || 'Вход не завершён'
	}, Math.max(3, interval) * 1000)
}

/* ------------------------------------------------------------------ Меню выбора версии */

function closePicker() {
	$('#picker').hidden = true
	state.picker = { items: [], onPick: null, active: null }
}

$$('[data-picker-close]').forEach((node) => node.addEventListener('click', closePicker))
document.addEventListener('keydown', (event) => {
	if (event.key === 'Escape' && !$('#picker').hidden) closePicker()
})
$('#picker-search')?.addEventListener('input', renderPickerList)

function openPicker({ title, hint, items, active, onPick }) {
	state.picker = { items, onPick, active: active || null }
	$('#picker-title').textContent = title
	$('#picker-hint').textContent = hint || ''
	$('#picker-search').value = ''
	$('#picker').hidden = false
	renderPickerList()
	$('#picker-search').focus()
}

function renderPickerList() {
	const list = $('#picker-list')
	const filter = $('#picker-search').value.trim().toLowerCase()
	const items = state.picker.items.filter(
		(item) => !filter || String(item.id).toLowerCase().includes(filter),
	)
	list.innerHTML = ''
	if (!items.length) {
		list.innerHTML = '<p class="empty">Ничего не найдено</p>'
		return
	}
	for (const item of items.slice(0, 400)) {
		const tile = document.createElement('button')
		tile.type = 'button'
		tile.className = `tile${item.id === state.picker.active ? ' is-active' : ''}`
		tile.innerHTML = `
			<div class="tile__art art ${item.artClass}"><span class="art__tag">${escapeHtml(item.tag || item.id)}</span></div>
			<div class="tile__name">${escapeHtml(item.id)}</div>
			<div class="tile__sub muted">${escapeHtml(item.sub || '')}</div>`
		tile.addEventListener('click', () => {
			playSound()
			const handler = state.picker.onPick
			closePicker()
			handler?.(item)
		})
		list.appendChild(tile)
	}
}

/* ------------------------------------------------------------------ Менеджер версий */

async function refreshVersions() {
	const result = await api.versions.installed()
	if (!result.ok) return
	state.versions = result.items || []
	state.instances = result.instances || []
	state.selectedVersion = result.selected || null

	if (!state.selectedVersion || !state.versions.some((item) => item.id === state.selectedVersion)) {
		const lunInst = state.instances.find((item) => item.name?.toLowerCase().includes('lunacy'))
		state.selectedVersion = state.lunacyVersionId || lunInst?.versionId || state.versions[0]?.id || null
	}
	if (!state.instances.some((item) => item.id === state.contentTarget)) {
		state.contentTarget =
			state.instances.find((item) => item.versionId === state.selectedVersion)?.id ||
			state.instances[0]?.id ||
			null
	}
	renderVersions()
	renderPlayVersions()
	renderContentTargets()
}

function renderVersions() {
	const list = $('#version-list')
	list.innerHTML = ''
	if (!state.versions.length) {
		list.innerHTML = '<p class="empty">Пока не установлено ни одной сборки</p>'
	} else {
		for (const version of state.versions) {
			const active = version.id === state.selectedVersion
			const instance = version.instance || state.instances.find((item) => item.versionId === version.id)
			const counters = instance
				? `Модов: ${instance.mods} · Ресурспаков: ${instance.resourcepacks} · Шейдеров: ${instance.shaderpacks} · Объём: ${fmtSize(instance.sizeBytes)}`
				: 'Папка сборки создаётся при старте'

			const row = document.createElement('div')
			row.className = `row row--item${active ? ' row--active' : ''}`
			row.innerHTML = `
				<div class="row__main">
					<div class="row">
						<b>${escapeHtml(instance?.name || version.title || version.id)}</b>
						<span class="pill">${escapeHtml(version.loader || 'Vanilla')}</span>
						${active ? '<span class="pill pill--green">Выбрана</span>' : ''}
					</div>
					<div class="muted small">${escapeHtml(version.id)} · ${escapeHtml(instance?.dir || '')}</div>
					<div class="muted small">${escapeHtml(counters)}</div>
				</div>
				<div class="row__actions"></div>`

			const actions = row.querySelector('.row__actions')

			const addBtn = (label, cls, fn) => {
				const b = document.createElement('button')
				b.className = `btn btn--tiny ${cls}`
				b.textContent = label
				b.addEventListener('click', () => { playSound(); fn(); })
				actions.appendChild(b)
			}

			if (!active) {
				addBtn('Использовать', 'btn--primary', async () => {
					await api.versions.select(version.id)
					if (instance) await api.instances.select(instance.id)
					state.contentTarget = instance?.id || state.contentTarget
					await refreshVersions()
					toast(`Выбрана сборка: ${instance?.name || version.id}`)
				})
			}

			addBtn('Папка', 'btn--ghost', async () => {
				const target = instance || (await api.instances.create(version.id).then((r) => r.instance))
				if (target) api.instances.open(target.id)
			})

			addBtn('Моды', 'btn--ghost', async () => {
				const target = instance || (await api.instances.create(version.id).then((r) => r.instance))
				if (target) api.instances.open(target.id, 'mods')
			})

			if (instance) {
				addBtn('Клонировать', 'btn--ghost', async () => {
					const nextName = await uiPrompt('Клонирование сборки', `${instance.name} (Копия)`, 'Введите название новой сборки:')
					if (!nextName) return
					const res = await api.instances.clone(instance.id, nextName.trim())
					if (!res.ok) return toast(res.error, 'error')
					await refreshVersions()
					toast(`Сборка клонирована: «${res.instance.name}»`)
				})

				addBtn('Переименовать', 'btn--ghost', async () => {
					const nextName = await uiPrompt('Переименование сборки', instance.name, 'Введите новое название сборки:')
					if (!nextName || nextName === instance.name) return
					const res = await api.instances.rename(instance.id, nextName.trim())
					if (!res.ok) return toast(res.error, 'error')
					await refreshVersions()
					toast(`Сборка переименована в «${res.instance.name}»`)
				})
			}

			addBtn('Починить', 'btn--ghost', async () => {
				const res = await api.versions.verify(version.id)
				if (!res.ok) return toast(res.error, 'error')
				if (res.state?.ok) return toast(`${version.id}: все файлы в порядке`)
				if (confirm(`В сборке не хватает файлов: ${res.state?.missingCount || '?'}. Докачать?`)) {
					const fixed = await api.versions.repair(version.id)
					if (!fixed.ok) return toast(fixed.error, 'error')
					await refreshVersions()
					toast('Сборка успешно починена!')
				}
			})

			addBtn('Удалить', 'btn--danger', async () => {
				const withInstance = instance
					? confirm(`Удалить вместе с личной папкой «${instance.name}» (моды, сейвы, конфиги)?\n\nOK — стереть всё\nОтмена — оставить файлы`)
					: false
				if (confirm(`Точно удалить версию ${version.id}?`)) {
					const res = await api.versions.remove(version.id, withInstance)
					if (!res.ok) return toast(res.error, 'error')
					await refreshVersions()
					loadInstalledContent()
					toast(`Версия ${version.id} удалена`)
				}
			})

			list.appendChild(row)
		}
	}

	const lunInst = state.instances.find((item) => item.name?.toLowerCase().includes('lunacy'))
	const isLunActive = Boolean(
		(state.lunacyVersionId && state.selectedVersion === state.lunacyVersionId) ||
		(lunInst && state.selectedVersion === lunInst.versionId)
	)
	$('#lun-active-pill').hidden = !isLunActive
}

function renderPlayVersions() {
	const select = $('#play-version')
	if (!select) return
	select.innerHTML = ''
	for (const version of state.versions) {
		const instance = version.instance || state.instances.find((item) => item.versionId === version.id)
		const option = document.createElement('option')
		option.value = version.id
		option.textContent = `${instance?.name || version.title || version.id} [${version.loader || 'Vanilla'}]`
		if (version.id === state.selectedVersion) option.selected = true
		select.appendChild(option)
	}

	const current = state.versions.find((v) => v.id === state.selectedVersion)
	if (current) {
		const loader = (current.loader || 'vanilla').toLowerCase()
		const iconLetter = loader.includes('fabric') ? 'F' : loader.includes('forge') ? 'M' : loader.includes('lunacy') ? 'L' : 'V'
		$('#play-ver-icon').textContent = iconLetter
		$('#play-version-hint').textContent = `${current.loader || 'Vanilla'} · ${current.id}`
	}
}

$('#play-version')?.addEventListener('change', async (event) => {
	playSound()
	state.selectedVersion = event.target.value
	await api.versions.select(state.selectedVersion)
	await refreshVersions()
})

/* Виджет Lunacy Visuals */

function setLunacyVersion(version) {
	state.lunMc = version
	$('#lun-mc-label').textContent = version ? `Minecraft ${version}` : 'выбери версию'
	$('#lun-art-tag').textContent = version ? `Lunacy ${version}` : 'Lunacy Visuals'
}

$('#btn-lun-pick')?.addEventListener('click', () => {
	playSound()
	const available = state.hasSubscription ? (state.mcVersions || []) : ['1.21.11']
	const items = available.map((ver) => ({
		id: ver,
		artClass: 'art--lunacy',
		tag: `Lunacy ${ver}`,
		sub: state.hasSubscription ? 'Fabric + Lunacy Visuals стек' : 'Fabric + Lunacy Free (1.21.11)',
	}))
	openPicker({
		title: 'Версия Lunacy Visuals',
		hint: state.hasSubscription
			? 'Сборка включает Fabric и официальный visual-мод'
			: 'Бесплатная сборка доступна только для Minecraft 1.21.11',
		items,
		active: state.lunMc,
		onPick: (item) => {
			setLunacyVersion(item.id)
			api.settings.set({ modMcVersion: item.id })
		},
	})
})

$('#btn-lun-install')?.addEventListener('click', async () => {
	playSound()
	const version = state.lunMc || state.mcVersions[0]
	const btn = $('#btn-lun-install')
	btn.disabled = true
	state.lunacyBusy = true
	setProgress('#progress-lunacy', 0)
	$('#lun-hint').textContent = 'Установка сборки Lunacy Visuals…'

	const result = await api.versions.lunacy(version)
	btn.disabled = false
	state.lunacyBusy = false

	if (!result.ok) {
		setProgress('#progress-lunacy', null)
		$('#lun-hint').textContent = result.error
		toast(result.error, 'error')
		return
	}
	setProgress('#progress-lunacy', 100)
	$('#lun-hint').textContent = `Сборка «${result.instanceName}» готова к запуску`
	await refreshVersions()
	toast('Сборка Lunacy Visuals установлена и активирована!')
})

$('#btn-lun-restore')?.addEventListener('click', async () => {
	playSound()
	const version = state.lunMc || state.mcVersions[0]
	const btn = $('#btn-lun-restore')
	btn.disabled = true
	state.lunacyBusy = true
	setProgress('#progress-lunacy', 20)
	$('#lun-hint').textContent = 'Восстановление файлов визуала и проверка Fabric...'
	toast('Восстановление визуала и проверка Fabric loader...')
	try {
		const res = await api.versions.restoreLunacy(version)
		if (!res.ok) {
			setProgress('#progress-lunacy', null)
			$('#lun-hint').textContent = res.error || 'Ошибка восстановления'
			toast(res.error || 'Ошибка восстановления', 'error')
			return
		}
		setProgress('#progress-lunacy', 100)
		$('#lun-hint').textContent = `Визуал для «${res.instanceName}» успешно восстановлен!`
		await refreshVersions()
		toast('Визуал успешно восстановлен и проверен!')
	} catch (err) {
		setProgress('#progress-lunacy', null)
		$('#lun-hint').textContent = err.message
		toast(`Ошибка восстановления: ${err.message}`, 'error')
	} finally {
		btn.disabled = false
		state.lunacyBusy = false
		setTimeout(() => setProgress('#progress-lunacy', 0), 2500)
	}
})

$('#btn-lun-activate')?.addEventListener('click', async () => {
	playSound()
	const targetId = state.lunacyVersionId || state.instances.find((i) => i.name?.toLowerCase().includes('lunacy'))?.versionId
	if (!targetId) {
		toast('Сначала установи сборку кнопкой слева', 'error')
		return
	}
	await api.versions.select(targetId)
	await refreshVersions()
	toast('Сборка Lunacy активирована для игры')
})

function updateLunacyUI() {
	const locked = !state.hasSubscription
	$('#lun-locked').hidden = !locked
	if (locked) {
		$('#lun-card-title').textContent = 'Сборка Lunacy (FREE)'
	} else {
		$('#lun-card-title').textContent = 'Сборка Lunacy Visuals'
	}
}

/* Обычные версии */

bindSegmented('#ver-kind', 'kind', (kind) => {
	playSound()
	state.verKind = kind
	$('#ver-art-tag').textContent = KIND_TITLE[kind] || kind
	$('#ver-art').className = `widget__art art ${ART_CLASS[kind] || 'art--vanilla'}`
	$('#ver-build-wrap').hidden = kind === 'vanilla'
	state.verMc = null
	$('#ver-mc-label').textContent = 'выбери версию'
	loadRemoteVersions()
})

function setSegmentedValue(containerSelector, dataKey, value) {
	const container = $(containerSelector)
	if (!container) return
	Array.from(container.querySelectorAll('.seg')).forEach((button) => {
		button.classList.toggle('is-active', button.dataset[dataKey] === value)
	})
}

function bindSegmented(containerSelector, dataKey, onChange) {
	const container = $(containerSelector)
	if (!container) return
	container.addEventListener('click', (event) => {
		const button = event.target.closest('.seg')
		if (!button || !container.contains(button)) return
		Array.from(container.querySelectorAll('.seg')).forEach((item) => item.classList.remove('is-active'))
		button.classList.add('is-active')
		onChange(button.dataset[dataKey])
	})
}

const KIND_TITLE = { vanilla: 'Vanilla', fabric: 'Fabric', forge: 'Forge' }

async function loadRemoteVersions(background = false) {
	const kind = state.verKind
	const snapshots = $('#ver-snapshots').checked
	if (!background) $('#ver-hint').textContent = 'Загрузка списка версий…'
	let result
	try {
		result = await api.versions.remote({ kind, snapshots })
	} catch (error) {
		if (!background) $('#ver-hint').textContent = error?.message || 'Не удалось загрузить список версий'
		return
	}
	if (!result.ok) {
		if (!background) $('#ver-hint').textContent = result.error
		return
	}
	state.remoteCache ||= {}
	state.remoteCache[kind] = result.items || []
	if (result.builds?.length) fillSelect($('#ver-build'), result.builds)
	if (!background) $('#ver-hint').textContent = `Доступно версий: ${result.items.length}`
}

function fillSelect(select, values, selected) {
	if (!select) return
	select.innerHTML = ''
	for (const value of values) {
		const option = document.createElement('option')
		option.value = typeof value === 'string' ? value : value.id || value.value
		option.textContent = typeof value === 'string' ? value : value.label || value.id
		select.appendChild(option)
	}
	if (selected) select.value = selected
}

$('#btn-ver-refresh')?.addEventListener('click', () => { playSound(); loadRemoteVersions(); })
$('#ver-snapshots')?.addEventListener('change', () => { playSound(); loadRemoteVersions(); })

$('#btn-ver-pick')?.addEventListener('click', async () => {
	playSound()
	const kind = state.verKind
	state.remoteCache ||= {}
	let items = state.remoteCache[kind]
	if (!items || !items.length) {
		await loadRemoteVersions()
		items = state.remoteCache[kind] || []
	}
	const tiles = items.map((item) => ({
		id: item.id,
		artClass: item.type === 'snapshot' ? 'art--snapshot' : (ART_CLASS[kind] || 'art--vanilla'),
		tag: item.id,
		sub: item.type || (kind === 'vanilla' ? 'Release' : KIND_TITLE[kind]),
	}))
	openPicker({
		title: `Версии ${KIND_TITLE[kind]}`,
		hint: 'Кликни по плитке для выбора',
		items: tiles,
		active: state.verMc,
		onPick: async (picked) => {
			state.verMc = picked.id
			$('#ver-mc-label').textContent = picked.id
			if (kind === 'forge') {
				const builds = await api.versions.builds({ kind: 'forge', mcVersion: picked.id })
				if (builds.ok && builds.items?.length) {
					fillSelect($('#ver-build'), builds.items)
					$('#ver-hint').textContent = `Forge сборок: ${builds.items.length}`
				}
			}
		},
	})
})

$('#btn-ver-install')?.addEventListener('click', async () => {
	playSound()
	const kind = state.verKind
	if (!state.verMc) {
		toast('Сначала выбери версию игры', 'error')
		return
	}
	const btn = $('#btn-ver-install')
	btn.disabled = true
	setProgress('#progress-version', 0)
	$('#ver-hint').textContent = 'Установка версии…'

	const payload = { kind, mcVersion: state.verMc }
	if (kind === 'forge') payload.forgeId = $('#ver-build').value
	if (kind === 'fabric' && $('#ver-build').value) payload.loaderVersion = $('#ver-build').value

	const result = await api.versions.install(payload)
	btn.disabled = false

	if (!result.ok) {
		setProgress('#progress-version', null)
		$('#ver-hint').textContent = result.error
		toast(result.error, 'error')
		return
	}
	setProgress('#progress-version', 100)
	$('#ver-hint').textContent = `Успешно установлено: ${result.selected}`
	await refreshVersions()
	toast('Версия установлена и выбрана для игры!')
})

$('#btn-ver-verify')?.addEventListener('click', async () => {
	playSound()
	const target = state.selectedVersion
	if (!target) return toast('Сначала выбери сборку в списке', 'error')
	$('#ver-health').textContent = 'Проверка целостности файлов…'
	const check = await api.versions.verify(target)
	if (!check.ok) return toast(check.error, 'error')
	if (check.state?.ok) {
		$('#ver-health').textContent = `Всё в порядке: библиотек ${check.state.libraries}, Java ${check.javaMajor || '?'}`
		toast('Сборка в идеальном состоянии')
	} else {
		$('#ver-health').textContent = `Не хватает файлов: ${check.state.missingCount}. Докачиваю…`
		const repair = await api.versions.repair(target)
		if (!repair.ok) return toast(repair.error, 'error')
		$('#ver-health').textContent = 'Сборка успешно починена'
		toast('Сборка починена!')
	}
})

/* ------------------------------------------------------------------ 4. Мастерская (Modrinth) */

bindSegmented('#cat-kind', 'kind', (kind) => {
	playSound()
	state.catKind = kind
	syncCatalogLoaders()
	searchCatalog()
})

$$('#cat-categories .cat-pill').forEach((pill) => {
	pill.addEventListener('click', () => {
		playSound()
		$$('#cat-categories .cat-pill').forEach((p) => p.classList.remove('is-active'))
		pill.classList.add('is-active')
		state.catCategory = pill.dataset.cat
		searchCatalog()
	})
})

$('#cat-sort')?.addEventListener('change', (e) => {
	playSound()
	state.catSort = e.target.value
	searchCatalog()
})

$('#btn-cat-search')?.addEventListener('click', () => { playSound(); searchCatalog(); })
$('#cat-query')?.addEventListener('keydown', (e) => {
	if (e.key === 'Enter') { playSound(); searchCatalog(); }
})
$('#cat-mc')?.addEventListener('change', (e) => {
	state.catMc = e.target.value
	searchCatalog()
})
$('#cat-loader')?.addEventListener('change', (e) => {
	state.catLoader = e.target.value
	searchCatalog()
})

async function openWorkshop() {
	await refreshVersions()
	loadInstalledContent()
	if (!state.catalogLoaded) await searchCatalog()
}

function showCatalogSkeleton() {
	const list = $('#catalog-list')
	list.innerHTML = ''
	for (let i = 0; i < 6; i++) {
		const c = document.createElement('div')
		c.className = 'mcard mcard--skeleton'
		list.appendChild(c)
	}
}

async function searchCatalog() {
	state.catalogLoaded = true
	showCatalogSkeleton()
	setProgress('#progress-catalog', null)

	const result = await api.catalog.search({
		kind: state.catKind,
		query: $('#cat-query').value.trim(),
		category: state.catCategory,
		sort: state.catSort,
		mcVersion: catalogMcVersion(),
		loader: catalogLoader(state.catKind),
		limit: 24,
	})

	if (!result.ok) {
		$('#catalog-list').innerHTML = `<p class="empty">${escapeHtml(result.error)}</p>`
		return
	}
	state.catalogItems = result.items || []
	renderCatalog()
}

function catalogMcVersion() {
	const v = $('#cat-mc')?.value
	return v && v !== 'all' ? v : null
}

function catalogLoader(kind) {
	if (kind !== 'mod') return null
	const l = $('#cat-loader')?.value
	return l && l !== 'auto' && l !== 'all' ? l : null
}

function syncCatalogLoaders() {
	const loaderSelect = $('#cat-loader')
	if (!loaderSelect) return
	if (state.catKind !== 'mod') {
		loaderSelect.hidden = true
		return
	}
	loaderSelect.hidden = false
	fillSelect(loaderSelect, [
		{ id: 'auto', label: 'Загрузчик (Авто)' },
		{ id: 'fabric', label: 'Fabric' },
		{ id: 'forge', label: 'Forge' },
		{ id: 'neoforge', label: 'NeoForge' },
		{ id: 'quilt', label: 'Quilt' },
	], state.catLoader)
}

function syncCatalogVersions() {
	const select = $('#cat-mc')
	if (!select) return
	const current = currentInstance()
	const curMc = current?.mc || ''
	const versions = [{ id: 'all', label: 'Все версии MC' }]
	
	for (const v of state.mcVersions) {
		if (!versions.some((x) => x.id === v)) versions.push({ id: v, label: `MC ${v}` })
	}
	fillSelect(select, versions, curMc || 'all')
}

function currentInstance() {
	return state.instances.find((item) => item.id === state.contentTarget) || null
}

function renderContentTargets() {
	const select = $('#cat-target')
	if (!select) return
	select.innerHTML = ''
	for (const inst of state.instances) {
		const opt = document.createElement('option')
		opt.value = inst.id
		opt.textContent = `${inst.name} (${inst.loader || 'Vanilla'})`
		if (inst.id === state.contentTarget) opt.selected = true
		select.appendChild(opt)
	}
}

$('#cat-target')?.addEventListener('change', (e) => {
	playSound()
	state.contentTarget = e.target.value
	api.instances.select(state.contentTarget, true)
	loadInstalledContent()
	syncCatalogVersions()
	searchCatalog()
})

$('#btn-open-dir-2')?.addEventListener('click', async () => {
	playSound()
	if (state.contentTarget) {
		await api.instances.open(state.contentTarget)
	} else {
		api.openPath()
	}
})

function renderCatalog() {
	const list = $('#catalog-list')
	list.innerHTML = ''
	if (!state.catalogItems.length) {
		list.innerHTML = '<p class="empty">Ничего не нашлось. Попробуй изменить запрос или версию.</p>'
		return
	}

	for (const item of state.catalogItems) {
		const card = document.createElement('article')
		card.className = 'mcard'
		const iconHtml = item.icon
			? `<img class="mcard__icon" src="${escapeHtml(item.icon)}" alt="" loading="lazy" />`
			: `<div class="mcard__icon">${escapeHtml((item.title || '?').slice(0, 1))}</div>`

		card.innerHTML = `
			${iconHtml}
			<div class="mcard__body">
				<div class="mcard__title">
					<span>${escapeHtml(item.title)}</span>
					<span class="pill">${fmtCount(item.downloads)} ⬇</span>
				</div>
				<p class="mcard__desc">${escapeHtml(item.description || 'Без описания')}</p>
				<div class="mcard__meta">
					<span>Автор: <b>${escapeHtml(item.author || 'Неизвестен')}</b></span>
					${item.categories?.length ? `<span>${item.categories.slice(0, 2).join(', ')}</span>` : ''}
				</div>
				<div class="mcard__actions">
					<button class="btn btn--tiny btn--primary btn-install">Установить</button>
					<button class="btn btn--tiny btn--ghost btn-details">Подробнее</button>
				</div>
			</div>`

		const installBtn = card.querySelector('.btn-install')
		installBtn.addEventListener('click', async () => {
			playSound()
			installBtn.disabled = true
			installBtn.textContent = 'Загрузка…'
			setProgress('#progress-catalog', 0)

			const res = await api.catalog.install({
				projectId: item.id,
				kind: state.catKind,
				mcVersion: catalogMcVersion(),
				loader: catalogLoader(state.catKind),
				resolved: true,
				instanceId: state.contentTarget,
			})
			installBtn.disabled = false
			installBtn.textContent = 'Установить'

			if (!res.ok) {
				setProgress('#progress-catalog', null)
				toast(res.error, 'error')
				return
			}
			setProgress('#progress-catalog', 100)
			toast(`${item.title} установлен в сборку!`)
			loadInstalledContent()
		})

		const detailsBtn = card.querySelector('.btn-details')
		detailsBtn.addEventListener('click', () => {
			playSound()
			openModDetails(item)
		})

		list.appendChild(card)
	}
}

async function openModDetails(item) {
	state.activeModalMod = item
	$('#mod-modal-title').textContent = item.title
	$('#mod-modal-author').textContent = `Автор: ${item.author || '—'}`
	$('#mod-modal-downloads').textContent = `${fmtCount(item.downloads)} загрузок`
	$('#mod-modal-license').textContent = 'Modrinth'
	$('#mod-modal-desc').textContent = item.description || 'Загрузка подробной информации…'
	$('#mod-modal-icon').src = item.icon || ''
	$('#mod-modal-gallery').innerHTML = ''
	$('#mod-modal').hidden = false

	// Получаем подробности с API
	const details = await api.catalog.details(item.slug || item.id)
	if (details.ok) {
		$('#mod-modal-desc').textContent = details.description || details.body || item.description
		$('#mod-modal-license').textContent = `Лицензия: ${details.license || 'Open'}`
		if (details.gallery?.length) {
			$('#mod-modal-gallery').innerHTML = details.gallery
				.map((g) => `<img class="mod-modal__screenshot" src="${escapeHtml(g.url)}" alt="" />`)
				.join('')
		}
	}
}

$('#btn-mod-modal-web')?.addEventListener('click', () => {
	playSound()
	if (state.activeModalMod?.pageUrl) api.openExternal(state.activeModalMod.pageUrl)
})

$('#btn-mod-modal-install')?.addEventListener('click', async () => {
	playSound()
	if (!state.activeModalMod) return
	const item = state.activeModalMod
	const btn = $('#btn-mod-modal-install')
	btn.disabled = true
	btn.textContent = 'Установка…'

	const res = await api.catalog.install({
		projectId: item.id,
		kind: state.catKind,
		mcVersion: catalogMcVersion(),
		loader: catalogLoader(state.catKind),
		resolved: true,
		instanceId: state.contentTarget,
	})
	btn.disabled = false
	btn.textContent = 'Установить в сборку'

	if (!res.ok) return toast(res.error, 'error')
	toast(`Установлено: ${item.title}`)
	loadInstalledContent()
	$('#mod-modal').hidden = true
})

$$('[data-modmodal-close]').forEach((n) => n.addEventListener('click', () => {
	$('#mod-modal').hidden = true
}))

async function loadInstalledContent() {
	const result = await api.catalog.installed(state.contentTarget)
	const list = $('#content-list')
	list.innerHTML = ''
	if (!result.ok) return

	const titles = { mod: 'Мод', resourcepack: 'Ресурспак', shader: 'Шейдер' }
	let total = 0

	for (const [kind, files] of Object.entries(result.items || {})) {
		for (const file of files) {
			total++
			const isMod = kind === 'mod'
			const isEnabled = file.enabled !== false
			const row = document.createElement('div')
			row.className = `row row--item ${!isEnabled ? 'item-disabled' : ''}`
			row.innerHTML = `
				<div class="row" style="gap: 12px; align-items: center;">
					${isMod ? `
					<label class="mod-toggle" title="${isEnabled ? 'Мод активен (кликните, чтобы отключить)' : 'Мод отключён (кликните, чтобы включить)'}">
						<input type="checkbox" class="mod-toggle-input" ${isEnabled ? 'checked' : ''} />
						<span class="mod-toggle__slider"></span>
					</label>` : ''}
					<div class="row__main">
						<b>${escapeHtml(file.displayName || file.name)}</b>
						<div class="row">
							<span class="pill">${titles[kind] || kind}</span>
							${!isEnabled ? '<span class="pill pill--amber">Отключён (.disabled)</span>' : ''}
							<span class="muted small">${fmtSize(file.size)}</span>
						</div>
					</div>
				</div>
				<button class="btn btn--tiny btn--danger" data-action="remove">Удалить</button>`

			if (isMod) {
				const checkbox = row.querySelector('.mod-toggle-input')
				checkbox?.addEventListener('change', async () => {
					playSound()
					const res = await api.catalog.toggleMod({ filename: file.name, instanceId: state.contentTarget })
					if (res.ok) {
						toast(res.enabled ? `Мод «${escapeHtml(file.displayName || file.name)}» включён` : `Мод «${escapeHtml(file.displayName || file.name)}» отключён`)
						loadInstalledContent()
					} else {
						toast(res.error || 'Ошибка изменения статуса мода', 'error')
					}
				})
			}

			row.querySelector('[data-action="remove"]')?.addEventListener('click', async () => {
				playSound()
				await api.catalog.remove(kind, file.name, state.contentTarget)
				loadInstalledContent()
			})
			list.appendChild(row)
		}
	}
	$('#content-count').textContent = `${total} файлов`
	if (!total) {
		list.innerHTML = '<p class="empty">В этой сборке пока нет установленных модов или ресурспаков</p>'
	}
}

// Ручной импорт файлов в сборку
$('#btn-import-file')?.addEventListener('click', () => {
	playSound()
	const input = document.createElement('input')
	input.type = 'file'
	input.multiple = true
	input.accept = '.jar,.zip'
	input.onchange = async () => {
		if (!input.files || !input.files.length) return
		let count = 0
		for (const f of input.files) {
			if (f.path) {
				await api.catalog.importFile({ filePath: f.path, instanceId: state.contentTarget })
				count++
			}
		}
		playSound()
		toast(`Импортировано файлов: ${count}`)
		loadInstalledContent()
	}
	input.click()
})


/* ------------------------------------------------------------------ 5. Консоль */

const LOG_LIMIT = 3000

function isErrorLine(line) {
	return /⛔|Ошибка|ERROR|SEVERE|Exception|Caused by|FATAL|CrashReport/i.test(line)
}

function isWarnLine(line) {
	return /WARN|WARNING|Предупреждение/i.test(line)
}

function isGameLine(line) {
	return /\[Render thread|\[Server thread|\[Client thread|Minecraft|LWJGL/i.test(line)
}

function pushLog(entry) {
	state.logs.push(entry)
	if (state.logs.length > LOG_LIMIT) state.logs.shift()
	const view = $('.view[data-view="console"]')
	if (view && view.classList.contains('is-active')) renderLogs()
}

function visibleLogs() {
	const filter = state.logFilter.trim().toLowerCase()
	return state.logs.filter((entry) => {
		if (state.logMode === 'err' && !isErrorLine(entry.line)) return false
		if (state.logMode === 'game' && !isGameLine(entry.line)) return false
		if (filter && !entry.line.toLowerCase().includes(filter)) return false
		return true
	})
}

function renderLogs() {
	const node = $('#log-view')
	if (!node) return
	const items = visibleLogs().slice(-800)
	node.innerHTML = items.length
		? items.map((entry) => {
			const time = String(entry.time || '').slice(11, 19)
			let cls = 'logline'
			if (isErrorLine(entry.line)) cls += ' logline--error'
			else if (isWarnLine(entry.line)) cls += ' logline--warn'
			else if (isGameLine(entry.line)) cls += ' logline--game'
			return `<div class="${cls}"><span class="logline__time">${time}</span><span class="logline__text">${escapeHtml(entry.line)}</span></div>`
		}).join('')
		: '<p class="muted small">Журнал пуст. Запустите игру или установку для отображения логов.</p>'

	$('#log-hint').textContent = `Строк: ${items.length} из ${state.logs.length}`
	if (state.logAuto) node.scrollTop = node.scrollHeight
}

async function openConsole() {
	if (!state.logsLoaded) {
		const res = await api.logs.get()
		if (res.ok) {
			state.logs = res.items || []
			state.logsLoaded = true
		}
	}
	renderLogs()
}

api.onLog((entry) => pushLog(entry))

$('#log-filter')?.addEventListener('input', (e) => {
	state.logFilter = e.target.value
	renderLogs()
})

$('#log-autoscroll')?.addEventListener('change', (e) => {
	state.logAuto = e.target.checked
	if (state.logAuto) renderLogs()
})

$('#log-tab-all')?.addEventListener('click', () => { playSound(); setLogMode('all'); })
$('#log-tab-game')?.addEventListener('click', () => { playSound(); setLogMode('game'); })
$('#log-tab-err')?.addEventListener('click', () => { playSound(); setLogMode('err'); })

function setLogMode(mode) {
	state.logMode = mode
	$('#log-tab-all').classList.toggle('is-active', mode === 'all')
	$('#log-tab-game').classList.toggle('is-active', mode === 'game')
	$('#log-tab-err').classList.toggle('is-active', mode === 'err')
	renderLogs()
}

$('#btn-log-copy')?.addEventListener('click', async () => {
	playSound()
	const text = visibleLogs().map((e) => `[${String(e.time).slice(11, 19)}] ${e.line}`).join('\n')
	await navigator.clipboard.writeText(text)
	toast('Логи скопированы в буфер')
})

$('#btn-log-clear')?.addEventListener('click', async () => {
	playSound()
	await api.logs.clear()
	state.logs = []
	renderLogs()
})

$('#btn-log-file')?.addEventListener('click', async () => {
	playSound()
	await api.logs.open()
})

$('#btn-crash-copy')?.addEventListener('click', async () => {
	playSound()
	if (state.lastCrashReason) {
		await navigator.clipboard.writeText(state.lastCrashReason)
		toast('Причина ошибки скопирована')
	}
})

$('#btn-crash-fix')?.addEventListener('click', () => {
	playSound()
	switchView('versions')
	$('#btn-ver-verify')?.click()
})

/* ------------------------------------------------------------------ 6. Настройки */

$$('[data-settab]').forEach((tab) => {
	tab.addEventListener('click', () => {
		playSound()
		$$('[data-settab]').forEach((t) => t.classList.remove('is-active'))
		tab.classList.add('is-active')
		const pane = tab.dataset.settab
		$$('[data-setpane]').forEach((p) => p.classList.toggle('is-active', p.dataset.setpane === pane))
	})
})

async function loadSettings() {
	const res = await api.settings.get()
	if (!res.ok) return
	state.settings = res.settings || {}
	state.memory = res.memory || state.memory

	// Применяем тему и оформление
	applyTheme(state.settings.themeAccent || 'emerald')
	applyScale(state.settings.uiScale || '100')
	$('#set-bgglow').checked = state.settings.bgGlow !== false
	$('#set-sounds').checked = state.settings.soundEffects !== false
	$('#ambient-mesh').hidden = state.settings.bgGlow === false
	const starfieldOn = state.settings.starfield !== false
	if ($('#set-starfield')) $('#set-starfield').checked = starfieldOn
	document.body.classList.toggle('no-starfield', !starfieldOn)
	if ($('#set-discord')) $('#set-discord').checked = state.settings.discordRpc !== false
	if ($('#set-discord-id')) $('#set-discord-id').value = state.settings.discordClientId || ''

	// RAM
	const ram = $('#set-ram')
	ram.max = String(state.memory.maxGb || 16)
	ram.value = String(Math.min(state.settings.memoryMax || 4, state.memory.maxGb || 16))
	$('#ram-label').textContent = `${ram.value} ГБ`
	$('#ram-total').textContent = `В ПК: ${state.memory.totalGb} ГБ всего (свободно ${state.memory.freeGb} ГБ)`

	// Экран
	$('#set-w').value = state.settings.width || 1280
	$('#set-h').value = state.settings.height || 720
	$('#set-fs').checked = Boolean(state.settings.fullscreen)
	$('#set-after').value = state.settings.afterLaunch || 'hide'
	$('#set-autoconnect').value = state.settings.serverAutoConnect || ''

	// Java
	$('#set-java').value = state.settings.javaPath || ''
	$('#set-jvm').value = state.settings.jvmArgs || ''

	// Хранилище
	$('#set-dir').value = state.settings.gameDir || ''

	// Сеть
	$('#set-autoupd').checked = state.settings.checkUpdatesOnStart !== false
	$('#set-automod').checked = state.settings.autoUpdateMod !== false
	$('#set-mirror').value = state.settings.assetsMirror || 'mojang'
	setSegmentedValue('#set-threads', 'threads', String(state.settings.downloadThreads || 3))

	renderSystem(res.system)
	await Promise.all([loadJava(), loadGpu()])
}

function applyTheme(theme) {
	document.body.setAttribute('data-theme', theme)
	$$('#theme-picker .theme-btn').forEach((b) => b.classList.toggle('is-active', b.dataset.theme === theme))
}

function applyScale(scale) {
	document.body.style.zoom = `${Number(scale) / 100}`
	setSegmentedValue('#set-scale', 'scale', String(scale))
}

$$('#theme-picker .theme-btn').forEach((b) => {
	b.addEventListener('click', () => {
		playSound()
		applyTheme(b.dataset.theme)
		state.settings.themeAccent = b.dataset.theme
	})
})

bindSegmented('#set-scale', 'scale', (scale) => {
	playSound()
	applyScale(scale)
	state.settings.uiScale = scale
})

bindSegmented('#set-threads', 'threads', (threads) => {
	playSound()
	state.settings.downloadThreads = Number(threads)
})

$$('.btn--res').forEach((btn) => {
	btn.addEventListener('click', () => {
		playSound()
		$('#set-w').value = btn.dataset.w
		$('#set-h').value = btn.dataset.h
		toast(`Разрешение: ${btn.dataset.w}×${btn.dataset.h}`)
	})
})

$$('.btn--ram').forEach((btn) => {
	btn.addEventListener('click', () => {
		playSound()
		const gb = btn.id === 'btn-ram-rec' ? (state.memory.recommendedGb || 4) : Number(btn.dataset.gb)
		$('#set-ram').value = gb
		$('#ram-label').textContent = `${gb} ГБ`
	})
})

$('#set-ram')?.addEventListener('input', (e) => {
	$('#ram-label').textContent = `${e.target.value} ГБ`
})

$$('.btn--jvm').forEach((btn) => {
	btn.addEventListener('click', () => {
		playSound()
		$('#set-jvm').value = btn.dataset.jvm
		toast('JVM аргументы применены')
	})
})

async function loadJava(force = false) {
	const res = await api.system.java(force)
	if (!res.ok) return
	const select = $('#java-select')
	select.innerHTML = ''
	const autoOpt = document.createElement('option')
	autoOpt.value = ''
	autoOpt.textContent = 'Автоматический подбор (Рекомендуется)'
	select.appendChild(autoOpt)

	for (const j of res.items || []) {
		const opt = document.createElement('option')
		opt.value = j.path
		opt.textContent = `${j.label} [${j.path}]`
		select.appendChild(opt)
	}
	select.value = res.selected || ''
}

$('#java-select')?.addEventListener('change', (e) => {
	$('#set-java').value = e.target.value
})
$('#btn-java-refresh')?.addEventListener('click', () => { playSound(); loadJava(true); })
$('#btn-pick-java')?.addEventListener('click', async () => {
	playSound()
	const res = await api.settings.pickJava()
	if (res.ok && res.settings) $('#set-java').value = res.settings.javaPath || ''
})

async function loadGpu(force = false) {
	const res = await api.system.gpu(force)
	if (!res.ok) return
	const items = res.items || []
	const select = $('#gpu-select')
	select.innerHTML = ''
	for (const gpu of items) {
		const opt = document.createElement('option')
		opt.value = gpu.id
		opt.textContent = `${gpu.name} ${gpu.primary ? '(Основная)' : ''}`
		select.appendChild(opt)
	}
	if (res.selected) select.value = res.selected
	$('#gpu-badge').textContent = items.length > 1 ? `${items.length} GPU` : 'GPU'
}

$('#btn-pick-dir')?.addEventListener('click', async () => {
	playSound()
	const res = await api.settings.pickFolder()
	if (res.ok && res.gameDir) $('#set-dir').value = res.gameDir
})
$('#btn-open-dir')?.addEventListener('click', () => { playSound(); api.openPath(); })

// Очистка хранилища
$('#btn-clean-cache')?.addEventListener('click', async () => {
	playSound()
	const res = await api.storage.clean('cache')
	$('#storage-clean-hint').textContent = `Кэш очищен. Освобождено: ${res.freedFormatted}`
	toast(`Освобождено: ${res.freedFormatted}`)
})
$('#btn-clean-logs')?.addEventListener('click', async () => {
	playSound()
	const res = await api.storage.clean('logs')
	$('#storage-clean-hint').textContent = `Логи очищены. Освобождено: ${res.freedFormatted}`
	toast(`Освобождено: ${res.freedFormatted}`)
})
$('#btn-clean-all')?.addEventListener('click', async () => {
	playSound()
	if (confirm('Очистить весь временный кэш загрузок и старые логи?')) {
		const res = await api.storage.clean('all')
		$('#storage-clean-hint').textContent = `Полная очистка завершена. Освобождено: ${res.freedFormatted}`
		toast(`Освобождено: ${res.freedFormatted}`)
	}
})

// Диагностика ПК
function renderSystem(system) {
	const list = $('#sysinfo')
	if (!system || !list) return
	const rows = [
		['Операционная система', `${system.osName || system.platform || 'Windows'} ${system.release || ''} (${system.arch || 'x64'})`],
		['Процессор', `${system.cpu || 'CPU'} · ${system.cores || '?'} ядер/потоков`],
		['Оперативная память', `${state.memory.totalGb} ГБ всего · ${state.memory.freeGb} ГБ свободно`],
		['Имя компьютера', system.hostname || 'Localhost'],
	]
	list.innerHTML = rows.map((r) => `
		<div class="row row--item">
			<div class="row__main"><b>${r[0]}</b><span class="muted small">${escapeHtml(r[1])}</span></div>
		</div>`).join('')
}

$('#btn-run-diagnostics')?.addEventListener('click', async () => {
	playSound()
	const card = $('#diag-results-card')
	const list = $('#diag-results-list')
	card.hidden = false
	list.innerHTML = '<p class="muted small">Тестирование сетевых шлюзов и прав доступа…</p>'

	const res = await api.system.diagnose()
	if (!res.ok) return

	list.innerHTML = (res.checks || []).map((c) => `
		<div class="row row--item">
			<div class="row__main">
				<b>${escapeHtml(c.name)}</b>
				<span class="muted small">${c.detail || c.error || ''}</span>
			</div>
			<span class="pill ${c.ok ? 'pill--green' : 'toast--error'}">${c.ok ? `OK (${c.latency})` : 'Ошибка'}</span>
		</div>`).join('')
})

// Сохранение настроек
$('#btn-save')?.addEventListener('click', async () => {
	playSound()
	const patch = {
		memoryMax: Number($('#set-ram').value),
		javaPath: $('#set-java').value.trim(),
		width: Number($('#set-w').value) || 1280,
		height: Number($('#set-h').value) || 720,
		fullscreen: $('#set-fs').checked,
		afterLaunch: $('#set-after').value,
		serverAutoConnect: $('#set-autoconnect').value.trim(),
		jvmArgs: $('#set-jvm').value.trim(),
		themeAccent: state.settings.themeAccent || 'emerald',
		uiScale: state.settings.uiScale || '100',
		bgGlow: $('#set-bgglow').checked,
		soundEffects: $('#set-sounds').checked,
		starfield: $('#set-starfield') ? $('#set-starfield').checked : true,
		discordRpc: $('#set-discord') ? $('#set-discord').checked : true,
		discordClientId: $('#set-discord-id') ? $('#set-discord-id').value.trim() : '',
		downloadThreads: state.settings.downloadThreads || 3,
		assetsMirror: $('#set-mirror').value,
		checkUpdatesOnStart: $('#set-autoupd').checked,
		autoUpdateMod: $('#set-automod').checked,
		gpuId: $('#gpu-card')?.hidden ? null : $('#gpu-select')?.value,
	}

	const res = await api.settings.set(patch)
	if (!res.ok) return toast(res.error, 'error')
	state.settings = res.settings
	$('#ambient-mesh').hidden = !patch.bgGlow
	document.body.classList.toggle('no-starfield', !patch.starfield)
	if (api.discord) {
		api.discord.toggle(patch.discordRpc)
		if (api.discord.setClientId) api.discord.setClientId(patch.discordClientId)
	}
	const hint = $('#settings-status-hint')
	if (hint) {
		hint.textContent = '✓ Все настройки сохранены и применены'
		hint.style.color = 'var(--green)'
		setTimeout(() => {
			hint.textContent = 'Изменения применяются сразу после сохранения'
			hint.style.color = ''
		}, 3000)
	}
	toast('Настройки сохранены')
})

$('#btn-reset')?.addEventListener('click', async () => {
	playSound()
	if (confirm('Сбросить все настройки на стандартные?')) {
		await api.settings.reset()
		await loadSettings()
		toast('Настройки сброшены')
	}
})

$('#link-discord-dev')?.addEventListener('click', (e) => {
	e.preventDefault()
	api.openExternal('https://discord.com/developers/applications')
})

function refreshPlayScreen() {
	renderPlayAccounts()
	renderPlayVersions()
}

/* ------------------------------------------------------------------ Запуск игры */

function setPlayButtonState(running, label = null) {
	const btn = $('#btn-play')
	if (!btn) return
	btn.disabled = running
	const span = btn.querySelector('.btn--play__inner span')
	if (span) {
		span.textContent = running ? (label || 'В ИГРЕ') : 'ИГРАТЬ'
	}
	btn.classList.toggle('btn--playing', running)
}

$('#btn-play')?.addEventListener('click', async () => {
	playSound('launch')
	setPlayButtonState(true, 'ЗАПУСК…')
	$('#game-state').textContent = 'Запуск и проверка HWID…'
	const res = await api.game.launch($('#play-version').value)
	if (!res.ok) {
		setPlayButtonState(false)
		$('#game-state').textContent = res.error
		toast(res.error, 'error')
	}
})

api.onGameState((data) => {
	if (data.running) {
		setPlayButtonState(true, 'В ИГРЕ')
		$('#game-state').textContent = `Игра запущена (${data.nickname})`
		$('#game-status-box').classList.add('status--running')
		$('#crash-assistant').hidden = true
		return
	}
	setPlayButtonState(false)
	$('#game-status-box').classList.remove('status--running')
	if (data.code) {
		const reason = data.reason || `Игра закрылась с кодом ${data.code}`
		state.lastCrashReason = reason
		$('#game-state').textContent = reason
		$('#crash-assistant').hidden = false
		$('#crash-title').textContent = `Вылет игры (код ${data.code})`
		$('#crash-desc').textContent = reason
		toast(reason, 'error')
		return
	}
	$('#game-state').textContent = 'Игра не запущена'
	$('#crash-assistant').hidden = true
})

api.onProgress((data) => {
	const percent = data.percent ?? null
	if (data.target === 'update') {
		$('#update-percent').hidden = false
		$('#update-percent').textContent = percent === null ? '…' : `${percent}%`
		$('#update-bar-wrap').hidden = false
		setProgress('#update-bar-wrap', percent)
		return
	}
	if (data.target === 'catalog') {
		setProgress('#progress-catalog', percent)
		return
	}
	if (data.target === 'version') {
		setProgress('#progress-version', percent)
		if (state.lunacyBusy) setProgress('#progress-lunacy', percent)
		return
	}
	if (data.target === 'mod' || data.target === 'free') setProgress('#progress-lunacy', percent)
})

/* ------------------------------------------------------------------ Обновление */

async function checkUpdate() {
	$('#update-chip').hidden = false
	$('#update-text').textContent = 'Проверка версии…'
	const res = await api.update.check()
	if (!res.ok || res.checked === false) {
		$('#update-text').textContent = 'Обновлений нет'
		return
	}
	const launcher = res.launcher || {}
	if (!launcher.updateAvailable) {
		$('#update-text').textContent = `v${res.current} — последняя`
		return
	}
	state.update = { version: launcher.version, downloaded: Boolean(res.ready) }
	$('#update-text').textContent = `Доступно v${launcher.version}`
	$('#btn-update-download').hidden = Boolean(res.ready)
	$('#btn-update-install').hidden = !res.ready
}

$('#btn-update-download')?.addEventListener('click', async () => {
	playSound()
	const btn = $('#btn-update-download')
	btn.disabled = true
	$('#update-percent').hidden = false
	$('#update-bar-wrap').hidden = false
	setProgress('#update-bar-wrap', 0)
	const res = await api.update.download(state.update.version)
	btn.disabled = false
	if (!res.ok) return toast(res.error, 'error')
	btn.hidden = true
	$('#btn-update-install').hidden = false
	toast('Обновление скачано')
})

$('#btn-update-install')?.addEventListener('click', async () => {
	playSound()
	await api.update.install()
})

/* ------------------------------------------------------------------ 2.0 Pro Функции */

/* 1. Интерактивное звёздное поле (Starfield Canvas) */
function initStarfield() {
	const canvas = document.getElementById('starfield-canvas')
	if (!canvas) return
	const ctx = canvas.getContext('2d')
	let width = (canvas.width = window.innerWidth)
	let height = (canvas.height = window.innerHeight)

	window.addEventListener('resize', () => {
		width = canvas.width = window.innerWidth
		height = canvas.height = window.innerHeight
	})

	const mouse = { x: width / 2, y: height / 2, active: false }
	window.addEventListener('mousemove', (e) => {
		mouse.x = e.clientX
		mouse.y = e.clientY
		mouse.active = true
	})
	window.addEventListener('mouseleave', () => {
		mouse.active = false
	})

	const STAR_COUNT = 70
	const stars = []
	for (let i = 0; i < STAR_COUNT; i++) {
		stars.push({
			x: Math.random() * width,
			y: Math.random() * height,
			vx: (Math.random() - 0.5) * 0.4,
			vy: (Math.random() - 0.5) * 0.4,
			radius: Math.random() * 1.6 + 0.6,
			color: Math.random() > 0.35 ? 'rgba(52, 211, 153, ' : 'rgba(167, 139, 250, ',
			baseAlpha: Math.random() * 0.5 + 0.25,
		})
	}

	function animate() {
		if (document.body.classList.contains('no-starfield')) {
			requestAnimationFrame(animate)
			return
		}
		ctx.clearRect(0, 0, width, height)

		for (let i = 0; i < stars.length; i++) {
			const s = stars[i]
			s.x += s.vx
			s.y += s.vy

			if (mouse.active) {
				const dx = mouse.x - s.x
				const dy = mouse.y - s.y
				const dist = Math.hypot(dx, dy)
				if (dist < 130) {
					s.x -= (dx / dist) * 0.5
					s.y -= (dy / dist) * 0.5
				}
			}

			if (s.x < 0) s.x = width
			if (s.x > width) s.x = 0
			if (s.y < 0) s.y = height
			if (s.y > height) s.y = 0

			ctx.beginPath()
			ctx.arc(s.x, s.y, s.radius, 0, Math.PI * 2)
			ctx.fillStyle = s.color + s.baseAlpha + ')'
			ctx.fill()

			for (let j = i + 1; j < stars.length; j++) {
				const s2 = stars[j]
				const d = Math.hypot(s.x - s2.x, s.y - s2.y)
				if (d < 85) {
					ctx.beginPath()
					ctx.moveTo(s.x, s.y)
					ctx.lineTo(s2.x, s2.y)
					ctx.strokeStyle = `rgba(52, 211, 153, ${(1 - d / 85) * 0.12})`
					ctx.lineWidth = 0.6
					ctx.stroke()
				}
			}
		}
		requestAnimationFrame(animate)
	}
	animate()
}

/* 2. Интерактивный 3D скин-подиум */
function initSkinPodium() {
	const podium = document.getElementById('skin-podium')
	const stage = document.getElementById('skin-stage')
	if (!podium || !stage) return

	podium.addEventListener('mousemove', (e) => {
		const rect = podium.getBoundingClientRect()
		const centerX = rect.left + rect.width / 2
		const centerY = rect.top + rect.height / 2
		const deltaX = (e.clientX - centerX) / (rect.width / 2)
		const deltaY = (e.clientY - centerY) / (rect.height / 2)

		const rotY = Math.max(-35, Math.min(35, deltaX * 35))
		const rotX = Math.max(-15, Math.min(15, -deltaY * 15))

		stage.style.transform = `rotateY(${rotY}deg) rotateX(${rotX}deg)`
	})

	podium.addEventListener('mouseleave', () => {
		stage.style.transform = 'rotateY(0deg) rotateX(0deg)'
	})
}

/* 3. Drag & Drop файлов */
function initDragAndDrop() {
	const overlay = $('#drop-overlay')
	if (!overlay) return

	window.addEventListener('dragover', (e) => {
		e.preventDefault()
		overlay.hidden = false
	})

	window.addEventListener('dragleave', (e) => {
		if (e.relatedTarget === null) {
			overlay.hidden = true
		}
	})

	window.addEventListener('drop', async (e) => {
		e.preventDefault()
		overlay.hidden = true
		if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length) {
			let count = 0
			for (const file of e.dataTransfer.files) {
				if (file.path) {
					const res = await api.catalog.importFile({ filePath: file.path, instanceId: state.contentTarget })
					if (res && res.ok) count++
				}
			}
			if (count > 0) {
				playSound()
				toast(`Успешно установлено файлов: ${count}`)
				loadInstalledContent()
			}
		}
	})
}

/* 6. Галерея скриншотов */
let activeScreenshotPath = null

async function openScreenshots() {
	const targetSelect = $('#sc-target')
	if (targetSelect) {
		targetSelect.innerHTML = ''
		for (const inst of state.instances) {
			const opt = document.createElement('option')
			opt.value = inst.id
			opt.textContent = inst.name
			if (inst.id === state.contentTarget) opt.selected = true
			targetSelect.appendChild(opt)
		}
	}
	await loadScreenshots()
}

async function loadScreenshots() {
	const grid = $('#sc-grid')
	if (!grid) return
	grid.innerHTML = '<p class="muted small">Загрузка снимков…</p>'

	const instId = $('#sc-target')?.value || state.contentTarget
	const res = await api.screenshots.list(instId)
	grid.innerHTML = ''

	const files = Array.isArray(res) ? res : (res && res.items ? res.items : [])
	$('#sc-count').textContent = `${files.length} снимков`

	if (!files.length) {
		grid.innerHTML = '<div class="empty-hint">В этой сборке пока нет скриншотов. Нажмите F2 в игре, чтобы сделать первый снимок!</div>'
		return
	}

	for (const sc of files) {
		const card = document.createElement('div')
		card.className = 'sc-card'
		card.innerHTML = `
			<img class="sc-card__img" src="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 9'><rect width='16' height='9' fill='%23111a14'/></svg>" alt="${escapeHtml(sc.name)}" />
			<div class="sc-card__overlay">
				<div class="sc-card__name">${escapeHtml(sc.name)}</div>
				<div class="sc-card__meta">${fmtSize(sc.size)} · ${sc.time ? new Date(sc.time).toLocaleDateString() : ''}</div>
			</div>
		`
		grid.appendChild(card)

		api.screenshots.read(sc.fullPath).then((dataUrl) => {
			if (dataUrl) {
				const img = card.querySelector('.sc-card__img')
				if (img) img.src = dataUrl
			}
		})

		card.addEventListener('click', () => {
			playSound()
			openScreenshotModal(sc)
		})
	}
}

async function openScreenshotModal(sc) {
	activeScreenshotPath = sc.fullPath
	$('#sc-modal-title').textContent = sc.name
	$('#sc-modal-meta').textContent = `${fmtSize(sc.size)} · ${sc.time ? new Date(sc.time).toLocaleString() : ''}`
	const modalImg = $('#sc-modal-img')
	modalImg.src = ''

	const dataUrl = await api.screenshots.read(sc.fullPath)
	if (dataUrl) modalImg.src = dataUrl

	$('#screenshot-modal').hidden = false
}

function initScreenshots() {
	$('#sc-target')?.addEventListener('change', () => {
		playSound()
		loadScreenshots()
	})
	$('#btn-sc-refresh')?.addEventListener('click', () => {
		playSound()
		loadScreenshots()
	})
	$('#btn-sc-folder')?.addEventListener('click', () => {
		playSound()
		const instId = $('#sc-target')?.value || state.contentTarget
		api.screenshots.openFolder(instId)
	})
	$$('[data-scmodal-close]').forEach((el) => {
		el.addEventListener('click', () => {
			$('#screenshot-modal').hidden = true
		})
	})
	$('#btn-sc-open-folder')?.addEventListener('click', () => {
		playSound()
		const instId = $('#sc-target')?.value || state.contentTarget
		api.screenshots.openFolder(instId)
	})
	$('#btn-sc-open-ext')?.addEventListener('click', () => {
		playSound()
		if (activeScreenshotPath) api.screenshots.openFile(activeScreenshotPath)
	})
	$('#btn-sc-delete')?.addEventListener('click', async () => {
		if (!activeScreenshotPath) return
		if (confirm('Точно удалить этот скриншот?')) {
			playSound()
			await api.screenshots.delete(activeScreenshotPath)
			$('#screenshot-modal').hidden = true
			toast('Скриншот удалён')
			loadScreenshots()
		}
	})
}

/* ------------------------------------------------------------------ Старт */

async function boot() {
	initStarfield()
	initSkinPodium()
	initScreenshots()
	initDragAndDrop()
	updateLunacyUI()
	const info = await api.info()
	if (info.ok) {
		$('#app-version').textContent = `v${info.version}`
		state.web = info.web || {}
		state.memory = info.memory || state.memory
	}
	const restored = await api.restore()
	if (restored.ok && restored.session) await onAuthorized(restored.session)
	else showApp(false)
}

boot()
