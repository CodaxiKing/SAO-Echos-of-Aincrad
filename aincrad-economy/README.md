# AincradEconomy

A moeda **Col** e o que se compra com ela (etapas A3–A4 do plano). Precisa do **AincradCore**.

## O que faz (A3)

- **Col** — saldo por jogador no banco do core (MySQL na rede: o mesmo saldo em todos os servidores).
  Cada mudança grava `saldo = saldo + delta` (atômico) e fica no registro `eco_transactions` com o
  motivo; os outros servidores são avisados pelo MessageBus e releem o saldo de quem está online lá.
- **Vault** — com o Vault instalado, o Col vira a economia do servidor para plugins de terceiros
  (inteiro, sem centavos; sem contas de banco).
- **Col por mob** — ao matar um mob, `level × col_por_level` (±`variacao`). O level vem do
  AincradRPG; sem ele, conta `level_sem_rpg`.
- **Lojas de NPC** (`shops.yml`) — preços fixos de compra e venda, itens do Minecraft ou do RPG
  (`rpg:<id>[:raridade]`). Abrem ao clicar num bloco ou entidade ligados pelo admin; NPCs de loja
  não tomam dano. Só compra de volta itens “limpos” (sem nome/marca de outro plugin).
- **Loja em tela plana** — com o AincradUI instalado e o pack carregado, a loja abre desenhada na
  tela, com cursor: cabeçalho com o saldo, abas Comprar/Vender, lista com páginas e o painel do item
  com quantidade (− e +), total e o botão. Sem o AincradUI ou sem o pack, abre o menu de baú.
  `nome:` e `icone:` em `shops.yml` ajustam o que aparece; os textos ficam em `loja.tela` das mensagens.
- **/pagar** — com mínimo e taxa configuráveis; paga quem está offline também.
- **Troca segura** (`/trocar`) — janela onde cada um só mexe no seu lado e oferece Col; qualquer
  mudança derruba as confirmações e trava o confirmar por `espera_confirmar` segundos. Os dois
  confirmam → itens e Col trocam de dono juntos. Fechar, sair ou desligar o servidor devolve tudo.

## O que faz (A4)

- **Leilão** (`/leilao`) — anuncia o item da mão com preço e prazo (`horas_padrao`, máximo
  `horas_maximo`), até `max_anuncios` por jogador. Os anúncios valem para a rede toda. Comprar pelo
  menu pede confirmação; a taxa (`taxa_venda`) sai do valor do vendedor, que recebe mesmo offline
  e é avisado no servidor onde estiver. Dois compradores ao mesmo tempo: só um leva (o outro recebe
  o Col de volta). Clicar no próprio anúncio cancela. Vencidos e cancelados vão para a **caixa de
  retirada** (`/leilao retirar`); quem entra com itens lá é avisado.
- **Casas** — o admin marca a área com `/aincrad varinha` e cria com `/aincrad casa criar <nome> <preço> [andar]`
  (vira a região `casa_<nome>` do core; a chegada é onde o admin está). Quem está dentro de uma casa
  à venda usa `/casa comprar` (uma por jogador por padrão). Dentro da casa só o dono, os convidados
  (`/casa convidar <jogador>`) e admins quebram, colocam, abrem portas, baús, botões, molduras e
  suportes de armadura. Casa sem dono: só admin mexe. `/casa vender` devolve `reembolso` do preço.

## Casas: placa, restauração e inatividade

Três coisas trazidas do plugin Crown-Housing, sem o aluguel (em Aincrad a casa é comprada):

- **Placa na frente da casa** — `/aincrad casa placa <nome>` põe um texto flutuante onde o admin
  está (`placa <nome> tirar` remove). Ele mostra "à venda" com o preço, "em leilão" com o lance
  atual, ou "casa de Fulano", e se atualiza sozinho.
