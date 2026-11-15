# Relatório de testes, v1.20.0

## Feito aqui (sem o jogo)

| Teste | Resultado |
|---|---|
| Compilação de checagem de todo o código (197 arquivos) | Nenhum erro do código do 1227. Os erros que sobram são de classes do Minecraft/Forge, ausentes fora do Gradle |
| APIs do Minecraft/Forge usadas pelo código novo | Conferidas nos fontes MCP 1.8.9 e Forge 1.8.9 (resource packs, TESR e `renderTileEntityFast`, `GuiContainer`, inventário, raycast, partículas, texturas) |
| `LyricsTest`, 15 testes | 15 passaram |
| `SettingTest` + `PureLogicTest`, 12 testes da versão anterior | 12 passaram |
| `ProfilesTest` | Só roda no GitHub (precisa das classes do jogo) |

O que os 15 testes de `LyricsTest` cobrem:
- LRC com `[mm:ss.xx]` e `[mm:ss.xxx]`, `[mm:ss:xx]`, marcas repetidas e `[offset]` positivo e
  negativo;
- Enhanced LRC por palavra;
- UTF-8 com e sem BOM e Windows-1252, com acentos portugueses;
- letra sem tempo (não sincronizada) e linha atual em qualquer instante, inclusive voltando no
  tempo;
- palavras estimadas sempre dentro da linha e escrita/leitura de volta do `.lrc`;
- limpeza de títulos ("(Ao Vivo)", "- Remastered 2011", "feat.", "Official Video");
- correspondência: aceita a mesma música; recusa outro artista, títulos parecidos de músicas
  diferentes e duração muito diferente;
- registros do LRCLIB com campos nulos e escolha do melhor resultado;
- FFT achando um tom de 1 kHz na banda certa, e o buffer de PCM.

O FFT e o parser foram testados com dados sintéticos; o LRCLIB real não foi chamado nos testes.

## Para testar no jogo (CI compila e roda os testes; o resto é com você)

### Música e letras
1. **Música local com `.lrc` do lado** (OGG e WAV): as linhas devem entrar na hora certa.
   Pause, retome, use "Test from here" e o próximo/anterior: a linha deve acompanhar na hora.
2. **Música local sem `.lrc`, com nome "Artista - Título":** deve aparecer "Looking for lyrics…"
   e depois as letras do LRCLIB, ou "No lyrics".
   - Arquivo com nome incompleto: deve dar "No lyrics" ou abrir a lista para escolher, nunca uma
     música errada.
   - Música instrumental: deve dar "Instrumental", sem texto.
3. **Desconecte a internet:** deve aparecer uma mensagem de erro; ao voltar, "Search again" deve
   funcionar.
4. **Spotify no Windows:**
   - Settings › Audio › "Windows media link" deve mostrar "running";
   - as letras devem seguir a música;
   - ao avançar dentro do Spotify, a linha deve mudar em até ~0,5 s.
5. **YouTube no Chrome:** o mesmo teste do Spotify. Se o título vier como "Artista - Música
   (Official Video)", deve achar mesmo assim.
6. **Sem a ponte** (desligue a opção): deve aparecer o aviso "shares no timeline" e o relógio
   manual deve funcionar.
7. **Editor:** marque linhas com Espaço, salve, troque de música e volte: o `.lrc` salvo deve
   ser usado.
8. **Sincronia por música:** -0.25 s / +0.25 s devem ficar guardados para aquela música.

### Render
9. **Os quatro modos:** World Floating, Player Follow, World Anchor e Screen Overlay. Teste:
   - andando, pulando e caindo;
   - em primeira e terceira pessoa;
   - perto de paredes (o texto não deve atravessar);
   - com Motion Blur ligado e desligado.
10. **Presets e animações:** os 8 presets e as 8 animações. Confira a legibilidade com céu claro
    e escuro.
11. **Now Playing:** capas Square, Circle e Vinyl. Com o chat aberto, clique nos botões e use a
    rodinha.
12. **Visualizer:** os 8 estilos com o player do 1227. Com Stereo Mix ligado no Windows, teste
    com o Spotify.
13. **Cinematic Mode:** normal e Photo mode.

### Módulos novos
14. **Waypoints:** tecla B, beam, lista (renomear, cor, ícone, apagar, export/import) e
    separação por servidor e por dimensão.
15. **Inventory Counter, Armor e Alerts:** força a vida baixa, a armadura quase quebrando e o
    inventário cheio.
