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
