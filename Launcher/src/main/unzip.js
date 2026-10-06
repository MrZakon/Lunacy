import fs from 'node:fs'
import fsp from 'node:fs/promises'
import path from 'node:path'
import zlib from 'node:zlib'
import { promisify } from 'node:util'

const inflateRawAsync = promisify(zlib.inflateRaw)

/**
 * Асинхронный распаковщик ZIP: не блокирует event-loop интерфейса лаунчера.
 */
export async function extractZipAsync(zipPath, destDir, { onEntry, onProgress } = {}) {
	const buf = await fsp.readFile(zipPath)
	const eocd = findEocd(buf)
	if (!eocd) throw new Error('Некорректный ZIP: не найден End of Central Directory')

	const { entryCount, cdOffset } = eocd
	let ptr = cdOffset
	const written = []

	for (let i = 0; i < entryCount; i++) {
		if (buf.readUInt32LE(ptr) !== 0x02014b50) throw new Error('Повреждён центральный каталог ZIP')
		const method = buf.readUInt16LE(ptr + 10)
		const compressedSize = buf.readUInt32LE(ptr + 20)
		const nameLen = buf.readUInt16LE(ptr + 28)
		const extraLen = buf.readUInt16LE(ptr + 30)
		const commentLen = buf.readUInt16LE(ptr + 32)
		const localOffset = buf.readUInt32LE(ptr + 42)
		const name = buf.toString('utf8', ptr + 46, ptr + 46 + nameLen)
		ptr += 46 + nameLen + extraLen + commentLen

		if (compressedSize === 0xffffffff || localOffset === 0xffffffff) {
			throw new Error('ZIP64 не поддерживается встроенным распаковщиком')
		}

		if (onEntry && onEntry(name) === false) continue

		const target = safeJoin(destDir, name)
		if (name.endsWith('/')) {
			await fsp.mkdir(target, { recursive: true })
			continue
		}

		if (buf.readUInt32LE(localOffset) !== 0x04034b50) throw new Error(`Повреждён локальный заголовок: ${name}`)
		const lNameLen = buf.readUInt16LE(localOffset + 26)
		const lExtraLen = buf.readUInt16LE(localOffset + 28)
		const dataStart = localOffset + 30 + lNameLen + lExtraLen
		const raw = buf.subarray(dataStart, dataStart + compressedSize)
		const content = method === 0 ? raw : await inflateRawAsync(raw)

		await fsp.mkdir(path.dirname(target), { recursive: true })
		await fsp.writeFile(target, content)
		written.push(target)

		if (onProgress) onProgress(i + 1, entryCount)
	}

	return written
}

/**
 * Минимальный синхронный распаковщик ZIP без внешних зависимостей.
 * Поддерживает методы 0 (store) и 8 (deflate) — этого достаточно для модов и ресурспаков.
 */
export function extractZip(zipPath, destDir, { onEntry } = {}) {
	const buf = fs.readFileSync(zipPath)
	const eocd = findEocd(buf)
	if (!eocd) throw new Error('Некорректный ZIP: не найден End of Central Directory')

	const { entryCount, cdOffset } = eocd
	let ptr = cdOffset
	const written = []

	for (let i = 0; i < entryCount; i++) {
		if (buf.readUInt32LE(ptr) !== 0x02014b50) throw new Error('Повреждён центральный каталог ZIP')
		const method = buf.readUInt16LE(ptr + 10)
		const compressedSize = buf.readUInt32LE(ptr + 20)
		const nameLen = buf.readUInt16LE(ptr + 28)
		const extraLen = buf.readUInt16LE(ptr + 30)
		const commentLen = buf.readUInt16LE(ptr + 32)
		const localOffset = buf.readUInt32LE(ptr + 42)
		const name = buf.toString('utf8', ptr + 46, ptr + 46 + nameLen)
		ptr += 46 + nameLen + extraLen + commentLen

		if (compressedSize === 0xffffffff || localOffset === 0xffffffff) {
			throw new Error('ZIP64 не поддерживается встроенным распаковщиком')
		}

		// onEntry может отфильтровать запись: вернёт false — файл пропускается.
		if (onEntry && onEntry(name) === false) continue

		const target = safeJoin(destDir, name)
		if (name.endsWith('/')) {
			fs.mkdirSync(target, { recursive: true })
			continue
		}

		if (buf.readUInt32LE(localOffset) !== 0x04034b50) throw new Error(`Повреждён локальный заголовок: ${name}`)
		const lNameLen = buf.readUInt16LE(localOffset + 26)
		const lExtraLen = buf.readUInt16LE(localOffset + 28)
		const dataStart = localOffset + 30 + lNameLen + lExtraLen
		const raw = buf.subarray(dataStart, dataStart + compressedSize)
		const content = method === 0 ? raw : zlib.inflateRawSync(raw)

		fs.mkdirSync(path.dirname(target), { recursive: true })
		fs.writeFileSync(target, content)
		written.push(target)
	}

	return written
}

