# AincradSocial

Amigos, party, guilda e mensagens no estilo SAO, para Paper 1.21.11. Funciona sozinho pelos comandos e
pelo chat; com o **AincradUI** instalado, tudo aparece no menu SAO e os membros da party ganham
barras de vida embaixo da barra do jogador, como no anime.

## Instalação

1. Compile o AincradUI antes (este projeto usa a API dele): `./gradlew build` aqui já compila os dois
   juntos (build composto com `../aincrad-ui`).
2. `build/libs/AincradSocial-<versão>.jar` em `plugins/`, junto com o AincradUI.
3. Ligue o servidor: são criados `config.yml`, `guilds.yml`, `messages.yml` e, com o uso, `dados.yml` e `guildas.yml`.

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

## Guilda (como no anime)

A guilda é o grupo permanente: dura entre sessões, tem hierarquia, símbolo, base e disputa a linha
de frente (modelo: Cavaleiros do Juramento de Sangue, DDA, o Exército, Fuurinkazan, Gatos Negros do
Luar e, como guilda vermelha, a Laughing Coffin). Tudo em `guilds.yml`.

| Cargo | Pode |
|---|---|
| Comandante (líder) | Tudo, incluindo tag, emblema, uniforme, regras, passar a liderança e desfazer |
| Vice-comandante | Convidar, aceitar candidaturas, expulsar e promover abaixo dele, sacar do tesouro, alianças |
| Oficial | Convidar, aceitar candidaturas, expulsar abaixo dele, cofre |
| Membro | Depositar, tirar do cofre (limite por dia), casa |
| Recruta | Casa; vira membro sozinho depois de `recruta_dias` |

O líder muda as permissões editáveis com `/guilda config permissao <permissão> <cargo>`.

- **Fundar**: andar 3 liberado (AincradFloors), quest `selo_da_guilda` (AincradQuests), level 10 e
  5.000 Col (AincradEconomy ou Vault). O que depende de plugin ausente não é cobrado. A guilda precisa
  de 3 membros em 7 dias, senão é desfeita e metade do Col volta.
- **Entrar**: só convite, candidatura ou aberta. Quem sai ou é expulso espera 24 h para entrar em outra.
- **Identidade**: `[TAG]` antes do nome no chat (verde para a sua guilda, azul para aliadas, vermelha
  para guilda vermelha), na lista de jogadores e acima da cabeça; emblema antes do nome na barra de
  vida e no painel de perfil do AincradUI.
- **Uniforme** (nível 2): `/guilda uniforme` entrega couro tingido nas cores da guilda, cosmético (sem
  armadura) e marcado.
- **Casa** (nível 2): o líder define com `/guilda casa definir`; `/guilda casa` leva até lá depois de
  3 s parado (cancela se mover ou levar dano). Quando o AincradEconomy tiver casas, a sede passa a ser
  uma casa comprada com o tesouro.
- **Cofre**: baú compartilhado de 9 a 54 espaços conforme o nível; histórico de quem guardou e tirou.
- **Tesouro**: Col da guilda, depósitos de qualquer membro, saques do vice-comandante para cima e
  taxa automática de 0 a 10% sobre o Col caçado (aplicada pelo plugin de economia via
  `taxIncome`). Tudo no extrato.
- **Nível**: XP por chefe derrotado (+ bônus de Last Attack), quest e mob hostil (com limite diário).
  Níveis 1 a 5 com 10 a 50 vagas.
- **Alianças** (nível 3): até 3 guildas, chat comum `/ga`, sem fogo amigo, tag azul.
- **Linha de frente**: `/guilda ranking` por chefes, Last Attacks e andar mais alto. Guilda vermelha
  (líder com cursor vermelho no AincradReputation) sai do ranking.
- **Manutenção** a cada 10 min: recrutas promovidos, líder inativo há 30 dias substituído (vice, oficial
  mais antigo ou membro mais antigo), guildas pequenas desfeitas, reembolsos pagos quando o jogador entra.

