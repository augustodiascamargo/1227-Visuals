// Shows the latest version from the 1227 API (same manifest the launcher uses).
(async function () {
  const API = "https://api.1227client.workers.dev/v1/manifest";
  const el = document.getElementById("latest-text");
  try {
    const r = await fetch(API);
    if (!r.ok) throw new Error(r.status);
    const m = await r.json();
    const v = (m.versions || []).find((x) => x.version === m.latest);
    if (!v) {
      el.textContent = "Nenhuma versão publicada ainda.";
      return;
    }
    el.textContent = "";
    const a = document.createElement("a");
    a.className = "btn primary";
    a.href = v.file.url;
    a.textContent = "Baixar 1227 v" + v.version;
    const meta = document.createElement("p");
    meta.className = "muted small";
    meta.textContent = `Minecraft ${v.minecraft} · Forge ${v.forge} · ${(v.file.size / 1048576).toFixed(1)} MB · SHA-256 ${v.file.sha256}`;
    el.append(a, meta);
  } catch (e) {
    el.textContent = "Não foi possível consultar a versão agora. Tente de novo mais tarde.";
  }
})();
