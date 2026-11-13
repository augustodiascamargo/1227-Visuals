# 1227 — v1.18.0 (Minecraft 1.8.9 · Forge 11.15.1.2318 · Java 8)

Mod client-side e puramente visual. Não envia pacotes, não desenha através de paredes e não muda alcance,
timing de ataque ou outras mecânicas do servidor.

## O que tem neste repositório
| Pasta | O que é |
|---|---|
| raiz (`build.gradle`, `src/`) | O mod Forge |
| `docs/` | `AUDIT.md` (auditoria), `PROGRESS.md` (progresso, testes e próximas etapas), `RELEASE.md` (como publicar) |

## Compilar
Envie esta pasta para um repositório no GitHub. Aba **Actions** > "Build mod jar" > baixe o artefato `1227-jar`.
O `.jar` vai em `.minecraft/mods` com Forge 1.8.9.

## Abrir o menu
- Tecla padrão: **Right Control** (configurável em Settings > Client > Menu key; Esc/Backspace = nenhuma).
- **/1227** no chat sempre abre o menu, mesmo sem tecla. `/1227 hud` abre o editor de HUD;
  `/1227 export` copia a config para a área de transferência e `/1227 import` carrega de lá.
- As teclas ficam na configuração do 1227 (não em Controles), e o menu avisa quando outra tecla do Minecraft,
  de outro mod ou do próprio 1227 usa a mesma.
- Config: `config/1227.json` (uma `lumenvisuals.json` antiga é importada uma vez). Perfis: `config/1227/profiles`.
  Sons próprios: `config/1227/sounds`.

## Categorias
| Categoria | Módulos |
|---|---|
| ★ Favorites | Módulos marcados com a estrela no menu |
| Skies | Dynamic Skies (30 céus, transição suave) |
| Ambience | Ambience (12 estilos), Ambient Particles, World (fullbright, hora do dia, clima — só na sua tela), Item Physics |
| Auras | Target Aura (24 estilos), Player Aura (os mesmos 24), Player Glow |
| Cosmetics | Aura Hat, Halo (com China Hat), Wings, Energy Shell; materiais animados (Plasma, Aurora, Galaxy, Fire, Electric, Hologram e outros) |
| Movement | Trails (16), Jump Effects (20), Player FX (aparecer, sumir, teleporte) |
| Combat | Hit Effect (+ burst de crítico), Kill Effect, Shockwave, Damage Numbers, Victory & Defeat, Spacing Circle |
| Weapons | Weapon Effects (40, presos à lâmina em 1ª pessoa), Item Glow, Weapon Models (6 espadas 3D originais) |
| Camera | Motion Blur, View Bobbing, Static FOV, Zoom, Camera FX, Low Fire |
| Animations | 1.7 Animations |
| Gameplay | Toggle Sprint |
| Hit Sounds | Hit Sounds (+ 5 pacotes com som de hit, crítico e kill) |
| HUD | Dynamic Island, Hit Marker, Crosshair, Nametags, Chat, Watermark, Held Item, Info Bar (FPS/ping/CPS/velocidade), Keystrokes, Target HUD, Combo Counter, Armor, Potions, Coordinates, Movement Status |
| Settings | Client (tecla, cor, escala, vidro, editor de HUD, copiar/colar config), Presets, Performance |

## Editor de HUD
Settings > Client > **Edit HUD layout** (ou `/1227 hud`). Arraste para mover (encaixa nas bordas, no centro e
nos outros elementos), role a roda do mouse para mudar o tamanho, botão direito para voltar ao padrão. Os botões
no topo ligam e desligam cada elemento. Posições são salvas como fração da tela.

Todo módulo tem "Reset to defaults" no fim das opções; Settings > Client tem o reset geral.

## Arquitetura
```
core/    Module, Setting (Bool/Num/Mode/Col/Key/Action), ModuleManager, ConfigManager, InputManager,
         Notifier, CombatTracker, Command1227
client/  menu (menus), settings (tela de configurações, perfis), perf (performance, benchmark), ui (design system)
vfx/     ParticleSystem (pool + atlas), Lines, Arcs, ShockwaveSystem, AuraLibrary, SkyPreset, Sprite,
         SkyShader, MaterialShader (materiais dos cosméticos), CosmeticMesh
module/  um arquivo por módulo
ui/      ClickGui, Gfx, Icons, Glass
hud/     HudModule (base movível), elementos do HUD, HudEditorScreen, ClickCounter
render/  HandRenderer (mão em 1ª pessoa), HandFx (efeitos presos ao item), Silhouette, PhysicsItemRender
audio/   HitSoundEngine, SoundBank
```
A entrada (teclas) fica em `InputManager`, separada da interface. O código depende só do Forge 1.8.9;
nada depende de Lunar ou de outro launcher.

## Compatibilidade com launchers
- **Forge 1.8.9:** alvo do projeto.
- **Lunar Client 1.8.9:** não verificado. O Lunar não carrega mods Forge próprios em 1.8.9 de forma oficial
  (o carregamento de mods próprios deles é para Fabric 1.16.5+). Uma integração exigiria um adaptador separado.
- **Outros launchers:** funciona onde houver Forge 1.8.9 com pasta `mods`.

## Limitações conhecidas
- Não rodei dentro do Minecraft: o código foi conferido pelo compilador e contra a documentação da API do Forge 1.8.9.
- Weapon Effects: efeitos de partículas na espada; os modelos 3D das armas não mudam.
- 1.7 Animations: blockhit, comer/beber, arco, swing ao usar item e posição/escala do item.
  "Old rod" é uma aproximação. Old sneaking e old damage (armadura vermelha) não foram implementados.
  Com mapas na mão o desenho volta ao vanilla. Pode conflitar com OptiFine shaders, que também mexem na mão.
- Motion Blur: some com F1 (HUD oculto) e pode não funcionar com OptiFine "Fast Render".
- Victory & Defeat: a derrota é a sua morte. A vitória é detectada por palavras no chat ("victory", "you won",
  "vitória", "venceu"...), porque o Minecraft 1.8.9 não tem um sinal universal de "partida ganha".
