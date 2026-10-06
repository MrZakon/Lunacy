import net from 'net';

/**
 * Encodes a VarInt into a Buffer
 */
function encodeVarInt(val) {
  const bytes = [];
  let v = val;
  while (true) {
    if ((v & ~0x7f) === 0) {
      bytes.push(v);
      break;
    } else {
      bytes.push((v & 0x7f) | 0x80);
      v >>>= 7;
    }
  }
  return Buffer.from(bytes);
}

/**
 * Reads a VarInt from a buffer starting at offset
 */
function readVarInt(buffer, offset = 0) {
  let result = 0;
  let numRead = 0;
  let currentByte = 0;

  while (true) {
    if (offset + numRead >= buffer.length) {
      return { value: null, bytesRead: 0 };
    }
    currentByte = buffer[offset + numRead];
    result |= (currentByte & 0x7f) << (7 * numRead);
    numRead++;

    if (numRead > 5) {
      throw new Error('VarInt is too big');
    }

    if ((currentByte & 0x80) !== 0x80) {
      break;
    }
  }

  return { value: result, bytesRead: numRead };
}

/**
 * Pings a Minecraft server and returns status, players, latency, and favicon.
 */
export async function pingServer(host, port = 25565, timeoutMs = 4000) {
  return new Promise((resolve) => {
    let settled = false;
    const finish = (result) => {
      if (!settled) {
        settled = true;
        try { socket.destroy(); } catch (_) {}
        resolve(result);
      }
    };

    const timer = setTimeout(() => {
      finish({ online: false, error: 'Таймаут соединения' });
    }, timeoutMs);

    const startTime = Date.now();
    const socket = net.createConnection({ host, port }, () => {
      try {
        // Construct Handshake Packet (ID 0x00)
        const hostBuf = Buffer.from(host, 'utf8');
        const hostLen = encodeVarInt(hostBuf.length);
        const protocolVersion = encodeVarInt(765); // 1.20.4 protocol
        const portBuf = Buffer.alloc(2);
        portBuf.writeUInt16BE(port, 0);
        const nextState = encodeVarInt(1); // 1 for status

        const packetData = Buffer.concat([
          Buffer.from([0x00]),
          protocolVersion,
          hostLen,
          hostBuf,
          portBuf,
          nextState
        ]);

        const packetLength = encodeVarInt(packetData.length);
        const handshake = Buffer.concat([packetLength, packetData]);

        // Request Packet (Length 1, Packet ID 0x00)
        const request = Buffer.from([0x01, 0x00]);

        socket.write(Buffer.concat([handshake, request]));
      } catch (err) {
        clearTimeout(timer);
        finish({ online: false, error: err.message });
      }
    });

    socket.setTimeout(timeoutMs);

    let incoming = Buffer.alloc(0);

    socket.on('data', (chunk) => {
      incoming = Buffer.concat([incoming, chunk]);

      try {
        let offset = 0;
        const totalLength = readVarInt(incoming, offset);
        if (totalLength.value === null) return;
        offset += totalLength.bytesRead;

        const packetId = readVarInt(incoming, offset);
        if (packetId.value === null) return;
        offset += packetId.bytesRead;

        const strLen = readVarInt(incoming, offset);
        if (strLen.value === null) return;
        offset += strLen.bytesRead;

        if (incoming.length - offset < strLen.value) {
          // Incomplete response, wait for next chunk
          return;
        }

        const jsonStr = incoming.toString('utf8', offset, offset + strLen.value);
        const pingMs = Math.max(1, Date.now() - startTime);
        clearTimeout(timer);

        try {
          const data = JSON.parse(jsonStr);
          let motdText = '';
          if (typeof data.description === 'string') {
            motdText = data.description;
          } else if (data.description && typeof data.description === 'object') {
            motdText = data.description.text || (data.description.extra ? data.description.extra.map(e => (typeof e === 'string' ? e : e.text)).join('') : '');
          }

          finish({
            online: true,
            pingMs,
            motd: motdText.replace(/§[0-9a-fk-or]/gi, '').trim() || 'Сервер Minecraft',
            players: {
              online: data.players ? (data.players.online ?? 0) : 0,
              max: data.players ? (data.players.max ?? 0) : 0
            },
            version: data.version ? data.version.name : 'Unknown',
            favicon: data.favicon || null
          });
        } catch (e) {
          finish({ online: true, pingMs, motd: 'Онлайн (JSON parse error)', players: { online: 0, max: 0 } });
        }
      } catch (err) {
        clearTimeout(timer);
        finish({ online: false, error: err.message });
      }
    });

    socket.on('error', (err) => {
      clearTimeout(timer);
      finish({ online: false, error: err.message || 'Ошибка подключения' });
    });

    socket.on('timeout', () => {
      clearTimeout(timer);
      finish({ online: false, error: 'Превышено время ожидания' });
    });
  });
}
