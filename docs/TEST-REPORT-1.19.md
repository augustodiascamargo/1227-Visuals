# Relatório de testes, v1.19.0

## Feito aqui (sem o jogo)

- Compilação local de todos os arquivos: nenhum erro do próprio código. Os erros que sobram são
  só das classes do Minecraft/Forge, que não existem fora do build do Gradle.
- Nomes das APIs usadas conferidos nos fontes do Minecraft 1.8.9 (MCP) e do Forge 1.8.9:
  `WorldProvider.setWeatherRenderer`, `World.getPrecipitationHeight`,
  `BiomeGenBase.canRain/getEnableSnow/getFloatTemperature` e `Material.blocksMovement`.
- Formato das poças: o campo de ruído gerado em imagem cobre cerca de 9 %, 16 % e 30 % do chão
  com "Puddle amount" em 0.15, 0.25 e 0.40, com poças de 2 a 6 blocos.

## Para testar no jogo

1. **Instalador:** baixe o artefato do GitHub, deixe `Instalar 1227.bat` ao lado do jar e dê
   dois cliques. A janela do instalador deve abrir mesmo sem Java associado ao `.jar`.
2. **Wet World:** ative o módulo e clique em "Preview rain (20 s)". Confira:
   - os riscos de chuva;
   - o chão mais escuro;
   - as poças com reflexo do céu;
   - as ondinhas e os respingos.
   Teste também com chuva real e com World → Weather em Rain.
3. **Chuva em bioma frio:** num bioma de neve, a neve deve continuar a do Minecraft.
4. **Item Glow:** com estilo Glow e com Outline, e com Animations 1.7 ligado e desligado, o
   brilho deve ficar em volta da espada, nunca por cima dela.
5. **Weapon Effects:** "Blade glow" deve aparecer como uma aura em volta da lâmina, sem bolha
   branca na mão.
6. **Aim Helper:** ao atacar um mob, confira o anel grosso subindo e descendo, o brilho no corpo
   e a faixa que acompanha o anel. Atrás de um bloco, nada deve aparecer.
7. **Weapon Models:**
   - troque de slot para a espada e veja a animação de sacar;
   - aperte V para inspecionar, nos 4 estilos;
   - bata várias vezes: os golpes alternam de lado;
   - corra para ver a pose de corrida.
   Se algum giro sair invertido, diga qual.