function safeJoin(destDir, entryName) {
	const normalized = path.normalize(entryName).replace(/^([/\\])+/, '')
	const target = path.join(destDir, normalized)
	const rel = path.relative(destDir, target)
	if (rel.startsWith('..') || path.isAbsolute(rel)) {
		throw new Error(`Небезопасный путь в архиве: ${entryName}`)
	}
	return target
}

function findEocd(buf) {
	const min = Math.max(0, buf.length - 66 * 1024)
	for (let i = buf.length - 22; i >= min; i--) {
		if (buf.readUInt32LE(i) === 0x06054b50) {
			return {
				entryCount: buf.readUInt16LE(i + 10),
				cdOffset: buf.readUInt32LE(i + 16),
			}
		}
	}
	return null
}

export function isValidZip(zipPath) {
	try {
		if (!fs.existsSync(zipPath)) return false
		const buf = fs.readFileSync(zipPath)
		const eocd = findEocd(buf)
		if (!eocd || eocd.entryCount === 0) return false
		const { entryCount, cdOffset } = eocd
		let ptr = cdOffset
		for (let i = 0; i < entryCount; i++) {
			if (ptr + 46 > buf.length) return false
			if (buf.readUInt32LE(ptr) !== 0x02014b50) return false
			const nameLen = buf.readUInt16LE(ptr + 28)
			const extraLen = buf.readUInt16LE(ptr + 30)
			const commentLen = buf.readUInt16LE(ptr + 32)
			const localOffset = buf.readUInt32LE(ptr + 42)
			ptr += 46 + nameLen + extraLen + commentLen
			if (localOffset + 4 > buf.length) return false
			if (buf.readUInt32LE(localOffset) !== 0x04034b50) return false
		}
		return true
	} catch {
		return false
	}
}

/**
 * Читает и парсит JSON-файл из ZIP/JAR архива без распаковки на диск.
 */
export function readZipJson(zipPath, entryName) {
	try {
		if (!fs.existsSync(zipPath)) return null
		const buf = fs.readFileSync(zipPath)
		const eocd = findEocd(buf)
		if (!eocd || eocd.entryCount === 0) return null
		const { entryCount, cdOffset } = eocd
		let ptr = cdOffset
		for (let i = 0; i < entryCount; i++) {
			if (ptr + 46 > buf.length) return null
			if (buf.readUInt32LE(ptr) !== 0x02014b50) return null
			const method = buf.readUInt16LE(ptr + 10)
			const compressedSize = buf.readUInt32LE(ptr + 20)
			const nameLen = buf.readUInt16LE(ptr + 28)
			const extraLen = buf.readUInt16LE(ptr + 30)
			const commentLen = buf.readUInt16LE(ptr + 32)
			const localOffset = buf.readUInt32LE(ptr + 42)
			const name = buf.toString('utf8', ptr + 46, ptr + 46 + nameLen)
			ptr += 46 + nameLen + extraLen + commentLen
			if (name === entryName) {
				if (localOffset + 30 > buf.length) return null
				if (buf.readUInt32LE(localOffset) !== 0x04034b50) return null
				const lNameLen = buf.readUInt16LE(localOffset + 26)
				const lExtraLen = buf.readUInt16LE(localOffset + 28)
				const dataStart = localOffset + 30 + lNameLen + lExtraLen
				if (dataStart + compressedSize > buf.length) return null
				const raw = buf.subarray(dataStart, dataStart + compressedSize)
				const content = method === 0 ? raw : zlib.inflateRawSync(raw)
				return JSON.parse(content.toString('utf8'))
			}
		}
	} catch {
		return null
	}
	return null
}

