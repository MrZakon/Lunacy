import { execSync } from 'node:child_process';

let cachedHwid = null;

export function getHwid() {
	if (cachedHwid) return cachedHwid;

	// 1. Попытка через wmic (для старых версий Windows)
	try {
		const out = execSync('wmic csproduct get uuid', { encoding: 'utf8', timeout: 3000 });
		const lines = out.split('\n').map(l => l.trim()).filter(Boolean);
		if (lines[1] && lines[1] !== 'UNKNOWN_HWID' && lines[1].length > 8) {
			cachedHwid = lines[1];
			return cachedHwid;
		}
	} catch {}

	// 2. Попытка через PowerShell Get-CimInstance (Windows 11 24H2+, где wmic удален)
	try {
		const out = execSync('powershell -NoProfile -NonInteractive -Command "(Get-CimInstance Win32_ComputerSystemProduct).UUID"', {
			encoding: 'utf8',
			timeout: 5000,
		}).trim();
		if (out && out.length > 8 && !out.includes('error')) {
			cachedHwid = out;
			return cachedHwid;
		}
	} catch {}

	// 3. Быстрый и надежный системный fallback: MachineGuid из реестра Windows
	try {
		const out = execSync('reg query "HKLM\\SOFTWARE\\Microsoft\\Cryptography" /v MachineGuid', {
			encoding: 'utf8',
			timeout: 3000,
		});
		const match = /MachineGuid\s+REG_SZ\s+([A-Fa-f0-9-]+)/i.exec(out);
		if (match && match[1]) {
			cachedHwid = match[1].trim();
			return cachedHwid;
		}
	} catch {}

	cachedHwid = 'UNKNOWN_HWID';
	return cachedHwid;
}
