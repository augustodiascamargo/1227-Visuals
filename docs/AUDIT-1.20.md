# 1227 v1.20: auditoria, comparação com outros clients e plano

Data: 13/11/2026. A base auditada é o projeto completo da v1.19.0, com `build.gradle`, workflow do
GitHub Actions, fontes e recursos. O pedido citava um pacote só com jar, sources e `.bat`, mas o
projeto Gradle inteiro já estava disponível, então nada precisou ser pedido.

O que não pôde ser feito aqui:

- O jar final não foi compilado neste ambiente: o ForgeGradle 2.1 precisa baixar o Minecraft e o
  Forge, e quem faz isso é o GitHub Actions.
- Aqui o código passou por uma compilação de checagem. Ela não acusou nenhum erro no código do
  1227; os erros que sobraram vêm só de classes do Minecraft/Forge, que não existem fora do Gradle.
- Os nomes das APIs foram conferidos nos fontes do Minecraft 1.8.9 (MCP) e do Forge 1.8.9.

## 1. Mapa da arquitetura

| Pacote | Papel |
|---|---|
| `core` | `Module` (registro no barramento de eventos ao ativar), `Setting` (tipos: Bool, Num, Mode, Col, Text, Key, Action), `ModuleManager` (ordem e categorias), `ConfigManager` (JSON, backups), `Notifier` (Dynamic Island), `InputManager`/`Keybinds`, `CombatTracker` |
| `module` | Os módulos. Cada um assina os eventos do Forge de que precisa (`RenderWorldLastEvent`, `RenderGameOverlayEvent`, `ClientTickEvent`...) |
| `hud` | `HudModule`: posição em fração da tela, escala, fundo e editor; um arquivo por HUD |
| `ui`, `client.ui` | Desenho 2D (`Gfx`, `TtfFont` Poppins), kit imediato (`Ui`, `UiScreen`, `DS`) |
| `client.menu`, `client.settings` | Telas próprias (menu principal, pausa, servidores, mundos, configurações, galerias) |
| `client.perf` | `PerfSystem`, `PerfPresets`, `Benchmark`, e agora `Occlusion` e `BlockEntityCulling` |
| `vfx`, `render` | Partículas próprias (`ParticleSystem`), shaders, malhas, mão em primeira pessoa (`HandRenderer`/`HandFx`) |
| `music`, `audio` | Player local (`AudioPlayer`), biblioteca, Spotify/YouTube por título de janela (`External`) e, novo, `NowPlaying`, `MediaBridge`, `PcmTap`, `Fft`, `MusicAnalysis`, `CaptureSource` |
| `lyrics` (novo) | `Lyrics`, `LrcParser`, `LyricsMatch`, `LrcLib`, `LyricsService` |

Não há mixins nem coremod: tudo é feito com eventos do Forge, reflexão pontual e conferida, e
wrappers. Isso continua igual.

## 2. Problemas encontrados no código atual e correções

| # | Problema | Efeito | Correção na 1.20 |
|---|---|---|---|
| 1 | Os módulos não desligáveis (Music Player, Interface, Client, Performance, Presets, Menu Style) **nunca eram registrados no barramento de eventos** | As teclas do player (M para abrir, play/pause, próxima), a sincronia de volume e o título da janela do Interface não funcionavam | `ModuleManager.init` registra esses módulos |
| 2 | `ConfigManager.save` marcava "salvo" antes de gravar | Uma falha de disco ou arquivo bloqueado perdia as mudanças | Agora as mudanças ficam pendentes e o salvamento é tentado de novo a cada 2 s. O `move` é atômico quando o sistema permite |
| 3 | Config corrompido virava `.bak` e o jogo voltava aos padrões | Perda de todas as configurações | O arquivo é guardado como `1227.json.corrupt-<data>` e o backup legível mais recente de `1227-backups` é carregado |
| 4 | Sem versão de formato nem migração | Renomear algo apagava a opção do usuário | Formato 2 com `migrate()`; primeira migração: o HUD "Music" virou "Now Playing" |
| 5 | Importar um perfil quebrado passava pelo mesmo caminho do config principal | Podia mover o perfil para "corrompido" e restaurar um backup | Importação separada: um perfil ruim não muda nada |
| 6 | Capas de música: `covers.clear()` sem liberar texturas, e `ImageIO.read` na thread de render | Vazamento de memória de vídeo e engasgo na primeira exibição | Cache LRU de 48 itens que apaga a textura descartada; leitura em thread própria e upload na thread do jogo |
| 7 | Títulos de janela do Spotify/YouTube lidos via JNA na thread de render, uma vez por segundo | Engasgos periódicos | Thread própria, que só roda enquanto alguém pede |
| 8 | Posição do `AudioPlayer` contava bytes escritos | Ficava até 0,25 s adiantada (o buffer) | Usa `getLongFramePosition()`, o que a placa de som já tocou |
| 9 | Arquivos de música quebrados eram pulados para sempre | Loop rápido de erros sem dispositivo de som | Para após 3 falhas seguidas e avisa |
| 10 | `stop()` podia esperar até 500 ms na thread do jogo com um `write` bloqueado | Travadinha ao trocar de música | Fecha a linha antes de esperar, que libera o `write`; espera no máximo 300 ms |
| 11 | Reflexão das partículas do Minecraft procurava qualquer array 2D sem validar | Risco com outros mods | Confere o tipo `List[][]`, linhas e listas não nulas e o tipo de cada item; se algo não bater, o limitador se desliga e registra no log |
| 12 | FPS em segundo plano dormia na thread de render | Sem lag de entrada (só age sem foco), mas podia atrapalhar o benchmark | Espera limitada a 200 ms e desligada durante o benchmark |