16. **Smart Armor:** abra um baú com armaduras melhores e piores que as suas.
17. **Resource packs:** importar um zip, ordenar, aplicar, e um pack de outra versão (deve
    aparecer o aviso de formato).
18. **Server Profiles:** salve um perfil, vincule ao servidor, saia e entre de novo.

### Performance
19. **Os 4 presets e a comparação A/B:** os resultados devem trazer avg, 1%, 0.1% (com mais de
    1000 frames), ms, memória, entidades e partículas.
20. **"Skip chests and signs hidden behind blocks"** num lobby cheio de baús: nada pode sumir na
    sua frente.
21. **"Skip mobs hidden behind blocks":** jogadores nunca devem sumir.

## v1.20.2

Feito aqui: compilação de checagem de todo o código (201 arquivos), com a análise de fluxo do
javac. Nenhum erro do código do 1227; sobram só os de classes do Minecraft/Forge, ausentes fora do
Gradle. A busca por opções usadas antes de serem criadas não achou nenhum caso. `LyricsTest` (15)
e `SettingTest` + `PureLogicTest` (12) passaram. Nada disto foi testado dentro do jogo.

Para testar no jogo:
22. **Player FX:** troque o "Style" entre Classic, Beam, Portal, Smoke, Pixels e Rift. Com um
    config antigo em que o Arrival Effects estava ligado, o Player FX deve abrir ligado e no
    mesmo estilo.
23. **Aim Helper "Glow point":** bata num jogador. O ponto de luz deve ficar no corpo dele, no
    lugar escolhido em "Point" (mais perto da mira, peito ou cabeça), e se mover suave. A câmera
    nunca deve se mexer sozinha.
24. **Inspeção dos modelos 3D:** aperte a tecla de inspeção e troque de slot. Só a arma deve
    girar, e os braços não podem aparecer de lado.
25. **Item Glow:** com Glow, Outline e Glow + Outline, numa espada normal e numa encantada. O
    contorno deve ter a mesma espessura em volta da lâmina, sem aparecer uma segunda espada
    embaixo. Teste também em 1080p e numa janela pequena.
26. **Player Glow:**
    - em Target, Everyone e Self (3rd person), o contorno deve ficar só na borda;
    - o meio do corpo não pode ficar branco;
    - o contorno deve sumir atrás de paredes;
    - durante o flash vermelho de dano, o contorno deve manter a cor.
27. **Body:** Glass, Ghost, Hologram, Shadow, Tint e Rainbow. Confira:
    - o nome em cima da cabeça continua aparecendo;
    - armadura, item na mão e capa aparecem com o mesmo material;
    - em terceira pessoa, o seu personagem também muda (com "Self").
28. **No Hurt Cam:** tome dano de queda e de um jogador.
    - Com 0%, a câmera e a mão não devem inclinar.
    - Com 50%, devem inclinar menos que o normal.
    - Em terceira pessoa, o seu personagem ainda pisca vermelho (se "Keep red flash" estiver
      ligado).
    - O Hit Color e o Target HUD continuam funcionando.

## v1.21.0

Feito aqui:
- **Compilação de checagem** de todo o código (222 arquivos), com a análise de fluxo do javac.
  Nenhum erro do código do 1227. Sobram só os de classes do Minecraft/Forge, ausentes fora do
  Gradle.
- **APIs novas de rede, pacotes, entidades e opções:** conferidas nos fontes do MCP 1.8.9 e do
  Forge 1.8.9.
- **Testes:**
  - `PvpPerfTest`, 8 testes novos: percentis, 1% low, jitter RFC 3550, janelas de tempo, regras de
    classificação dos hits, taxa de feedback, e o status ping contra um servidor local de teste que
    fala o protocolo 1.8;
  - `LyricsTest` (15) e `SettingTest` + `PureLogicTest` (12) passaram de novo.
- **Busca por opções usadas antes de serem criadas:** nenhum caso.

**Não feito aqui:** nenhum benchmark dentro do jogo, porque não dá para rodar o Minecraft neste
ambiente. Por isso não há números de antes e depois.

### Para testar no jogo

Registre sempre: configuração, cenário, duração, FPS, frame time, 1% low, memória e latência.

29. **Mesmo lugar, mesma direção, A/B de 2 x 20 s.** Rode em quatro cenários: mundo vazio, muitos
    blocos, muitas entidades, muitas partículas e cosméticos.
    - Compare cada preset PvP contra a sua configuração.
    - O resultado deve dizer "scene changed" se você se mexer.
