# O que o 1227 faz além de desenhar (v1.27.0)

Conferido no código (`grep` por sockets, URLs e envio de pacotes em `src/main/java`). O 1227 não tem
conta própria, não coleta estatísticas e não envia nada para o autor do mod.

## Altera o jogo
| Módulo | Padrão | O que faz |
|---|---|---|
| Toggle Sprint | desligado | Mantém a tecla de correr pressionada (`KeyBinding.setKeyBindState`). O sprint em si segue as regras do Minecraft: fome, colisão, parar ao bater. |

Todo o resto só muda o que é desenhado ou tocado na sua máquina.

## Fala com a rede
| Recurso | Padrão | Para onde | O que envia | Quando |
|---|---|---|---|---|
| 1227 Link, canal do servidor | **ligado** | o servidor em que você entra | o nome do canal `1227|fx` (pacote REGISTER). Com isso o servidor pode saber que você usa o 1227. Estado de cosméticos e emotes só vai se o servidor tiver o plugin 1227 Relay e responder | ao entrar num servidor |
| 1227 Link, relay online | ligado, mas **sem endereço** não conecta | o endereço `wss://` que você colocar | UUID, nome, cores e quais cosméticos estão ligados, emote atual. A sala é um hash do endereço do servidor. O token da sessão vai só para a Mojang (`joinServer`), nunca para o relay | enquanto o módulo e o relay estiverem ligados |
| Latency Monitor | desligado | o servidor em que você está | um "status ping" (o mesmo da lista de servidores) a cada 3–60 s | com "Measure with status pings" |
| Floating / World Lyrics | desligado | `lrclib.net` | título e artista da música | ao tocar uma música com letras ligadas |
| Music (playlists) | desligado | `open.spotify.com/oembed`, `youtube.com/oembed` | o link da playlist que você colou | ao colar o link |
| Discord Presence | desligado | o Discord aberto no seu PC (pipe local) | "Jogando 1227Visuals" | com o módulo ligado, só no Windows |

## Como desligar
- **1227 Link:** Cosmetics › 1227 Link (desligar o módulo, ou só "Use the server's 1227 Relay plugin",
  ou só "Online relay"). "Connection status" mostra as rotas ativas.
- Os outros: desligar o módulo.

## Ponto em aberto
O 1227 Link vem ligado e manda o REGISTER do canal para qualquer servidor. Não mudei o padrão nesta
versão, porque isso muda o comportamento de quem já usa. Se preferir que venha desligado, é uma linha
em `SocialModule`.
