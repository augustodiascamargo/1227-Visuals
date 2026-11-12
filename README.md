# 1227 — v1.12.0 (Minecraft 1.8.9 · Forge 11.15.1.2318 · Java 8)

Mod client-side e puramente visual. Não envia pacotes, não desenha através de paredes e não muda alcance,
timing de ataque ou outras mecânicas do servidor.

## O que tem neste repositório
| Pasta | O que é |
|---|---|
| raiz (`build.gradle`, `src/`) | O mod Forge |
| `launcher/` | Launcher (Tauri 2 + React + TypeScript + Rust). Build: workflow "Build launcher" |
| `api/` | API de versões e notícias (Cloudflare Worker) |
| `site/` | Site oficial estático (Cloudflare Pages) |
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
| Ambience | Ambience (12 estilos), Ambient Particles, World (fullbright, hora do dia, clima — só na sua tela) |
| Auras | Target Aura (24 estilos), Player Aura (os mesmos 24), Player Glow |
| Cosmetics | Aura Hat, Halo |
| Movement | Trails (16), Jump Effects (20), Player FX (aparecer, sumir, teleporte) |
| Combat | Hit Effect (+ burst de crítico), Kill Effect, Shockwave, Damage Numbers, Victory & Defeat |
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
vfx/     ParticleSystem (pool + atlas), Lines, Arcs, ShockwaveSystem, AuraLibrary, SkyPreset, Sprite
module/  um arquivo por módulo
ui/      ClickGui, Gfx, Icons, Glass
hud/     HudModule (base movível), elementos do HUD, HudEditorScreen, ClickCounter
render/  HandRenderer (mão em 1ª pessoa), HandFx (efeitos presos ao item)
audio/   HitSoundEngine, SoundBank
```
A entrada (teclas) fica em `InputManager`, separada da interface. O código depende só do Forge 1.8.9;
nada depende de Lunar ou de outro launcher.

## Contas do launcher
A tela **Contas** permite manter perfis locais com nome escolhido pelo usuário. Consulte `docs/ACCOUNT_PROFILES.md` para o estado da autenticação e os próximos requisitos. Esses perfis são úteis para identificação, desenvolvimento e ambientes de teste que aceitam jogadores sem autenticação. Eles não criam uma sessão Minecraft, não comprovam propriedade do jogo e não liberam servidores autenticados.

O login Microsoft oficial ainda precisa ser configurado e implementado por meio de um aplicativo OAuth registrado, com validação Xbox Live/XSTS e Minecraft Services. O launcher não solicita a senha Microsoft diretamente nem simula uma autenticação. A versão atual do launcher também ainda não inicia o Minecraft por conta própria; ela instala/atualiza o mod e integra-se a instalações Forge existentes.

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
