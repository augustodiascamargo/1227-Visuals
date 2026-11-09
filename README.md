# 1227 — v1.4.0 (Minecraft 1.8.9 · Forge 11.15.1.2318 · Java 8)

Mod client-side e puramente visual. Não envia pacotes, não desenha através de paredes e não muda alcance,
timing de ataque ou outras mecânicas do servidor.

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