## 3. Comparação com outros clients

Fontes: páginas públicas e listas de mods. Os resultados de busca usados estão no fim deste
documento. O código, as interfaces e os recursos desses clients não foram copiados; a tabela
compara só as funções.

| Funcionalidade | Onde aparece | O 1227 tinha? | O que melhorar | Como no Forge 1.8.9 | Prioridade | Situação |
|---|---|---|---|---|---|---|
| Waypoints | Lunar, Badlion | Não | Lista, beam, ícone, import/export, por servidor | `RenderWorldLastEvent` + JSON por servidor/mundo | Alta | **Feito** |
| Contador de itens | Lunar (Item Counter), Badlion | Não | Itens escolhidos, ids extras, aviso de pouco | `HudModule`, contagem do inventário 4x/s | Alta | **Feito** |
| Armor status | Lunar, Badlion, Feather | Básico | Barra, %, aviso piscando | `ArmorHud` | Alta | **Melhorado** |
| Sugestão de armadura melhor | Alguns mods de QoL | Não | Pontos de armadura + EPF de Protection | `DrawScreenEvent.Post` sobre `GuiContainer` | Média | **Feito** |
| Keybinds/array list | Vários | Não | Módulos com tecla e estado | `HudModule` | Média | **Feito** |
| Alertas (vida, comida, durabilidade...) | Badlion, mods de QoL | Parcial (notificações) | Alertas com cooldown e som | `ClientTickEvent` + `Notifier` | Média | **Feito** |
| Gerenciador de screenshots | Lunar, Feather | Sim | Filtros, grupos por dia, copiar caminho | `ScreenshotScreen` | Média | **Melhorado** |
| Gerenciador de resource packs | Lunar, Feather | Não (só a tela vanilla) | Verificação, ordem, importação, backup | `ResourcePackRepository` | Média | **Feito** |
| Pack Display HUD | Lunar | Não | Mostrar o pack ativo | `HudModule` | Baixa | **Feito (1.20.1)** |
| Perfis por servidor | Feather, Lunar | Perfis sem vínculo | Vincular perfil a servidor | Detecta servidor no tick e aplica perfil | Média | **Feito** |
| Entity culling / FPS | Feather, Lunar, Badlion | Distância única | Distâncias separadas e oclusão opcional | `RenderLivingEvent.Pre` + raios em cache | Alta | **Feito** |
| Block entity culling | Mods de FPS | Não | Baús e placas atrás de blocos | Wrapper nos `TileEntitySpecialRenderer` | Alta | **Feito** |
| Benchmark com 1% / 0,1% low | Ferramentas externas | Avg + 1% | 0,1%, frame time, memória, entidades, A/B | `Benchmark` | Média | **Feito** |
| Letras sincronizadas no mundo | Projeto "Sing the Lyrics" (inspiração) | Não | Tudo | Ver seção 4 | Altíssima | **Feito** |
| Visualizador de áudio | Raro em clients | Não | FFT real | `PcmTap` + `Fft` | Alta | **Feito** |
| Modo cinematográfico/foto | Lunar (hide HUD), replay tools | F1 vanilla | Esconder o HUD e manter letras e música | Cancelar `RenderGameOverlayEvent.Pre` | Média | **Feito** |
| Contador de dias | Lunar | Não | Linha no Clock | `ClockHud` | Baixa | **Feito** |
| Nick hider (local) | Lunar, Badlion | Não | Esconder o próprio nick na tela | Evento de chat + HUDs próprios | Baixa | **Feito (1.20.1)**; a tab list vanilla não muda |
| No Hurt Cam | Lunar, Badlion, Feather | Não | Tirar a inclinação ao tomar dano, com força ajustável | `hurtTime` lido como 0 só durante o desenho do quadro (`RenderTickEvent`) | Média | **Feito (1.20.2)** |
| Contorno de jogador só na borda | Lunar (Hit/Glow), outros | Brilho pintava o corpo | Contorno em pixels da tela, atrás do corpo | Cópias deslocadas na projeção + viés de profundidade | Alta | **Refeito (1.20.2)** |
| Freelook | Lunar, outros | Não | Não planejado: vários servidores proíbem | | | Fora de escopo |
| Auto GG / mensagens automáticas | Lunar, Badlion | Não | Não: envia chat ao servidor | | | Fora de escopo |
| Replay | Mods de replay | Não | Projeto enorme | | | Fora de escopo |

