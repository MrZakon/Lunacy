import fs from 'node:fs'
import path from 'node:path'
import { spawn, spawnSync } from 'node:child_process'
import { buildLaunchCommand, listInstalled, verifyVersion } from './minecraft.js'
import { ensureInstanceDirs, ensureSharedDirs } from './instances.js'
import { gpuEnv, systemInfo } from './system.js'

export { systemInfo }

/* ------------------------------------------------------------------ Java */

/** Реальная мажорная версия Java по `java -version`. */
export function javaMajorOf(javaPath) {
	try {
		const result = spawnSync(javaPath, ['-version'], { encoding: 'utf8', windowsHide: true })
		const output = `${result.stderr || ''}${result.stdout || ''}`
		const match = output.match(/version "(\d+)(?:\.(\d+))?/)
		if (!match) return { major: null, output: output.trim() }
		const first = Number(match[1])
		const major = first === 1 ? Number(match[2] || 8) : first
		return { major, output: output.split(/\r?\n/)[0] || '' }
	} catch (error) {
		return { major: null, output: error.message }
	}
}

/** Понятное объяснение кода выхода вместо голого «ошибка 1». */
export function diagnoseExit(code, lines) {
	const text = lines.join('\n')
	if (/UnsupportedClassVersionError/i.test(text)) {
		const need = text.match(/class file version (\d+)/)
		const major = need ? Number(need[1]) - 44 : null
		return `Java слишком старая${major ? ` — нужна Java ${major}` : ''}. Выбери другую Java в настройках.`
	}
	if (/Could not find or load main class/i.test(text)) {
		return 'Java не нашла главный класс — версия установлена не полностью. Нажми «Проверить и починить».'
	}
	if (/NoClassDefFoundError|ClassNotFoundException/i.test(text)) {
		return 'Не хватает библиотек версии. Нажми «Проверить и починить» во вкладке «Менеджер версий».'
	}
	if (/no lwjgl|UnsatisfiedLinkError|Failed to locate library/i.test(text)) {
		return 'Не распакованы нативные библиотеки (LWJGL). Нажми «Проверить и починить».'
	}
	if (/OutOfMemoryError|Could not reserve enough space|Invalid maximum heap size/i.test(text)) {
		return 'Не хватило оперативной памяти — уменьши выделенную RAM в настройках.'
	}
	if (/Pixel format not accelerated|GLFW error|Failed to initialize GLFW|EXCEPTION_ACCESS_VIOLATION/i.test(text)) {
		return 'Проблема с видеодрайвером или выбранной видеокартой — обнови драйвер и проверь выбор GPU в настройках.'
	}
	if (/Incompatible mod set|Mod resolution encountered an incompatible mod set|requires .* of fabric/i.test(text)) {
		return 'Конфликт модов — мод собран под другую версию игры или загрузчика. Убери лишние моды в «Мастерской».'
	}
	if (/A potential solution has been determined|Missing or unsupported mandatory dependencies/i.test(text)) {
		return 'Forge нашёл конфликт модов — не хватает зависимостей. Смотри строки выше в консоли.'
	}
	if (/Invalid session|Failed to verify username|Authentication/i.test(text)) {
		return 'Игра не приняла сессию аккаунта — перезайди по лицензии во вкладке «Аккаунты».'
	}
	if (code === 1) {
		return 'Игра завершилась с кодом 1 — смотри последние строки в консоли, там причина.'
	}
	return `Игра завершилась с кодом ${code}.`
}

/**
 * Запуск игры. Версия берётся из установленных (versions/<id>/<id>.json),
 * ник — из менеджера аккаунтов (offline = пиратка, online = лицензия).
 */
export function launchGame({
	settings,
	account,
	versionId,
	instanceDir,
	javaPath,
	gpu,
	logFile,
	onLog,
	onExit,
}) {
	const gameDir = settings.gameDir
	fs.mkdirSync(gameDir, { recursive: true })

	const installed = listInstalled(gameDir, { includeDependencies: true })
	if (!installed.length) {
		throw new Error('Не установлена ни одна версия — открой вкладку «Менеджер версий»')
	}
	const target =
		installed.find((item) => item.id === versionId) ||
		installed.find((item) => !item.dependency) ||
		installed[0]

	// Игра запускается в личной папке сборки — там свои моды, ресурспаки, шейдеры и сейвы.
	const runDir = instanceDir || gameDir
	if (instanceDir) ensureInstanceDirs(instanceDir)

	// 1. Проверяем целостность до запуска — понятная ошибка вместо кода 1.
	const state = verifyVersion(gameDir, target.id)
	onLog?.(
		`Проверка ${target.id}: библиотек ${state.libraries}, не хватает ${state.missingCount}, client.jar ${state.clientJarOk ? 'есть' : 'нет'}`,
	)
	if (!state.clientJarOk || state.missingCount > 0) {
		throw new Error(
			`Версия ${target.id} установлена не полностью (нет ${state.missingCount + (state.clientJarOk ? 0 : 1)} файлов). Нажми «Проверить и починить» во вкладке «Менеджер версий».`,
		)
	}

	// 2. Проверяем Java нужной мажорной версии.
	const java = javaPath || (process.platform === 'win32' ? 'java.exe' : 'java')
	if (java !== 'java' && java !== 'java.exe' && !fs.existsSync(java)) {
		throw new Error(`Java не найдена по пути ${java} — выбери её в настройках`)
	}
	const detected = javaMajorOf(java)
	if (!detected.major) {
		throw new Error(
			`Java не отвечает (${java}). Установи JDK/JRE и выбери его в настройках. Ответ: ${detected.output || 'пусто'}`,
		)
	}
	const required = state.javaMajor || (Number(String(target.mc).split('.')[1]) >= 21 ? 21 : 17)
	if (detected.major < required) {
		throw new Error(
			`Для ${target.id} нужна Java ${required}, а выбрана Java ${detected.major}. Выбери подходящую в настройках («Java»).`,
		)
	}

	// 3. Собираем команду.
	const command = buildLaunchCommand({
		gameDir,
		instanceDir: runDir,
		versionId: target.id,
		account,
		settings,
		javaPath: java,
	})

	const ramGb = Math.max(2, Number(settings.memoryMax) || 4)
	const mcVersion = target.mc || target.id
	onLog?.(`Запуск Minecraft ${mcVersion} | Java ${detected.major} | Выделение ОЗУ: ${ramGb} GB`)

	if (logFile) {
		try {
			fs.mkdirSync(path.dirname(logFile), { recursive: true })
			fs.writeFileSync(
				logFile,
				`# Lunacy Launcher — запуск ${new Date().toISOString()}\n# Запуск Minecraft ${mcVersion} | Java ${detected.major} | Выделение ОЗУ: ${ramGb} GB\n\n`,
				'utf8',
			)
		} catch {
			/* лог-файл не критичен */
		}
	}
	const appendLog = (line) => {
		if (!logFile) return
		try {
			fs.appendFileSync(logFile, `${line}\n`, 'utf8')
		} catch {
			/* игнорируем */
		}
	}

	const child = spawn(java, command.args, {
		cwd: command.cwd,
		env: { ...process.env, ...gpuEnv(gpu) },
		windowsHide: false,
		detached: false,
	})

	// Последние строки вывода нужны, чтобы объяснить причину падения.
	const tail = []
	const pushLine = (line) => {
		tail.push(line)
		if (tail.length > 120) tail.shift()
		appendLog(line)
		onLog?.(line)
	}

	let started = false
	const readStream = (stream) => {
		stream?.setEncoding('utf8')
		let buffer = ''
		stream?.on('data', (chunk) => {
			buffer += chunk
			const parts = buffer.split(/\r?\n/)
			buffer = parts.pop() || ''
			for (const line of parts) {
				if (!line.trim()) continue
				if (!started && /Setting user|LWJGL Version|Backend library|Sound engine started/i.test(line)) {
					started = true
				}
				pushLine(line.trim())
			}
		})
	}
	readStream(child.stdout)
	readStream(child.stderr)

	child.on('error', (error) => {
		pushLine(`Ошибка запуска процесса: ${error.message}`)
		onExit?.(-1, `Не удалось запустить Java: ${error.message}`)
	})

	child.on('close', (code) => {
		const exitCode = code ?? 0
		const reason = exitCode === 0 ? null : diagnoseExit(exitCode, tail)
		if (reason) pushLine(`⛔ ${reason}`)
		onExit?.(exitCode, reason, tail.slice(-40))
	})

	return child
}

/** Путь к Java из настроек или из PATH. */
export function resolveJava(javaPath) {
	if (javaPath && fs.existsSync(javaPath)) return javaPath
	return process.platform === 'win32' ? 'java.exe' : 'java'
}

/**
 * Общая папка игры: только единые для всех сборок пакеты
 * (versions / libraries / assets / cache) и корень instances.
 * Моды, ресурспаки и шейдеры живут внутри каждой сборки.
 */
export function ensureGameDirs(gameDir) {
	ensureSharedDirs(gameDir)
	fs.mkdirSync(path.join(gameDir, 'logs'), { recursive: true })
	return gameDir
}
