# 1227: auditoria técnica (fase 1)

Data: 11/11/2026. Base auditada: o código-fonte completo do projeto (o mesmo que gera `1227-1.11.0.jar`), com `build.gradle`, `mcmod.info`, recursos e o workflow do GitHub Actions. Os arquivos `.jar` citados no pedido não vieram anexados; isso não bloqueou nada, porque o projeto de desenvolvimento completo já estava disponível.

## 1. Estrutura e build

| Item | Situação |
|---|---|
| Minecraft / Forge / Java | 1.8.9 / 11.15.1.2318 / Java 8 (`build.gradle`, ForgeGradle 2.1) |
| Build | `.github/workflows/build.yml`: Temurin 8 + Gradle 2.14.1, `setupCIWorkspace build`, publica o jar como artefato |
| Código | 18,6 mil linhas de Java em 7 pacotes (`core`, `module`, `hud`, `ui`, `vfx`, `render`, raiz) |
| Recursos | Fonte Poppins (SIL OFL, com `OFL.txt`); logo; modelos `knife.k127` e `sahur.m127` e textura `sahur.png` (enviados pelo usuário); nenhum som em arquivo (os sons são sintetizados em código) |
| Mixins / coremod | Nenhum. Tudo é feito com eventos do Forge, o que reduz o risco de conflito com outros mods e clients |

### Como os módulos funcionam
- `ModuleManager.init()` registra cada módulo em ordem fixa. `Module.setEnabled` registra ou remove o módulo do `MinecraftForge.EVENT_BUS`, então um módulo desligado não processa eventos.
- As configurações (`Setting.Bool/Num/Mode/Col/Key/Action`) são declaradas no construtor. O `ConfigManager` salva tudo em `config/1227.json` com escrita atômica (arquivo temporário + move) e salvamento limitado no tempo.
- Os módulos se comunicam por eventos próprios (`CombatTracker.HitEvent/KillEvent`) e por singletons (`ParticleSystem`, `Arcs`, `Lines`, `ShockwaveSystem`, `BodyFx`), que podem ser trocados por instâncias isoladas para as prévias do menu (`PreviewStage`).

## 2. Inventário de módulos (verificado no código)

O número indica quantas configurações reais o módulo tem. Considerei "real" quando a lógica existe e é usada, não só a interface.

**Visual (mundo e jogadores):** Dynamic Skies (15; 50 céus, camada GLSL), Ambience (7), Ambient Particles (3), World (5), Target Aura (5), Player Aura (5), Player Glow (6), Aura Hat (6), Halo (3), Wings (7), Trails (4), Jump Effects (8), Player FX (7).

**Combate:** Hit Effect (7), Kill Effect (4; 11 estilos), Shockwave (8; 9 estilos), Damage Numbers (9), Victory & Defeat (8), Hit Marker (4), Weapon Effects (15), Item Glow (9), Weapon Models (25; 18 modelos).

**Câmera e animação:** Motion Blur (7; FBO de acumulação), View Bobbing (2), Static FOV (3), Zoom (5), Camera FX (6), Low Fire (2), 1.7 Animations (11), Toggle Sprint (3; regras vanilla), Emotes (3; 22 emotes).

**Interface:** Dynamic Island (24), Crosshair (13), Nametags (8), Chat (5), Menu Style (10), Client (14), Presets (4), Performance (7).

**HUD (editor com arrastar, snap e guias):** Watermark, Held Item, Info Bar (FPS/ping/CPS/velocidade), Keystrokes, Target HUD, Combo Counter, Armor, Potions, Coordinates, Movement Status, mais os novos desta etapa: Clock, Memory, Server Info, Compass e Session Stats.

**Áudio:** Hit Sounds (15). Sons sintetizados em código (sem direitos de terceiros), pacotes, volume, pitch, variação, combo pitch, limite de sobreposição, intervalo mínimo, disparo "hit confirmado" ou "no golpe", prévia e pasta para WAVs próprios.

## 3. Problemas encontrados e situação

| Problema | Situação |
|---|---|
| A descrição do Dynamic Skies dizia "30 skies", mas são 50 | Corrigido |
| O config não tinha backup automático | Corrigido: guarda os 5 últimos em `config/1227-backups/` |
| HUD sem trava, sem layouts salvos e sem import/export | Corrigido: trava com clique do meio, 3 slots, copiar e colar |
| Faltavam HUDs de relógio, memória, servidor, bússola e estatísticas da sessão | Implementados |
| Performance só tinha 3 níveis e nenhum preset de um clique | Corrigido: nível Ultra e 4 presets |
| Liquid Glass preto no HUD do Lunar | Corrigido na v1.10 (cópia do framebuffer principal); falta confirmar no jogo |
| A vida de outros jogadores é falsa em alguns servidores | Limitação do servidor. Uso a vida do placar quando ela existe (v1.10) |
| Mãos e golpes por espada calculados sem poder ver | Precisam de teste no jogo; existem ajustes manuais |
| Nenhum teste dentro do jogo pode rodar neste ambiente | Risco aberto. Ver "Testes" no PROGRESS.md |

## 4. Riscos e dependências
- **Sem execução do jogo aqui:** a compilação é checada com `javac` contra o próprio código; símbolos do Minecraft e do Forge ficam sem resolver localmente e só são resolvidos no build do CI.
- **Desempenho:** sem benchmark medido. Não declaro ganho de FPS; os presets só reduzem o custo dos efeitos do próprio 1227.
- **Launcher:** autenticação Microsoft exige um Client ID do Azure registrado pelo dono do projeto e aprovado para a API do Minecraft. Isso não pode ser feito por mim (ver PROGRESS.md).
- **Ambiente sem internet para pacotes:** o launcher (npm/cargo) não pôde ser compilado aqui. O build roda no GitHub Actions.
