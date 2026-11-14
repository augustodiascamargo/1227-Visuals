# 1227 Link: emotes e cosméticos entre jogadores do 1227

## O que é
Quem usa o 1227 vê as danças, os emotes e os cosméticos (Halo, Aura Hat, Wings) de outros
jogadores do 1227, e eles veem os seus. Tudo é só visual.

## A limitação (e por que existe um plugin)
Um mod instalado só no cliente não consegue fazer o servidor repassar dados entre jogadores. Por
isso o 1227 Link precisa do **1227 Relay**, um plugin pequeno para servidores Spigot/Paper 1.8.8+.
- O código está em `server/1227-relay`.
- O GitHub Actions compila o plugin no job `relay-plugin` e entrega o artefato
  `1227-relay-plugin`.
- Para instalar, é só colocar o `.jar` na pasta `plugins` do servidor.

**Em servidores sem o plugin:**
- o cliente envia só o registro padrão de canal de plugin (`REGISTER 1227|fx`), o mesmo que todo
  cliente com mods envia, e que esses servidores ignoram;
- nada mais é enviado, nunca;
- o resto do 1227 funciona igual.

## Protocolo (versão 1, canal `1227|fx`)
Toda mensagem tem o formato `[0x27][1][tipo][corpo]` e no máximo 256 bytes.

| Tipo | Direção | Conteúdo |
|---|---|---|
| HELLO (1) | cliente → servidor | versão do mod, capacidades |
| STATE (2) | cliente → servidor | cosméticos compartilhados (bits) + 6 cores + emote atual + tempo dele |
| EMOTE (3) | cliente → servidor | emote começou |
| STOP (4) | cliente → servidor | emote terminou |
| WELCOME (0x41) | servidor → cliente | o relay existe: limite de mensagens, nome |
| RELAY (0x42) | servidor → cliente | mensagem de outro jogador: UUID + tipo + corpo original |
| LEAVE (0x43) | servidor → cliente | jogador saiu ou trocou de mundo |

**Fluxo:**
1. O cliente registra o canal.
2. O plugin responde WELCOME e envia o último estado dos outros jogadores do mesmo mundo.
3. Só depois disso o cliente começa a enviar.

**Envio:** o estado vai no máximo a cada 250 ms e de novo a cada 30 s. Assim, quem entra depois
ou um relay reiniciado recebem o estado mesmo assim.

**Reconexão e troca de servidor:**
- ao trocar de mundo (inclusive quando um proxy troca você de servidor), o cliente esquece os
  jogadores e registra o canal de novo;
- se estava conectado, manda HELLO e espera WELCOME por 5 s.

**Segurança:**
- O plugin confere o cabeçalho, o tamanho, o tipo e o limite de mensagens (8 por segundo por
  jogador; o excesso é descartado e ninguém é expulso).
- O plugin troca o remetente pelo UUID real do jogador, então ninguém se passa por outro.
- O cliente confere tudo de novo:
  - emotes dentro da lista;
  - bits conhecidos;
  - tempos limitados;
  - texto com tamanho máximo;
  - mensagens de jogadores fora do seu mundo são ignoradas.
- Não existe dado de posição, input ou combate no protocolo.

## Opções (Cosmetics › 1227 Link)
- Compartilhar meus emotes / meus cosméticos.
- Ver emotes / cosméticos dos outros, com chaves separadas para halos, aura hats e asas.
- Máximo de jogadores com cosméticos compartilhados (os mais próximos primeiro).
- Selo "1227" acima de outros jogadores do 1227.
- "Connection status".

O cosmético de outro jogador aparece com o **formato e o estilo das suas configurações** e com as
**cores dele**. O emote de outro jogador termina pelas mesmas regras do seu: se ele andar, se
tomar dano, ou depois de ~3 s nos emotes que não repetem.

## O que ainda não é compartilhado
- **Auras, efeitos de entrada e efeitos de eliminação.** Esses efeitos vêm de muitos módulos com
  dezenas de estilos, e passá-los exige um formato de "estilo" por módulo. O protocolo já tem
  versão e bits livres para isso.
- **O estilo de cada cosmético:** só as cores vão.
- **Servidores Forge e proxies BungeeCord/Velocity:** o relay é para Spigot/Paper. Atrás de um
  proxy, cada servidor de jogo precisa do plugin.
