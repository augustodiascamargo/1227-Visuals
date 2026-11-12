import { useCallback, useEffect, useState } from "react";
import { api, errorText, type StateView, type UpdateInfo } from "./api";
import Home from "./pages/Home";
import Install from "./pages/Install";
import Updates from "./pages/Updates";
import News from "./pages/News";
import Support from "./pages/Support";
import Accounts from "./pages/Accounts";

type Page = "home" | "install" | "accounts" | "updates" | "news" | "support";

const NAV: { id: Page; label: string }[] = [
  { id: "home", label: "Início" },
  { id: "install", label: "Instalação" },
  { id: "accounts", label: "Contas" },
  { id: "updates", label: "Atualizações" },
  { id: "news", label: "Notícias" },
  { id: "support", label: "Suporte" },
];

export default function App() {
  const [page, setPage] = useState<Page>("home");
  const [state, setState] = useState<StateView | null>(null);
  const [updates, setUpdates] = useState<UpdateInfo | null>(null);
  const [updateError, setUpdateError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setState(await api.state());
  }, []);

  const checkUpdates = useCallback(async () => {
    setUpdateError(null);
    try {
      setUpdates(await api.checkUpdates());
    } catch (e) {
      setUpdateError(errorText(e));
    }
  }, []);

  useEffect(() => {
    refresh();
    checkUpdates();
  }, [refresh, checkUpdates]);

  return (
    <div className="shell">
      <aside className="side">
        <div className="brand">
          <img src="/logo.png" alt="" className="brand-logo" />
          <div>
            <div className="brand-name">1227</div>
            <div className="brand-sub">CLIENT</div>
          </div>
        </div>
        <nav>
          {NAV.map((n) => (
            <button key={n.id} className={"nav" + (page === n.id ? " on" : "")} onClick={() => setPage(n.id)}>
              {n.label}
              {n.id === "updates" && updates?.updateAvailable && <span className="dot" />}
            </button>
          ))}
        </nav>
        <div className="side-foot">
          <div>Mod: {state?.installed ? "v" + state.installed.version : "não instalado"}</div>
          <div>Launcher v{state?.launcherVersion ?? "-"}</div>
        </div>
      </aside>
      <main className="main">
        {!state ? (
          <div className="loading">Carregando…</div>
        ) : page === "home" ? (
          <Home state={state} updates={updates} updateError={updateError} onChanged={refresh} onRetry={checkUpdates} go={setPage} />
        ) : page === "install" ? (
          <Install state={state} onChanged={refresh} />
        ) : page === "accounts" ? (
          <Accounts />
        ) : page === "updates" ? (
          <Updates updates={updates} error={updateError} onRetry={checkUpdates} />
        ) : page === "news" ? (
          <News />
        ) : (
          <Support state={state} />
        )}
      </main>
    </div>
  );
}