30. **FPS limitado e ilimitado, VSync ligado e desligado** (Low Latency Mode). Depois "Measure
    stable frame time" e compare o 1% low com e sem o limite medido.
31. **Background FPS:** troque de janela durante a partida e volte. O jogo deve responder no
    primeiro frame.
32. **Servidor PvP com poucos e com muitos jogadores:**
    - Combat tab: tentativas e níveis;
    - um amigo batendo no mesmo alvo deve gerar "Weak";
    - bater durante a imunidade deve gerar "Unknown".
33. **Latency Monitor:**
    - ping baixo e alto (servidor distante);
    - variação (um download rodando);
    - queda de conexão (desligue o Wi-Fi por alguns segundos): deve aparecer uma "stall" e,
      depois, uma desconexão.
34. **Troca rápida de alvos, combos, W-tap e blockhit:** confira a Timeline e o W-Tap Trainer.
35. **Input Monitor:** o "wait for tick" deve ficar em média perto de 25 ms em qualquer FPS.
36. **Competitive Mode:** abra o Preview, desmarque um módulo, ligue, reinicie o jogo e desligue.
    Tudo deve voltar como era.
37. **PC fraco (CPU e GPU):** o Low-End PvP e a Dynamic Quality. O Overview deve mostrar a
    qualidade cosmética baixando nas lutas.

## v1.22.0

Feito aqui:
- **Compilação de checagem** de todo o código (232 arquivos), com análise de fluxo: nenhum erro do
  1227. As APIs novas do Minecraft, do Forge e do LWJGL foram conferidas nos fontes do MCP e do
  Forge.
- **`LinkTest`, 3 testes novos:** ida e volta do protocolo pelo relay, todas as mensagens, entradas
  inválidas descartadas. Os 35 testes anteriores continuam passando.
- **Plugin 1227 Relay:**
  - compilado contra stubs mínimos da API do Bukkit (a API real não está disponível aqui; o CI
    compila contra a spigot-api 1.8.8);
  - testado de ponta a ponta com servidor e jogadores falsos: WELCOME, repasse só no mesmo mundo,
    UUID do remetente, corpo intacto, emote, mensagens inválidas descartadas, limite de mensagens e
    estado entregue a quem entra depois.
- **Causa do inventário deslocado:** confirmada no código do Minecraft (`InventoryEffectRenderer`).

**Não testado no jogo:** nada desta versão foi rodado dentro do Minecraft.

### Para testar no jogo
38. **Centered Inventory:** tome uma poção (ou `/effect`).
    - O inventário deve ficar no centro, com os efeitos ao lado.
    - Teste também em janela pequena (deve virar ícones) e no criativo: abas, busca e botões de
      página.
    - Desligue o módulo: deve voltar ao comportamento vanilla.
39. **Crosshair Editor:**
    - cada controle deve mudar a prévia na hora;
    - compare a prévia em 1x com a mira de verdade, que deve ser idêntica;
    - teste os fundos, os presets e o "Restore default".
40. **Display:**
    - mude o tamanho da janela, aceite e reverta;
    - deixe o tempo de 12 s acabar: deve voltar sozinho;
    - entre em tela cheia num modo diferente da área de trabalho;
    - use o F11.
    - HUDs e menus devem se ajustar e o GUI Scale não deve mudar.
41. **World Lyrics:**
    - os 8 movimentos com uma música com Enhanced LRC e com uma de LRC comum;
    - perto de paredes e dentro de casa, nenhuma palavra deve aparecer dentro de bloco;
    - meça o FPS com 160 palavras.
42. **1227 Link:**
    - dois clientes 1227 num servidor Spigot 1.8.8 com o plugin: emotes, danças, halo, aura hat e
      asas com as cores de cada um, e o selo;
    - um sai e volta: o outro deve voltar a ver;
    - num servidor sem o plugin, "Connection status" deve dizer que não há relay.

## v1.22.1

Feito aqui:
- **Compilação de checagem** de todo o código (236 arquivos), com análise de fluxo: nenhum erro do
  1227.
- **Testes Java:**
  - `WsClientTest` (2, novos): handshake WebSocket, frames binários com máscara, ping/pong, close, e o
    id da sala;
  - `LinkTest` (3);
  - os anteriores continuam passando.
