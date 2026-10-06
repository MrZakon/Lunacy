import net from 'net';
import crypto from 'crypto';

export const DEFAULT_DISCORD_CLIENT_ID = process.env.LUNACY_DISCORD_CLIENT_ID || '1548006102392635522';

/**
 * Zero-dependency Discord Rich Presence client via named pipes (Windows / Linux / macOS)
 */
export class DiscordRpc {
  constructor(clientId = DEFAULT_DISCORD_CLIENT_ID) {
    this.clientId = clientId;
    this.socket = null;
    this.connected = false;
    this.ready = false;
    this.enabled = true;
    this.currentActivity = null;
    this.retryTimer = null;
    this.receiveBuffer = Buffer.alloc(0);
    this.lastError = null;
    this.onStatus = null;
  }

  setClientId(newId) {
    const requested = String(newId || '').trim();
    const next = /^\d{17,20}$/.test(requested) ? requested : DEFAULT_DISCORD_CLIENT_ID;
    if (this.clientId !== next) {
      this.clientId = next;
      if (this.connected) {
        this.destroy();
        this.connect();
      }
    }
  }

  getPipePath(id = 0) {
    if (process.platform === 'win32') {
      return `\\\\.\\pipe\\discord-ipc-${id}`;
    }
    const envPath = process.env.XDG_RUNTIME_DIR || process.env.TMPDIR || process.env.TMP || process.env.TEMP || '/tmp';
    return `${envPath.replace(/\/$/, '')}/discord-ipc-${id}`;
  }

  connect(pipeId = 0) {
    if (!this.enabled || this.connected || this.socket) return;
    if (pipeId > 9) {
      // Discord не запущен или недоступен — пробуем снова через 25 секунд
      if (this.retryTimer) clearTimeout(this.retryTimer);
      this.retryTimer = setTimeout(() => this.connect(0), 25000);
      return;
    }

    const path = this.getPipePath(pipeId);
    let socket;
    try {
      socket = net.createConnection(path);
    } catch {
      this.connect(pipeId + 1);
      return;
    }

    socket.on('connect', () => {
      this.socket = socket;
      this.connected = true;
      this.receiveBuffer = Buffer.alloc(0);
      this.lastError = null;
      this.onStatus?.({ connected: true, ready: false, clientId: this.clientId });
      this.sendHandshake();
    });

    socket.on('error', (error) => {
      this.lastError = error?.message || String(error);
      this.onStatus?.({ connected: false, ready: false, error: this.lastError, pipeId });
    });

    socket.on('close', () => {
      this.socket = null;
      this.connected = false;
      this.ready = false;
      if (this.enabled) {
        if (this.retryTimer) clearTimeout(this.retryTimer);
        const nextPipe = pipeId < 9 ? pipeId + 1 : 0;
        this.retryTimer = setTimeout(() => this.connect(nextPipe), pipeId < 9 ? 150 : 15000);
      }
    });

    socket.on('data', (data) => {
      this.receiveBuffer = Buffer.concat([this.receiveBuffer, data]);
      while (this.receiveBuffer.length >= 8) {
        const len = this.receiveBuffer.readInt32LE(4);
        if (len < 0 || len > 8 * 1024 * 1024) {
          this.lastError = 'Discord вернул некорректный пакет';
          socket.destroy();
          return;
        }
        if (this.receiveBuffer.length < 8 + len) return;
        const frame = this.receiveBuffer.subarray(8, 8 + len);
        this.receiveBuffer = this.receiveBuffer.subarray(8 + len);
        try {
          const json = JSON.parse(frame.toString('utf8'));
          this.handleMessage(json);
        } catch {}
      }
    });
  }

  handleMessage(json) {
    if (json?.evt === 'READY') {
      this.ready = true;
      this.lastError = null;
      this.onStatus?.({ connected: true, ready: true, clientId: this.clientId });
      if (this.currentActivity) this.sendActivity(this.currentActivity);
      return;
    }
    if (json?.evt === 'ERROR') {
      this.lastError = json?.data?.message || json?.message || 'Discord отклонил Rich Presence';
      this.onStatus?.({ connected: this.connected, ready: this.ready, error: this.lastError });
      return;
    }
    if (json?.cmd === 'SET_ACTIVITY') {
      this.onStatus?.({ connected: true, ready: true, activitySent: true });
    }
  }

  send(op, payload) {
    if (!this.socket || !this.connected) return;
    try {
      const json = JSON.stringify(payload);
      const len = Buffer.byteLength(json);
      const packet = Buffer.alloc(8 + len);
      packet.writeInt32LE(op, 0);
      packet.writeInt32LE(len, 4);
      packet.write(json, 8, len, 'utf8');
      this.socket.write(packet);
    } catch (_e) {
      // Ignore write errors
    }
  }

  sendHandshake() {
    this.send(0, { v: 1, client_id: this.clientId });
  }

  sendActivity(activity) {
    if (!this.enabled || !this.connected || !this.ready) return;

    let startTimestamp;
    if (activity.startTimestamp) {
      const ts = Number(activity.startTimestamp);
      startTimestamp = ts > 1e11 ? Math.floor(ts / 1000) : ts;
    }

    const payload = {
      cmd: 'SET_ACTIVITY',
      args: {
        pid: process.pid,
        activity: {
          state: activity.state || 'В лаунчере • Кастомные визуалы',
          details: activity.details || 'Lunacy Launcher 2.1 • lunacyvisual.fun',
          timestamps: startTimestamp ? { start: startTimestamp } : undefined,
          assets: {
            large_image: activity.largeImage || 'lunacy_logo',
            large_text: activity.largeText || 'Lunacy Visuals 2.1 • lunacyvisual.fun',
            small_image: activity.smallImage || 'lunacy_logo',
            small_text: activity.smallText || 'Визуалы Lunacy Visuals активны'
          },
          buttons: activity.buttons || [
            { label: '🌐 Перейти на сайт', url: 'https://lunacyvisual.fun' },
            { label: '✨ Скачать визуалы', url: 'https://lunacyvisual.fun' }
          ]
        }
      },
      nonce: crypto.randomUUID()
    };

    this.send(1, payload);
  }

  setActivity(activity) {
    this.currentActivity = activity;
    if (this.ready) {
      this.sendActivity(activity);
    }
  }

  clearActivity() {
    this.currentActivity = null;
    if (!this.connected || !this.ready) return;
    this.send(1, {
      cmd: 'SET_ACTIVITY',
      args: {
        pid: process.pid,
        activity: null
      },
      nonce: crypto.randomUUID()
    });
  }

  destroy() {
    if (this.retryTimer) clearTimeout(this.retryTimer);
    if (this.socket) {
      this.socket.destroy();
      this.socket = null;
    }
    this.connected = false;
    this.ready = false;
    this.receiveBuffer = Buffer.alloc(0);
  }
}

export const discordRpc = new DiscordRpc();