- World: hora e clima mudam só no desenho. Com chuva no servidor e "Clear", o som de chuva ainda pode tocar.
- Zoom: usa o FOV do próprio Minecraft (limite de 10x). Low Fire e Camera FX são só visuais.
- Damage Numbers: usa a vida que o servidor envia; servidores que escondem a vida dos jogadores não mostram números.
- APIs não verificadas contra o código-fonte (sem acesso à rede aqui): `ItemCameraTransforms.applyTransform`,
  `RenderItem.renderItem(ItemStack, IBakedModel)`, `GL14.glBlendColor`, `FontRenderer.getColorCode`, os nomes SRG dos
  campos privados do `ItemRenderer` e `FOVUpdateEvent.newfov`. Se a build do GitHub falhar, o log aponta a linha.

## Mudanças na v1.1.1
- Weapon Effects: corrigida a faixa escura que aparecia na tela dependendo do ângulo (rastro/partículas que
  chegavam atrás da câmera viravam um polígono gigante; cor do OpenGL que ficava "presa" após os efeitos).
- Céus com aurora: as cortinas agora dão a volta completa no céu, sem emenda; as linhas de luz se espalham por todo o horizonte.
- Novo módulo Static FOV (Camera): o FOV não muda mais com sprint, Speed ou lentidão; dá para manter o zoom do arco e o FOV de voo.

## Mudanças na v1.2.0
- Dynamic Skies: camada de shader GLSL opcional (Nebula, Aurora Veil, Cosmic Ocean, Plasma Storm, Warp Stars, Sunset Clouds),
  com cores do céu ou personalizadas. Se a placa de vídeo não compilar o shader, a camada se desliga sozinha (aviso no log).
- Weapon Models: 6 espadas 3D feitas em código (Katana, Crystal Blade, Energy Saber, Rune Greatsword, Twin Fang, Obsidian Edge),
  com sombreamento e partes que brilham. Só em 1ª pessoa e só para você; em 3ª pessoa aparece a espada normal (o 1.8.9 não tem
  como trocar o modelo do item na mão do jogador sem mexer no código do jogo).
- Nametags: nome com fundo, vida, barra de vida e ping; segue as regras de time do servidor e não aparece através de paredes.
- Chat: mensagens entram deslizando e somem suavemente; ao abrir o chat volta o chat normal (links e rolagem funcionam).
- Watermark (5 estilos) e Held Item (cartão do item ao trocar, com encantamentos e durabilidade).
- Hit Sounds: pacotes Neon, Crystal, Arcade, Heavy e Soft, com 3 variações de hit, som de crítico e de kill; tom que sobe com o combo.
- Tudo foi feito do zero; nenhum código, textura, shader ou som de outro client foi usado.

## Mudanças na v1.2.1
- Menu e HUD: painéis arredondados, vidro (glass), botões, sliders, círculos e estrelas não apareciam porque o
  Minecraft mantém o descarte de faces (culling) ligado na interface e as formas eram desenhadas no sentido horário.
  Agora o culling é desligado ao desenhar as formas 2D.
- Faixa/bloco escuro na tela: partículas (trails, jump, auras, combate) que nasciam colado na câmera viravam um
  quadrado enorme cortado pelo plano da câmera. Agora toda partícula some suavemente quando a câmera chega perto dela,
  usando a posição real da câmera (também em 3ª pessoa).
- Mão em 1ª pessoa: a matriz de projeção é salva e restaurada por valor em vez de usar a pilha do OpenGL (que pode ter
  só 2 níveis e transbordar com OptiFine/shaders).
- Rodapé do menu: o indicador de FPS/efeitos foi para a parte de baixo da barra lateral (antes sobrepunha o texto).
- Movement Status: largura corrigida.

## Mudanças na v1.3.0
- Weapon Effects e Item Glow não substituem mais o desenho da mão do Minecraft: os efeitos são desenhados logo antes
  e o próprio jogo desenha a espada por cima. Só 1.7 Animations e Weapon Models ainda assumem o desenho da mão.
- Settings > Client > "First-person hand": "Compatibility" nunca substitui o desenho da mão (para launchers/mods que
  também mexem na mão). Nesse modo os efeitos continuam, mas as poses 1.7 e as espadas 3D ficam desligadas.
- Raios de verdade na lâmina (Weapon Effects > "Lightning arcs on blade": Auto/On/Off; Weapon Models > "Lightning arcs").
- Weapon Models: novo modelo Storm Blade, controles de espessura, comprimento e posição (X/Y/Z); espessura padrão maior.

## Mudanças na v1.4.0
- Motion Blur reescrito: em vez de copiar a tela e sobrepor a cópia (o que podia escurecer a imagem), um framebuffer
  separado guarda a média ponderada dos últimos quadros e ela é desenhada opaca por cima do mundo. O brilho fica
  sempre igual; também desliga neblina e a textura de luz do mundo enquanto desenha. Requer framebuffers (padrão no 1.8.9).
- Nova página Settings > Menu Style: Glass, Frosted, Solid, Transparent, Minimal e Neon, com tinta, opacidade,
  força do desfoque, borda, raio dos cantos, escurecimento do fundo e brilho no topo. Muda ao vivo, sem reabrir o menu.
  Opção para os painéis de vidro do HUD e da Dynamic Island seguirem o mesmo estilo.
- Glass refeito: o fundo é desfocado duas vezes (shader do Minecraft + cópia com 28 amostras), com camada leitosa,
  tinta translúcida, borda clara fina e luz descendo da borda de cima.
- Preview ao vivo dos efeitos: ao clicar em Preview, o menu sai da frente, um boneco só seu (cópia do seu personagem,
  existe apenas na sua tela) aparece à sua frente e o efeito toca nele — o Kill Effect faz o boneco sumir, o Hit Effect
  faz ele reagir. Depois o menu volta sozinho (ou clique para voltar). Previews novos: crítico, vitória, derrota,
  teleporte e Damage Numbers.

