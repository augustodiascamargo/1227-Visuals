import { useEffect, useState } from "react";
import { api, errorText, type NewsItem } from "../api";

export default function News() {
  const [items, setItems] = useState<NewsItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  function load() {
    setError(null);
    api.news().then(setItems).catch((e) => setError(errorText(e)));
  }

  useEffect(load, []);

  return (
    <div className="page">
      <h2>Notícias</h2>
      {error && (
        <div className="notice err">
          {error} <button className="link" onClick={load}>Tentar de novo</button>
        </div>
      )}
      {!items && !error && <div className="muted">Carregando…</div>}
      {items?.length === 0 && <div className="muted">Nenhuma notícia por enquanto.</div>}
      {items?.map((n, i) => (
        <section key={i} className="card">
          <div className="row">
            <h3>{n.title}</h3>
            <span className="muted">{n.date}</span>
          </div>
          {/* plain text only: the API's text is never rendered as HTML */}
          <p className="pre">{n.body}</p>
        </section>
      ))}
    </div>
  );
}
