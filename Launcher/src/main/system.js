import os from 'node:os'
import fs from 'node:fs'
import path from 'node:path'
import { execFile, execFileSync } from 'node:child_process'

const isWin = process.platform === 'win32'
const isMac = process.platform === 'darwin'

/* ------------------------------------------------------------------ RAM */

export function memoryInfo() {
	const totalBytes = os.totalmem()
	const totalGb = Math.max(2, Math.floor(totalBytes / 1024 ** 3))
	const freeGb = Math.max(1, Math.floor(os.freemem() / 1024 ** 3))
	// Максимум в слайдере = сколько реально стоит в ПК.
	const maxGb = totalGb
	// Рекомендация: половина памяти, но не больше 16 ГБ.
	const recommendedGb = Math.min(16, Math.max(2, Math.floor(totalGb / 2)))
	return { totalGb, freeGb, maxGb, recommendedGb }
}

/* ----------------------------------------------------------------- Java */

function runQuiet(file, args, timeout = 6000) {
	return new Promise((resolve) => {
		try {
			execFile(file, args, { timeout, windowsHide: true }, (error, stdout, stderr) => {
				resolve({ error, out: `${stdout || ''}${stderr || ''}` })
			})
		} catch (error) {
			resolve({ error, out: '' })
		}
	})
}

function javaBinary(dir) {
	return path.join(dir, 'bin', isWin ? 'java.exe' : 'java')
}

function pushIfJava(list, file) {
	try {
		if (file && fs.existsSync(file) && fs.statSync(file).isFile()) {
			const normalized = path.normalize(file)
			if (!list.includes(normalized)) list.push(normalized)
		}
	} catch {
		/* ignore */
	}
}

function scanJavaRoot(list, root) {
	try {
		if (!fs.existsSync(root)) return
		for (const entry of fs.readdirSync(root)) {
			pushIfJava(list, javaBinary(path.join(root, entry)))
		}
	} catch {
		/* ignore */
	}
}

/** Рекурсивный поиск java в runtime-папках Minecraft (глубина ограничена). */
function scanRuntimeDir(list, root, depth = 0) {
	if (depth > 4) return
	try {
		if (!fs.existsSync(root)) return
		for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
			const full = path.join(root, entry.name)
			if (entry.isDirectory()) {
				if (entry.name === 'bin') {
					pushIfJava(list, path.join(full, isWin ? 'java.exe' : 'java'))
				} else {
					scanRuntimeDir(list, full, depth + 1)
				}
			}
		}
	} catch {
		/* ignore */
	}
}

export function javaCandidatePaths(extraDirs = []) {
	const list = []

	if (process.env.JAVA_HOME) pushIfJava(list, javaBinary(process.env.JAVA_HOME))

	if (isWin) {
		const programFiles = [
			process.env.ProgramFiles || 'C:\\Program Files',
			process.env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)',
			process.env.ProgramW6432 || 'C:\\Program Files',
		]
		const vendors = [
			'Java',
			'Eclipse Adoptium',
			'Eclipse Foundation',
			'AdoptOpenJDK',
			'Zulu',
			'BellSoft',
			'Amazon Corretto',
			'Microsoft',
			'RedHat',
			'Semeru',
			'JetBrains',
			'Temurin',
			'GraalVM',
		]
		for (const base of programFiles) {
			for (const vendor of vendors) scanJavaRoot(list, path.join(base, vendor))
			scanJavaRoot(list, base)
		}
		const appData = process.env.APPDATA
		if (appData) {
			scanRuntimeDir(list, path.join(appData, '.minecraft', 'runtime'))
		}
		const localAppData = process.env.LOCALAPPDATA
		if (localAppData) {
			scanRuntimeDir(
				list,
				path.join(localAppData, 'Packages'),
				3,
			)
			scanJavaRoot(list, path.join(localAppData, 'Programs', 'Eclipse Adoptium'))
		}
		scanRuntimeDir(
			list,
			path.join(
				process.env.ProgramFiles || 'C:\\Program Files',
				'Minecraft Launcher',
				'runtime',
			),
		)
	} else if (isMac) {
		scanJavaRoot(list, '/Library/Java/JavaVirtualMachines')
		for (const entry of ['/Library/Java/JavaVirtualMachines']) {
			try {
				for (const dir of fs.readdirSync(entry)) {
					pushIfJava(list, path.join(entry, dir, 'Contents', 'Home', 'bin', 'java'))
				}
			} catch {
				/* ignore */
			}
		}
		pushIfJava(list, '/usr/bin/java')
	} else {
		scanJavaRoot(list, '/usr/lib/jvm')
		pushIfJava(list, '/usr/bin/java')
		pushIfJava(list, '/usr/local/bin/java')
		scanRuntimeDir(list, path.join(os.homedir(), '.minecraft', 'runtime'))
	}

	for (const dir of extraDirs) scanRuntimeDir(list, dir)

	// java из PATH
	try {
		const out = execFileSync(isWin ? 'where' : 'which', ['java'], {
			encoding: 'utf8',
			timeout: 4000,
			windowsHide: true,
		})
		for (const line of out.split(/\r?\n/)) pushIfJava(list, line.trim())
	} catch {
		/* java нет в PATH */
	}

	return list
}

export function parseJavaVersion(output) {
	const match = /version "([^"]+)"/.exec(output || '')
	if (!match) return null
	const raw = match[1]
	let major = Number.parseInt(raw.split('.')[0], 10)
	if (major === 1) major = Number.parseInt(raw.split('.')[1] || '8', 10)
	const is64 = /64-Bit/i.test(output)
	const vendor = /openjdk/i.test(output) ? 'OpenJDK' : 'Oracle/другой'
	return { raw, major: Number.isFinite(major) ? major : null, is64, vendor }
}