## Mudanças na v1.5.0
- Menu repaginado: janela maior, fonte lisa (Poppins, licença SIL OFL, incluída em assets/m1227/fonts com o OFL.txt)
  desenhada em alta resolução em vez da fonte pixelada do Minecraft, barra lateral com marca, busca, contadores e
  seu personagem + FPS, cartões com bolinha de status, chave de atalho em "chip", switches e estrelas novos,
  seletores curtos viram botões segmentados.
- Galeria de efeitos com preview ao vivo: ao abrir Hit Effect, Kill Effect, Jump Effects, Target Aura ou Player Aura,
  o seletor de estilo vira uma grade de cartões; cada cartão mostra o efeito tocando em loop num boneco, em 3D,
  usando o mesmo código do jogo (não é vídeo gravado). Clique no cartão para escolher.
- Novo estilo de menu Liquid Glass (padrão): fundo bem transparente com efeito de lente, borda com brilho especular.
- Botão "Menu style" no topo do menu abre direto a página Settings > Menu Style (agora a primeira de Settings).
- Editor de HUD: botões para ligar/desligar também Dynamic Island, Crosshair, Nametags, Chat e Hit Marker
  (ex.: para trazer a Dynamic Island de volta depois de desligar).

## Mudanças na v1.5.1
- Dynamic Island: a animação de "mola" que muda o tamanho da ilha podia explodir para infinito/NaN depois de um quadro
  longo (travada, alt-tab, PC mais lento), e a ilha sumia de vez. Agora é calculada em passos pequenos e, se algum
  valor ficar inválido, ela se recupera sozinha no quadro seguinte.
- Galeria: o boneco dos previews agora é o seu personagem com a sua skin; câmera mais próxima.
- Weapon Effects ganhou galeria com os 40 efeitos (rastro de espada + impacto em cada cartão).

## Mudanças na v1.6.0
- Trails ganhou galeria: cada um dos 16 estilos aparece num cartão com uma luz correndo em "8" e deixando o rastro,
  com o mesmo código do rastro real (cada cartão tem a própria memória, sem mexer no seu rastro).
- Novo seletor de cores: linha compacta com amostra e código hex; ao clicar abre um quadrado de saturação/brilho,
  barra de matiz, 12 cores prontas, "Use accent" e "Reset". As cores agora guardam o brilho (antes toda cor salva
  voltava com brilho máximo; tintas escuras, como a do Menu Style, ficavam claras depois de reabrir o jogo).

## Mudanças na v1.7.0
- Símbolo 1227 no canto do menu (e opcional no Watermark), desenhado a partir da arte enviada como máscara branca e
  pintado com a cor de destaque escolhida, com brilho pulsando.
- Kill Effect: Ragdoll, Decapitation (opção de partículas vermelhas), Shatter, Ice Shatter, Executioner (gigante de
  obsidiana original com martelo) e Tung Sahur (modelo enviado pelo usuário, que bate duas vezes no alvo com o taco e
  lança o corpo no terceiro golpe). O corpo do jogador derrotado é refeito com a skin dele em 6 partes com física
  (gravidade, giro, quique e atrito); o corpo original é escondido só enquanto está morto. Tudo é só visual, no cliente.
- Weapon Models: Butterfly Knife (modelo e animação do .blend enviado: abre ao sacar e gira na tecla de inspeção, V),
  Cleaver, Dragon Fang, Holy Claymore e Void Rapier.
- Emotes (Animations): roda de emotes na tecla "." (ponto) com 12 emotes (Wave, Groove, Clap, Flex, Salute, Sit, Spin, Facepalm,
  T-Pose, Cheer, Bow, Laugh). A câmera vira para mostrar o personagem; andar, pular, agachar, atacar ou levar dano
  encerra. Só você vê os emotes (não há envio para o servidor).
- tools/: conversores em Python usados para transformar o FBX (esqueleto + animação, com o golpe do taco adicionado)
  e o .blend em modelos leves (assets/m1227/models). Para trocar os modelos, rode bake.py / bakeknife.py.

### Licença dos modelos enviados
O modelo do Tung Sahur (ZAHUR.fbx + textura) e a butterfly knife (.blend) foram enviados pelo usuário. Antes de
publicar o 1227, confira a licença de cada um (por exemplo, se exigem crédito ao autor) e coloque o crédito aqui.

## Mudanças na v1.20.1

- **Build:** corrigido o erro de compilação em `AlertsModule`: uma opção era usada antes de ser
  criada ("variable durability might not have been initialized").
- **Checagem local mais forte:** agora inclui a análise de fluxo do javac, que pega esse tipo de
  erro antes do GitHub. O código inteiro passou sem nenhum erro desse tipo.
- **Novos HUDs e módulos:**
  - **Pack Display:** o resource pack ativo, com ícone.
  - **Saturation:** fome, saturação escondida e exaustão (no servidor, a exaustão é estimativa).
  - **Nick Hider:** mostra outro nome no lugar do seu no chat, no scoreboard, na Dynamic Island,
    no menu de módulos e no menu principal. Só na sua tela.
- **HUD Editor:**
  - clique num elemento e use as setas para mover 1 px (Shift: 10 px);
  - C centraliza, H esconde;
  - o elemento selecionado fica destacado.
- **Layouts de HUD antigos:** os que guardavam o widget como "Music" agora posicionam o
  "Now Playing".

## Mudanças na v1.20.0: Floating Lyrics, música, QoL e performance

Auditoria completa em `docs/AUDIT-1.20.md` (arquitetura, problemas corrigidos, comparação com
outros clients, pendências). Guia das letras em `docs/LYRICS.md`. Testes em
`docs/TEST-REPORT-1.20.md`.

- **1227 Floating Lyrics** (Music):
  - letras sincronizadas flutuando no mundo, em quatro modos: World Floating, Player Follow,
    World Anchor e Screen Overlay;
  - 8 presets, 8 animações, destaque por palavra ou karaokê e contagem nas pausas
    instrumentais;
  - glow, contorno, sombra e fundo;
  - ajuste de sincronia global e por música.
