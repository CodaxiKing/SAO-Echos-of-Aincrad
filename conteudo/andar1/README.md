# Pacote de conteúdo — Andar 1

O conteúdo do primeiro andar: mobs, itens, lojas, o chefe e as quests, da praça da Cidade do
Início até a sala do Illfang. São só arquivos de configuração; **o mapa é você quem constrói**.

> Os arquivos carregam sem erro e os testes conferem que tudo o que um cita do outro existe
> (`Andar1PackTest` no AincradRPG, no AincradQuests e no AincradBosses). **Nada foi jogado num
> servidor**: os números de level, dano, preço e chance são pontos de partida.

## O que vem

| Arquivo | O que traz |
|---|---|
| `AincradRPG/spawns.yml` | 5 regiões de mobs, com nome, level, equipamento e drops |
| `AincradRPG/mobs.yml` | A faixa de level de cada região |
| `AincradRPG/items.yml` | 21 itens: espadas, adagas, floretes, machados, lanças, escudos e armaduras |
| `AincradRPG/rare-mobs.yml` | O Ragout Rabbit, na floresta |
| `AincradEconomy/shops.yml` | 7 lojas: Cidade do Início, Horunka e Tolbana |
| `AincradFloors/floors.yml` | O andar 1 com a cidade e as duas vilas como zona segura |
| `AincradBosses/bosses/andar1.yml` | Illfang, com quatro barras de vida e os Sentinelas |
| `AincradBosses/roaming.yml` | O Lobo Alfa como chefe de campo da floresta |
| `AincradQuests/quests/andar1.yml` | 10 quests de história, 3 diárias e 1 semanal |

Culinária, pesca, forja e alquimia usam o que o AincradLife já traz; este pacote não mexe nelas.

## Instalar

1. Ligue o servidor uma vez com os plugins, para cada um criar a sua pasta, e desligue.
2. Copie o conteúdo de `conteudo/andar1/plugins/` para a pasta `plugins/` do servidor,
   substituindo os arquivos.
3. Apague `plugins/AincradQuests/quests/exemplo.yml`.
4. Ligue o servidor e monte o mapa seguindo as seções abaixo.

Os pacotes dos próximos andares vão **somar** nestes mesmos arquivos (`items.yml`, `spawns.yml`,
`shops.yml`); eles não substituem o andar 1.

## 1. Regiões

Os nomes precisam ser exatamente estes.

- **Zonas seguras** (as três primeiras): marque os dois cantos com `/aincrad varinha` e crie com
  `/aincrad regiao criar <nome>`.
- **Áreas de mobs** (as outras cinco): `/aincrad spawn varinha`, marque os dois cantos e use
  `/aincrad spawn criar <nome>`. Como o nome já está no `spawns.yml` do pacote, os mobs começam a
  nascer na hora. No labirinto use `/aincrad spawn criar <nome> exato`, para a parte de baixo e o
  topo não se misturarem.
- Para ajustar level, peso ou quantidade depois, sem abrir o arquivo: `/aincrad spawn`.

| Região | O que é | Level dos mobs |
|---|---|---|
| `cidade_inicio` | A Cidade do Início inteira (zona segura) | — |
| `horunka` | A vila de Horunka (zona segura) | — |
| `tolbana` | Tolbana, ao pé do labirinto (zona segura) | — |
| `campos_oeste` | Os campos fora do portão oeste da cidade | 1 a 3 |
| `floresta_horunka` | A floresta em volta de Horunka | 3 a 5 |
| `planicie_tolbana` | A planície entre a floresta e Tolbana | 4 a 6 |
| `labirinto_andar1` | Os andares de baixo da torre | 6 a 8 |
| `labirinto_andar1_topo` | Os andares de cima, até a porta do chefe | 8 a 9 |

- As regiões de mobs não podem se sobrepor às zonas seguras nem umas às outras.
- A sala do chefe fica **fora** das duas regiões do labirinto, senão os kobolds nascem dentro dela.
- Confira com `/aincrad spawn lista` e, parado num lugar, com `/aincrad spawn aqui`.

## 2. Andar e portal

1. No ponto de chegada, na praça: `/aincrad andar setspawn 1`.
2. Olhando para o bloco do Teleport Gate da praça: `/aincrad gate criar 1`.

O `floors.yml` trata o andar 1 como o mundo `world` inteiro. Se os seus andares ficam empilhados
num mundo só, troque para `tipo: regiao` e crie a região `andar1`.

## 3. Mobs

| Região | Mob | É um | Level | Quando |
|---|---|---|---|---|
| Campos do Oeste | Frenzy Boar | porco | 1–2 | sempre |
| | Lobo Jovem | lobo | 2 | de dia |
| | Dire Wolf | lobo | 2–3 | de noite |
| Floresta de Horunka | Little Nepent | zumbi com folhas na cabeça | 3–4 | sempre |
| | Dire Wolf | lobo | 3–5 | sempre |
| | Coelho da Floresta | coelho | 3 | sempre |
| | Aranha da Mata | aranha | 4–5 | de noite |
| | Ragout Rabbit (raro) | coelho | 3 | chance baixa |
| Planície de Tolbana | Rampage Boar | hoglin | 4–5 | sempre |
| | Dire Wolf | lobo | 5–6 | sempre |
| | Kobold Scout | zumbi | 5–6 | sempre |
| | Esqueleto Errante | esqueleto | 6 | de noite |
| Labirinto (baixo) | Ruin Kobold Trooper | zumbi | 6–8 | sempre |
| | Ruin Kobold Archer | esqueleto | 7–8 | sempre |
| Labirinto (topo) | Ruin Kobold Sentinel | zumbi | 8–9 | sempre |
| | Ruin Kobold Trooper | zumbi | 8–9 | sempre |

