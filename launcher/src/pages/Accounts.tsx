import { useCallback, useEffect, useState } from "react";
import { api, errorText, type AccountStore } from "../api";

type Notice = { kind: "ok" | "err" | "info"; text: string } | null;

export default function Accounts() {
  const [store, setStore] = useState<AccountStore | null>(null);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);
  const [showMicrosoftInfo, setShowMicrosoftInfo] = useState(false);

  const refresh = useCallback(async () => {
    try {
      setStore(await api.accounts());
    } catch (e) {
      setNotice({ kind: "err", text: errorText(e) });
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function run(action: () => Promise<AccountStore>, success: string) {
    setBusy(true);
    setNotice(null);
    try {
      setStore(await action());
      setNotice({ kind: "ok", text: success });
    } catch (e) {
      setNotice({ kind: "err", text: errorText(e) });
    } finally {
      setBusy(false);
    }
  }

  async function addOffline() {
    const clean = name.trim();
    if (!clean) {
      setNotice({ kind: "err", text: "Digite um nome para o perfil." });
      return;
    }
    const before = store?.profiles.length ?? 0;
    await run(() => api.addOfflineAccount(clean), `Perfil ${clean} criado.`);
    if ((store?.profiles.length ?? 0) >= before) setName("");
  }

  const selected = store?.profiles.find((p) => p.id === store.selectedProfileId) ?? null;

  return (
    <div className="page accounts-page">
      <div className="eyebrow">Gerenciamento de identidade</div>
      <h2>Contas</h2>
      <p className="muted account-intro">
        Gerencie os perfis usados para identificar suas instalações do 1227. A conta selecionada fica salva neste computador.
      </p>

      <section className="account-choice-grid">
        <article className="account-choice">
          <div className="account-choice-icon microsoft-icon">M</div>
          <div className="account-choice-title-row">
            <h3>Conta Microsoft</h3>
            <span className="tag">Oficial</span>
          </div>
          <p className="muted small">
            Login oficial para contas que possuem Minecraft Java. A autenticação precisa passar pelos serviços Microsoft, Xbox e Minecraft.
          </p>
          <button className="account-action" onClick={() => setShowMicrosoftInfo((v) => !v)}>
            {showMicrosoftInfo ? "Ocultar informações" : "Configurar login Microsoft"}
          </button>
          {showMicrosoftInfo && (
            <div className="account-explainer">
              <b>Autenticação oficial ainda não configurada</b>
              <p className="muted small">
                Esta versão não inicia uma sessão Microsoft. Para ativar o login real, o projeto precisa de um Application (Client) ID registrado,
                fluxo OAuth com retorno seguro, validação Xbox Live/XSTS e autenticação nos serviços Minecraft. O launcher não deve pedir sua senha
                diretamente nem armazenar tokens em texto puro.
              </p>
            </div>
          )}
        </article>

        <article className="account-choice">
          <div className="account-choice-icon offline-icon">◌</div>
          <div className="account-choice-title-row">
            <h3>Perfil Offline</h3>
            <span className="tag">Local</span>
          </div>
          <p className="muted small">
            Crie um nome local para desenvolvimento, testes ou ambientes que aceitam jogadores sem autenticação. Isso não cria uma conta Minecraft nem comprova propriedade do jogo.
          </p>
          <div className="offline-create">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter" && !busy) void addOffline(); }}
              placeholder="Nome do jogador"
              maxLength={16}
              autoComplete="off"
              spellCheck={false}
              aria-label="Nome do perfil offline"
            />
            <button className="primary" disabled={busy || name.trim().length < 3} onClick={() => void addOffline()}>
              Criar perfil
            </button>
          </div>
          <div className="form-hint">3–16 caracteres: letras, números e underline.</div>
        </article>
      </section>

      {notice && <div className={`notice ${notice.kind}`}>{notice.text}</div>}

      <section className="card account-list-card">
        <div className="account-list-heading">
          <div>
            <h3>Seus perfis</h3>
            <p className="muted small">{store?.profiles.length ?? 0} perfil(is) salvo(s)</p>
          </div>
          {selected && <span className="tag ok">Selecionado: {selected.name}</span>}
        </div>

        {!store ? (
          <div className="muted small">Carregando perfis…</div>
        ) : store.profiles.length === 0 ? (
          <div className="empty-profiles">
            <div className="empty-symbol">＋</div>
            <b>Nenhum perfil cadastrado</b>
            <span className="muted small">Crie um perfil offline ou configure a autenticação oficial quando estiver disponível.</span>
          </div>
        ) : (
          <div className="profile-list">
            {store.profiles.map((profile) => {
              const active = profile.id === store.selectedProfileId;
              return (
                <div className={`profile-row ${active ? "active" : ""}`} key={profile.id}>
                  <div className={`profile-avatar ${profile.profileType}`}>
                    {profile.name.slice(0, 1).toUpperCase()}
                  </div>
                  <div className="profile-details">
                    <b>{profile.name}</b>
                    <span className="muted small">{profile.profileType === "microsoft" ? "Conta Microsoft" : "Perfil Offline · local"}</span>
                  </div>
                  {active ? (
                    <span className="selected-label">Em uso</span>
                  ) : (
                    <button disabled={busy} onClick={() => void run(() => api.selectAccount(profile.id), `${profile.name} selecionado.`)}>
                      Selecionar
                    </button>
                  )}
                  <button
                    className="danger-quiet"
                    disabled={busy}
                    title={`Remover ${profile.name}`}
                    aria-label={`Remover ${profile.name}`}
                    onClick={() => {
                      if (window.confirm(`Remover o perfil ${profile.name} deste launcher?`)) {
                        void run(() => api.removeAccount(profile.id), "Perfil removido.");
                      }
                    }}
                  >
                    Remover
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </section>

      <div className="account-disclaimer">
        <b>Importante:</b> nesta versão, os perfis offline são apenas perfis locais salvos pelo launcher. O projeto ainda não possui o mecanismo de inicialização do Minecraft nem autenticação Microsoft integrada. Um perfil local não libera servidores que exigem autenticação oficial e não substitui uma licença do jogo.
      </div>
    </div>
  );
}
