# 1227 Client: auditoria (v1.14.0), arquitetura proposta e plano

## 1. Auditoria do ZIP recebido

| Item | Resultado |
|---|---|
| `1227-1.14.0.jar` | Build real do GitHub Actions: 209 classes, recursos (fontes, logo, modelos) e `mcmod.info`. **O mod compila no ambiente oficial** (ForgeGradle 2.1, Forge 11.15.1.2318, Java 8) |
| `1227-1.14.0-sources.jar` | Mesmo código do projeto, com nomes SRG (gerados pelo ForgeGradle). Nenhuma diferença de lógica em relação ao repositório |
| Tamanho | 108 arquivos Java, cerca de 19,7 mil linhas, 61 módulos registrados |
| Acoplamento ao Forge | 54 dos 108 arquivos importam `net.minecraftforge` (eventos, `GuiIngameForge`, `ClientCommandHandler`). É o principal obstáculo para o Fabric |

### Recursos preservados (todos existem e funcionam no código)
- **Céu e mundo:** Dynamic Skies (50 céus + GLSL), Ambience, Ambient Particles, World.
- **Auras e jogadores:** Player Aura, Target Aura, Player Glow com outline, Aura Hat, Halo com China Hat, Wings, Trails, Afterimage, Jump Effects com Block Wave, Player FX.
- **Combate:** Hit Effect, Hit Color, Kill Effect (11), Shockwave (8), Projectile Trails, Damage Numbers, Match FX, Weapon Effects (40), Item Glow com outline, Weapon Models (18).
- **Câmera:** Motion Blur, Zoom, FOV, Camera FX, View Bobbing, Low Fire, 1.7 Animations.
- **Jogabilidade:** Toggle Sprint, Emotes (22), Hit Sounds.
- **Interface:** Crosshair, Nametags, Chat, Tab List, Boss Bar, Scoreboard, Dynamic Island, 17 HUDs, Click GUI, Menu Style (11), Presets, Performance, configuração JSON com backups, layouts de HUD e atalhos.

### Problemas encontrados
1. **Lógica misturada com a API do Forge:** módulos assinam eventos do Forge direto. Para o Fabric, isso vira uma camada de eventos própria (seção 2).
2. **Sem teste automatizado da lógica pura** (matemática de efeitos, configs): adicionar testes JUnit no módulo `common`.
3. **Desempenho:** Afterimage, outlines e Hit Color redesenham o modelo do jogador. Precisam de limite por quantidade de jogadores e entrar nos presets Low/Medium.
4. **Recursos dos vídeos que são cheats:** o vídeo da Clarity/Moon DLC mostra uma lista de módulos com Aura, Backtrack, Fake Lag, Scaffold, TargetStrafe e Auto Totem. **Esses não serão feitos.** Só os visuais dos vídeos entram (céus elétricos, silhuetas luminosas, efeitos vermelhos, aurora verde).

## 2. Arquitetura proposta

```
1227/
├─ common/        lógica sem Minecraft: configs, definições de módulos, matemática de efeitos,
│                 música, cliente Discord, previsão de trajetória   (Java 8, testável com JUnit)
├─ forge/         o mod Forge atual + adaptador de eventos do Forge  (ForgeGradle 2.1)
└─ fabric/        o mod Legacy Fabric 1.8.9 + mixins que disparam os mesmos eventos (Loom)
```

- **Eventos próprios (`net.m1227.api.Events`):** RenderWorld, RenderHud, ClientTick, Attack, KeyInput e os demais. O Forge publica esses eventos a partir dos eventos do Forge; o Fabric, a partir de mixins. Os módulos passam a assinar só os eventos do 1227. Assim a mesma lógica roda nas duas plataformas.
- **Renderização:** os dois lados usam o mesmo Minecraft 1.8.9 (mappings MCP vs. Yarn/Legacy). O código de render fica num source set compartilhado, compilado duas vezes com remapeamento. É o ponto mais trabalhoso do Fabric.
- **Sem servidor próprio:**
  - Arquivos do jogo baixados das fontes oficiais: piston-meta e libraries da Mojang, maven do Forge, meta do Legacy Fabric.
  - Sem login próprio: o login e o Play ficam no launcher oficial do Minecraft (decisão de 12/11/2026, sem Azure).
  - Atualizações do próprio 1227 via GitHub Releases (a API pública do GitHub, sem backend meu).
  - **A API e o site foram removidos na v1.15.0.** As releases do GitHub são o único ponto de distribuição.

