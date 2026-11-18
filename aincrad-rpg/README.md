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
- **Bônus de combate nos itens** — além dos atributos do Minecraft, um modelo pode ter `critico`,
  `dano_critico`, `roubo_vida`, `esquiva` e `aparar` (todos em %). A soma dos itens usados vale até
  os limites de `config.yml → combate_itens`. Esquiva cancela o golpe; aparar corta o dano
  (`aparar_reducao`) e pede uma arma na mão; o crítico multiplica o dano (`critico_base` mais o
  dano crítico). Um item só conta no seu slot e não conta se pede level acima do jogador. Os
  golpes das Sword Skills também podem sair críticos.
- **Modelo 3D por item** — `modelo: "aincrad:arma/elucidator"` (modelo de item do resource pack)
  ou `modelo: 10005` (custom model data, para packs antigos ou do ItemsAdder).
- **Mobs por região** — `spawns.yml` diz quais mobs nascem em cada região, do Minecraft ou do
  **MythicMobs** (veja abaixo).
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
| `/aincrad rpg spawns` | Regiões do `spawns.yml`: mobs vivos, máximo e o que está errado | `aincrad.rpg.admin` |
| `/aincrad rpg mob <ZOMBIE\|mythic:Nome> [level] [modelo]` | Cria um mob onde você está, para testar | `aincrad.rpg.admin` |
| `/aincrad rpg raro <id>` | Faz um mob raro aparecer onde você está | `aincrad.rpg.admin` |

`/aincrad reload` recarrega `config.yml`, `levels.yml`, `mobs.yml`, `spawns.yml`, `items.yml` e as
mensagens.

## Mobs por região (`spawns.yml`)

Para os campos terem os mobs deles — o javali e o lobo do andar 1, por exemplo:

1. **Marque a região**: `/aincrad varinha`, clique esquerdo num canto e direito no outro, e
   `/aincrad regiao criar campos_andar1`. (`/aincrad regiao listar|info|remover` são do core.)
2. **Crie os mobs no MythicMobs**, com o modelo do ModelEngine no arquivo do próprio mob. Teste o
   nome com `/aincrad rpg mob mythic:JavaliFrenetico`.
3. **Liste a região no `spawns.yml`**:

   ```yaml
   regioes:
     campos_andar1:
       maximo: 14            # mobs vivos na região ao mesmo tempo
       por_vez: 2            # quantos nascem por rodada (a cada "intervalo" segundos)
       raio: 40              # nascem a até tantos blocos de um jogador
       distancia_minima: 10  # e nunca mais perto que isto
       sumir_longe: 72       # somem sem jogador a esta distância
       spawn_natural: false  # false = o Minecraft não põe mobs aqui
       mobs:
         - { mob: "mythic:JavaliFrenetico", peso: 3, level: "1-2" }
         - { mob: "mythic:LoboTerrivel", peso: 1, level: "2-4" }
   ```

4. `/aincrad reload` e `/aincrad rpg spawns` para conferir.

- `mob:` é um mob do Minecraft (`WOLF`) ou `mythic:Nome`, o nome interno do MythicMobs.
- `level:` aceita `3`, `"2-4"` ou `{min: 2, max: 4}`. Sem ele, vale a faixa do lugar no `mobs.yml`.
  O level dá XP, vida e dano pelas regras do `mobs.yml`, também para mobs do MythicMobs; o nome
  que o MythicMobs deu fica, com o `[Lv.X]` na frente. Não use `LevelModifiers` no MythicMobs, ou
  a vida escala duas vezes.
- `modelo:` põe um modelo do ModelEngine sobre um mob do Minecraft, sem MythicMobs. O mob continua
  com o comportamento do Minecraft (um `PIG` com modelo de javali não ataca).
- Os mobs nascem só com jogador por perto, no chão mais próximo da altura dele (serve para campo
  e para labirinto), e não são salvos com o mapa: somem quando ninguém está perto ou o servidor
  reinicia, e voltam a nascer.
- `clima: nevoa` e `periodo: noite` numa linha fazem o mob nascer só com esse clima (do
  AincradWorld) ou nesse período: é assim que a névoa ou a noite trocam os mobs de uma região.
- O MythicMobs e o ModelEngine são opcionais e chamados por reflexão. **Esta parte compila e passa
  nos testes de regras, mas não foi testada com os dois plugins num servidor.**

**Mobs do andar sem MythicMobs.** Uma linha de mob do Minecraft pode ter `id` (o nome que as
quests usam em `alvo:`), `nome`, `equipamento`, `drops` (trocam os drops normais; aceitam
materiais, `rpg:<id>` e `life:<ingrediente>`) e `agressivo: true` para lobos. Zumbis e esqueletos
do `spawns.yml` nascem adultos e não queimam no sol; hoglins não viram zoglins. O pacote
[conteudo/andar1](../conteudo/andar1/README.md) usa tudo isso.

## Mobs raros (`rare-mobs.yml`)

Mobs que aparecem com chance baixa perto de um jogador que está na região, **fogem dele** e, se
forem pegos, soltam um drop raro. Só existe um de cada por vez, e ele some depois de um tempo.

```yaml
mobs:
  coelho_do_ragu:
    mob: RABBIT
    nome: "<gold>Ragout Rabbit"
    regiao: campos_andar1
    chance: 0.01                    # por rodada de 30 s, por jogador na região
    level: 1
    foge: true
    drop: "life:carne_coelho_raro"  # MATERIAL, rpg:<id> ou life:<ingrediente do AincradLife>
```

O coelho do ragu solta a carne que o Ensopado de Coelho do AincradLife pede como ingrediente de
rank S. `periodo:` e `clima:` valem aqui também. Para testar sem esperar a chance:
`/aincrad rpg raro <id>`. O arquivo vem vazio.

## Itens únicos (`items.yml → unico: true`)

Um modelo marcado com `unico: true` existe **uma vez só na rede inteira** — a Elucidator, a Dark
Repulser.

- Cada exemplar leva um número de série, gravado no banco (`rpg_unique`). **Criar o segundo é
  recusado** em qualquer servidor: o drop não cai, e `/aincrad rpg item` e a API devolvem nada.
- No chão, o item **não é destruído** (lava, fogo, explosão), **não some** com o tempo, brilha, e se
  cair no vazio volta ao ponto de chegada do mundo.
- A cada minuto o plugin anota com quem o item está (inventário e baú do fim). Se o dono não é
  visto com ele por `unicos.dono_some_dias` (30), o item **volta a poder cair**; a cópia antiga se
  desfaz quando reaparecer.
- `/unicos` lista os itens únicos: os já encontrados, com o dono, e os que ainda ninguém achou
  (sem dizer o nome).
- Um item guardado num baú comum não é visto: para o plugin, o dono sumiu.

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
String getMobId(Entity)               // o "id" da linha do spawns.yml (ou "mythic:Lobo" / "WOLF"), ou null
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
Os testes cobrem curva de XP, subir vários levels, level máximo, morte, tirar XP, pontos, XP de mob
com redução, sorteio de level e de raridade (`RpgLogicTest`), as contas de crítico, esquiva, aparar
e roubo de vida (`ItemCombatTest`) e a leitura do `spawns.yml`, o sorteio
por peso e as contas dos mobs por região (`SpawnRulesTest`).

## Ainda falta

- Botão "Status" no menu do AincradUI chamando `AincradRPGAPI.openStatus`.
