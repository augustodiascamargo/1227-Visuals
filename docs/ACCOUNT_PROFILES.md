# Contas do 1227 Launcher

## Estado desta implementação

A navegação **Contas** oferece duas modalidades visíveis:

- **Conta Microsoft:** área reservada para autenticação oficial. O fluxo ainda não está implementado nesta versão.
- **Perfil Offline:** cria, seleciona e remove perfis locais com nomes de 3 a 16 caracteres (`A-Z`, `a-z`, `0-9` e `_`).

Os perfis ficam em `accounts.json`, dentro da pasta de dados do launcher. Essa implementação não armazena credenciais, senhas ou tokens.

## Limitações importantes

O perfil offline é apenas uma identidade local armazenada pelo launcher. Ele não cria uma sessão de jogo, não altera arquivos de autenticação do Minecraft, não concede licença e não permite entrar em servidores que validam contas oficiais. Só ambientes de teste/local ou servidores configurados por seus administradores para aceitar conexões sem autenticação podem aceitar esse tipo de identidade.

O launcher também ainda não tem uma rotina própria para iniciar o Minecraft. A função existente instala/atualiza o mod em uma instância própria ou na pasta `mods` de uma instalação Forge já existente.

## O que falta para autenticação Microsoft

A implementação real exige, no mínimo:

1. Registrar um aplicativo apropriado no portal de identidade da Microsoft e configurar o identificador público do aplicativo.
2. Implementar o fluxo OAuth adequado para aplicativo desktop, com callback/retorno validado e proteção contra CSRF.
3. Trocar a autorização pelos tokens necessários nos serviços Xbox Live e XSTS.
4. Autenticar nos serviços oficiais do Minecraft e consultar o perfil Java associado.
5. Guardar tokens de atualização em armazenamento seguro do sistema operacional (por exemplo, Windows Credential Manager), nunca em JSON ou logs.
6. Tratar expiração, revogação, logout, ausência de entitlement e falhas de rede.
7. Só depois integrar a sessão autenticada ao processo de inicialização do jogo.

Não use campos de nome/UUID inventados como substitutos de autenticação oficial.
