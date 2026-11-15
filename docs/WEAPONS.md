# Modelos de arma 3D (v1.23.0)

O **Weapon Models** desenha espadas com modelos 3D:
- os 18 modelos feitos em código;
- 11 modelos 3D com textura que **vêm no jar**: Ashen Reaver, Dreaded Blade, Gravewake Blade,
  Iron Sword, Karambit, M48 Kukri, Monkey Sword, Obsidian Energy Sword, PS1 Butterfly Knife,
  Sculk Greatsword e Sword of Daylight;
- os seus, da pasta **`.minecraft/config/1227/weapons`**, com textura e animações próprias.

Os modelos do jar são extraídos na primeira vez para `config/1227/weapons-included`, para que os
ajustes deles possam ser salvos ao lado. A cada versão nova os arquivos de modelo são renovados, mas
os seus ajustes (`.1227.json`) são mantidos. Os créditos estão em `THIRD_PARTY_LICENSES.md`.
É só visual e só na sua tela: alcance, tempo de ataque e hitbox continuam os do Minecraft.

## Formatos aceitos
| Formato | O que é lido |
|---|---|
| `.bbmodel` (Blockbench: Java, Generic/"free", Bedrock) | cubos (inflate, rotação em qualquer eixo, rotação de UV, "rescale" do Java), malhas, grupos com pivô, texturas embutidas ou ao lado do arquivo, texturas animadas (faixa vertical), animações por keyframe (linear, Catmull-Rom, step, bezier; números ou Molang simples) |
| `.gltf` / `.glb` | nós (TRS ou matriz), malhas com UV e normais, textura e cor base, materiais emissivos e transparentes, animações de nós |
| `.fbx` (binário 7.x) | hierarquia com transformação completa, malhas, UV, normais, cor ou textura de cada material (embutida ou ao lado / pasta `textures`). Animações de FBX não são lidas: exporte em glTF para manter |
| `.obj` + `.mtl` | malhas, UV, normais, cor `Kd`, textura `map_Kd` |

Subpastas funcionam (até 3 níveis). Deixe a textura junto do modelo.

## Como usar
1. Coloque os arquivos na pasta. No jogo: **Weapon Models › Weapon editor › Weapons folder › Open**.
2. Clique em **Reload**. Os modelos aparecem em **Your models**.
3. Escolha um. Ele vale para todas as espadas, ou só para um tipo em **Per sword type**
   (madeira, pedra, ferro, ouro, diamante).

## Como o modelo fica na mão
Sem configurar nada, o 1227 olha a forma do modelo e decide:
- **Eixo da lâmina:** o eixo principal da geometria, alinhado aos eixos do modelo quando está perto.
- **Ponta:** o lado de cima (+Y). Se o modelo estiver deitado, a ponta é o lado mais longe da origem.
- **Empunhadura:** o trecho estreito e de largura constante abaixo da guarda. Pomo e anéis não
  contam. Cabos longos (machados, lanças) são segurados perto do fim.
- **Tamanho:** o mesmo comprimento das espadas do 1227. Modelos de item do Java usam a escala de
  primeira pessoa do próprio arquivo, limitada entre 0,8x e 1,2x.

Partes de efeito (grupos ou cubos chamados glow, fx, emissive, neon, luminous; `light_emission`; ou
textura em modo emissive/additive) não entram nesse cálculo e brilham no escuro.

Para ajustar, use o editor em **Your models › (o modelo)**:
- tamanho, posição (ao longo, através, profundidade) e ângulos (twist, tilt, roll);
- ponto de empunhadura;
- trocar ponta e cabo, virar o fio;
- partes brilhantes, terceira pessoa e estilo de golpe;
- uma animação por espaço (Idle, Swing, Inspect, Draw), com botões para tocar cada uma.

Tudo fica salvo em `<arquivo>.1227.json`, ao lado do modelo. Para voltar ao automático, apague esse
arquivo ou use **Reset placement**.

## Animações
- **Idle** fica em loop. **Swing**, **Inspect** e **Draw** tocam uma vez quando você ataca, aperta
  a tecla de inspeção ou troca para a espada.
- Elas entram e saem misturadas com o Idle, sem pulo.
- **Auto** escolhe pelo nome da animação:
  - Idle: idle, hold, rest, ou um "preview" curto em loop;
  - Swing: swing, attack, slash, strike, hit ou stab, com até 1,2 s;
  - Inspect: inspect, showcase, twirl ou spin, ou um swing mais longo;
  - Draw: draw, equip, unsheath ou pull.
- Sem animação num espaço, o 1227 usa os próprios movimentos (Twirl, Toss, Spin, Flip e os estilos
  de golpe).
- As animações mexem só no modelo. O golpe de verdade continua o do Minecraft.

## Terceira pessoa
Ligado por padrão (**Show in third person**): a sua espada aparece com o modelo em F5 e no boneco do
inventário. **Also on other players' swords** mostra o seu modelo nas espadas dos outros, só na sua
tela. Os modelos internos também aparecem em terceira pessoa.

## Licenças
Os modelos que você coloca na pasta ficam no seu computador; nada deles vai para o jar. Antes de **publicar** um modelo
(num pack, num repositório ou num vídeo), confira a licença dele. Muitos modelos gratuitos são
CC BY e pedem crédito ao autor. Personagens e itens de filmes ou séries são marcas de terceiros.
O campo `credit` do arquivo aparece no editor quando existe.
