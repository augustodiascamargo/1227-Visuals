// 1227 Link protocol v1, the same bytes as net.m1227.social.LinkProtocol (Java) and the 1227 Relay plugin.
export const MAGIC = 0x27, VERSION = 1;
export const HELLO = 1, STATE = 2, EMOTE = 3, STOP = 4;
export const WELCOME = 0x41, RELAY = 0x42, LEAVE = 0x43;
export const MAX_BYTES = 256;

/** A message a client may send: header, size and a client type. */
export function checkClient(b) {
  return b instanceof Uint8Array && b.length >= 3 && b.length <= MAX_BYTES && b[0] === MAGIC && b[1] === VERSION && b[2] >= HELLO && b[2] <= STOP;
}

function uuidBytes(hex) {
  const out = new Uint8Array(16);
  for (let i = 0; i < 16; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
  return out;
}

/** RELAY: [27 01 42][uuid 16][client type][client body]. */
export function relayOf(uuidHex, msg) {
  const out = new Uint8Array(3 + 16 + 1 + (msg.length - 3));
  out.set([MAGIC, VERSION, RELAY], 0);
  out.set(uuidBytes(uuidHex), 3);
  out[19] = msg[2];
  out.set(msg.subarray(3), 20);
  return out;
}

export function leaveOf(uuidHex) {
  const out = new Uint8Array(19);
  out.set([MAGIC, VERSION, LEAVE], 0);
  out.set(uuidBytes(uuidHex), 3);
  return out;
}

export function welcomeOf(rate, name) {
  const n = new TextEncoder().encode(name).subarray(0, 32);
  const out = new Uint8Array(5 + n.length);
  out.set([MAGIC, VERSION, WELCOME, Math.max(1, Math.min(50, rate)), n.length], 0);
  out.set(n, 5);
  return out;
}

export function toB64(u8) {
  let s = "";
  for (const x of u8) s += String.fromCharCode(x);
  return btoa(s);
}

export function fromB64(s) {
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}
