import { useEffect, useState } from "react";
import { api, errorText, mb, type Progress, type StateView, type UpdateInfo } from "../api";

interface Props {
  state: StateView;
  updates: UpdateInfo | null;
  updateError: string | null;
  onChanged: () => void;
  onRetry: () => void;
  go: (p: "install") => void;
}

type Msg = { kind: "ok" | "err" | "info"; text: string } | null;

export default function Home({ state, updates, updateError, onChanged, onRetry, go }: Props) {
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState<Progress | null>(null);
  const [msg, setMsg] = useState<Msg>(null);

  useEffect(() => {
    const un = api.onProgress(setProgress);
    return () => {
      un.then((f) => f());
    };
  }, []);

  const latest = updates?.manifest.latest;
  const installed = state.installed?.version ?? null;
  const needsFolder = state.settings.target === "existing" && !state.settings.existingModsDir;

  async function run(label: string, fn: () => Promise<unknown>, done: string) {
    setBusy(true);
    setMsg({ kind: "info", text: label });
    setProgress(null);
    try {
      await fn();
      setMsg({ kind: "ok", text: done });
      onChanged();
      onRetry();
    } catch (e) {
      setMsg({ kind: "err", text: errorText(e) });
    } finally {
      setBusy(false);
      setProgress(null);
    }
  }

  async function verify() {
    setBusy(true);
    try {
      const ok = await api.verify();
      setMsg(ok ? { kind: "ok", text: "Arquivo íntegro: o SHA-256 confere." } : { kind: "err", text: "O arquivo instalado está faltando ou foi alterado. Use Reparar." });
    } catch (e) {
      setMsg({ kind: "err", text: errorText(e) });
    } finally {
      setBusy(false);
    }
  }

  const pct = progress && progress.total > 0 ? Math.round((progress.received / progress.total) * 100) : 0;

  return (
    <div className="page">
      <section className="hero">
        <div className="hero-text">
          <div className="eyebrow">Minecraft 1.8.9 · Forge 11.15.1.2318</div>
          <h1>1227</h1>
          <p className="muted">Visuais, HUD e efeitos para PvP. Apenas visual: sem vantagens de jogo.</p>
        </div>
        <div className="status-card">
          <div className="row">
            <span className="muted">Instalado</span>
            <b>{installed ? "v" + installed : "—"}</b>
          </div>
          <div className="row">
            <span className="muted">Mais recente</span>
            <b>{latest ? "v" + latest : updateError ? "indisponível" : "verificando…"}</b>
          </div>
          <div className="row">
            <span className="muted">Destino</span>
            <b className="ellipsis" title={state.modsDir ?? ""}>
              {state.settings.target === "own" ? "Instância do 1227" : "Forge existente"}
            </b>
          </div>
        </div>
      </section>

      {needsFolder && (
        <div className="notice">
          Escolha a pasta <b>mods</b> da sua instalação Forge 1.8.9 em <button className="link" onClick={() => go("install")}>Instalação</button>.
        </div>
      )}
      {updateError && (
        <div className="notice err">
          {updateError} <button className="link" onClick={onRetry}>Tentar de novo</button>
        </div>
      )}

      <div className="actions">
        {latest && (!installed || updates?.updateAvailable) && (
          <button className="primary" disabled={busy || needsFolder} onClick={() => run("Baixando e verificando…", () => api.install(latest), "Versão " + latest + " instalada.")}>
            {installed ? "Atualizar para v" + latest : "Instalar v" + latest}
          </button>
        )}
        {installed && latest && (
          <button disabled={busy || needsFolder} onClick={() => run("Reinstalando…", () => api.install(installed), "Reparo concluído.")}>
            Reparar
          </button>
        )}
        {installed && (
          <button disabled={busy} onClick={verify}>
            Verificar arquivos
          </button>
        )}
        {state.installed?.backup && (
          <button disabled={busy} onClick={() => run("Restaurando…", api.rollback, "Versão anterior restaurada.")}>
            Voltar à versão anterior
          </button>
        )}
        {busy && progress?.stage === "download" && (
          <button className="ghost" onClick={() => api.cancel()}>
            Cancelar
          </button>
        )}
      </div>

      {busy && progress && (
        <div className="progress">
          <div className="bar" style={{ width: pct + "%" }} />
          <span>
            {progress.stage === "download" ? `${mb(progress.received)} de ${mb(progress.total)} (${pct}%)` : progress.stage === "install" ? "Instalando…" : "Concluído"}
          </span>
        </div>
      )}
      {msg && <div className={"notice " + msg.kind}>{msg.text}</div>}

      <section className="card">
        <h3>Como jogar</h3>
        {state.settings.target === "existing" ? (
          <p className="muted">
            O 1227 é instalado na pasta mods que você escolheu. Abra o jogo pelo launcher que você já usa, com o perfil Forge 1.8.9, e o 1227 carrega junto.
          </p>
        ) : (
          <p className="muted">
            A instância própria do 1227 guarda o mod em <code>{state.modsDir}</code>. Iniciar o jogo direto daqui (com login Microsoft) é a próxima etapa do launcher; por enquanto use o modo "Forge existente" para jogar.
          </p>
        )}
      </section>
    </div>
  );
}