- **A casa volta a ser como era** — ao criar a casa, o plugin guarda um modelo dela
  (`plugins/AincradEconomy/casas/<nome>.nbt`). Quando o dono vende, é despejado ou some, os
  blocos, baús, molduras e suportes de armadura voltam ao modelo, e **o que o dono deixou dentro
  se perde** (o `/casa vender` avisa). Depois de reformar a casa, guarde de novo com `/aincrad casa
  modelo <nome>`. Desliga em `casas.restaurar_ao_devolver`. Numa rede, a restauração acontece no
  servidor onde a casa fica, em até um minuto.
- **Dono que sumiu** — com `casas.inatividade_dias` maior que 0, a casa de quem não entra há
  tantos dias volta ao mercado, sem reembolso. Vem desligado (0). A contagem começa quando a regra
  vê o dono pela primeira vez, não na data da compra.

## O corretor (`/casas`) e o imposto

- **`/casas`** (ou `/corretor`) abre a lista de **casas por andar**: quantas estão à venda em cada
  andar e, dentro do andar, cada casa com o preço, o lance do leilão ou o dono. Clicar numa casa à
  venda leva o jogador até ela por `casas.visita.segundos` (30) e o traz de volta ao fim do tempo,
  quando ele sai da casa ou quando desconecta. A visita só vale para andares em que o jogador já
  pode entrar (`AincradFloorsAPI.canEnter`); a lista dos outros andares ele vê, sem visitar.
- **NPC corretor**: um NPC do AincradQuests com `comando: casas` abre a mesma lista.
- **Imposto** (`casas.imposto`): a cada `a_cada_dias` (7) o dono paga `fracao` (2%) do preço da
  casa. É cobrado sozinho quando ele está online; `/casa imposto` mostra o vencimento e `/casa
  imposto pagar <períodos>` adianta até `max_adiantado` (8) períodos, para quem vai ficar fora.
  Vencido e sem pagar por `tolerancia_dias` (7), **a casa é retomada** e volta ao mercado (ou a
  leilão). É o que impede as casas de acabarem. `fracao: 0` desliga.

## Casas com vista: venda por leilão (etapa 50)

Casas raras — a cabana do andar 22 — não têm preço fixo: vão a **leilão**.

- O admin cria a casa normalmente e a põe em leilão: `/aincrad casa leilao <nome> <lance mínimo>
  [horas]` (48 h por padrão). Dali em diante ela é uma casa de leilão; `/aincrad casa leilao <nome>
  tirar` devolve o preço fixo.
- Dentro da casa: `/casa leilao` mostra o lance atual e o tempo que falta; `/casa lance <valor>` dá
  um lance. `/casa comprar` não vale para ela.
- O lance sai do saldo e fica em **custódia**. Quem é superado recebe o Col de volta na hora.
- Cada lance precisa superar o anterior em `casas.leilao.incremento` (5%). Um lance nos últimos
  `prorrogar_minutos` estica o prazo, para ninguém ganhar no último segundo.
- No fim do prazo **o maior lance leva** a casa e o Col sai de circulação. Sem lances, o leilão
  recomeça.
- Quando o dono **vende ou é despejado, a casa volta a leilão** sozinha.
- O limite de casas por jogador (`casas.max_por_jogador`) vale para os lances.

> Compila e passa nos testes de regras, mas não foi testado num servidor de verdade.

## Comandos