- **Fontes das letras:**
  - `.lrc` locais, inclusive Enhanced LRC, UTF-8 ou Windows-1252;
  - LRCLIB, com User-Agent, uma requisição por vez, respeito ao 429/Retry-After, cache e
    conferência de título/artista/duração;
  - escolha manual quando não há certeza.
- **Relógio das letras:**
  - player local, exato: posição da placa de som;
  - Spotify e YouTube pelos controles de mídia do Windows (script PowerShell opcional que vai
    no jar, só leitura);
  - relógio manual quando não há linha do tempo.
- **Tela Lyrics e editor:** cronometrar com Espaço, ajustar, editar, salvar `.lrc`.
- **Now Playing:**
  - capa quadrada, redonda ou vinil girando;
  - álbum, origem, tempos e estado;
  - botões clicáveis com o chat aberto e posições prontas;
  - aparece e some suavemente.
- **Audio Visualizer:** 8 estilos, FFT real do player do 1227. Para Spotify/YouTube, dispositivo
  de captura (Stereo Mix) só com consentimento; sem dados, modo "simulated" com etiqueta.
- **Music Atmosphere:** anéis no chão a cada batida e luzes subindo com a energia da música.
- **Cinematic Mode** (Camera): esconde o HUD, a mira, o chat e a mão, mas mantém letras e
  música; tem Photo mode.
- **HUD e QoL:**
  - Inventory Counter;
  - Armor com barra e aviso;
  - Keybinds HUD;
  - Alerts: vida, comida, durabilidade, poções, inventário cheio, alvo perdido e kill;
  - Smart Armor;
  - Waypoints, com tela própria e import/export;
  - dia do jogo no Clock.
- **Telas:**
  - Resource packs: verificação, ordem, importação e backup;
  - Screenshots: filtros por data, grupos por dia, busca e copiar caminho.
- **Server Profiles:** vincula um perfil salvo a um servidor (ou ao singleplayer) e aplica ou
  sugere ao entrar.
- **Performance:**
  - distâncias separadas para jogadores, mobs e itens;
  - oclusão opcional de mobs (nunca de jogadores) e de baús/placas atrás de blocos;
  - a reflexão das partículas é validada antes de usar.
- **Benchmark:** 0,1% low, frame time médio e máximo, memória, entidades, partículas, gráficos e
  versão; comparação A/B entre as configurações atuais e um preset.
- **Correções:**
  - Música:
    - teclas e tick dos módulos sempre ativos (M, play/pause, volume, título da janela) nunca
      rodavam; agora rodam;
    - texturas de capa vazavam, e capas e títulos de janela eram lidos na thread de render;
    - posição do player adiantada pelo buffer;
    - loop de arquivos quebrados.
  - Config:
    - o salvamento perdia mudanças se a gravação falhasse;
    - um config corrompido voltava aos padrões (agora restaura o backup);
    - agora há migração de formato.

## Mudanças na v1.19.0: chuva de verdade, brilho no alvo e espadas animadas

- **Instalador no Windows:** o download agora traz `Instalar 1227.bat` junto do jar. Ele abre o
  instalador com o Java do sistema ou com o Java que já vem no launcher do Minecraft, então
  funciona mesmo quando o Windows pergunta "com qual app abrir" o `.jar`. Se não houver Java
  nenhum, ele abre a pasta `mods` e explica como copiar o arquivo.
- **Wet World refeito:**
  - chuva própria em riscos finos que caem e inclinam com o vento (densidade, vento e brilho
    ajustáveis; "Minecraft" volta à chuva original; neve continua a do Minecraft);
  - chão molhado: o chão aberto escurece e ganha um brilho do céu em ângulos baixos;
  - poças de verdade: manchas orgânicas de vários blocos, bordas suaves, refletem o céu (mais
    quando você olha de lado), pegam o brilho do sol ou da lua, enchem com a chuva e secam
    depois;
  - ondinhas e respingos onde as gotas caem;
  - segue a chuva que você vê (o tempo do módulo World conta);
  - botão "Preview rain (20 s)" para testar sem esperar chover.
- **Item Glow / Weapon Effects:** o brilho agora é desenhado atrás do item, então aparece em
  volta da espada e não por cima dela. O glow virou anéis de cópias deslocadas, que seguem o
  formato da lâmina.
- **Aim Helper como no vídeo:**
  - anel grosso e brilhante (um tubo com halo) que sobe e desce no corpo e inclina de leve;
  - brilho no corpo do alvo: uma luz colorida no corpo inteiro e uma faixa mais forte que
    acompanha o anel;
  - luz suave em volta da silhueta;
  - tudo com teste de profundidade: só aparece no que você realmente vê.
- **Weapon Models melhorados:**
  - lâminas com seção real (face plana e fio chanfrado mais claro), guardas e cabos com cantos
    chanfrados, tsubas redondas e brilho metálico;
  - animações novas:
    - sacar a arma (Spin, Flip ou Rise);
    - inspeção (Twirl, Turn over, Toss, Show off);
    - golpes alternando de lado;
    - respiração parada e pose de corrida;
    - velocidade das animações ajustável.

## Mudanças na v1.18.0: o 1227 vira um client dentro do mod
Instalação: Minecraft 1.8.9, depois Forge 1.8.9, depois `1227.jar` na pasta `mods`. Não precisa de launcher. Detalhes em `docs/CLIENT.md`, sistema visual em `docs/DESIGN.md` e testes em `docs/TEST-REPORT-1.18.md`.

**Telas novas do 1227** (cada uma pode ser desligada em Settings › Interface, e todas têm um botão para a tela original do Minecraft):
- **Menu principal:**
  - Singleplayer, Multiplayer, Options, Modules, Cosmetics, Profiles, Mods, Language, Screenshots, menu clássico e Quit;
  - cartão com o seu nome e a sua skin, e "What's new";
  - fundo Aurora, Constellation, imagem própria (`config/1227/backgrounds`) ou sólido;
  - layout à esquerda ou centralizado.
