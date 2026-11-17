# Auditoria v1.27.0

Evidência conferida no código do projeto e no código do Minecraft 1.8.9 / Forge 11.15.1.2318.
Nada aqui foi rodado dentro do jogo por mim.

## Defeitos confirmados (corrigidos)
| # | Achado | Evidência | Severidade | Impacto | Confiança | Como reproduzir |
|---|---|---|---|---|---|---|
| 1 | Item Physics desenha blocos soltos em 2x | `PhysicsItemRender.doRender` não aplicava `scale(0.5)`; o vanilla aplica em `RenderEntityItem.doRender` para modelos `isGui3d()` | média | visual errado com o módulo ligado | alta (leitura dos dois códigos; bate com a pedra 1,5–2x vista ao vivo) | soltar pedra com o módulo desligado e ligado, lado a lado |
| 2 | Instalador arquiva as versões antigas antes de copiar a nova | `InstallerMain.installTo`: `Files.move` dos antigos vinha antes de `Files.copy` | alta | cópia falhando (disco cheio, permissão) deixava a pasta mods sem o 1227 | alta (teste `failureKeepsThePreviousVersion`) | destino bloqueado; ver teste |
| 3 | Instalador trata `1227-relay-*.jar` como versão antiga do cliente | regex `1227(-.*)?\.jar` | baixa | moveria o plugin se estivesse em mods | alta (teste) | — |
| 4 | O .bat pode abrir o jar errado | `for %%f in (1227*.jar)` pega também `1227-relay-1.0.0.jar` (fica com o último); sem achado, usa qualquer `*.jar` | média | abre o plugin de servidor ou outro jar como instalador | alta (leitura; o .bat não foi executado aqui) | pôr os dois jars na pasta |
| 5 | README e descrições desatualizados | README dizia v1.18.0; README, `mcmod.info` e `Mod1227` diziam "puramente visual / não envia pacotes", mas `SocialLink` envia REGISTER e `ToggleSprintModule` segura a tecla | média | usuário mal informado | alta | — |
| 6 | Brilho do item deslocado (1.26.0) | `HandFx` usava `applyTransform`; o item real passa por `ForgeHooksClient.handleCameraTransforms` | média | segunda espada de luz | alta no código; **falta confirmar no jogo** | Item Glow com espada normal |

## Suspeitas (não corrigidas)
| # | Suspeita | Por quê | O que falta |
|---|---|---|---|
| S1 | 1227 Link ligado por padrão revela o uso do mod ao servidor | REGISTER do canal `1227|fx` em todo servidor | decisão sua sobre o padrão (ver `docs/PRIVACY.md`) |
| S2 | Build não reproduzível | ForgeGradle 2.1 + Gradle 2.14.1 sem wrapper; dependências em repositórios antigos | não consigo rodar a build aqui (sem rede); precisa de log do Actions |
| S3 | Procedência dos 11 modelos incluídos | só o M48 Kukri tem autor no arquivo; a Sword of Daylight é design da DreamWorks | links de origem e licença de cada um, ou retirar do jar |
| S4 | Limpeza de recursos ao desligar módulos / trocar de mundo | não auditado módulo a módulo nesta versão | revisão dedicada + teste longo no jogo |

## Melhorias propostas (não feitas)
- Safe Mode depois de crash; diagnóstico exportável com dados pessoais ocultos.
- Filtros no menu: ativos, com configuração, dependem de servidor.
- Painel de estado das integrações (Link, Discord, Lyrics).
- Gradle Wrapper com checksum, se a build legada aceitar.

## Testes executados aqui
- `InstallerTest`: 4 passam.
- `WeaponImportTest` + `WeaponMotionTest`: 13 passam.
- Checagem de compilação (sem as classes do Minecraft): sem erros no código alterado.
