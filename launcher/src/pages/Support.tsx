import { api, type StateView } from "../api";

export default function Support({ state }: { state: StateView }) {
  return (
    <div className="page">
      <h2>Suporte</h2>
      <section className="card">
        <h3>Pastas</h3>
        <div className="actions">
          <button onClick={() => api.openFolder("mods")}>Abrir pasta mods</button>
          <button onClick={() => api.openFolder("backups")}>Abrir backups</button>
          <button onClick={() => api.openFolder("launcher")}>Abrir pasta do launcher</button>
        </div>
        <p className="muted small">Launcher: <code>{state.appDir}</code></p>
      </section>
      <section className="card">
        <h3>Problemas comuns</h3>
        <ul>
          <li><b>O jogo não abre com o 1227:</b> confira se o perfil é Forge 1.8.9 (11.15.1.2318) e se ele usa Java 8.</li>
          <li><b>"Feche o jogo antes de atualizar":</b> o Windows bloqueia o jar enquanto o Minecraft está aberto.</li>
          <li><b>Verificação SHA-256 falhou:</b> o download veio corrompido. Nada é instalado nesse caso; tente de novo.</li>
          <li><b>Algo quebrou depois de atualizar:</b> use "Voltar à versão anterior" no Início.</li>
          <li><b>Configurações do mod:</b> ficam em <code>config/1227.json</code>, com backups automáticos em <code>config/1227-backups</code>.</li>
        </ul>
      </section>
      <section className="card">
        <h3>Sobre</h3>
        <p className="muted">
          O 1227 é um projeto independente e não é afiliado à Mojang Studios nem à Microsoft. Minecraft é marca da Mojang Studios. O launcher não pede, não guarda e não envia a sua senha.
        </p>
      </section>
    </div>
  );
}