- **Menu de pausa:**
  - voltar, opções, LAN, conquistas, estatísticas, opções de mods e sair (os mesmos passos do vanilla);
  - 10 atalhos: editor de HUD, módulos, cosméticos, visuais, performance, perfis, teclas, client, screenshots e música;
  - fundo desfocado (opcional).
- **Servidores:**
  - abas Servers, Favorites, History e LAN;
  - ícone, MOTD, ping real com barras, jogadores (passe o mouse para ver a lista) e aviso de versão diferente;
  - adicionar, editar, excluir, conexão direta, reordenar com Shift+setas e atualizar com F5;
  - usa a lista do `servers.dat` e a conexão do Forge;
  - nada aparece antes de o servidor responder.
- **Mundos:** busca, jogar, criar, renomear, excluir (com confirmação) e abrir a pasta saves.
- **Settings** (uma tela só, 13 categorias): General, Interface, HUD, Visuals, Cosmetics, PvP, Animations, Audio, Performance, Controls, World, Profiles e Advanced.
  - As opções do Minecraft (vídeo, som, chat, skin, mouse, GUI scale e outras) ficam junto com os módulos do 1227.
  - Busca em todos os módulos; cada módulo abre a sua página de opções.
- **Death screen e Disconnected (com Reconnect):** telas próprias.
- **Outras telas do Minecraft com o visual do 1227:** Connecting, Loading terrain e as confirmações.
- **Screenshot manager:** grade de miniaturas, visualizar, abrir, copiar a imagem, excluir e navegar com as setas.

**Animação de abertura** (Settings › Interface › Opening animation; design próprio):
- **Press to enter** (padrão): as faíscas montam o símbolo sobre um fundo de fumaça e aparece "Press any key to enter". Ao apertar uma tecla ou clicar, o símbolo gira, solta faíscas e voa para o canto do menu enquanto os botões entram;
- **Converge:** centenas de faíscas voam em curva e montam o símbolo do 1227, seguidas de uma onda, de um brilho que atravessa o símbolo e do nome digitado letra por letra;
- **Glitch:** o símbolo chega com separação RGB, fatias deslocadas e cintilação, e o nome se embaralha até ficar certo;
- **Minimal:** aparece suave com um brilho atravessando.
- No fim, o símbolo voa até o canto do menu enquanto os botões entram.
- Toca só quando o jogo abre ou toda vez que o menu abrir (você escolhe). Clique ou tecla pula a animação, e "Preview" mostra de novo.

**Cosméticos novos** (Cosmetics › Halo):
- **Comet:** núcleo brilhante acima da cabeça com uma cauda longa que flui e balança quando você anda ou vira. Cores Blue, Red, Violet, Gold ou as suas, e cauda com comprimento ajustável;
- **Thorn Crown:** coroa escura de espinhos com as pontas na sua cor;
- **Comet + Crown:** os dois juntos;
- **Orbiting Badges:** medalhas com o símbolo do 1227 girando em volta da cintura.

**Seu personagem no menu principal** (Settings › Interface):
- Com a sua skin, carregada pelo próprio Minecraft a partir da sua conta.
- Depois da abertura, ele entra andando pela direita, para, vira de frente e passa a acompanhar o mouse com a cabeça, respirando.
- Se o Halo estiver ligado com Comet ou Thorn Crown, o personagem usa o cosmético.
- É desenhado só a partir da skin, sem mundo nem entidade falsa, então outros mods não são afetados.
- "Character walks in" desligado faz ele já aparecer parado.

**Aim Helper refeito** (Combat): agora marca o próprio alvo, como no vídeo do MoonDLC.
- **Marcador:** anel que sobe e desce pelo corpo do alvo, ponto de referência (mais perto da mira, peito ou cabeça), anel + ponto, ou cantoneiras.
- **Card ao lado do alvo:** rosto, nome, distância e anel de vida.
- **Marcas de acerto:** um X onde cada golpe pegou.
- Só aparece no alvo que você está vendo, nunca através de paredes, e não mexe na câmera nem mira por você.
- O painel antigo continua, com o nome "Target Info".

**Opções novas de client:**
- **Fog:** sem neblina, leve, vanilla ou densa; água e lava limpas; cor de neblina própria (dá o ar verde do vídeo).
- **Block Overlay:** cor, espessura, arco-íris e preenchimento do contorno do bloco.
- **Hit Particles:** multiplicador das partículas de acerto, críticos e sharpness sempre visíveis.
- **Reach Display:** distância do último golpe.
- **Título da janela:** personalizável em Settings › Interface.
- **Kill Effect "Pixel Burst":** cubinhos que explodem, caem e quicam no chão.

**Etapa 8, menus novos:**
- **Sky gallery:**
  - os 58 céus em miniaturas animadas, desenhadas com as mesmas partes do céu real (degradê, estrelas, aurora, nebulosa, galáxia, sol ou lua, grade);
  - coleções Night, Sunset, Space, Aurora e Neon, favoritos e busca;
  - opções do céu ao lado. No mundo, o céu muda atrás da janela.
  - Abre em Settings › World e pelo atalho Skies da pausa.
- **Music:** o mesmo fundo dos outros menus, e a engrenagem abre Settings › Audio (Discord e opções de música).
- **Weapon editor e Cosmetics** (etapa 5), agora também na pausa.

**Etapa 12, testes, otimização e instalador:**
- **Testes automáticos (JUnit, rodam no `gradle build` do GitHub Actions):**
  - configurações (limites, passos, ciclos, cores, JSON com valores ruins);
  - links do Spotify e do YouTube (inclusive tentativas de link malicioso);
  - nomes de músicas, os 58 céus, leitura de campos por tipo e nomes de perfis seguros.
  - Os testes acharam um erro real: o texto das configurações podia passar do limite quando vinha de fora do campo de texto. Corrigido.
- **Otimização:** as páginas Performance e Profiles e o tema da tela de carregamento liam arquivos do disco a cada frame; agora usam cache (relido só quando o arquivo muda ou a cada 1 s).
- **Instalador dentro do jar:** dois cliques no `1227-<versão>.jar` abrem o instalador. Ele:
  - acha a pasta do Minecraft;
  - confere se o Forge 1.8.9 está instalado (e abre a página do Forge se não estiver);
  - copia o mod para `mods` e move as versões antigas do 1227 para `mods/1227-old`, sem apagar nada.
  - Também funciona pela linha de comando: `java -jar 1227.jar --install [pasta]`.
  - Na pasta `mods`, o mesmo jar continua funcionando como mod normal.
