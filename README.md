# 1227 — v1.1.0 (Minecraft 1.8.9 · Forge 11.15.1.2318 · Java 8)

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
| Weapons | Weapon Effects (40, presos à lâmina em 1ª pessoa), Item Glow |
| Camera | Motion Blur, View Bobbing, Zoom, Camera FX, Low Fire |
| Animations | 1.7 Animations |
| Gameplay | Toggle Sprint |
| Hit Sounds | Hit Sounds |
| HUD | Dynamic Island, Hit Marker, Crosshair, Info Bar (FPS/ping/CPS/velocidade), Keystrokes, Target HUD, Combo Counter, Armor, Potions, Coordinates, Movement Status |
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
