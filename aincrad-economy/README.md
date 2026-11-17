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
- **/pagar** — com mínimo e taxa configuráveis; paga quem está offline também.
- **Troca segura** (`/trocar`) — janela onde cada um só mexe no seu lado e oferece Col; qualquer
  mudança derruba as confirmações e trava o confirmar por `espera_confirmar` segundos. Os dois
  confirmam → itens e Col trocam de dono juntos. Fechar, sair ou desligar o servidor devolve tudo.

## Comandos

| Comando | O que faz | Permissão |
|---|---|---|
| `/col` | Seu saldo | `aincrad.economy.use` |
| `/col <jogador>` | Saldo de outro (online ou não) | `aincrad.economy.admin` |
| `/pagar <jogador> <qtd>` | Paga Col (`1500`, `1.500`, `1,5k`, `2m`) | `aincrad.economy.use` |
| `/trocar <jogador>` · `aceitar` · `recusar` | Troca segura | `aincrad.economy.use` |
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
```

`TransactionEvent` (cancelável) sai antes de cada mudança feita neste servidor, com o valor
(positivo = entrada) e o motivo. O AincradSocial já usa `getBalance`/`withdraw`/`deposit` para o
tesouro da guilda.

## Banco

`eco_accounts` (uuid, name, balance, updated), `eco_transactions` (registro), `eco_shop_links`
(bloco/entidade → loja). Criadas pelas migrações do core.

## Compilar

```bash
./gradlew build
```

Compila com o `../aincrad-core` (build composto) e o VaultAPI (jitpack, só para compilar). O jar sai
em `build/libs/AincradEconomy-<versão>.jar`. Testes: `EcoMathTest` (formatação, taxa, Col de mob,
soma sem estouro, leitura de quantidades).
