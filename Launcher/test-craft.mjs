import net from 'net';
import crypto from 'crypto';

const sock = net.createConnection('\\\\.\\pipe\\discord-ipc-0');
sock.on('connect', () => {
  const payload = JSON.stringify({ v: 1, client_id: '450485984333660181' });
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
          state: 'Lunacy Visuals 2.0',
          details: 'lunacyvisual.fun',
          assets: {
            large_image: 'default',
            large_text: 'Lunacy Launcher'
          },
          buttons: [
            { label: '🌐 lunacyvisual.fun', url: 'https://lunacyvisual.fun' }
          ]
        }
      },
      nonce: crypto.randomUUID()
    };
    const s = JSON.stringify(act);
    const b = Buffer.alloc(8 + Buffer.byteLength(s));
    b.writeInt32LE(1, 0);
    b.writeInt32LE(Buffer.byteLength(s), 4);
    b.write(s, 8, 'utf8');
    sock.write(b);
  } else if (json.cmd === 'SET_ACTIVITY') {
    console.log('SET_ACTIVITY with 450485984333660181:', json.data);
    sock.destroy();
    process.exit(0);
  }
});
