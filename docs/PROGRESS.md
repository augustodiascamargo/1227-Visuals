> O plano atual do 1227 Client está em `docs/PLAN-CLIENT.md`. Este arquivo é o histórico das etapas anteriores. Na v1.15.0, a API e o site foram removidos: o projeto não depende de nenhum servidor próprio.

# 1227 Client: registro de progresso

Este arquivo é o registro contínuo do projeto. Cada etapa anota o que foi feito, o que foi testado (e como) e o que falta.

## Estrutura do repositório

| Pasta | Conteúdo |
|---|---|
| `/` (raiz) | Mod Forge 1.8.9 (`build.gradle`, `src/`); fica na raiz para não quebrar o build existente |
| `docs/` | Auditoria, progresso e processo de release |
| `.github/workflows/` | `build.yml` (mod), `release.yml` (release no GitHub) |

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

## Decisão (12/11/2026): o 1227 continua como mod
O foco volta a ser só o mod. Na v1.14.0 a pasta `launcher/` e o workflow `launcher.yml` foram removidos a pedido. A API e o site continuam opcionais, para divulgar as versões.

## Etapa 3 (v1.13.0): concluída
- HUD Scoreboard: desliga o sidebar do Forge (`GuiIngameForge.renderObjective`) só enquanto está ativo.
- Tab List: ordem e cabeçalho/rodapé lidos do vanilla por reflexão por tipo, com fallback por nome.
- Boss Bar: decrementa `BossStatus.statusBarTime` como o vanilla, para a barra sumir no tempo certo.
- HUD Hit Delay.
- Projectile Trails.
- Teste: compilação com `javac` sem erros próprios. Falta teste no jogo.
- Não feito: **Freelook**. Vários servidores grandes proíbem, e sem mixin ele precisaria mexer na rotação do jogador, o que pode ser lido como vantagem. Fica fora até haver um jeito só de câmera.

## Etapa 4 (v1.14.0): concluída
- Block Wave movido para Jump Effects (`vfx/BlockWave`).
- `render/Silhouette`: desenha entidades e itens como silhueta de cor sólida (GL_COMBINE com cor constante e alpha da textura), sempre com teste de profundidade. É a base de Afterimage, dos contornos de Player Glow e Item Glow e do Hit Color.
- Ambient Particles: voadores com rastro (retos, curvas e viradas bruscas).
- China Hat no Halo.
- Pesquisa: o TikTok não pode ser consultado daqui (só busca na web, sem acesso aos vídeos). Usei listas de mods "visuals" de PvP no Modrinth e no CurseForge como referência de recursos comuns no gênero (China Hat, Hit Color, contornos, rastros). Nada foi copiado: só a ideia do recurso.
- Teste: compilação com `javac` sem erros próprios. Falta teste no jogo, principalmente da espessura do contorno e da pose dos fantasmas.

## Próximas etapas (histórico, antes da decisão acima)

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

## Decisão (12/11/2026): launcher removido de novo
O launcher Java (que preparava o Forge e abria o launcher oficial) foi feito e removido a pedido, porque o usuário não quer depender do launcher oficial. Sem login Microsoft próprio (sem Azure), não há outro jeito legítimo de abrir o jogo, então o 1227 segue como mod.

## v1.17.0: cosméticos com materiais, Spacing Circle, W-Tap Indicator e Item Physics
- Pedidos do usuário: Spacing Circle, W-Tap Indicator e efeitos (plasma, aurora etc.) no China Hat e em outros cosméticos.
- **Pesquisa:** busca na web por mods "visuals" de PvP 1.8.9 (Modrinth/CurseForge: Visual+, ChinaHat, TargetESP e outros). A maioria dos recursos comuns já existe no 1227 (China Hat, Target ESP = Target Aura, Jump Circles = Jump Effects). Novo a partir da pesquisa: **Item Physics**. Nada foi copiado: só a ideia do recurso.
- **Testes aqui:**
  - compilação com `javac` sem erros próprios;
  - o shader dos materiais foi compilado e desenhado em WebGL (Chromium) num chapéu e numa cápsula, e conferido por captura;
  - o W-Tap foi modelado sobre a ordem de atualização do 1.8.9 (o estado do sprint no fim do tick é o que vai para o servidor).
- **Falta testar no jogo:** o Item Physics (substitui o renderizador de itens; com outro mod de Item Physics, vale o último carregado), a posição do China Hat em cada formato e as notas do W-Tap com e sem Toggle Sprint.

## v1.18.0: client completo dentro do mod
- Pedido: experiência de client inteiro só com o `1227.jar` (menus próprios, configurações unificadas, otimização, design system), sem launcher obrigatório.
- **Feito:**
  - Menu System, Settings System, Performance System, Profile System, Screenshot manager e o design system;
  - documentação em `docs/CLIENT.md` e `docs/DESIGN.md`.
- **Testado aqui:**
  - compilação sem erros próprios;
  - todos os nomes da API do Minecraft 1.8.9 usados conferidos com o código-fonte descompilado do 1.8.9 (repositório MavenMCP-1.8.9 no GitHub);
  - a lógica de sair, renascer e conectar comparada com a do vanilla.
- **Falta testar no jogo:** o roteiro completo está em `docs/TEST-REPORT-1.18.md`.
- **Limites do Forge 1.8.9 sem patch de classes:** listados em `docs/CLIENT.md` (listas vanilla de teclas, packs e idiomas, a tela "Building terrain", freelook e otimização de chunks).

## Decisões técnicas
- **Mod na raiz do repositório:** mantém o `build.yml` e o ForgeGradle 2.x funcionando como estão.
- **API sem banco de dados:** o manifesto e as notícias são JSON versionados no Git, publicados no deploy. Para cerca de 100 usuários, o plano gratuito do Workers sobra.
- **Sem conta própria do 1227:** não há finalidade real nesta etapa (pedido do item 12).
- **O manifesto não executa nada:** só descreve versões e arquivos; o launcher valida tudo antes de usar.
