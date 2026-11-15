# 1227 Link: relay online (sem plugin no servidor)

Com este relay, jogadores do 1227 veem os emotes e os cosméticos uns dos outros em **qualquer**
servidor, sem pedir nada ao dono do servidor. É a mesma ideia do Lunar Client: os clientes falam
com um serviço próprio, não com o servidor do Minecraft.

## Publicar (grátis, uma vez, ~5 minutos)
1. Crie uma conta gratuita na Cloudflare: https://dash.cloudflare.com/sign-up. O plano gratuito de
   Workers inclui os Durable Objects com SQLite que o relay usa.
2. Instale o Node.js LTS: https://nodejs.org.
3. Nesta pasta (`server/1227-cloud-relay`), rode:
   ```
   npx wrangler login
   npx wrangler deploy
   ```
4. O wrangler mostra o endereço, por exemplo `https://1227-link.SEU-NOME.workers.dev`.
5. No jogo, abra **Cosmetics › 1227 Link › Relay address** e coloque o mesmo endereço com `wss://`:
   `wss://1227-link.SEU-NOME.workers.dev`.
6. Passe esse endereço para os seus amigos. **Todos precisam usar o mesmo relay** para se verem.

Para testar se está no ar, abra `https://1227-link.SEU-NOME.workers.dev/health` no navegador: deve
aparecer "1227 Link relay OK".

## Como funciona
- **Salas:** uma por servidor de Minecraft. O id da sala é um hash SHA-256 do endereço do servidor,
  então o relay nunca sabe em qual servidor você está.
- **Identidade:** antes de conectar, o 1227 avisa a Mojang com um id aleatório, a mesma chamada que o
  jogo faz ao entrar num servidor. O seu token só vai para a Mojang. O relay pergunta à Mojang se
  esse jogador fez isso mesmo (`hasJoined`), e assim ninguém aparece com o nome de outra pessoa.
- **Dados:** o relay recebe só o seu UUID, o seu nome e as mensagens cosméticas (cosméticos
  compartilhados, emotes). Nada do jogo em si.
- **Proteção:** o relay limita cada jogador a 8 mensagens por segundo, aceita no máximo 256 bytes
  por mensagem e confere o formato. Quando a mesma conta reconecta, a conexão antiga é fechada.
- **Custo:** salas paradas não custam nada (WebSocket Hibernation). Um grupo de amigos fica bem
  dentro dos limites diários do plano gratuito.

## Opções (`wrangler.toml`, seção `[vars]`)
- `RELAY_NAME`: o nome que aparece no "Connection status".
- `ALLOW_UNVERIFIED`: com `"true"`, aceita também jogadores que a Mojang não confirmou (contas
  offline, ou a Mojang limitando pedidos). O padrão `"false"` é mais seguro.

## Testes
`npm test` (Node 18+) roda a lógica da sala num runtime simulado, sem precisar de conta:
- repasse;
- estado para quem entra depois;
- saída;
- mensagens inválidas;
- limite de mensagens;
- reconexão.
