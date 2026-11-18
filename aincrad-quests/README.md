# AincradQuests

As missões de NPC de cada andar: objetivos em etapas, recompensas pelas APIs dos outros plugins,
diárias e semanais, quest de party, NPCs com diálogo e o diário. Etapas 37 e 38 do plano.

Precisa do **AincradCore**. Opcionais: AincradRPG (XP, level mínimo, itens e mobs do `spawns.yml`),
AincradEconomy (Col), AincradFloors (andar liberado), AincradBosses (objetivo de chefe), AincradLife
(pescar e cozinhar), AincradSocial (party) e AincradReputation (título de recompensa).

> Compila e passa nos testes de regras, mas **ainda não foi testado num servidor de verdade**.

## Uma quest (`quests/*.yml`)

Todo arquivo `.yml` da pasta `quests/` é lido. O `exemplo.yml` explica cada campo.

```yaml
lobos_na_estrada:
  nome: "Lobos na estrada"
  descricao: "Os lobos estão atacando quem sai da cidade."
  tipo: historia            # historia, diaria ou semanal
  party: false              # true: matar conta para a party por perto
  npc: guarda               # quem dá e a quem se entrega
  requisitos: { level: 1, andar: 1, quest: outra_quest }
  etapas:
    - { tipo: matar, alvo: WOLF, quantidade: 5, texto: "Mate 5 lobos" }
  recompensas:
    xp: 120
    col: 80
    itens: ["BREAD 4", "rpg:espada_de_ferro"]
    titulo: "Caçador de Lobos"
    comandos: ["aincrad skill dar <jogador> espada 50"]
```

| Objetivo | Conta quando |
|---|---|
| `matar` | O jogador mata o mob. `alvo` é o tipo (`WOLF`) ou o mob do `spawns.yml` do AincradRPG (`mythic:LoboTerrivel`); sem alvo, qualquer mob |
| `coletar` | Tem os itens no inventário (não são tirados) |
| `entregar` | Fala com o NPC da quest com os itens (são tirados) |
| `falar` | Fala com o NPC indicado |
| `chegar` | Entra na região do core indicada |
| `chefe` | Participa da vitória sobre o chefe (`alvo` = id do chefe) |
| `pescar`, `cozinhar` | Conclui a ação no AincradLife |

As etapas são feitas em sequência. Com todas feitas, a quest fica pronta e é entregue ao NPC, que
paga a recompensa. Sem o AincradRPG o XP vira XP do Minecraft; sem o AincradEconomy o Col não é pago.

- **Diária** renova à meia-noite; **semanal**, na segunda-feira (fuso do servidor).
- **Party**: nas quests com `party: true`, cada mob morto por um membro conta para os outros que
  estão a até 64 blocos e têm a quest ativa.
- Itens de `coletar` e `entregar` são materiais do Minecraft (`LEATHER`).

## NPCs

NPCs próprios e simples: uma entidade parada que não se fere, com nome.

| Comando | O que faz |
|---|---|
| `/aincrad npc criar <id> <nome>` | Cria o NPC onde você está (o nome aceita cores MiniMessage) |
| `/aincrad npc mover <id>` | Traz o NPC para onde você está |
| `/aincrad npc fala <id> <texto>` | O que ele diz ao ser clicado |
| `/aincrad npc comando <id> <comando>` | O jogador roda o comando ao clicar (`nenhum` tira). Ex.: `chefe informante` faz do NPC um informante dos chefes |
| `/aincrad npc remover <id>` · `listar` | Remove · lista |

- Ficam em `npcs.yml`. O tipo de entidade (`tipo: VILLAGER`) pode ser trocado no arquivo.
- Não são salvos com o mapa: o plugin os põe de volta quando o pedaço carrega, então não duplicam.
- Sobre a cabeça, **!** amarelo (quest disponível) ou **?** verde (algo a entregar ou alguém com quem
  falar). Cada jogador vê o ícone que vale para as quests dele.
- Clicar abre o diálogo: as quests disponíveis (clique aceita), as em andamento e as prontas (clique
  entrega).

## Jogador

| Comando | O que faz |
|---|---|
| `/quests` | O diário: quests ativas com o objetivo atual e as concluídas. Clicar numa ativa a rastreia na tela |
| `/quests abandonar <id>` | Abandona uma quest ativa |

## Admin

`/aincrad quest dar|concluir|resetar <jogador> <id>` e `/aincrad quest lista` (quests carregadas e
erros dos arquivos). Permissão `aincrad.admin`; as ações entram no registro do AincradAdmin.

## API

`com.aincrad.quests.api.AincradQuestsAPI` (estático, também no ServicesManager): `isCompleted(Player,
id)`, `getActive(Player)` e `giveQuest(Player, id)`. Evento `QuestCompleteEvent`.

## Banco

`quest_progress` (uuid, quest, estado, etapa, contagem, quando concluiu, quantas vezes), no banco do
core.

## Escolhas diferentes do plano

- Com o AincradUI, a entrada **Quests** do menu SAO abre o diário e a quest rastreada aparece
  numa linha embaixo da barra de vida. Sem ele, o diário é o `/quests` e o rastreio fica na barra de ação.
- O diálogo é um menu com as quests do NPC, não uma janela de falas com opções.
- Não há integração com Citizens nem FancyNpcs: só os NPCs próprios.
- Recompensa de skill sai pela lista `comandos`.

## Compilar

```bash
./gradlew build
```

O jar sai em `build/libs/AincradQuests-0.1.0.jar`.
