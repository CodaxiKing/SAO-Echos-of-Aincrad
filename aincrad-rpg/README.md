# AincradRPG

Level, XP, atributos, level dos mobs e itens com raridade (etapas A1–A2 do plano).
Precisa do **AincradCore** (banco, mensagens, menus, regiões, `/aincrad`).

## O que faz

- **Level e XP** — curva em `levels.yml` (fórmula `base × level^expoente` ou tabela), level máximo 100.
  O XP do Minecraft é desligado: a barra de XP mostra o level e o progresso do RPG.
- **Atributos** — cada level dá pontos (`pontos_por_level`) para **Força** (dano), **Agilidade**
  (velocidade de ataque e de movimento) e **Vitalidade** (vida). O level também dá vida (`vida_por_level`).
- **Morte** — perde uma fração do XP do level atual (`morte.perda_xp`, padrão 10%); nunca desce de level.
- **Mobs com level** — sorteado na zona onde o mob nasce (`mobs.yml`): zona = região do AincradCore
  (vale a menor, se houver várias), depois o mundo, depois o padrão. Vida e dano crescem por level,
  o nome mostra `[Lv.X]`. Abates dão XP = base × level do mob, com redução quando o jogador está
  muito acima. Mob com nome dado por outro plugin (bosses) não recebe level automático.
- **Itens com raridade** — modelos em `items.yml` (material, nome, level mínimo, slot, faixas de bônus,
  chance de drop). Raridades comum → lendário multiplicam os bônus (`config.yml → raridades`).
  Arma acima do level bate com `penalidade_arma`; armadura acima do level volta para o inventário.
- **Encantamento** — mesa de encantamento bloqueada por padrão (`encantamento: bloquear`).

## Comandos

| Comando | O que faz | Permissão |
|---|---|---|
| `/status` | Menu de status: clique num atributo gasta 1 ponto | `aincrad.rpg.use` |
| `/status <forca\|agilidade\|vitalidade> [qtd]` | Gasta pontos pelo chat | `aincrad.rpg.use` |
| `/status texto` | Status no chat (Bedrock/console de quem prefere texto) | `aincrad.rpg.use` |
| `/status resetar` | Devolve os pontos dos atributos | `aincrad.rpg.resetar` (op) |
| `/level [jogador]` | Level e XP | `aincrad.rpg.use` |
| `/aincrad rpg xp dar\|tirar <jogador> <qtd>` | Dá ou tira XP (tirar pode descer de level) | `aincrad.rpg.admin` |
| `/aincrad rpg level <jogador> <n>` | Define o level (pontos recalculados, atributos zerados) | `aincrad.rpg.admin` |
| `/aincrad rpg item <id> [raridade\|aleatoria] [jogador]` | Gera item de `items.yml` | `aincrad.rpg.admin` |
| `/aincrad rpg resetar <jogador>` · `info <jogador>` | Devolve pontos · mostra os dados | `aincrad.rpg.admin` |

`/aincrad reload` recarrega `config.yml`, `levels.yml`, `mobs.yml`, `items.yml` e as mensagens.

## Banco

Tabela `rpg_players` (uuid, level, xp, points, forca, agilidade, vitalidade, updated), criada pela
migração do core. Carrega no pré-login (fora da thread principal), grava ao sair, a cada
`salvar_a_cada` segundos e ao desligar. Funciona com SQLite ou MySQL (o que estiver no core).

## API (para os outros plugins)

`com.aincrad.rpg.api.AincradRPGAPI` — métodos estáticos (dá para chamar por reflexão):

```java
int  getLevel(Player)                 long getXp(Player)          long getXpToNext(Player)
long addXp(Player, long qtd, String motivo)   int getAttribute(Player, "forca")
int  getMobLevel(Entity)              void setMobLevel(LivingEntity, int)
ItemStack createItem(String id, String raridade)   String getRarity(ItemStack)
int  getRequiredLevel(ItemStack)      void openStatus(Player)
```

Eventos: `XpGainEvent` (cancelável, dá para mudar a quantidade — bônus de party, eventos) e
`PlayerLevelUpEvent` (level antigo e novo). O AincradSocial e o AincradFloors já procuram o
`getLevel(Player)` por reflexão.

## Compilar

```bash
./gradlew build
```

Compila junto com o `../aincrad-core` (build composto). O jar sai em `build/libs/AincradRPG-<versão>.jar`.
Os testes (`RpgLogicTest`) cobrem curva de XP, subir vários levels, level máximo, morte, tirar XP,
pontos, XP de mob com redução, sorteio de level e de raridade.

## Ainda falta

- Dividir o XP de mob com a party do AincradSocial (dá para fazer ouvindo o `XpGainEvent`).
- Botão "Status" no menu do AincradUI chamando `AincradRPGAPI.openStatus`.