/** Список установленных Java с версиями — для выпадашки в настройках. */
export async function detectJavaInstalls(extraDirs = []) {
	const candidates = javaCandidatePaths(extraDirs)
	const checked = await Promise.all(
		candidates.map(async (file) => {
			const { error, out } = await runQuiet(file, ['-version'])
			if (error && !out) return null
			const info = parseJavaVersion(out)
			if (!info) return null
			return {
				path: file,
				version: info.raw,
				major: info.major,
				bits: info.is64 ? 64 : 32,
				vendor: info.vendor,
				label: `Java ${info.major || '?'} — ${info.raw}${info.is64 ? ' (64-bit)' : ' (32-bit)'}`,
			}
		}),
	)

	const unique = new Map()
	for (const item of checked) {
		if (item && !unique.has(item.path.toLowerCase())) unique.set(item.path.toLowerCase(), item)
	}
	return [...unique.values()].sort((a, b) => (b.major || 0) - (a.major || 0))
}

/** Подбор Java под конкретную версию Minecraft. */
export function pickJavaFor(javaList, requiredMajor) {
	if (!javaList.length) return null
	if (!requiredMajor) return javaList[0]
	const exact = javaList.find((item) => item.major === requiredMajor)
	if (exact) return exact
	const higher = javaList
		.filter((item) => (item.major || 0) >= requiredMajor)
		.sort((a, b) => (a.major || 0) - (b.major || 0))[0]
	return higher || javaList[0]
}

/* ------------------------------------------------------------- Видеокарты */

function classifyGpu(name) {
	const text = name.toLowerCase()
	if (text.includes('nvidia') || text.includes('geforce') || text.includes('rtx') || text.includes('gtx')) {
		return 'nvidia'
	}
	if (text.includes('radeon') || text.includes('amd') || text.includes('rx ')) return 'amd'
	if (text.includes('intel') || text.includes('uhd') || text.includes('iris')) return 'intel'
	return 'other'
}

/** Список видеокарт ПК. Если их несколько — игрок выбирает, на какой запускать. */
export async function detectGpus() {
	const names = []
	if (isWin) {
		const ps = await runQuiet(
			'powershell',
			[
				'-NoProfile',
				'-NonInteractive',
				'-Command',
				'Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name',
			],
			8000,
		)
		if (!ps.error) {
			names.push(...ps.out.split(/\r?\n/).map((line) => line.trim()).filter(Boolean))
		}
		if (!names.length) {
			const wmic = await runQuiet('wmic', ['path', 'win32_VideoController', 'get', 'name'], 8000)
			names.push(
				...wmic.out
					.split(/\r?\n/)
					.map((line) => line.trim())
					.filter((line) => line && !/^name$/i.test(line)),
			)
		}
	} else if (isMac) {
		const out = await runQuiet('system_profiler', ['SPDisplaysDataType'], 10000)
		for (const line of out.out.split(/\r?\n/)) {
			const match = /Chipset Model:\s*(.+)/.exec(line)
			if (match) names.push(match[1].trim())
		}
	} else {
		const out = await runQuiet('sh', ['-c', 'lspci | grep -Ei "vga|3d|display"'], 8000)
		for (const line of out.out.split(/\r?\n/)) {
			const match = /:\s*(.+)$/.exec(line.trim())
			if (match) names.push(match[1].trim())
		}
	}

	const unique = [...new Set(names)].filter(Boolean)
	return unique.map((name, index) => ({
		id: `gpu-${index}`,
		name,
		vendor: classifyGpu(name),
		primary: index === 0,
	}))
}

/**
 * Переменные окружения, чтобы игра стартовала на выбранной видеокарте.
 * На ноутах с двумя GPU это реально переключает рендер.
 */
export function gpuEnv(gpu) {
	if (!gpu) return {}
	if (isWin) {
		if (gpu.vendor === 'nvidia') return { SHIM_MCCOMPAT: '0x800000001' } // dGPU
		if (gpu.vendor === 'intel') return { SHIM_MCCOMPAT: '0x800000002' } // iGPU
		if (gpu.vendor === 'amd') {
			// В зависимости от того встроенная она или нет. Предположим, если название содержит Graphics - встройка
			if (gpu.name.toLowerCase().includes('graphics') || gpu.name.toLowerCase().includes('radeon(tm)')) {
				return { SHIM_MCCOMPAT: '0x800000002', GPU_DEVICE_ORDINAL: '0' }
			}
			return { SHIM_MCCOMPAT: '0x800000001', GPU_DEVICE_ORDINAL: '1' } // dGPU
		}
		return { SHIM_MCCOMPAT: '0x800000002' }
	}
	if (isMac) return {}
	if (gpu.vendor === 'nvidia') {
		return { __NV_PRIME_RENDER_OFFLOAD: '1', __GLX_VENDOR_LIBRARY_NAME: 'nvidia' }
	}
	if (gpu.vendor === 'amd') return { DRI_PRIME: '1' }
	return {}
}

/* --------------------------------------------------------------- Сводка */

export function systemInfo() {
	const memory = memoryInfo()
	const cpus = os.cpus()
	return {
		platform: process.platform,
		arch: process.arch,
		release: os.release(),
		cpu: cpus[0]?.model?.trim() || 'Неизвестный CPU',
		cores: cpus.length,
		...memory,
		hostname: os.hostname(),
	}
}
