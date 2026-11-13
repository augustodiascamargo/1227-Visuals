# Publicar uma versão

1. Atualize `version` em `build.gradle` e `VERSION` em `src/main/java/net/m1227/Mod1227.java`, e escreva o changelog no `README.md`.
2. Crie e envie a tag: `git tag v1.15.0 && git push origin v1.15.0`.
3. O workflow `release.yml` compila o mod, confere que a tag bate com o `build.gradle` e cria a Release no GitHub com `1227-X.Y.Z.jar` e o arquivo `.sha256`.

Não existe servidor, API ou site próprio: o GitHub Releases é o único ponto de distribuição.
