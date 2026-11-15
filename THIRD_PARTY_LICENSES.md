# Recursos de terceiros no 1227

| Recurso | Origem | Licença / uso | Alterações |
|---|---|---|---|
| Certificados raiz públicos (`assets/m1227/certs/extra-roots.pem`): ISRG Root X1/X2, GTS Root R1/R3/R4, SSL.com Root RSA/ECC, DigiCert Global Root G2, Amazon Root CA 1 | Lista de CAs da Mozilla (pacote `ca-certificates`), obtidos em 2026-11-15 | Certificados públicos, feitos para serem distribuídos e usados para verificar TLS | Nenhuma: só concatenados num arquivo |

## Modelos de arma incluídos (`assets/m1227/weapons`)

Modelos 3D gratuitos e liberados para uso, enviados pelo dono do projeto. Vão no jar sem alteração
de geometria, textura ou animação. O 1227 só acrescenta os arquivos `.1227.json` com o encaixe na
mão. O crédito vem de dentro do arquivo quando o autor o colocou lá.

| Modelo | Arquivo | Autor / crédito | Licença | Alterações |
|---|---|---|---|---|
| Ashen Reaver | `ashen-reaver.bbmodel` | não informado no arquivo | gratuito, uso liberado | nenhuma |
| Dreaded Blade (textura animada) | `dreaded-blade.bbmodel` | não informado (namespace `origin_valley`) | gratuito, uso liberado | nenhuma |
| Gravewake Blade | `gravewake-blade.bbmodel` | não informado | gratuito, uso liberado | nenhuma |
| Iron Sword (com animações) | `iron-sword.bbmodel` | não informado | gratuito, uso liberado | nenhuma |
| M48 Kukri | `m48-kukri.bbmodel` | "Made in France for the NOE project by Dalviin" | gratuito, uso liberado | nenhuma |
| Monkey Sword | `monkey-sword.bbmodel` | não informado | gratuito, uso liberado | nenhuma |
| Obsidian Energy Sword | `obsidian-energy-sword.bbmodel` | não informado | gratuito, uso liberado | nenhuma |
| Sculk Greatsword | `sculk-greatsword.bbmodel` | não informado | gratuito, uso liberado | nenhuma |
| Sword of Daylight | `trollhunters-sword-of-daylight.bbmodel` | não informado; o design é da série Trollhunters (DreamWorks) | gratuito, uso liberado (modelo) | nenhuma |
| Karambit | `karambit/karambit.gltf` | não informado (exportado do Blockbench) | gratuito, uso liberado | nenhuma (`.1227.json`: ponta e cabo trocados) |
| PS1 Butterfly Knife | `butterfly-knife/ps1-butterfly-knife.fbx` + `BKTextureMap.png` | não informado | gratuito, uso liberado | nenhuma |

Quando souber a página de origem e o autor de cada um, acrescente aqui: licenças como CC BY pedem
o crédito.

Nenhum som ou animação de terceiros além desses está incluído. Os fundos do Crosshair Editor usam
as texturas do próprio Minecraft instalado no seu jogo, lidas em tempo real e não copiadas para o
mod. Os modelos que você coloca em `config/1227/weapons` são lidos do seu computador e não vão
para o jar (ver `docs/WEAPONS.md`).
