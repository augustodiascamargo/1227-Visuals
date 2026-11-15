// 1227 Link online relay. Lets 1227 players see each other's emotes and cosmetics on ANY Minecraft
// server, without a plugin on that server: the clients talk to this relay, the same idea as Lunar's
// own service. Cosmetic data only; the relay never sees anything of the game itself.
//
// GET /v1/room/<32 hex>?u=<uuid hex>&n=<name>&s=<server id>  (WebSocket)
//   room = SHA-256 of the Minecraft server address (the relay never learns the address)
//   identity = Mojang's hasJoined check of the server id the client just registered with Mojang
import { checkClient, relayOf, leaveOf, welcomeOf, toB64, fromB64, HELLO, STATE } from "./protocol.js";

const RATE = 8; // messages per second per player; extra ones are dropped

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (url.pathname === "/" || url.pathname === "/health") return new Response("1227 Link relay OK\n");
    const m = url.pathname.match(/^\/v1\/room\/([0-9a-f]{32})$/);
    if (!m) return new Response("not found\n", { status: 404 });
    if ((req.headers.get("Upgrade") || "").toLowerCase() !== "websocket") return new Response("expected a WebSocket\n", { status: 426 });
    const u = url.searchParams.get("u") || "", n = url.searchParams.get("n") || "", s = url.searchParams.get("s") || "";
    if (!/^[0-9a-f]{32}$/.test(u) || !/^[A-Za-z0-9_]{1,16}$/.test(n) || !/^[0-9a-f]{8,64}$/.test(s)) {
      return new Response("bad request\n", { status: 400 });
    }
    let verified = false;
    try {
      const r = await fetch("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" + encodeURIComponent(n) + "&serverId=" + s);
      if (r.status === 200) {
        const j = await r.json();
        verified = !!j && j.id === u;
      }
    } catch (e) {
      verified = false;
    }
    if (!verified && env.ALLOW_UNVERIFIED !== "true") return new Response("not verified with Mojang\n", { status: 403 });
    const headers = new Headers(req.headers);
    headers.set("X-1227-UUID", u);
    const stub = env.ROOMS.get(env.ROOMS.idFromName(m[1]));
    return stub.fetch(new Request(req, { headers }));
  },
};

/** One room = the players of one Minecraft server. WebSocket Hibernation: idle rooms cost nothing. */
export class Room {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  name() {
    return this.env.RELAY_NAME || "1227 Link";
  }

  async fetch(req) {
    const uuid = req.headers.get("X-1227-UUID");
    if (!uuid) return new Response("bad request\n", { status: 400 });
    // the same player connecting again (reconnect, second client): the old connection goes
    for (const old of this.ctx.getWebSockets(uuid)) {
      try {
        old.serializeAttachment({ ...old.deserializeAttachment(), replaced: true });
        old.close(4000, "replaced");
      } catch (e) {}
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server, [uuid]);
    server.serializeAttachment({ uuid, state: null, tokens: RATE, at: Date.now() });
    server.send(welcomeOf(RATE, this.name()));
    this.sendStatesTo(server, uuid);
    return new Response(null, { status: 101, webSocket: client });
  }

  sendStatesTo(ws, uuid) {
    for (const o of this.ctx.getWebSockets()) {
      if (o === ws) continue;
      const a = o.deserializeAttachment();
      if (a && a.uuid !== uuid && a.state && !a.replaced) {
        try {
          ws.send(relayOf(a.uuid, fromB64(a.state)));
        } catch (e) {}
      }
    }
  }

  async webSocketMessage(ws, msg) {
    if (typeof msg === "string") return;
    const data = new Uint8Array(msg);
    if (!checkClient(data)) return;
    const a = ws.deserializeAttachment();
    if (!a) return;
    const now = Date.now();
    a.tokens = Math.min(RATE, a.tokens + ((now - a.at) / 1000) * RATE);
    a.at = now;
    if (a.tokens < 1) {
      ws.serializeAttachment(a);
      return;
    }
    a.tokens -= 1;
    if (data[2] === HELLO) {
      ws.serializeAttachment(a);
      ws.send(welcomeOf(RATE, this.name()));
      this.sendStatesTo(ws, a.uuid);
      return;
    }
    if (data[2] === STATE) a.state = toB64(data);
    ws.serializeAttachment(a);
    const out = relayOf(a.uuid, data);
    for (const o of this.ctx.getWebSockets()) {
      if (o === ws) continue;
      try {
        o.send(out);
      } catch (e) {}
    }
  }

  async webSocketClose(ws, code, reason) {
    this.gone(ws);
    try {
      ws.close(code, reason);
    } catch (e) {}
  }

  async webSocketError(ws) {
    this.gone(ws);
  }

  gone(ws) {
    const a = ws.deserializeAttachment();
    if (!a || a.replaced) return;
    const out = leaveOf(a.uuid);
    for (const o of this.ctx.getWebSockets()) {
      if (o === ws) continue;
      try {
        o.send(out);
      } catch (e) {}
    }
  }
}
