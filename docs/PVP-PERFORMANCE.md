# 1227 v1.21: PvP Performance, Latency Monitor e Hit Registration Monitor

Documentação técnica da versão 1.21.0. Tudo aqui é do lado do cliente e só observa ou muda o que
o seu jogo desenha. Nenhum pacote é atrasado, descartado, duplicado, reordenado, alterado ou criado.
Nada muda alcance, hitbox, velocidade, timer, ping ou a lógica de combate do servidor.

## 1. Auditoria (Fase 1)

| Sistema | O que foi encontrado | Correção na 1.21 |
|---|---|---|
| **Background FPS** (`PerfSystem`) | `Thread.sleep` dentro do evento de render, até 200 ms por frame, na thread principal. | Agora só muda o valor que o limitador do próprio Minecraft lê (`Display.sync`, depois da troca de buffer). Nada dorme nos nossos eventos. Ao voltar o foco, o próximo frame já usa o seu limite. A tela Video Settings sempre mostra e salva o seu valor. |
| **Occlusion** | O limite de "60 testes por frame" era contado por fatias de 8 ms, não por frame. A 30 FPS isso permitia ~240 testes (720 raios) por frame. Cada consulta criava `Long`, `Vec3` e `Entry`. O cache não era limpo ao trocar de mundo (ids reaproveitados). | O contador é zerado no início de cada frame real, com limite de 40 testes (120 raios). A tabela de longs não aloca nada num acerto de cache. O cache é limpo ao trocar de mundo e tem limite de 4096 entradas. |
| **Limitador de partículas** (`PerfSystem`) | Percorria todas as partículas do Minecraft a cada tick. | A passada de distância roda a cada 5 ticks. A passada de excesso só roda quando há excesso. A contagem fica disponível para o painel. |
| **BlockEntityCulling** | Embrulhava renderizadores de qualquer mod. Um mod que busca o próprio renderizador e faz cast para a classe dele quebraria. Ao desligar, os embrulhos ficavam. | Por padrão só os renderizadores do Minecraft são embrulhados ("Also cull other mods' block entities" para os outros). Ao voltar para os valores vanilla, os originais são recolocados. O teste de frustum já é feito pelo Forge antes. |
| **CombatTracker** | Considerava hit qualquer aumento de `hurtTime` em até 700 ms. Não separava dano de outro jogador, fogo ou flecha. Não tinha níveis de confiança. | Reescrito como Hit Registration Monitor (seção 4). |
| **ReachHud** | Mostrava a distância da tentativa como "distância do hit". | Separa a tentativa e o momento do feedback, com a classificação. |
| **WTapHud** | Deduzia o estado de sprint enviado a partir do estado local. | Lê os pacotes de sprint que o jogo realmente enviou, na ordem real com o ataque. O que é inferido está marcado. |
| **Benchmark** | Buffer fixo de 65 536 frames (perdia frames acima de ~2000 FPS em 30 s). Não tinha P50/P95/P99 nem gráfico. Aquecimento de 2 s mesmo depois de mudar a distância de renderização. O A/B restaurava só o config do 1227, então as opções de vídeo do Minecraft ficavam com os valores do preset B. Não detectava que a cena mudou. | Buffer que cresce até 1M de frames, percentis, gráfico, aquecimento de 3 s (6 s depois de um preset), detecção de movimento, A/B que devolve tudo (inclusive as opções do Minecraft) e metadados (resolução, VSync, limite, módulos visuais, partículas separadas). |
| **ProjectileTrails** | Criava um `HashMap` e um `double[]` por projétil a cada tick. | Reaproveita os arrays e limpa os projéteis que sumiram. |
| **HUDs** | Cada painel "Glass/Liquid Glass" criava um `ScaledResolution` por frame. Não havia como saber qual HUD pesava. | A escala é lida uma vez por frame. Cada HUD mede o próprio custo (ms por frame), que aparece no painel e no Analyzer. |

Não mexi em parâmetros da JVM nem do GC. Pooling de objetos só onde já existia (partículas).

## 2. FPS, frame time, input lag, latência e hitreg

Cada número do 1227 diz de onde vem:
- **MEASURED (verde):** medido aqui com `System.nanoTime()`.
- **ESTIMATE (âmbar):** calculado a partir de medições.
- **SERVER (azul):** número enviado pelo servidor.
- **N/A (cinza):** não há medição confiável.

O 1227 não consegue fazer o servidor aceitar um ataque, nem diminuir o ping físico.

## 3. Latency Monitor

