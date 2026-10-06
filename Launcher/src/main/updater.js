import fs from 'node:fs'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { CONFIG } from './config.js'
import { ApiError, apiRequest } from './http.js'
import { extractZipAsync } from './unzip.js'

/** Числовое сравнение версий: 1.2.10 > 1.2.9, релиз > пререлиза. */
export function parseVersion(value) {
	const text = String(value ?? '0').trim().replace(/^v/i, '')
	const [core, pre = ''] = text.split('-')
	const numbers = core
		.split('.')
		.map((part) => Number.parseInt(part, 10))
		.map((part) => (Number.isFinite(part) ? part : 0))
	while (numbers.length < 3) numbers.push(0)
	return { numbers, pre: pre ? pre.split('.') : [] }
}

export function compareVersions(a, b) {
	const left = parseVersion(a)
	const right = parseVersion(b)
	const length = Math.max(left.numbers.length, right.numbers.length)
	for (let i = 0; i < length; i += 1) {
		const diff = (left.numbers[i] || 0) - (right.numbers[i] || 0)
		if (diff !== 0) return diff > 0 ? 1 : -1
	}
	if (!left.pre.length && right.pre.length) return 1
	if (left.pre.length && !right.pre.length) return -1
	const preLength = Math.max(left.pre.length, right.pre.length)
	for (let i = 0; i < preLength; i += 1) {
		const x = left.pre[i]
		const y = right.pre[i]
		if (x === y) continue
		if (x === undefined) return -1
		if (y === undefined) return 1
		const nx = Number(x)
		const ny = Number(y)
		if (Number.isFinite(nx) && Number.isFinite(ny)) return nx > ny ? 1 : -1
		return x > y ? 1 : -1
	}
	return 0
}

export function normalizeManifest(raw, current, extra = {}) {
	const version = String(raw?.version || current || '0.0.0')
	const available = raw?.available !== false
	return {
		current: String(current || '0.0.0'),
		version,
		updateAvailable: compareVersions(version, current || '0.0.0') > 0,
		notes: raw?.notes || raw?.note || null,
		plan: raw?.plan || null,
		endsAt: raw?.endsAt || null,
		available,
		canDownload: available && (extra.canDownload ?? true),
		downloadUrl: raw?.downloadUrl || null,
	}
}

/**
 * Проверка версии лаунчера — РОВНО ОДНА попытка.
 * Если не вышло — ошибка наверх, без автоповторов.
 */
export async function checkLauncherVersion(current) {
	const payload = await apiRequest(CONFIG.client.launcherMeta, { retries: 0 })
	return normalizeManifest(payload?.data, current)
}

/** Версия мода — требует Bearer + активную подписку. */
export async function checkModVersion(current, token) {
	if (!token) return normalizeManifest(null, current, { canDownload: false })
	try {
		const payload = await apiRequest(CONFIG.client.modMeta, { token, retries: 0 })
		return normalizeManifest(payload?.data, current)
	} catch (error) {
		if (error instanceof ApiError && error.isSubscriptionError) {
			return {
				...normalizeManifest(null, current, { canDownload: false }),
				blocked: 'subscription',
				buyUrl: error.buyUrl || CONFIG.web.buy,
			}
		}
		throw error
	}
}

/** /api/client/info — версии MC, загрузчик, цены, статус подписки. */
export async function fetchClientInfo(token) {
	const payload = await apiRequest(CONFIG.client.info, { token, retries: 0 })
	return payload?.data || null
}

/* ------------------------------------------------- установка обновления */

function writeScript(file, content) {
	fs.writeFileSync(file, content, { encoding: 'utf8' })
	if (process.platform !== 'win32') fs.chmodSync(file, 0o755)
	return file
}

/**
 * Самообновление: распаковывает скачанный архив и подменяет файлы лаунчера.
 * Если с сайта пришёл .exe / .msi — просто запускает установщик.
 */
export async function installLauncherUpdate({
	file,
	appDir,
	exePath,
	workDir,
	isPackaged = true,
	onLog,
}) {
	if (!fs.existsSync(file)) throw new Error('Файл обновления не найден')

	const lower = file.toLowerCase()
	if (lower.endsWith('.exe') || lower.endsWith('.msi')) {
		onLog?.('Запускаю установщик обновления…')
		if (process.platform === 'win32') {
			const child = spawn('cmd.exe', ['/c', 'start', '""', `"${file}"`], {
				detached: true,
				stdio: 'ignore',
				windowsHide: true,
			})
			child.unref()
		} else {
			const child = spawn(file, [], { detached: true, stdio: 'ignore', windowsHide: false })
			child.unref()
		}
		return { mode: 'installer', restart: true }
	}

	if (!lower.endsWith('.zip')) {
		throw new Error('Непонятный формат обновления — ожидался .zip или .exe')
	}

	const stageDir = path.join(workDir, `update-${Date.now().toString(36)}`)
	fs.mkdirSync(stageDir, { recursive: true })
	onLog?.('Распаковываю обновление…')
	await extractZipAsync(file, stageDir)

	// Если внутри архива одна корневая папка — копируем её содержимое.
	let sourceDir = stageDir
	const entries = fs.readdirSync(stageDir, { withFileTypes: true })
	if (entries.length === 1 && entries[0].isDirectory()) {
		sourceDir = path.join(stageDir, entries[0].name)
	}

	// В dev-режиме подменять файлы Electron нельзя — просто отдаём папку.
	if (!isPackaged) {
		return { mode: 'manual', stageDir: sourceDir, restart: false }
	}

	if (process.platform === 'win32') {
		const script = path.join(workDir, `apply-update-${Date.now().toString(36)}.bat`)
		writeScript(
			script,
			[
				'@echo off',
				'chcp 65001 >nul',
				`echo Жду закрытия лаунчера...`,
				`:waitloop`,
				`tasklist /FI "PID eq ${process.pid}" | find "${process.pid}" >nul`,
				'if not errorlevel 1 (',
				'  timeout /t 1 /nobreak >nul',
				'  goto waitloop',
				')',
				`robocopy "${sourceDir}" "${appDir}" /E /IS /IT /R:3 /W:2 >nul`,
				`start "" "${exePath}"`,
				`rmdir /S /Q "${stageDir}" >nul 2>&1`,
				`del "%~f0"`,
			].join('\r\n'),
		)
		onLog?.('Применяю обновление и перезапускаю лаунчер…')
		const child = spawn('cmd.exe', ['/c', script], {
			detached: true,
			stdio: 'ignore',
			windowsHide: true,
		})
		child.unref()
		return { mode: 'replace', restart: true, script }
	}

	const script = path.join(workDir, `apply-update-${Date.now().toString(36)}.sh`)
	writeScript(
		script,
		[
			'#!/bin/sh',
			`while kill -0 ${process.pid} 2>/dev/null; do sleep 1; done`,
			`cp -R "${sourceDir}/." "${appDir}/"`,
			`rm -rf "${stageDir}"`,
			`"${exePath}" &`,
			'rm -- "$0"',
		].join('\n'),
	)
	const child = spawn('sh', [script], { detached: true, stdio: 'ignore' })
	child.unref()
	return { mode: 'replace', restart: true, script }
}
