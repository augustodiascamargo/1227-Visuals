# Publicar uma versão

1. Atualize `version` em `build.gradle` e `VERSION` em `src/main/java/net/m1227/Mod1227.java`. Escreva o changelog no `README.md` (seção `## Mudanças na vX.Y.Z`).
2. Rode `python3 tools/build_site.py` para atualizar a página de versões do site e faça o commit.
3. Crie e envie a tag:
   ```
   git tag v1.12.0
   git push origin v1.12.0
   ```
4. O workflow `release.yml`:
   - compila o mod e confere que a tag bate com o `build.gradle`;
   - cria a Release no GitHub com `1227-X.Y.Z.jar` e o `.sha256`;
   - adiciona a versão em `api/data/manifest.json` (SHA-256 e tamanho calculados do próprio jar) e faz o commit;
   - publica a API, se existirem os secrets `CLOUDFLARE_API_TOKEN` e `CLOUDFLARE_ACCOUNT_ID`.
5. **Site:** no painel da Cloudflare, crie um projeto Pages ligado ao repositório, com diretório de saída `site` e sem comando de build.

## Primeira configuração da API
- Rode `cd api && npx wrangler deploy` uma vez, ou deixe o workflow fazer isso.
- Se a URL final não for `https://api.1227client.workers.dev`, troque-a em dois lugares:
  - `launcher/src-tauri/src/manifest.rs` (`MANIFEST_URL` e `NEWS_URL`);
  - `site/assets/download.js`.
- As notícias ficam em `api/data/news.json` (somente texto; o launcher nunca renderiza HTML vindo da API).

## Segurança
- Não há segredos no código. Os tokens da Cloudflare ficam só nos *secrets* do GitHub.
- O launcher só aceita downloads HTTPS de `github.com` e dos CDNs do GitHub, com nome `1227-<versão>.jar`, até 64 MB e SHA-256 conferido.