| Métrica | Fonte | Observação |
|---|---|---|
| Ping da tab list | SERVER | Vanilla envia a cada ~30 s, já com média. A idade do valor aparece. |
| Round trip do status ping | MEASURED | Conexão separada e curta para o mesmo endereço, fazendo o que a lista de servidores faz. Mede só o trecho ping→pong. A cada 10 s (de 3 a 60). Desligado em singleplayer. Liga com o módulo "Latency Monitor". |
| Melhor, pior, média, P95 | MEASURED | Sobre as últimas 400 amostras. |
| Jitter | MEASURED | RFC 3550, sobre os round trips. |
| Jitter do keep-alive | MEASURED | Só quando os ids de keep-alive do servidor são o relógio dele em ms (vanilla e Spigot). Muitos proxies usam ids aleatórios: aí fica N/A. |
| TPS do servidor | ESTIMATE | Ritmo do relógio do mundo nos pacotes de tempo, contra o nosso relógio (10 pacotes). |
| Travadas (stalls) | MEASURED | 2 s ou mais sem nenhum pacote do servidor. |
| Correções de posição | MEASURED | Pacotes S08. |
| Picos, desconexões, tempo conectado, pacotes | MEASURED | |
| **Perda de pacotes** | **N/A** | O jogo usa TCP, que reenvia sozinho: a perda chega ao jogo só como atraso. Não há como contar. |

Gráfico: 30 s, 60 s ou 5 min, com o status ping, o ping da tab e marcas de pico, travada,
desconexão e timeout do status ping. O HUD "Latency Graph" mostra a versão pequena.

Como os pacotes são observados (`NetObserver`): um handler entra no pipeline da conexão, antes do
handler do Minecraft. Ele anota a hora e, para alguns tipos, põe (hora, pacote) numa fila que a
thread principal lê. Depois passa o mesmo pacote adiante, na hora, dentro de um `finally`.

## 4. Hit Registration Monitor

**Tentativa:** o evento de ataque do Forge, mais a hora em que o pacote de ataque foi escrito na
conexão.

**Evidências:**
- **Sinal de dano:** status "hurt" do servidor para o alvo, dentro da janela. A janela é 1,5× o
  round trip + 120 ms, entre 150 e 1000 ms; 600 ms sem medida.
- **Queda de vida:** só quando o servidor envia a vida real.
- **Knockback:** pacote de velocidade, ou o alvo acelerando para longe de você.
- **Crítico:** animação de crítico do servidor depois de uma tentativa que cumpria as condições.

**O que reduz a confiança:**
- outro jogador batendo perto do alvo na mesma janela;
- projétil perto do alvo;
- fogo, lava, veneno ou wither no alvo;
- sinal que chegou mais rápido que qualquer round trip medido (não pode ser resposta ao seu ataque).

**Níveis:**

| Nível | Quando |
|---|---|
| Strong | Sinal de dano + outro sinal, sem outra causa possível. |
| Moderate | Só o sinal de dano, sem outra causa; ou queda de vida sem sinal (hit mais forte durante a imunidade). |
| Weak | Houve dano, mas outra coisa pode ter causado; ou o monitor de rede estava fora e só a animação local foi vista. |
| No confirmation | A janela passou sem sinal de dano. **Não é "rejeitado".** |
| Unknown | Alvo imune pelo hit anterior (10 ticks), sumiu, ou faltam dados. |

**Duas métricas separadas:**
- **Estimated Hit Feedback Rate** = (Strong + Moderate) / (Strong + Moderate + No confirmation).
  Weak e Unknown ficam de fora.
- **Authoritative Hit Registration:** sempre **indisponível**. Só um plugin no servidor saberia.

**Outras ferramentas:**
- **Combat Timeline:** tentativa, envio, dano, crítico, knockback, queda de vida, classificação,
  "você foi atingido", combo, sprint enviado e kill, cada um com a sua fonte.
- **Hit Timing Analyzer:** histograma dos intervalos entre tentativas, médias e variação. Não
  sugere taxa de cliques.
- **W-Tap Trainer:** hora do stop/start sprint enviados e do ataque, nota, histórico e consistência.
- **Reach:** olhos até a hitbox (com a borda de 0,1) na tentativa e no feedback.

O `HitEvent` usado pelos efeitos sai assim que o sinal de dano é ligado a uma tentativa sua.

## 5. Input Monitor

- **Wait for tick:** do fim do frame em que a janela leu o clique até o tick que o processou.
  No 1.8.9 os ticks são 20 por segundo, então essa espera vai de 0 a 50 ms, qualquer que seja o FPS.
- **Input to frame (estimativa):** do mesmo fim de frame até o fim do primeiro frame desenhado
  depois do tick. **Não é input-to-photon:** ficam de fora o tempo no mouse, USB e sistema antes da
  janela ler o clique, e o tempo da GPU e do monitor.

