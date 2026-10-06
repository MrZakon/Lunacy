import net from 'net';
import crypto from 'crypto';

const sock = net.createConnection('\\\\.\\pipe\\discord-ipc-0');
sock.on('connect', () => {
  const payload = JSON.stringify({ v: 1, client_id: '1500886498462011443' });
  const len = Buffer.byteLength(payload);
  const buf = Buffer.alloc(8 + len);
  buf.writeInt32LE(0, 0);
  buf.writeInt32LE(len, 4);
  buf.write(payload, 8, len, 'utf8');
  sock.write(buf);
});

sock.on('data', (d) => {
  const json = JSON.parse(d.slice(8).toString('utf8'));
  if (json.evt === 'READY') {
    const act = {
      cmd: 'SET_ACTIVITY',
      args: {
        pid: process.pid,
        activity: {
          state: 'Test Start',
          timestamps: { start: Math.floor(Date.now()) }
        }
      },
      nonce: crypto.randomUUID()
    };
    const actStr = JSON.stringify(act);
    const actLen = Buffer.byteLength(actStr);
    const actBuf = Buffer.alloc(8 + actLen);
    actBuf.writeInt32LE(1, 0);
    actBuf.writeInt32LE(actLen, 4);
    actBuf.write(actStr, 8, actLen, 'utf8');
    sock.write(actBuf);
  } else if (json.cmd === 'SET_ACTIVITY') {
    console.log('Timestamp returned:', json.data?.timestamps);
    sock.destroy();
    process.exit(0);
  }
});