- **Relay online:**
  - 3 testes em Node com runtime simulado: repasse, estado para quem entra depois, LEAVE, mensagens
    inválidas, limite de 8/s, reconexão;
  - ida e volta de bytes entre Java e JavaScript conferida.
- **TLS:** as 9 raízes empacotadas carregam no Java 8.
- **Crash do World Lyrics:** causa encontrada no código (a palavra reaproveitava o próprio espaço como
  primeira letra), corrigida.

**Não testado:** nada desta versão foi rodado dentro do Minecraft, e o relay não foi publicado numa
conta Cloudflare.

### Para testar
43. **World Lyrics** com "Scatter into letters", em todos os movimentos, por alguns minutos: não pode
    crashar.
44. **Item Glow com o Weapon Models ligado:**
    - Glow, Outline e Glow + Outline;
    - o brilho deve contornar a espada 3D, inclusive durante a inspeção e o draw;
    - sem modelo 3D, deve continuar como antes.
45. **Relay online:**
    - publique o relay e coloque o endereço em dois clientes;
    - entrem no mesmo servidor (qualquer um, sem plugin);
    - emotes, cosméticos e selo devem aparecer;
    - "Connection status" deve dizer "Linked via online relay";
    - derrube a internet de um deles: ele deve reconectar sozinho.

## v1.23.0

- **Importação:**
  - os 11 modelos de teste (9 `.bbmodel`, 1 glTF, 1 FBX) são lidos pelos leitores Java;
  - cada um foi desenhado num rasterizador de software, em três ângulos, para conferir geometria,
    UV, rotação dos cubos, rotação de UV e a textura do FBX;
  - a ponta e a empunhadura automáticas foram conferidas nessas imagens. O Karambit precisa de
    "Swap tip and handle" (já no pack de exemplo);
  - as duas animações da Iron Sword ("preview" e "swing") foram amostradas quadro a quadro.
- **Testes Java:** `WeaponImportTest` (9, novos):
  - faces, UV e normais dos cubos do Blockbench;
  - rotação de elemento;
  - interpolação linear, step e Catmull-Rom;
  - sinais e pivô das animações do Blockbench;
  - Molang;
  - glTF embutido;
  - encaixe na mão (eixo, ponta, empunhadura, invertido);
  - espaços de animação;
  - nomes.

**Não testado:** nada desta versão foi rodado dentro do Minecraft. A parte de OpenGL, a camada de
terceira pessoa e o editor só foram conferidos por leitura e pela checagem de compilação.

### Para testar
46. Sem copiar nada: os 11 modelos do jar devem aparecer no Weapon editor em "3D models (textured)".
    - A pasta `config/1227/weapons-included` deve ser criada.
    - Um modelo seu em `config/1227/weapons` deve aparecer em "Your models" depois de Reload.
47. Escolha cada um em primeira pessoa:
    - a textura deve estar certa;
    - a mão deve ficar no cabo;
    - a Dreaded Blade deve ter textura animada;
    - golpe, inspeção (V) e sacar devem funcionar;
    - com a Iron Sword, o Idle deve balançar e o Inspect tocar a animação "swing" do arquivo.
48. Ajuste no editor:
    - tamanho, posição, ângulos, empunhadura e trocar ponta/cabo devem mudar na hora;
    - feche e abra o jogo: o ajuste continua (arquivo `.1227.json` ao lado do modelo).
49. **Terceira pessoa:** F5 e o boneco do inventário mostram o modelo. Com "Also on other players'
    swords", as espadas dos outros também.
50. **Item Glow** (Glow, Outline) com um modelo importado: o brilho segue a silhueta, sem caixas
    nas partes transparentes.
51. **Per sword type:** um modelo para a espada de diamante e outro para a de ferro; trocar de
    espada deve trocar o modelo.

## v1.24.0

- **Testes Java:** `WeaponMotionTest` (4, novos):
  - o mesmo roteiro (andar, girar a câmera, pular, cair, dois golpes) dá a mesma pose final a 30,
    144 e 240 FPS;
  - cada um dos 10 golpes mexe a arma e volta exatamente ao repouso;
  - o combo segue a ordem normal, horizontal, vertical e recomeça;
  - atacar caindo dá o golpe crítico;
  - o bloqueio suave chega na pose de bloqueio do Minecraft, e o instantâneo já começa nela.

**Não testado:** nada desta versão foi rodado dentro do Minecraft. A ligação com o renderizador da
mão foi conferida por leitura e pela checagem de compilação.