- **Fabric:** cancelado a pedido; o 1227 é só Forge.

**Etapa 5, armas, cosméticos e efeitos de entrada:**
- **Cosmetics (tela nova):**
  - seu personagem em 3D com o que está ligado: Halo (China Hat, anel, Thorn Crown, Comet, orbes), Wings e Energy Shell;
  - gira sozinho ou arrastando com o mouse;
  - ao lado, a lista de cosméticos para ligar e ajustar, com a prévia mudando na hora.
  - Abre pelo menu principal, pela pausa e por Settings › Cosmetics.
- **Weapon editor (tela nova):**
  - a espada em 3D girando (arraste para virar);
  - grade com os 18 modelos, mais acabamento, cores, brilho, comprimento, espessura, largura, tamanho e mãos;
  - a butterfly abre e fecha na prévia.
  - Abre pela pausa (Weapons), pela tela de cosméticos e por Settings › Visuals.
- **Arrival Effects (Movement):**
  - efeito quando um jogador aparece, sai ou teleporta (nos dois pontos), e também para você ao entrar, renascer ou teleportar;
  - estilos Beam, Portal, Smoke, Pixels e Rift;
  - limite de efeitos por tick para não pesar quando muita gente carrega de uma vez.

**Etapa 4, Wet World** (World): na chuva, poças se formam aos poucos no chão aberto (e secam depois), refletem o céu (mais forte olhando de lado) e as gotas fazem ondinhas no chão e nas poças.

**Fundo "Smoke"** no menu: névoa cinza suave.

**Performance:**
- **Presets Low End, Balanced, High FPS e Visual Quality:** mudam opções reais do Minecraft (distância, gráficos, iluminação suave, nuvens, partículas, sombras, VBO, FPS máximo e VSync) e os limites do 1227. Cada um pode ser salvo com os seus valores.
- **Limites novos:** distância de entidades (jogadores só se você escolher), limite e distância das partículas do Minecraft, e FPS reduzido quando o jogo está em segundo plano.
- **Benchmark de 30 ou 60 s:** FPS médio, 1% low e pior frame, salvos em `config/1227/benchmarks.json` para comparar antes e depois. Nenhum ganho é prometido sem medir.

**Perfis:**
- perfis com nome: salvar, carregar, sobrescrever e excluir;
- copiar e colar a configuração pela área de transferência;
- restaurar os backups automáticos.

**Outros:**
- **Advanced:** tela de carregamento do Forge com as cores do 1227 (pelo `splash.properties` do próprio Forge, com restauração), pastas, reset geral com confirmação.
- **Sistema visual único** (cores, fontes, ícones, botões, sliders, toggles, campos, tooltips e notificações) usado por todas as telas novas.

## Mudanças na v1.17.0
- **Materiais animados para cosméticos** (shader próprio, `vfx/MaterialShader`): Plasma, Aurora, Galaxy, Fire, Electric, Hologram, Rainbow, Iridescent, Lava, Frost e Void. Sem suporte a shader na placa de vídeo, os cosméticos usam o degradê normal.
- **China Hat** (Halo › Style "China Hat"):
  - opção "Material" e cinco formatos: Cone, Bell (aba larga), Spiked (pontas), Two-tier (dois andares) e Flat disc;
  - borda brilhante e brilho nas bordas ajustáveis.
  - O Angel Halo também pode usar um material (vira um anel 3D).
- **Wings:** opção "Material" em todos os estilos.
- **Energy Shell (novo, Cosmetics):** casca de energia em volta do corpo que brilha nas bordas, anéis em órbita, os dois juntos ou um anel de Saturno. Usa os mesmos materiais.
- **Spacing Circle (novo, Combat):**
  - anel no chão com o raio da distância de combo (padrão 3 m), em quatro estilos (Solid, Dashed, Pulse e Glow);
  - mostra a distância dos seus olhos até a hitbox do oponente embaixo da mira e marca o chão embaixo dele;
  - a cor muda quando o oponente está dentro, perto ou fora do raio.
  - O oponente é o seu alvo atual ou o jogador mais perto que você está vendo (nunca através de paredes). Só informa: não mira, não move e não muda o alcance.
- **W-Tap Indicator (novo HUD):**
  - nota de cada golpe: Perfect (reset de 1 a 2 ticks), Good (3 a 5), Slow (6 ou mais), No reset ou No sprint;
  - quantos ticks durou o reset e quanto tempo depois do golpe ele começou;
  - linha do tempo do sprint com os golpes marcados e a % de golpes com sprint na sessão.
  - Só observa as suas teclas e o seu sprint; não aperta nada.
- **Item Physics (novo, Ambience):** itens no chão ficam deitados num ângulo aleatório e blocos apoiados no chão; enquanto caem, giram. Só o desenho muda (a posição e a coleta continuam iguais). Com o módulo desligado, os itens são desenhados exatamente como no vanilla.

## Mudanças na v1.16.0
- **Discord Presence:** o seu perfil mostra "Jogando 1227Visuals", com o servidor (ou "Singleplayer") e o tempo de jogo, pelo pipe local oficial do Discord (sem token e sem acesso à conta). Funciona no Windows.
  Configuração única:
  1. Em https://discord.com/developers/applications, clique em **New Application** e use o nome **1227Visuals** (é esse nome que aparece em "Jogando").
  2. Copie o **Application ID**.
  3. No 1227, abra Music › Discord Presence, cole o ID e ative o módulo.
