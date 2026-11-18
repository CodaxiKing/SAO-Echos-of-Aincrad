# AincradWorld

O mundo reage aos jogadores: **jornal semanal** (etapa 44), **calendário** com estações e datas
marcadas (57), **dia e noite** que importam (58) e **clima por andar** (59).

Precisa do **AincradCore**. Opcionais: AincradFloors (andar de cada lugar e zonas seguras),
AincradRPG (level e XP dos mobs), AincradEconomy (preço do jornal) e, para as notícias,
AincradBosses, AincradLife e AincradReputation.

> Compila e passa nos testes de regras, mas **ainda não foi testado num servidor de verdade**.
> O dia e noite e o clima **mexem no equilíbrio** de mobs e drops: o plano os deixa para depois do
> andar 1 pronto e jogado. Dá para desligar cada um (`noite.ativa`, `weather.yml → ativo`).

## Jornal (`/jornal`)

O plugin ouve o que os outros anunciam e guarda no banco do core (vale para a rede):

| Notícia | Vem de |
|---|---|
| Andar liberado | AincradFloors |
| Primeira vitória sobre um chefe, com o Last Attack | AincradBosses |
| Vencedor do torneio de pesca · casamento | AincradLife |
| Jogador preso · conquista anunciada | AincradReputation |
| Mudança de estação · data marcada | o próprio calendário |

| Comando | O que faz |
|---|---|
| `/jornal` | Abre a edição da semana, como livro |
| `/jornal lista` | As edições que têm notícias |
| `/jornal <edição>` | Uma edição antiga (`2026-W47`) |
| `/jornal comprar` | O livro da semana, para guardar (`jornal.preco` em Col) |

- A edição é a semana (de segunda a domingo). O texto de cada tipo de notícia fica em
  `messages.yml → noticia`; deixar um em branco tira o tipo do jornal.
- Para vender por NPC, crie um no AincradQuests e use `/aincrad npc comando <id> jornal comprar`.
- As edições com mais de `jornal.guardar_semanas` são apagadas.

## Calendário (`calendar.yml`, `/calendario`)

As estações giram a partir de `inicio`, cada uma com `dias_por_estacao` dias de verdade. Com 7, o
ano de Aincrad dura 28 dias.

```yaml
datas:
  natal: { nome: "Noite de Natal", dia: "12-24" }                          # dia do ano de verdade
  festival_da_colheita: { nome: "Festival da Colheita", estacao: outono, dia: 3 }   # 3º dia de cada outono
```

Quando a estação muda ou uma data começa, o servidor é avisado, entra no jornal e os outros
plugins recebem `SeasonChangeEvent` e `CalendarDateEvent` — uma vez só, mesmo com reinício.
O Nicholas do AincradBosses usa a data `natal` (`roaming.yml → calendario: natal`).

## Dia e noite (`world.yml → noite`)

À noite, **fora das zonas seguras**:

- o mob que nasce tem o level multiplicado (`level: 1.25`);
- o XP do abate é multiplicado (`xp: 1.5`);
- os drops do mob que nasceu de noite rendem mais (`drop: 1.5`);
- quem está fora das cidades é avisado quando anoitece.

Vale para os mobs com level do AincradRPG. O Col não muda. No `spawns.yml` do AincradRPG, uma linha
com `periodo: noite` só nasce de noite.

## Clima por andar (`weather.yml`)

Cada andar tem as suas chances de chuva, neve e névoa, que podem mudar por estação. A cada
`duracao_minutos` o clima de cada andar é sorteado de novo.

```yaml
andares:
  35:                                   # a Floresta da Perdição
    padrao: { nevoa: 0.6, chuva: 0.1 }
    inverno: { nevoa: 0.4, neve: 0.4 }
```

- **Chuva e neve**: o jogador vê chuva; onde o bioma é frio o Minecraft mostra neve, e onde é seco
  nada cai. É o bioma que decide, não o plugin.
- **Névoa**: a distância de visão do jogador cai para `nevoa.distancia_de_visao` pedaços.
- **Mobs**: no `spawns.yml` do AincradRPG, uma linha com `clima: nevoa` só nasce com névoa — é
  assim que o clima troca os mobs da região. Os mobs raros (`rare-mobs.yml`) aceitam o mesmo.
- O clima é sorteado em cada servidor da rede, não é compartilhado.

## API

`com.aincrad.world.api.AincradWorldAPI` (estático, também no ServicesManager): `getSeason()`,
`getSeasonDay()`, `isDate(id)`, `getDateStart(id)`, `getWeather(Location)` e `isNight(World)`.
Eventos: `SeasonChangeEvent` e `CalendarDateEvent`.

## Banco

`world_news` (as notícias) e `world_state` (a última estação e as datas já anunciadas).

## Escolhas diferentes do plano

- A névoa é feita com a distância de visão do jogador, não com um efeito do AincradEffects.
- O labirinto do AincradBosses não consulta o clima: só o gerador de mobs do AincradRPG.
- O multiplicador de Col da noite ficou de fora: os mobs não dão Col por conta própria.

## Compilar

```bash
./gradlew build
```

O jar sai em `build/libs/AincradWorld-0.1.0.jar`.
