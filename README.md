# 1227 — v1.0.0 (Minecraft 1.8.9 · Forge 11.15.1.2318 · Java 8)

Mod client-side e puramente visual. Não envia pacotes, não desenha através de paredes e não muda alcance,
timing de ataque ou outras mecânicas do servidor.

## Compilar
Envie esta pasta para um repositório no GitHub. Aba **Actions** > "Build mod jar" > baixe o artefato `1227-jar`.
O `.jar` vai em `.minecraft/mods` com Forge 1.8.9.

## Abrir o menu
- Tecla padrão: **Right Control** (configurável em Settings > Client > Menu key; Esc/Backspace = nenhuma).
- **/1227** no chat sempre abre o menu, mesmo sem tecla.
- As teclas ficam na configuração do 1227 (não em Controles), e o menu avisa quando outra tecla do Minecraft,
  de outro mod ou do próprio 1227 usa a mesma.
- Config: `config/1227.json` (uma `lumenvisuals.json` antiga é importada uma vez). Perfis: `config/1227/profiles`.
  Sons próprios: `config/1227/sounds`.

## Categorias
| Categoria | Módulos |
|---|---|
| Skies | Dynamic Skies (30 céus, transição suave) |
| Ambience | Ambience (12 estilos), Ambient Particles |
| Auras | Target Aura (24 estilos), Player Aura (os mesmos 24) |
| Cosmetics | Aura Hat, Halo |
| Movement | Trails (16), Jump Effects (20, também como impacto de aterrissagem) |
| Combat | Hit Effect, Kill Effect, Shockwave |
| Weapons | Weapon Effects (40) |
| Camera | Motion Blur, View Bobbing |
| Animations | 1.7 Animations |
| Gameplay | Toggle Sprint |
| Hit Sounds | Hit Sounds |
| HUD | Dynamic Island, Hit Marker |
| Settings | Client, Presets (Performance, Balanced, High Quality, Cinematic, Minimal PvP + paletas + 3 perfis), Performance |

Todo módulo tem "Reset to defaults" no fim das opções; Settings > Client tem o reset geral.

## Arquitetura
```
core/    Module, Setting (Bool/Num/Mode/Col/Key/Action), ModuleManager, ConfigManager, InputManager,
         Notifier, CombatTracker, Command1227
vfx/     ParticleSystem (pool + atlas), Lines, Arcs, ShockwaveSystem, AuraLibrary, SkyPreset, Sprite
module/  um arquivo por módulo
ui/      ClickGui, Gfx, Icons
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
