// 1227 API (Cloudflare Worker). Read-only JSON: version manifest and news.
// The data lives in ../data/*.json and is bundled at deploy time, so there is no database
// and no secret. Updating a release = edit data/manifest.json (CI prints the entry) + deploy.
import manifest from "../data/manifest.json";
import news from "../data/news.json";

const HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "access-control-allow-origin": "*",
  "cache-control": "public, max-age=300",
  "x-content-type-options": "nosniff",
};

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: HEADERS });
}

export default {
  async fetch(request) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: HEADERS });
    if (request.method !== "GET") return json({ error: "method not allowed" }, 405);
    const { pathname } = new URL(request.url);
    switch (pathname) {
      case "/v1/manifest":
        return json(manifest);
      case "/v1/news":
        return json(news);
      case "/health":
        return json({ ok: true });
      default:
        return json({ error: "not found" }, 404);
    }
  },
};
