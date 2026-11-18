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

## Comandos

| Comando | O que faz | Permissão |
|---|---|---|
| `/col` | Seu saldo | `aincrad.economy.use` |
| `/col <jogador>` | Saldo de outro (online ou não) | `aincrad.economy.admin` |
| `/pagar <jogador> <qtd>` | Paga Col (`1500`, `1.500`, `1,5k`, `2m`) | `aincrad.economy.use` |
| `/trocar <jogador>` · `aceitar` · `recusar` | Troca segura | `aincrad.economy.use` |
| `/leilao` · `/leilao vender <preço> [horas]` · `/leilao retirar` | Leilão | `aincrad.economy.use` |
| `/casa [info]` · `comprar` · `ir` · `convidar <j>` · `remover <j>` · `definirspawn` · `vender` | Casa | `aincrad.economy.use` |
| `/aincrad casa criar <nome> <preço> [andar]` · `remover <nome>` · `despejar <nome>` · `listar` | Admin de casas | `aincrad.economy.admin` |
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
- Teleporte para casa em outro servidor da rede (`/casa ir` só funciona no servidor da casa).