No menu SAO: Party → **Guilda**. Sem guilda: Fundar (nome e tag digitados no chat), convites
recebidos, guildas abertas e o ranking. Com guilda: Membros (promover, rebaixar, expulsar, passar a
liderança, mensagem), Cofre, Tesouro (depositar e sacar), Convidar, Candidaturas, Alianças, Linha de
frente, Ir para a casa, Pegar uniforme, Opções (recrutamento, taxa, emblema, cores, definir casa) e
Sair/Desfazer.

### Comandos da guilda

| Comando | O que faz |
|---|---|
| `/guilda criar <nome> <TAG>` | Funda a guilda |
| `/guilda info [tag]`, `/guilda lista` | Detalhes; guildas que aceitam candidatura |
| `/guilda convidar <jogador>`, `aceitar <tag>`, `recusar <tag>` | Convites |
| `/guilda candidatar <tag>`, `candidaturas [aceitar\|recusar <jogador>]` | Candidaturas |
| `/guilda sair`, `expulsar`, `promover`, `rebaixar`, `lider <jogador>` | Membros e cargos |
| `/guilda cofre`, `tesouro [depositar\|sacar <valor>]` | Cofre e tesouro (com extrato) |
| `/guilda casa [definir]`, `/guilda uniforme` | Sede e uniforme |
| `/guilda alianca [propor\|aceitar\|desfazer] <tag>`, `/guilda ranking` | Alianças e linha de frente |
| `/guilda config recrutamento\|taxa\|cores\|emblema\|tag\|permissao ...` | Opções do líder |
| `/guilda desfazer confirmar` | Desfaz (só o líder) |
| `/g <mensagem>` ou `#mensagem`, `/ga <mensagem>` | Chat da guilda; chat das aliadas |
| `/aincrad guilda renomear\|desfazer\|dar-xp <tag> ...` | Admin (`aincrad.admin`) |

### API

`AincradSocialAPI` no ServicesManager: `getParty`, `getGuild` (nome, tag, emblema, nível, cargo,
vermelha), `areAllies` (party, guilda ou aliança), `addGuildXp`, `recordBossKill(participantes, andar,
lastAttacker)` para o AincradBosses, `questCompleted` para o AincradQuests e `taxIncome` para a
economia. Eventos em `com.aincrad.social.api.event`: `GuildCreateEvent`, `GuildJoinEvent`,
`GuildLeaveEvent`, `GuildRankChangeEvent`, `GuildLevelUpEvent`, `GuildAllianceEvent`.

## Regras da party, amigos e mensagens

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

`dados.yml`: amizades, pedidos, caixas de entrada e nomes já vistos. `guildas.yml`: guildas, membros,
permissões, cofre, extrato, alianças, candidaturas e esperas de saída. Quando o aincrad-core existir,
os dois vão para as tabelas MySQL do plano. Gravado 5 s depois de cada
mudança (fora da thread principal) e ao desligar, com troca atômica do arquivo.

## Ficou para depois

- Chat da guilda entre servidores da rede (precisa do `MessageBus` do aincrad-core).
- Emblema como imagem do pack (hoje é um símbolo de texto) e banner da guilda (nível 5).
- Casa da guilda comprada com o tesouro (precisa das casas do AincradEconomy).
- Experiência e itens divididos na party.

## Desenvolvimento

```bash
./gradlew build
```

Testes (JUnit) cobrem amizades, convites e regras da party (expiração, lotação, líder saindo,
expulsar, promover, desfazer), caixa de entrada, a gravação do `dados.yml` e da guilda: criação e
validação, cargos e permissões, candidaturas, níveis e vagas, tesouro e taxa, limite do cofre,
alianças, manutenção (recruta, líder inativo, guilda pequena com reembolso), ranking, gravação do
`guildas.yml`, o `guilds.yml` padrão e as mensagens de cada resultado.