- O Frenzy Boar é um porco: não ataca. É o mob de quem acabou de chegar.
- Os mobs são do Minecraft com nome e equipamento. Para usar um modelo do MythicMobs, troque só a
  linha `mob:` por `mythic:NomeDoMob`; o `id`, o level e os drops continuam valendo.
- O Óvulo de Little Nepent da quest é uma `SPORE_BLOSSOM`, com 4% de chance por Nepent.
- O Lobo Alfa (chefe de campo) aparece na floresta com um jogador por perto e volta 6 horas depois
  de derrotado.

## 4. NPCs

Fique no lugar e use `/aincrad npc criar <id> <nome>`.

| Id | Nome sugerido | Onde | Para quê |
|---|---|---|---|
| `instrutor` | Instrutor | Praça da Cidade do Início | A primeira quest |
| `guarda_inicio` | Guarda do Portão | Portão oeste | Lobos, caminho para Horunka, ronda diária |
| `estalajadeiro` | Estalajadeiro | Estalagem | Caça aos javalis, carne diária |
| `curtidor` | Curtidor | Cidade do Início | Troca couro por um casaco |
| `pescador` | Pescador | Beira d'água da cidade | Pesca diária |
| `mae_de_agatha` | Mãe da Agatha | Casa em Horunka | A quest da Anneal Blade |
| `diabel` | Diabel | Praça de Tolbana | O labirinto e o chefe |

Para um informante dos chefes em Tolbana: crie outro NPC e use
`/aincrad npc comando <id> chefe informante`.

## 5. Lojas

Olhando para o NPC (ou bloco) de cada loja: `/aincrad loja npc <id>` (ou `/aincrad loja criar <id>`).

| Id | Onde | Vende |
|---|---|---|
| `mercador_inicio` | Cidade do Início | Comida, tochas, flechas; compra o que os mobs soltam |
| `ferreiro_inicio` | Cidade do Início | A arma de começo de cada tipo e o escudo de madeira |
| `armeiro_inicio` | Cidade do Início | O conjunto de couro |
| `pescador_inicio` | Cidade do Início | Vara e isca; compra peixe |
| `mercador_horunka` | Horunka | Bronze Sword e Montante de Ferro |
| `ferreiro_tolbana` | Tolbana | As armas de level 4–5, o broquel, o elmo e a cota de malha |
| `mercador_tolbana` | Tolbana | Comida, tochas e flechas |

A loja é ligada a uma entidade ou bloco. O NPC de quest `pescador` e a loja `pescador_inicio`
podem ser o mesmo personagem só se a loja for ligada a um bloco ao lado dele (uma banca, um
barril): clicar no NPC abre as quests.

## 6. A sala do Illfang

1. Num canto da sala: `/aincrad chefe sala illfang canto1`; no canto oposto: `canto2`.
2. Onde o chefe nasce: `/aincrad chefe sala illfang spawn`.
3. Deixe o vão da porta aberto, marque-o com `/aincrad varinha` e use `/aincrad chefe porta illfang`.

| Barra | Vida | O que muda |
|---|---|---|
| 1 | 100% | Machado e escudo; 3 Sentinelas a cada 40 s; golpe em área |
| 2 | 75% | Mais Sentinelas; começa a investir |
| 3 | 50% | 10% mais dano; Sentinelas de level 9 |
| 4 | 25% | Larga tudo e puxa a nodachi: 30% mais dano, 40% mais rápido, salto e fúria; sem Sentinelas |

- 800 de vida, level 10, 15 minutos de tempo limite, cristais bloqueados na sala.
- Cada participante (5% do dano ou mais) recebe 800 de XP e 500 Col.
- O Last Attack dá o **Coat of Midnight** lendário.
- Derrotá-lo libera o andar 2.

## 7. A história

| # | Quest | Quem dá | Pede | Recompensa além de XP e Col |
|---|---|---|---|---|
| 1 | O primeiro dia | Instrutor | 3 Frenzy Boars | Pão |
| 2 | Lobos na estrada | Guarda | Level 2; 6 Dire Wolves | — |
| 3 | O caminho para Horunka | Guarda | Level 3; chegar a Horunka | — |
| 4 | A estrada para Tolbana | Mãe da Agatha | Level 5; chegar a Tolbana | — |
| 5 | Batedores do labirinto | Diabel | Level 6; 12 Troopers (party) | Broquel de Ferro |
| 6 | A porta do chefe | Diabel | Level 8; 8 Sentinels (party) | — |
| 7 | O senhor dos kobolds | Diabel | Derrotar o Illfang (party) | Maçãs douradas |

Fora da linha principal:

- **Caça aos javalis** (Estalajadeiro): 8 javalis e 5 carnes.
- **Peles para o curtidor** (Curtidor): 6 couros por um Casaco de Couro.
- **O remédio secreto da floresta** (Mãe da Agatha): 10 Little Nepents e um óvulo pela **Anneal Blade**.
- Diárias: Ronda dos campos, Carne para a estalagem, Peixe fresco.
- Semanal: Limpeza do labirinto (40 Troopers e 10 Sentinels, em party).

## O que este pacote não tem

- Modelos 3D dos mobs e dos itens: são os do Minecraft.
- Falas dos NPCs: defina com `/aincrad npc fala <id> <texto>`.
- Estalagem, bancadas e pontos de pesca do AincradLife: são postos no mapa pelos comandos dele.
- Receitas, projetos de forja e fórmulas próprios do andar.
- A dungeon escondida e o dia do anúncio.