### Decisões que precisam de você
| Tema | O que dá para fazer de forma legítima |
|---|---|
| **Perfis offline** | Não haverá. Sem login próprio, o 1227 não tem como verificar quem possui o jogo; quem joga entra pela conta Microsoft no launcher oficial |
| **Login Microsoft** | **Descartado** (sem Azure). O launcher oficial faz o login |
| **Discord** | O Discord não permite que apps de terceiros leiam DMs ou mensagens da sua conta: só um bot pode ler canais, e somente nos servidores onde ele foi adicionado. Proposta: (1) Rich Presence, mostrando "Jogando 1227" no seu perfil; (2) janela no jogo com as mensagens de canais de um servidor seu, lidas por um bot que você cria (o token fica só no seu PC); (3) notificações e indicador de novas mensagens desses canais |
| **Música** | Arquivos locais (OGG e WAV de cara; MP3 com um decodificador LGPL embutido no build) com busca, capas (`cover.png`/`folder.jpg` da pasta), fila, histórico, mini player e atalhos. Streaming: só o controle do app oficial do Spotify via Web API, se você quiser depois. Não haverá download de músicas |

## 3. Plano por etapas (estado)

| # | Etapa | Estado |
|---|---|---|
| 1 | Auditoria | **Feita** (este documento) |
| 2 | Arquitetura compartilhada (`common`, eventos próprios) | A fazer, junto com a etapa 9 |
| 3 | Melhorias nos módulos existentes | Contínuo |
| 4 | Céus, clima e ambiente: céus elétricos, aurora verde, Picture Sky, chuva com poças e ondulações | **Começou nesta entrega** |
| 5 | Espadas, armas e cosméticos (editor, prévia em personagem 3D) | A fazer |
| 6 | Pearl Helper e Aim Helper (informativo) | **Feita nesta entrega** |
| 7 | Discord (Rich Presence "Jogando 1227Visuals") e Music Player (local + controle de Spotify/YouTube) | **Feita (v1.16.0)** |
| 8 | Nova interface (menus de céus, espadas, cosméticos, música e integrações) | A fazer |
| 9 | Fabric 1.8.9 | A fazer (exige a etapa 2) |
| 10 | Launcher desktop | **Cancelada** (12/11/2026): ver decisões abaixo |
| 11 | Integração launcher + Forge + Fabric | **Cancelada** junto com a etapa 10 |
| 12 | Testes, otimização e instaladores | A fazer |

**Limite de testes:** este ambiente não roda o Minecraft nem tem internet para baixar dependências. Tudo é compilado aqui contra o próprio código e no GitHub Actions contra o Forge e o Fabric reais. O teste dentro do jogo depende de você.

## Decisões do usuário (12/11/2026)
- **Sem Azure, de forma definitiva.**
- **Sem launcher próprio** (pedido do usuário: o launcher oficial não deve precisar ser aberto). Um launcher que abre o jogo sozinho exige login Microsoft próprio, e sem Azure isso não existe. O 1227 continua como mod: o `.jar` vai na pasta `mods` de qualquer instalação Forge 1.8.9 (launcher oficial, Lunar com Forge, Prism, MultiMC etc.).
- **Discord:** só Rich Presence ("Jogando 1227Visuals").
- **Música:** arquivos locais e playlists do Spotify/YouTube, tocando nos apps oficiais e controladas pelo 1227.
