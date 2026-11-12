# 1227 Client: registro de progresso

Este arquivo é o registro contínuo do projeto. Cada etapa anota o que foi feito, o que foi testado (e como) e o que falta.

## Estrutura do repositório

| Pasta | Conteúdo |
|---|---|
| `/` (raiz) | Mod Forge 1.8.9 (`build.gradle`, `src/`); fica na raiz para não quebrar o build existente |
| `launcher/` | Launcher em Tauri 2 + React + TypeScript + Rust |
| `api/` | API em Cloudflare Worker (manifesto de versões e notícias) |
| `site/` | Site estático (Cloudflare Pages), gerado por `tools/build_site.py` |
| `docs/` | Auditoria, progresso e processo de release |
| `.github/workflows/` | `build.yml` (mod), `release.yml` (release + manifesto + API) e `launcher.yml` (instalador Windows) |

## Etapa 1 (v1.12.0): concluída

**Mod**
- HUDs novos: Clock, Memory, Server Info, Compass e Session Stats.
- Editor de HUD:
  - Trava por elemento (clique do meio e opção "Lock position").
  - 3 slots de layout (Save/Load) e Copy/Paste pela área de transferência.
- Backup automático do config a cada início (os 5 mais recentes).
- Performance: nível Ultra e 4 presets de um clique.
- Auditoria completa em `docs/AUDIT.md`.

**Launcher (base)**
- Telas: Início, Instalação, Atualizações, Notícias e Suporte.
- Destino de instalação: instância própria (padrão, nunca mexe na `.minecraft`) ou uma pasta mods de Forge existente escolhida pelo usuário.
- Detecção de Java (JAVA_HOME, PATH, pastas comuns e o runtime do launcher oficial), com aviso quando não é Java 8, e seleção manual do `java.exe`.
- Atualização por manifesto:
  - HTTPS obrigatório, hosts permitidos e nome de arquivo fixo `1227-<versão>.jar`.
  - Tamanho máximo e verificação de SHA-256 e de tamanho durante o download.
  - Cancelamento seguro e progresso.
  - Backup antes de instalar, rollback, "Verificar arquivos" (re-hash) e "Reparar".
- A interface não consegue passar caminhos nem URLs livres para o Rust: só pastas escolhidas no diálogo do sistema.

**API, site e CI**
- Worker com `/v1/manifest`, `/v1/news` e `/health` (somente leitura, sem segredos).
- `tools/manifest_entry.py` gera a entrada do manifesto a partir do jar (SHA-256 e tamanho reais).
- Site com início, recursos, download (versão vinda da API), versões (gerada do changelog), documentação, FAQ, privacidade e termos.
- `release.yml`: tag `vX.Y.Z`, build, release no GitHub, manifesto commitado e deploy da API (se houver os secrets da Cloudflare).
- `launcher.yml`: testes Rust e instalador NSIS do Windows.

### Testes desta etapa

| Teste | Como | Resultado |
|---|---|---|
| Código do mod compila (exceto símbolos do Minecraft/Forge) | `javac` sobre todo o `src/` | Sem erros próprios |
| Validação de nomes e caminhos e versão do Java (launcher) | `rustc --test` (paths.rs, java.rs) | 2/2 passaram |
| Tipos da interface do launcher | `tsc` com declarações mínimas de React/Tauri | Sem erros |
| Rotas da API | Node com `Request` real: 200, 404 e 405 | OK |
| Gerador do manifesto | jar falso → entrada com SHA-256 e tamanho corretos, `latest` calculado | OK |
| Site | Gerado e renderizado no Chromium (capturas) | OK |
| YAML dos workflows | `yaml.safe_load` | OK |

**Não testado aqui (sem internet para pacotes e sem o jogo):**
- `cargo build` e `cargo test` completos do launcher, e o `npm install`: rodam no `launcher.yml`.
- O mod dentro do jogo.
- A interface do launcher em janela real e com escala do Windows diferente.

## Próximas etapas

### Etapa 2: o launcher inicia o jogo sozinho
- Download dos arquivos oficiais do Minecraft 1.8.9 a partir dos servidores da Mojang (version manifest, libraries, natives e assets), com verificação de SHA-1, mais as bibliotecas do Forge 11.15.1.2318 do Maven do Forge. Nada é redistribuído pelo 1227.
- Login Microsoft (OAuth device code → Xbox Live → XSTS → Minecraft services) e verificação de propriedade do jogo.
  - **Depende de você:** registrar um aplicativo no Azure (Client ID público, sem segredo) e pedir a aprovação da Microsoft/Mojang para usar a API do Minecraft com esse aplicativo. O Client ID entra em um arquivo de configuração.
  - Os tokens ficam no Gerenciador de Credenciais do Windows, nunca em texto.
- Configuração de memória RAM e argumentos do Java (só aparecem quando o lançamento próprio existir).
- **Fora do escopo por regra:** modo offline ou "sem conta" para jogar. Isso permitiria rodar o jogo sem possuí-lo. Quem não quer logar pelo launcher do 1227 pode usar o modo "Forge existente" com o launcher que já usa.

### Etapa 3: mais conteúdo no mod
- Personalizadores de Scoreboard, Tab List e Boss Bar.
- Hit Delay Indicator (só o tempo entre os seus golpes, sem alterar nada).
- Projectile Trails, Time Presets e Color Grading.
- Freelook visual (só a câmera, sem mudar a direção do jogador), desligado por padrão por causa das regras de servidor.
- Benchmark medido dos presets de desempenho (FPS médio e 1% low numa cena fixa, antes e depois).

### Etapa 4: distribuição
- Assinatura de código do instalador do Windows (exige certificado comprado; sem ele, o SmartScreen avisa).
- Domínio próprio para o site e a API (opcional; os `*.workers.dev` e `*.pages.dev` gratuitos funcionam).

## Decisões técnicas
- **Mod na raiz do repositório:** mantém o `build.yml` e o ForgeGradle 2.x funcionando como estão.
- **API sem banco de dados:** o manifesto e as notícias são JSON versionados no Git, publicados no deploy. Para cerca de 100 usuários, o plano gratuito do Workers sobra.
- **Sem conta própria do 1227:** não há finalidade real nesta etapa (pedido do item 12).
- **O manifesto não executa nada:** só descreve versões e arquivos; o launcher valida tudo antes de usar.


## Perfis de conta do launcher (adicionado nesta revisão)
- Tela Contas com escolha conceitual entre Conta Microsoft e Perfil Offline.
- Perfis offline locais podem ser criados, selecionados e removidos; nomes são validados (3–16 caracteres, letras ASCII, números e underline).
- Perfis são persistidos em `accounts.json` na pasta de dados do launcher. Nenhum token ou senha é armazenado.
- Login Microsoft está explicitamente sinalizado como ainda não configurado: depende de um Application/Client ID próprio e do fluxo OAuth + Xbox Live/XSTS + Minecraft Services.
- Limitação mantida de forma explícita: o launcher não inicia o Minecraft nesta versão e os perfis locais não representam autenticação nem licença.