## 4. Floating Lyrics: decisões técnicas

### Relógio
- **Player do 1227:** a posição vem da placa de som e é exata.
- **Spotify e YouTube:** a linha do tempo vem do Windows (GSMTC), lida por um script PowerShell
  que vai dentro do jar. Ela é publicada pelo Spotify e por navegadores Chromium. O script só
  lê, não usa rede e fecha junto com o Minecraft.
- **Sem linha do tempo:** só título da janela, sem posição. Não há sincronia falsa: o texto diz
  isso, e a tela de letras oferece um relógio manual ("Song starts now").

### Letras
Ordem de busca:
1. `.lrc` ao lado da música;
2. `config/1227-lyrics`;
3. cache;
4. LRCLIB.

No LRCLIB:
- envia User-Agent com a versão;
- faz no máximo uma requisição a cada 1,5 s;
- respeita o `Retry-After` do 429; o 404 é guardado como "não encontrado" por 3 dias;
- usa timeouts e roda numa thread própria.

Um resultado só é aceito sozinho com pontuação de título + artista + duração ≥ 0,72. Abaixo
disso, aparece na tela para você escolher. Nenhuma letra vai dentro do jar.

### Render
- Billboard em `RenderWorldLastEvent`, na posição interpolada da câmera.
- O tamanho da fonte é escolhido pelo tamanho na tela (24 a 96 px), para ficar nítido.
- Todo estado GL tocado é restaurado.
- Não cria entidades e não envia pacotes.

### Áudio externo para o visualizador
WASAPI loopback precisa de código nativo, que o Java 8 não tem. A alternativa é um dispositivo
de captura como "Mixagem estéreo" (Stereo Mix) ou um cabo virtual:
- só é usado com seu consentimento explícito;
- só aparecem dispositivos desse tipo, nunca microfones;
- os dados ficam só na memória.

Sem dados reais, o visualizador mostra um modo "simulated" com etiqueta, ou some.

## 5. Pendências (não feitas nesta versão)

- **Não testado no jogo:** nada da 1.20 foi testado no jogo. O roteiro está em `TEST-REPORT-1.20.md`.
- **Ponte do Windows:** escrita seguindo a API do Windows, mas não executada aqui (o ambiente é
  Linux). Se falhar, o status em Settings › Audio mostra o motivo, e o resto segue funcionando.
- **HUD Editor:** grupos, camadas e guias de alinhamento ainda não foram revistos.
- **Click GUI:** presets por módulo e preview ainda não foram revistos.
- **HUD caching:** HUD renderizado em textura não foi feito; o custo do HUD não foi medido como
  gargalo.
- **Partículas:** não há limite de partículas por módulo; continua o limite global.
- **Fonte:** só tem Latin-1. Acentos funcionam; letras em japonês, coreano e similares aparecem
  como "?".
- **HUDs pequenos:** Pack Display, Nick Hider e Saturation foram feitos na 1.20.1. Os layouts
  antigos com "Music" também já posicionam o "Now Playing" (1.20.1).

Fontes da pesquisa: [Lunar Client mods list](https://www.bisecthosting.com/pt-pt/blog/lunar-client-mods-list),
[best minecraft clients](https://sportskeeda.com/minecraft/best-minecraft-clients),
[Soar Client (1.8.9)](https://www.9minecraft.net/?p=524032),
[LRCLIB API (lrclibapi docs)](https://lrclibapi.readthedocs.io/en/stable/lrclib.html),
[LRCLIB fetch example](https://lrclibapi.readthedocs.io/en/latest/examples/fetch_lyrics.html).