| Comando | O que faz | Permissão |
|---|---|---|
| `/col` | Seu saldo | `aincrad.economy.use` |
| `/col <jogador>` | Saldo de outro (online ou não) | `aincrad.economy.admin` |
| `/pagar <jogador> <qtd>` | Paga Col (`1500`, `1.500`, `1,5k`, `2m`) | `aincrad.economy.use` |
| `/trocar <jogador>` · `aceitar` · `recusar` | Troca segura | `aincrad.economy.use` |
| `/leilao` · `/leilao vender <preço> [horas]` · `/leilao retirar` | Leilão | `aincrad.economy.use` |
| `/casa [info]` · `comprar` · `ir` · `convidar <j>` · `remover <j>` · `definirspawn` · `imposto [pagar <n>]` · `vender` | Casa | `aincrad.economy.use` |
| `/casas` | Corretor: casas por andar, com visita | `aincrad.economy.use` |
| `/aincrad casa criar <nome> <preço> [andar]` · `remover <nome>` · `despejar <nome>` · `placa <nome> [tirar]` · `modelo <nome>` · `listar` | Admin de casas | `aincrad.economy.admin` |
| `/aincrad col dar\|tirar\|definir <jogador> <qtd>` · `ver <jogador>` | Admin do saldo | `aincrad.economy.admin` |
| `/aincrad loja criar <id>` / `npc <id>` | Liga a loja ao bloco / entidade que você olha | `aincrad.economy.admin` |
| `/aincrad loja remover` · `listar` · `abrir <id> [jogador]` | Remove ligação, lista, abre | `aincrad.economy.admin` |

`/aincrad reload` recarrega `config.yml`, `shops.yml` e as mensagens.

## API

`com.aincrad.economy.api.AincradEconomyAPI` (registrada no ServicesManager; métodos estáticos):

```java
long    getBalance(Player)                         CompletableFuture<Long> getBalanceAsync(OfflinePlayer)
boolean withdraw(Player, long qtd, String motivo)  boolean deposit(Player, long qtd, String motivo)
boolean deposit(UUID, long qtd, String motivo)     boolean has(Player, long)
String  format(long)                               boolean openShop(Player, String loja)
boolean mail(UUID, ItemStack, String motivo)        UUID getHouseOwner(Location)   // dono da casa onde está o lugar
int     setHouseAccess(UUID dono, UUID outro, boolean abrir)   // abre as casas do dono para outro jogador

// Custódia: o Col sai do saldo e fica guardado até ser liberado ou devolvido.
String                     hold(Player, long qtd, String motivo)       // id da custódia, ou null sem saldo
CompletableFuture<Boolean> release(String id, UUID para, long qtd, String motivo)
CompletableFuture<Long>    refund(String id, String motivo)            // devolve o resto ao dono
CompletableFuture<Long>    held(String id)                             CompletableFuture<Long> burn(String id)
CompletableFuture<Boolean> burn(String id, long qtd)                   // tira de circulação só uma parte
```

Col em custódia não pode ser gasto (já não está no saldo). `release` nunca libera mais do que
há, mesmo com dois servidores pedindo ao mesmo tempo; `refund` devolve o valor exato que restava;
`burn` tira de circulação o resto (a taxa de um contrato). As encomendas do AincradLife usam isto.

`TransactionEvent` (cancelável) sai antes de cada mudança feita neste servidor, com o valor
(positivo = entrada) e o motivo. O AincradSocial já usa `getBalance`/`withdraw`/`deposit` para o
tesouro da guilda.

## Banco

`eco_accounts` (uuid, name, balance, updated), `eco_transactions` (registro), `eco_shop_links`
(bloco/entidade → loja), `eco_auctions` (anúncios, item serializado), `eco_mailbox` (caixa de
retirada), `eco_houses` (casas, dono, convidados, chegada) e `eco_holds` (custódia). Criadas pelas migrações do core.

## Compilar

```bash
./gradlew build
```

Compila com o `../aincrad-core` (build composto) e o VaultAPI (jitpack, só para compilar). O jar sai
em `build/libs/AincradEconomy-<versão>.jar`. Testes: `EcoMathTest` (formatação, taxa, Col de mob,
soma sem estouro, leitura de quantidades) e `AuctionMathTest` (prazo, taxas do leilão, acesso da casa).

## Ainda falta

- Estalagem e ferreiro do AincradLife cobrando em Col (quando o Life existir).
- O `/casa ir` leva ao servidor do andar da casa (o `servidor` do `floors.yml`) e termina a viagem na
  chegada. Sem o AincradFloors, só funciona no servidor da casa.
