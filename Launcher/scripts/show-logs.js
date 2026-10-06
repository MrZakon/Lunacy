#!/usr/bin/env node
/**
 * Показывает логи лаунчера и игры из папки userData.
 *
 *   npm run logs         — последние 200 строк каждого файла
 *   npm run logs -- -f   — следить в реальном времени
 */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

function userDataDir() {
	const name = 'Lunacy Launcher'
	if (process.platform === 'win32') {
		return path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'), name)
	}
	if (process.platform === 'darwin') {
		return path.join(os.homedir(), 'Library', 'Application Support', name)
	}
	return path.join(process.env.XDG_CONFIG_HOME || path.join(os.homedir(), '.config'), name)
}

const dir = path.join(userDataDir(), 'logs')
const files = ['launcher-latest.log', 'game-latest.log'].map((file) => path.join(dir, file))
const follow = process.argv.includes('-f') || process.argv.includes('--follow')

console.log(`Папка логов: ${dir}`)

for (const file of files) {
	if (!fs.existsSync(file)) {
		console.log(`— нет файла ${path.basename(file)} (запусти лаунчер хотя бы раз)`)
		continue
	}
	const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/)
	console.log(`\n===== ${path.basename(file)} — ${lines.length} строк =====`)
	console.log(lines.slice(-200).join('\n'))
}

if (follow) {
	console.log('\nСлежу за логами… Ctrl+C чтобы выйти')
	for (const file of files) {
		let size = fs.existsSync(file) ? fs.statSync(file).size : 0
		setInterval(() => {
			if (!fs.existsSync(file)) return
			const next = fs.statSync(file).size
			if (next <= size) {
				size = next
				return
			}
			const stream = fs.createReadStream(file, { start: size, end: next })
			stream.on('data', (chunk) => process.stdout.write(String(chunk)))
			size = next
		}, 700)
	}
}