### Para testar
52. Cada Swing style com uma espada customizada: os movimentos devem ser diferentes entre si e
    voltar à mão sem salto.
    - Clicando rápido (10+ CPS), a espada não deve tremer.
53. Combo swings ligado: três golpes seguidos devem sair normal, horizontal, vertical.
54. Pule e ataque caindo: deve sair o golpe por cima.
    - Ao cair de 3 ou mais blocos, a arma deve dar um tranco e voltar.
55. Gire a câmera rápido: a arma fica um pouco para trás e volta. Com Hand sway em 0, não deve mexer.
56. Bloqueie com espada customizada: a pose deve entrar suave (Smooth) ou na hora (Instant).
    - Com o blockhit das 1.7 Animations ligado, clicar bloqueando deve mostrar o golpe.
57. Inspecione (V) e ataque no meio: a inspeção deve voltar suave para a mão.
58. Presets: cada botão deve mudar os ajustes e mostrar o aviso.
    - Repita o teste 52 com o FPS limitado a 30 e depois a 240: a velocidade deve ser a mesma.

## v1.25.0

- **Testes Java:** os 13 de armas e movimento passam. Um teste novo confere que o ângulo do corte
  acompanha o golpe.
- **Checagem de compilação:** sem erros no código novo (efeitos, formas animadas, cópias de corpo,
  mão).
- **Voxels da Pixel Disintegration:** o mapeamento da skin foi conferido à mão, face por face, num
  voxel de cada canto da cabeça.

**Não testado:** nada desta versão foi rodado dentro do Minecraft. Os efeitos novos foram
conferidos por leitura.

### Para testar
59. Item Glow com a espada normal (Weapon Models desligado):
    - o brilho deve contornar a espada, sem uma segunda espada de luz embaixo;
    - testar também com OptiFine, se você usa.
60. Hit Effect › Style, cada estilo novo com "Preview":
    - Directional e Crescent Slash, com Weapon Models ligado: um golpe horizontal deve dar corte
      horizontal; um vertical (combo) ou crítico, corte de cima para baixo; golpes alternados
      trocam o lado do corte.
61. Kill Effect › Style, cada estilo novo com "Preview" e numa luta de verdade:
    - o corpo copiado deve ter a skin do jogador;
    - o original não deve aparecer junto;
    - nada deve atravessar paredes.
62. Pixel Disintegration com "Small pieces" e "Large pieces":
    - os voxels devem ter as cores certas da skin (rosto na frente, costas atrás);
    - devem soltar de cima para baixo.
63. **Desempenho:** com Performance HUD aberto, matar alguém com Pixel Disintegration e com Black
    Hole. Não deve haver travada grande.

## v1.26.0

- **Brilho:** a causa do brilho deslocado foi confirmada no código do Forge 1.8.9.
  - `IPerspectiveAwareModel.MapWrapper.handlePerspective` usa
    `TRSRTransformation.blockCornerToCenter`, diferente de `ItemCameraTransforms.applyTransform`.
- **Rotação:** a conta da rotação de repouso foi conferida no papel:
  T·Rx·Ry·Rz·S·(S⁻¹·Rz⁻¹·Ry⁻¹·Rx⁻¹·A·Rx·Ry·Rz·S) = T·A·Rx·Ry·Rz·S.
  - A animação A acontece no quadro normal da mão, e a rotação fica só no modelo.
- **Testes Java:** os 13 de armas e movimento continuam passando. A checagem de compilação não
  acusou erros no código novo.

**Não testado:** nada desta versão foi rodado dentro do Minecraft.

### Para testar
64. **Item Glow com a espada normal** (Weapon Models desligado), parado, andando e golpeando:
    - o brilho deve ficar só em volta da espada;
    - com Outline também;
    - com Weapon Effects › blade glow, a aura deve ficar em cima da lâmina.
65. **Espada 3D, defendendo:**
    - com Smooth e com Instant, nenhum braço deve aparecer voando;
    - ao soltar, as mãos voltam.
66. **Rotação:** mude Rotation X, Y e Z (por exemplo 90°) e depois:
    - saque (troque de slot): a animação deve ser a mesma de antes, só com o modelo girado;
    - inspecione com cada estilo;
    - golpeie.
67. **Target Aura e Player Aura:** cada um dos 15 estilos novos.
    - Atrás de uma parede, a aura não deve aparecer.
