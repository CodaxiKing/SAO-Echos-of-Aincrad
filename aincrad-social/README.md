# AincradSocial

Amigos, party e mensagens no estilo SAO, para Paper 1.21.11. Funciona sozinho pelos comandos e
pelo chat; com o **AincradUI** instalado, tudo aparece no menu SAO e os membros da party ganham
barras de vida embaixo da barra do jogador, como no anime.

## Instalação

1. Compile o AincradUI antes (este projeto usa a API dele): `./gradlew build` aqui já compila os dois
   juntos (build composto com `../aincrad-ui`).
2. `build/libs/AincradSocial-<versão>.jar` em `plugins/`, junto com o AincradUI.
3. Ligue o servidor: são criados `config.yml`, `messages.yml` e, com o uso, `dados.yml`.

## No menu SAO (com o AincradUI)

| Onde | O que faz |
|---|---|
| Party → **Party** | Membros com cabeça e vida. Clicar num membro: expulsar, tornar líder (se você for o líder), mandar mensagem, adicionar amigo. Botões: Convidar (jogadores online, os mais perto primeiro), Convites (aceitar ou recusar), Sair da party |
| Party → **Amigos** | Amigos (online primeiro). Clicar: convidar para a party, mandar mensagem, remover. Botões: Adicionar (jogadores por perto ou "Digitar nome..."), Pedidos (aceitar ou recusar) |
| Mensagens → **Caixa** | Mensagens recebidas, as novas marcadas. Abrir mostra a mensagem com Responder e Apagar |
| Mensagens → **Escrever** | Escolhe um amigo e digita a mensagem no chat |

O menu SAO não tem teclado: para escrever uma mensagem ou digitar um nome, o menu fecha e a
próxima linha que o jogador digitar no chat é usada (só ele vê). `cancelar` desiste.

## Comandos

| Comando | O que faz |
|---|---|
| `/amigo [lista]` | Lista os amigos (verde = online) |
| `/amigo adicionar\|aceitar\|recusar\|remover <jogador>` | Pedidos e amizades |
| `/amigo pedidos` | Pedidos recebidos |
| `/party [lista]` | Membros da party (★ = líder) |
| `/party convidar\|aceitar\|recusar <jogador>` | Convites (expiram em `party.convite_expira_segundos`) |
| `/party expulsar\|lider <jogador>` | Só o líder |
| `/party sair`, `/party desfazer` | Sair; desfazer (só o líder) |
| `/party chat <mensagem>` ou `@mensagem` no chat | Chat só da party |
| `/mensagem <jogador> [texto]` | Manda para a caixa de entrada (sem texto, pede no chat) |
| `/caixa`, `/caixa ler <n>`, `/caixa apagar <n>` | Caixa de entrada (com o AincradUI, `/caixa` abre no menu) |

Permissão: `aincrad.social.use` (todos). Pedidos e convites também chegam no chat com botões
clicáveis **[Aceitar] [Recusar]**.

## Regras

- **Amigos**: pedido e aceite; se os dois pedirem um ao outro, viram amigos na hora. Limite em
  `amigos.maximo`. Amigos são avisados quando você entra e sai (`amigos.avisar_entrada`).
- **Party**: até `party.tamanho_maximo` (8, como no anime). Quem convida vira líder quando o primeiro
  aceita. Se o líder sai, o próximo vira líder; com 1 membro, a party acaba. Sem fogo amigo
  (`party.fogo_amigo`), inclusive flechas. Sair do servidor tira o jogador da party. Parties não são
  guardadas: somem quando o servidor reinicia.
- **Mensagens**: só para amigos (`mensagens.so_amigos`), ficam na caixa mesmo com o destinatário
  offline; ao entrar, o jogador é avisado das não lidas. Até `mensagens.caixa_maxima` por jogador
  (as mais antigas saem). Texto puro, até 256 letras.

## Dados

`dados.yml`: amizades, pedidos, caixas de entrada e nomes já vistos. Gravado 5 s depois de cada
mudança (fora da thread principal) e ao desligar, com troca atômica do arquivo.

## Ficou para depois

- Guilda (aparece com cadeado no menu).
- Experiência e itens divididos na party.

## Desenvolvimento

```bash
./gradlew build
```

Testes (JUnit) cobrem amizades, convites e regras da party (expiração, lotação, líder saindo,
expulsar, promover, desfazer), caixa de entrada e a gravação do `dados.yml`.
