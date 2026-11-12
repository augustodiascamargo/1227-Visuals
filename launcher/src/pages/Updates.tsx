import type { UpdateInfo } from "../api";
import { mb } from "../api";

export default function Updates({ updates, error, onRetry }: { updates: UpdateInfo | null; error: string | null; onRetry: () => void }) {
  return (
    <div className="page">
      <h2>Atualizações</h2>
      {error && (
        <div className="notice err">
          {error} <button className="link" onClick={onRetry}>Tentar de novo</button>
        </div>
      )}
      {!updates && !error && <div className="muted">Consultando versões…</div>}
      {updates &&
        updates.manifest.versions.map((v) => (
          <section key={v.version} className="card">
            <div className="row">
              <h3>
                v{v.version} {v.version === updates.manifest.latest && <span className="tag">mais recente</span>}
                {v.version === updates.installed && <span className="tag ok">instalada</span>}
              </h3>
              <span className="muted">{v.date}</span>
            </div>
            <div className="muted small">
              Minecraft {v.minecraft} · Forge {v.forge} · {mb(v.file.size)} · SHA-256 {v.file.sha256.slice(0, 12)}…
            </div>
            {v.notes.length > 0 && (
              <ul>
                {v.notes.map((n, i) => (
                  <li key={i}>{n}</li>
                ))}
              </ul>
            )}
          </section>
        ))}
    </div>
  );
}