- **Music Player** (categoria Music; tecla M, configurável):
  - **Músicas locais:** arquivos OGG e WAV em `config/1227-music`. Subpastas viram álbuns, o nome "Artista - Título.ogg" vira o título e um `cover.png` na pasta vira a capa.
  - **Recursos:** busca, fila, histórico, anterior/play/próxima, barra de progresso com avanço, volume, aleatório e repetir (todas ou uma).
  - **Atalhos:** abrir o player, play/pause, próxima e anterior.
  - **Spotify e YouTube:** cole o link da playlist nas configurações. O botão abre a playlist no app do Spotify ou no navegador, onde ela toca com a sua conta. O 1227 mostra o que está tocando (pelo título da janela), controla com as teclas de mídia e mostra o nome e a capa da playlist (oEmbed público, sem login). Nada é baixado.
  - **HUD "Music" (mini player):** capa, título, artista e progresso.
- **Configurações de texto:** novo campo de texto no menu (aceita Ctrl+V), usado nos links e no ID do Discord.

## Mudanças na v1.15.0
- **Plano do 1227 Client:** auditoria do jar 1.14.0 e arquitetura em `docs/PLAN-CLIENT.md`.
- **Céus elétricos (camada GLSL "Electric Sky"):** Electric Cyan, Electric Gold, Electric Red, Electric Violet e Energy Blue.
- **Auroras em cortina (camada GLSL "Aurora Curtains"):** Green Aurora, Purple Aurora e Blue Aurora. São 58 céus no total.
- **Picture Sky:** use as suas imagens (panoramas PNG/JPG, de preferência 2:1) como céu, colocando-as em `config/1227-skies`. Tem rotação lenta e a opção de manter os efeitos do céu por cima.
- **Pearl Helper:** trajetória da ender pearl com a física do 1.8.9 (gravidade 0,03, arrasto 0,99, ou 0,8 na água), marcador no bloco onde ela cai e distância e tempo de voo. Opcionalmente também para bolas de neve e ovos. Só visual.
- **Aim Helper (HUD informativo):** distância, velocidade e estado (no chão, pulando ou caindo) do jogador na mira ou do seu alvo. Não mexe na mira.
- **Sem servidor próprio:** a API e o site foram removidos. As versões são publicadas só pelas Releases do GitHub.

## Mudanças na v1.14.0
- **Launcher removido** do projeto (pasta `launcher/` e o workflow dele). O 1227 é distribuído só como mod.
- **Block Wave** saiu do Shockwave e virou um estilo de **Jump Effects** (pulo e/ou aterrissagem): os blocos do chão sobem como cópias translúcidas numa onda.
- **Afterimage (novo):** cópias-fantasma do jogador deixadas nas últimas posições, cada uma com a pose que ele tinha ali, sumindo aos poucos. Cor única, degradê ou arco-íris; quantidade, espaçamento, duração e opacidade ajustáveis. Funciona em você e, se quiser, no alvo ou em todos.
- **Player Glow:** estilo "Outline" (contorno nítido em volta do jogador) ou "Glow + Outline", com espessura ajustável. O contorno respeita paredes.
- **Item Glow:** estilo "Outline" (contorno que segue o formato do item segurado) ou "Glow + Outline".
- **Ambient Particles:** luzes voando com rastro: Shooting Stars (retas), Wisps (curvas suaves), Darting Sparks (viradas bruscas) e Flyers Mix.
- **Hit Color (novo):** tinge o jogador ou mob atingido com a sua cor durante o piscar de dano (por cima do vermelho do vanilla).
- **Halo:** estilo China Hat (chapéu cônico translúcido que acompanha a cabeça, com cores girando).

## Mudanças na v1.13.0
- **Scoreboard (HUD):** a barra lateral vira um elemento de HUD que dá para mover e redimensionar, com opções para esconder os números vermelhos, título, sombra, espaçamento e o fundo do HUD. Mostra exatamente o que o servidor envia.
- **Tab List:** painel limpo com a cabeça dos jogadores, ping em ms colorido (ou barras), você em destaque, cabeçalho e rodapé do servidor e a mesma ordem do vanilla.
- **Boss Bar:** barra fina com a cor de destaque, roxa clássica ou por vida, porcentagem opcional, altura, largura e escala do texto ajustáveis, ou esconder por completo.
- **Hit Delay (HUD):** tempo desde o seu último hit e o intervalo entre os dois últimos, com barra. Só observa os seus ataques.
- **Projectile Trails:** rastros em flechas, pérolas, bolas de neve, ovos, poções e anzol (estilos Glow, Sparkle, Smoke, Rainbow e Flame), seus ou de todos.

## Mudanças na v1.12.0
- **HUDs novos:** Clock (hora, data e tempo de sessão), Memory (RAM do Java com barra e entidades renderizadas), Server Info (endereço, jogadores no tab e ping), Compass (bússola deslizante com graus) e Session Stats (kills, mortes, K/D, hits e melhor combo).
- **Editor de HUD:**
  - Clique do meio trava ou destrava um elemento (também em "Lock position").
  - Botões Save 1-3 e Load 1-3 guardam layouts; Copy e Paste levam o layout pela área de transferência.
- **Backups do config:** a cada início, uma cópia de `config/1227.json` vai para `config/1227-backups/` (as 5 mais recentes).
- **Performance:** nível Ultra e presets de um clique (Low-end PC, High FPS, Balanced e Visual quality). Os presets mais leves desligam o Motion Blur.
- **Launcher, API e site:** primeira etapa (veja `docs/PROGRESS.md`).

## Mudanças na v1.11.0
- **Facas (Weapon Models):** 6 modelos novos e originais: Karambit (lâmina em gancho com anel), Bayonet, Flip Knife, Huntsman (com dentes no dorso), Skeleton Knife (cabo vazado com anel) e Push Dagger. Agora são 18 modelos.
- **Finishes:** Emerald, Pearl, Sapphire, Crimson, Rose Steel, Ember Fade, Ocean Fade, Midnight, Gold e Toxic. A lâmina faz degradê até a cor da ponta; "Custom" usa as suas cores.
- **Inspect em todos os modelos:** a tecla Inspect (V) gira a arma na mão e a aproxima da câmera. A butterfly continua com o flip.
- **Céus "shader":** nova camada GLSL de nuvens volumétricas (Volumetric Clouds e Storm Clouds), com nuvens que se movem, sombra embaixo e relâmpagos no Storm.
  - Céus novos: Overcast, Silver Sky, Storm Front, Night Overcast, Ember Night (horizonte laranja) e Teal Night (aurora verde). São 50 no total.
  - Com "Shader layer" em Off, cada céu usa a própria camada (as nuvens desses céus); escolhendo outra camada, ela substitui.