Investigado e **não feito:** ler o mouse de novo antes de cada frame. No LWJGL 2 cada leitura
substitui o movimento da anterior, e o movimento ainda não usado se perderia. Não confirmei isso no
código do LWJGL (ele não está disponível aqui), então não arrisquei.

## 6. Modos e presets

- **Competitive Mode:**
  - desliga os módulos visuais que você escolher;
  - liga a PvP Visual Optimization;
  - Quality vai para Medium e o limite de partículas do Minecraft para 1500.
  - Mostra a lista antes, na tela "Preview".
  - Ao desligar, devolve tudo. Os valores antigos ficam em `config/1227/undo-competitive.json`.
  - Nunca mexe em: jogadores e a distância deles, nomes, mira, sons/marcador/cor de hit, números de
    dano, Target HUD.
- **Low Latency Mode:**
  - limite de FPS: o seu, ilimitado, refresh do monitor, 2× refresh, custom, ou "stable frame
    time" (medido em 10 s: o FPS que o PC mantém 95% do tempo, menos 5%);
  - VSync: o seu, ligado ou desligado;
  - opcional `glFinish` ("Limit frames queued by the GPU driver"), desligado por padrão. Ele pode
    reduzir atraso quando a GPU é o gargalo, mas baixa o FPS: meça com o A/B.
  - Limites acima de 259 usam o mesmo limitador LWJGL no início do frame.
- **PvP Visual Optimization:**
  - Dynamic Quality: reduz cosméticos em passos de 5% quando o frame passa 10% do alvo, e sobe
    devagar. O alvo nunca é maior que o limite de FPS ou o VSync;
  - parte do orçamento de partículas reservada para hit, crítico e kill;
  - distância dos cosméticos;
  - quantos outros jogadores (os mais próximos) recebem auras/cosméticos e efeitos de arma;
  - máximo de rastros de projétil por tick;
  - máximo de explosões de hit por segundo.
  - Os efeitos de combate nunca são reduzidos pela Dynamic Quality.
- **Presets PvP:** Competitive, Low Latency, Maximum FPS, Low-End PvP, Balanced PvP e Visual PvP.
  - Os quatro antigos continuam.
  - Todo preset abre uma prévia com cada valor que muda (atual → novo), e cada linha pode ser
    desmarcada.
  - "Restore settings from before the last preset" devolve tudo, inclusive as opções de vídeo.
  - Nenhum preset baixa a distância dos jogadores nem usa menos de 6 chunks.

## 7. PvP Performance HUD e Analyzer

**HUD:**
- 17 itens, cada um com chave própria ("Choose items myself").
- Layouts Minimal, Compact, Advanced e Detailed. O Detailed mostra a fonte de cada valor (m, s, e).
- 8 alertas, cada um só com uma medição confiável: FPS instável, ping alto, jitter alto, pico de
  frame, timeout da rede, FPS baixo em combate, efeitos pesados e partículas demais.
- O texto é refeito 4 vezes por segundo.

**Performance Analyzer** (dashboard, aba Analyzer):
- Regras sobre os dados coletados, cada uma com os números em que se baseia.
- Quando há uma correção segura, "Review fix" mostra a lista antes de aplicar. Depois dá para
  restaurar, e qualquer aviso pode ser ignorado.
- Nada é aplicado sozinho.
- Abrir: `/1227 perf`, ou o botão "Open Performance Dashboard" em Performance, Low Latency Mode ou
  PvP Visual Optimization.

## 8. Limitações

- Não rodei nada dentro do Minecraft. O código passou pela checagem do compilador (com análise de
  fluxo) e as APIs foram conferidas nos fontes do MCP 1.8.9 e do Forge 1.8.9. O jar final sai do
  GitHub Actions.
- **Não há relatório de benchmark com números:** não dá para rodar o jogo aqui, e não vou inventar
  resultados. O benchmark do jogo guarda tudo em `config/1227/benchmarks.json` para você comparar.
- Hit registration autoritativo exige um plugin no servidor.
- O status ping é uma conexão à parte. Ele mede a rota até o servidor (ou o proxy), não os pacotes
  do jogo. Alguns servidores podem limitar status pings frequentes: aumente o intervalo ou desligue.
- O TPS é uma estimativa e o jitter de rede soma ruído.
- Não implementado, porque dependeria de mexer em muitos módulos sem medir:
  - "Maximum simultaneous animations";
  - "Target effect quality" (o Target Aura continua com a própria qualidade).
- Não há prioridade de partículas configurável por módulo, só "Combat first" ou "Equal" e a
  reserva em %.
- O monitor de rede liga ao entrar num servidor. Se outro mod remover o handler "packet_handler",
  ele se desliga sozinho (o log avisa) e o Hit Registration Monitor usa só a animação local
  (nível Weak).
