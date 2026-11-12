import { useEffect, useState } from "react";
import { api, errorText, type JavaInfo, type Settings, type StateView } from "../api";

export default function Install({ state, onChanged }: { state: StateView; onChanged: () => void }) {
  const [s, setS] = useState<Settings>(state.settings);
  const [javas, setJavas] = useState<JavaInfo[] | null>(null);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [suggested, setSuggested] = useState<string | null>(null);

  useEffect(() => {
    api.detectJava().then(setJavas);
    api.suggestModsDir().then(setSuggested);
  }, []);

  async function save(next: Settings) {
    setS(next);
    try {
      await api.saveSettings(next);
      setMsg({ ok: true, text: "Salvo." });
      onChanged();
    } catch (e) {
      setMsg({ ok: false, text: errorText(e) });
    }
  }

  async function pickMods() {
    const d = await api.pickFolder("Escolha a pasta mods da instalação Forge 1.8.9");
    if (d) save({ ...s, target: "existing", existingModsDir: d });
  }

  async function pickInstance() {
    const d = await api.pickFolder("Escolha onde fica a instância do 1227");
    if (d) save({ ...s, instanceDir: d });
  }

  async function pickJava() {
    const j = await api.pickJava();
    if (!j) {
      setMsg({ ok: false, text: "Esse arquivo não é um Java válido." });
      return;
    }
    if (!j.ok) setMsg({ ok: false, text: `Java ${j.major} encontrado; Minecraft 1.8.9 com Forge precisa do Java 8.` });
    setJavas((list) => [j, ...(list ?? []).filter((x) => x.path !== j.path)]);
    if (j.ok) save({ ...s, javaPath: j.path });
  }

  return (
    <div className="page">
      <h2>Instalação</h2>

      <section className="card">
        <h3>Onde instalar o 1227</h3>
        <label className="radio">
          <input type="radio" checked={s.target === "own"} onChange={() => save({ ...s, target: "own" })} />
          <div>
            <b>Instância própria do 1227</b>
            <div className="muted">Pasta separada, nunca mexe na sua .minecraft.</div>
            <div className="path">
              <code>{s.instanceDir}</code>
              <button onClick={pickInstance}>Alterar…</button>
            </div>
          </div>
        </label>
        <label className="radio">
          <input type="radio" checked={s.target === "existing"} onChange={() => s.existingModsDir && save({ ...s, target: "existing" })} />
          <div>
            <b>Forge 1.8.9 que já tenho</b>
            <div className="muted">O jar do 1227 vai para a pasta mods escolhida. Versões antigas do 1227 nela vão para o backup do launcher.</div>
            <div className="path">
              <code>{s.existingModsDir ?? "nenhuma pasta escolhida"}</code>
              <button onClick={pickMods}>Escolher pasta…</button>
              {suggested && suggested !== s.existingModsDir && (
                <button className="ghost" onClick={() => save({ ...s, target: "existing", existingModsDir: suggested })}>
                  Usar {suggested}
                </button>
              )}
            </div>
          </div>
        </label>
      </section>

      <section className="card">
        <h3>Java</h3>
        <p className="muted">Minecraft 1.8.9 com Forge 11.15.1.2318 roda com Java 8. Versões mais novas do Java não funcionam com essa versão do Forge.</p>
        {javas === null ? (
          <div className="muted">Procurando instalações do Java…</div>
        ) : javas.length === 0 ? (
          <div className="notice err">Nenhum Java encontrado. Instale o Java 8 (por exemplo, Temurin 8) e volte aqui.</div>
        ) : (
          <div className="list">
            {javas.map((j) => (
              <label key={j.path} className={"list-row" + (j.ok ? "" : " dim")}>
                <input type="radio" disabled={!j.ok} checked={s.javaPath === j.path} onChange={() => save({ ...s, javaPath: j.path })} />
                <span className="tag">{j.ok ? "Java 8" : "Java " + j.major}</span>
                <span className="ellipsis" title={j.path}>{j.path}</span>
                <span className="muted">{j.is64 ? "64-bit" : "32-bit"}</span>
              </label>
            ))}
          </div>
        )}
        <div className="actions">
          <button onClick={pickJava}>Localizar java.exe…</button>
          <button className="ghost" onClick={() => api.detectJava().then(setJavas)}>Procurar de novo</button>
        </div>
      </section>

      {msg && <div className={"notice " + (msg.ok ? "ok" : "err")}>{msg.text}</div>}
    </div>
  );
}