- **Shockwave "Block Wave":** os blocos do chão sobem como cópias translúcidas, com a cor de cada bloco, numa onda que se espalha. Só visual: o mundo não muda.

## Mudanças na v1.10.0
- **Weapon Models:**
  - **Mãos no cabo:** a mão (com a textura da sua skin) é desenhada presa ao cabo e acompanha o golpe, a rotação e o tamanho; a Katana e a Holy Claymore ganham a segunda mão. Opções: "Show hands on the grip", "Hand angle", "Second hand angle" e "Hand size". A mão esquerda antiga, que ficava fora do lugar, foi removida.
  - **Golpes:** partem da posição normal da mão, são suaves e voltam ao repouso. Duram 0,42 s só no desenho (o golpe real continua o do Minecraft).
  - **Correções:**
    - A Katana estava sendo desenhada com 25% de opacidade (transparente).
    - Lâminas podiam parecer transparentes por outro efeito deixar a profundidade desligada.
    - O Cleaver ficou mais fino, e "Blade width" não engrossa mais as lâminas largas.
    - Dragon Fang não é mais invertida.
    - Saber e Storm Blade não tremem mais.
    - "Thickness" agora muda só a espessura, não a largura.
- **Liquid Glass no HUD e no Dynamic Island:** o vidro copia a imagem do framebuffer principal do Minecraft. Em clients que desenham o HUD num buffer próprio (como o cache de HUD do Lunar), ele aparecia preto.
- **Vida do alvo:** se o servidor mostra a vida no placar (embaixo do nome ou no tab), o Dynamic Island, o Target HUD e o Damage Numbers usam esse valor.
- **Roda de emotes:** visual limpo, com um disco de vidro, divisórias finas e só a fatia em foco destacada.
- **Shockwave:** 8 estilos (Crystal Dome, Frost Nova, Inferno, Thunder, Void Rift, Sakura, Holy Light e Shatter Ring), com galeria de prévia.
- **Céus:** 14 novos (44 no total).
- **Novo cosmético Wings:** Angel, Demon, Butterfly, Energy e Crystal, batendo as asas (mais rápido no ar).

## Mudanças na v1.9.0
- **Controles do Minecraft:** só a tecla "Open 1227 menu" fica lá; as teclas de cada mod continuam no menu do 1227.
- **Liquid Glass fora do menu:**
  - Todos os HUDs (Keystrokes, Armor, Coords, Info, Potions, Target, Watermark, Held Item, Combo, Movement): fundo "Liquid Glass" (novo padrão).
  - Dynamic Island: "Background style" Liquid Glass / Glass / Solid.
  - Janela de configurações dos mods e roda de emotes.
- **4 estilos de menu novos (11 no total):**
  - Aurora: vidro com luzes de cor se movendo.
  - Gradient: borda com gradiente que corre em volta da janela.
  - Midnight: azul escuro com estrelas piscando.
  - OLED: preto puro com linha de destaque.
- **Accent mode:**
  - Static.
  - Two-tone: alterna entre duas cores.
  - Rainbow.
  - Pulse.
  - Tem velocidade ajustável e vale para menu, HUD e efeitos.
- **HUD:** fundos novos Accent bar, Outline e Gradient.
- **Crosshair:** estilos novos X, Triangle, Brackets e Diamond.

## Mudanças na v1.8.1
- **Teclas nas Configurações do Minecraft:** a tecla de abrir o menu aparece em Opções > Controles, na categoria "1227" (sincronizada com o menu do 1227). As outras teclas ficam só no menu do 1227.
- **Botões do mouse:** o botão do meio e os laterais podem ser usados como tecla (no menu do 1227: clique no campo de tecla e aperte o botão do mouse; ou pelos Controles).
- Botão "Open Minecraft Controls (1227 keys)" no módulo Client; o "Done" volta para o menu do 1227.
- A lista de conflitos de tecla não acusa mais as próprias teclas do 1227 duas vezes.

## Mudanças na v1.8.0
- **Configurações em janela:** clique na engrenagem do card (ou botão direito) para abrir as opções do mod numa janela; clique esquerdo no card liga/desliga. Esc, o X ou clicar fora fecham.
- **Emotes de dança:** 10 danças novas (Shuffle, Robot, Disco, Headbang, Glide, Hype, Breakdance, Swing, Sprinkler, Pump It) numa segunda página da roda. Troque de página pelas abas, rolando o mouse ou com botão direito.
- **Tung Tung Sahur maior:** agora ~2,1x a altura da vítima por padrão, com a opção "Tung Sahur size" (1x–3x) no Kill Effect.
- **Weapon Models:**
  - Rotação X/Y/Z e "Model size", aplicados em volta do cabo.
  - "Blade width" (padrão 1,6x) e Thickness padrão maior: as lâminas finas ficaram mais largas.
  - Detalhe: um friso de luz no centro da lâmina e brilho nos dois fios.
  - **Empunhadura e golpe próprios por espada** ("Hold & swing": Per model / Classic):
    - Katana e Holy Claymore: com as duas mãos e corte horizontal.
    - Cleaver, Obsidian Edge e Rune Greatsword: golpe pesado de cima pra baixo.
    - Void Rapier e Butterfly Knife: estocada.
    - Dragon Fang: pegada invertida.
    - Energy Saber e Storm Blade: arco girando.
    - Twin Fang: corte duplo.
    - Crystal Blade: flutuando.
    - As demais: clássico.

## Mudanças na v1.7.1
- Tecla padrão da roda de emotes trocada de B para "." (ponto), porque vários clients já usam B para emotes.
  Dá para mudar em Animations > Emotes > Emote key (o menu avisa se a tecla estiver em conflito).
