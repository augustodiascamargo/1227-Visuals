# 1227 Floating Lyrics, Now Playing e Visualizer

## Usar
1. Ative **Floating Lyrics** em Modules › Music (ou Settings › Audio).
2. Toque uma música:
   - no player do 1227 (`config/1227-music`, `.ogg` ou `.wav`; tecla M);
   - no Spotify;
   - no YouTube num navegador.
3. As letras aparecem na sua frente.
4. A tela **Lyrics** (Settings › Audio, ou o botão "Lyrics" no player) mostra:
   - de onde vieram as letras e a linha atual;
   - os resultados para escolher;
   - o ajuste de sincronia e o editor.

## De onde vêm as letras
Ordem de busca:
1. **Arquivo `.lrc` com o mesmo nome da música**, na mesma pasta. Exemplo:
   `Musicas/Minha Musica.ogg` + `Musicas/Minha Musica.lrc`.
2. **`config/1227-lyrics/Artista - Título.lrc`** (ou `Título.lrc`), para músicas do Spotify e
   do YouTube.
3. **Cache** em `config/1227/lyrics-cache`. Ele guarda:
   - letras já baixadas;
   - a sua escolha manual;
   - o ajuste de sincronia de cada música;
   - os "não encontrado", por 3 dias.
4. **LRCLIB** (lrclib.net), se "Search lyrics online" estiver ligado (padrão: ligado).
   - **O que é enviado:** título, artista, álbum e duração.
   - **Como:** uma requisição por vez, no máximo uma a cada 1,5 s, com User-Agent
     `1227/<versão>`.
   - **Limite de uso (429):** respeita o tempo pedido pelo servidor.
   - **Resultados:** só aceita sozinho um resultado que bate com título, artista e duração.
     Senão, mostra a lista para você escolher.

O 1227 não traz nenhuma letra dentro do jar. As letras do LRCLIB são dados da comunidade
publicados pelo próprio serviço; a responsabilidade pelo uso é de quem ouve, como em qualquer
player.

Formatos LRC aceitos:
- `[mm:ss.xx]` e `[mm:ss.xxx]`, várias marcas por linha;
- `[offset:+ms]` (positivo = letras mais cedo), `[ar:]`, `[ti:]`, `[al:]`;
- Enhanced LRC (`<mm:ss.xx>palavra`);
- UTF-8 com ou sem BOM; arquivos antigos em Windows-1252 também.

## Sincronia
- **Player do 1227:** exata (posição que a placa de som já tocou). Pausa, retomada, avançar e
  voltar mudam a linha na hora.
- **Spotify e navegadores (Windows 10/11):** a "ponte" lê a linha do tempo dos controles de mídia
  do Windows, com precisão de cerca de 0,1 s.
  - Liga em Settings › Audio › "Read Spotify / YouTube from Windows media controls".
  - O status aparece em "Windows media link".
  - Ela roda um script PowerShell que vai no jar: só lê, não usa rede e fecha junto com o jogo.
- **Sem linha do tempo** (sem a ponte, ou um player que não publica a posição): as letras não
  fingem estar sincronizadas. Use "Song starts now" na tela Lyrics no começo da música (relógio
  manual), e "Pause clock".
- **Ajustes:**
  - "Sync offset", nas opções do módulo, vale para todas as músicas;
  - "-0.25 s / +0.25 s", na tela Lyrics, é guardado para aquela música.

## Editor
- **Espaço:** marca a linha selecionada no tempo atual e passa para a próxima. Toque junto com a
  música para cronometrar tudo.
- **[ e ]:** movem a linha selecionada 0,1 s.
- **Também dá para:** editar o texto, inserir e apagar linhas, "Shift all", e "Test from here"
  (volta 2 s antes da linha).
- **Save .lrc:** grava ao lado da música, ou em `config/1227-lyrics` para Spotify e YouTube.

## Aparência
- **Modos:**
  - World Floating: flutua à sua frente e segue com suavidade;
  - Player Follow: preso à câmera;
  - World Anchor: fixo num lugar ("Anchor lyrics here");
  - Screen Overlay: 2D.
- **Presets:** Soft, Minimal, Neon, Cinematic, Glass, Aurora, Retro, Monochrome. Cada um muda
  fonte, cores, glow, contorno, fundo e animação. Depois dá para mexer em tudo.
- **Animações:** Rise, Fade, Drop, Slide, Typewriter, Word by Word, Karaoke e Smooth Flow.
- **Destaque da palavra:** palavra atual ou preenchimento de karaokê.
- **Pausas instrumentais:** a contagem "• • •" aparece nelas.
- **Paredes:**
  - "Stay in front of walls" aproxima o texto quando há uma parede no caminho;
  - "Hidden behind blocks" faz o texto respeitar a profundidade.

## Now Playing
Mostra:
- capa (quadrada, redonda ou vinil girando);
- título, artista e álbum;
- onde toca (1227, Spotify, Chrome...);
- progresso, tempos e estado.

Com o chat aberto, os botões funcionam com o mouse, e a rodinha muda o volume do player do 1227.
A posição pode ser um canto, o centro, ou o HUD editor.

## Audio Visualizer
- **Estilos:** Bars, Wave, Mirror, Dots, Ring, Circular Wave, Spectrum e Particles.
- **Com o player do 1227:** usa as amostras reais do que você ouve (FFT de 2048 pontos, 64
  bandas em escala logarítmica).
- **Spotify / YouTube:** dá para usar um dispositivo de captura como "Mixagem estéreo".
  - Precisa ser ativado no Windows: Som › Gravação › mostrar dispositivos desativados.
  - No 1227: "Use a capture device", "I allow..." e "Find capture devices".
  - Microfones nunca aparecem na lista.
- **Sem dados:** mostra "simulated" (com etiqueta) ou some.

## Music Atmosphere e Cinematic Mode
- **Music Atmosphere:** anéis no chão a cada batida, luzes subindo com a energia da música e um
  halo opcional. Só com áudio real.
- **Cinematic Mode** (Camera):
  - esconde hotbar, vida, mira, chat, mão e o HUD do 1227, mas mantém letras, Now Playing e
    visualizador;
  - tem câmera suave opcional;
  - "Photo mode" esconde tudo.
