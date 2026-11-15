// node --test test/  — the Room logic with a simulated Workers runtime (no Cloudflare account needed).
import test from "node:test";
import assert from "node:assert/strict";

class FakeWs {
  constructor() { this.sent = []; this.closed = null; this.att = null; this.tags = []; }
  send(b) { if (this.closed) throw new Error("closed"); this.sent.push(b); }
  close(code, why) { this.closed = [code, why]; }
  serializeAttachment(a) { this.att = JSON.parse(JSON.stringify(a)); }
  deserializeAttachment() { return this.att ? JSON.parse(JSON.stringify(this.att)) : null; }
}
globalThis.WebSocketPair = class { constructor() { this[0] = new FakeWs(); this[1] = new FakeWs(); } };
globalThis.Response = class { constructor(body, init) { this.status = (init && init.status) || 200; this.webSocket = init && init.webSocket; } };

const { Room } = await import("../src/index.js");
const P = await import("../src/protocol.js");

function ctx() {
  const all = [];
  return {
    all,
    acceptWebSocket(ws, tags) { ws.tags = tags; all.push(ws); },
    getWebSockets(tag) { return all.filter((w) => !w.closed && (!tag || w.tags.includes(tag))); },
  };
}
const req = (uuid) => ({ headers: { get: (h) => (h === "X-1227-UUID" ? uuid : null) } });
const A = "a".repeat(32), B = "b".repeat(32), C = "c".repeat(32);

function stateMsg(seq) {
  // [27 01 02][seq][flags][6 colours][emote][emoteMs] like Java LinkProtocol.state
  const b = new Uint8Array(3 + 4 + 4 + 24 + 1 + 4);
  b.set([0x27, 1, 2]);
  new DataView(b.buffer).setInt32(3, seq);
  new DataView(b.buffer).setInt32(7, 5);
  b[35] = 0xff; // emote -1
  return b;
}

test("welcome, relay to others, cached state for late joiners, leave", async () => {
  const c = ctx();
  const room = new Room(c, { RELAY_NAME: "Test" });
  await room.fetch(req(A));
  await room.fetch(req(B));
  const [wa, wb] = c.all;
  assert.equal(wa.sent[0][2], P.WELCOME);
  await room.webSocketMessage(wa, stateMsg(7).buffer);
  const got = wb.sent.at(-1);
  assert.equal(got[2], P.RELAY);
  assert.equal(Buffer.from(got.subarray(3, 19)).toString("hex"), A);
  assert.equal(got[19], P.STATE);
  assert.equal(new DataView(got.buffer, got.byteOffset).getInt32(20), 7);
  assert.equal(wa.sent.length, 1, "no echo to the sender");
  await room.fetch(req(C));
  const wc = c.all[2];
  assert.ok(wc.sent.some((m) => m[2] === P.RELAY && Buffer.from(m.subarray(3, 19)).toString("hex") === A), "late joiner got A's state");
  await room.webSocketClose(wa, 1000, "bye");
  assert.equal(wb.sent.at(-1)[2], P.LEAVE);
});

test("invalid and server-type messages are dropped, rate limit holds", async () => {
  const c = ctx();
  const room = new Room(c, {});
  await room.fetch(req(A));
  await room.fetch(req(B));
  const [wa, wb] = c.all;
  const before = wb.sent.length;
  await room.webSocketMessage(wa, new Uint8Array([0x27, 1, 0x42, 0, 0]).buffer);
  await room.webSocketMessage(wa, new Uint8Array(300).buffer);
  await room.webSocketMessage(wa, "text");
  assert.equal(wb.sent.length, before);
  for (let i = 0; i < 30; i++) await room.webSocketMessage(wa, new Uint8Array([0x27, 1, 4, 0, 0, 0, i]).buffer);
  assert.equal(wb.sent.length - before, 8, "8 tokens");
});

test("reconnecting player replaces the old connection without a LEAVE", async () => {
  const c = ctx();
  const room = new Room(c, {});
  await room.fetch(req(A));
  await room.fetch(req(B));
  await room.fetch(req(A));
  const old = c.all[0];
  assert.deepEqual(old.closed[0], 4000);
  const wb = c.all[1];
  await room.webSocketClose(old, 4000, "replaced");
  assert.ok(!wb.sent.some((m) => m[2] === P.LEAVE));
});
