# Pacote de conteúdo — Andar 4

O conteúdo do quarto andar, de Rovia até a sala de Wythege the Hippocampus. **Este pacote já traz
tudo dos andares 1 a 3**: `items.yml`, `spawns.yml`, `shops.yml` e os outros são um arquivo só por
plugin, então os pacotes somam. Quem instala o andar 4 não precisa dos outros três pacotes.

> Os arquivos carregam sem erro e os testes conferem que tudo o que um cita do outro existe
> (`Andar4PackTest` no AincradRPG, no AincradQuests e no AincradBosses). **Nada foi jogado num
> servidor**: level, dano, preço e chance são pontos de partida.

Os arquivos são gerados por `conteudo/tools/montar_andar4.py` a partir dos do pacote do andar 3.
Para mudar o andar 4, edite os blocos do script e rode-o de novo. Para mudar um andar anterior,
edite a origem dele e rode os scripts em ordem (`montar_andar2.py`, `montar_andar3.py`,
`montar_andar4.py`).

## Instalar

1. Copie o conteúdo de `conteudo/andar4/plugins/` para a pasta `plugins/` do servidor,
   substituindo os arquivos (inclusive os dos andares 1 a 3, que vêm iguais).
2. Apague `plugins/AincradQuests/quests/exemplo.yml`, se ainda existir.
3. Ligue o servidor e monte o mapa do andar 4 como abaixo. Os dos andares anteriores continuam
   como estavam ([andar 1](../andar1/README.md), [andar 2](../andar2/README.md),
   [andar 3](../andar3/README.md)).

## 1. Regiões

| Região | O que é | Level dos mobs |
|---|---|---|
| `andar4` | O andar inteiro (o `floors.yml` usa `tipo: regiao`) | — |
| `rovia` | A cidade de Rovia, toda de canais (zona segura) | — |
| `vila_usco` | A vila de Usco, na beira do lago (zona segura) | — |
| `castelo_yofel` | O castelo dos elfos escuros, no lago (zona segura) | — |
| `canais_de_rovia` | Os canais em volta de Rovia | 30 a 32 |
| `floresta_dos_ursos` | A mata na margem dos canais | 32 a 34 |
| `lago_da_caldeira` | O lago entre Usco e Yofel | 34 a 36 |
| `labirinto_andar4` | Os andares de baixo da torre | 36 a 38 |
| `labirinto_andar4_topo` | Os andares de cima, até a sala do Wythege | 38 a 39 |

- As quatro primeiras: `/aincrad varinha` e `/aincrad regiao criar <nome>`.
- As cinco de mobs: `/aincrad spawn` → **Marcar área nova** → escolha o nome na lista (no
  labirinto, "altura exata").
- No ponto de chegada: `/aincrad andar setspawn 4`; no portal de Rovia: `/aincrad gate criar 4`.
- Se o andar 4 fica num mundo próprio, troque no `floors.yml` para `tipo: mundo`.
- O `floors.yml` já traz o andar 5 (Karluin) como reserva: é ele que o Wythege libera.
- Marque as áreas de mobs **nas margens e nas pontes**, não no meio da água: os mobs nascem no
  chão. Os afogados entram na água sozinhos.

## 2. Mobs

| Região | Mob | É um | Level | Quando |
|---|---|---|---|---|
| Canais de Rovia | Water Wight | afogado | 30–31 | sempre |
| | Scuttle Crab | endermite | 30–32 | sempre |
| | Mosquito do Brejo | phantom | 31–32 | de noite |
| Floresta dos Ursos | Urso da Floresta | urso polar | 32–34 | sempre |
| | Lobo do Pântano | lobo | 32–33 | sempre |
| | Caçador Caído | esqueleto | 33–34 | de noite |
| Lago da Caldeira | Lanceiro Afogado | afogado com tridente | 34–36 | sempre |
| | Arqueiro do Lodo | bogged | 34–35 | sempre |
| | Bruxa do Brejo | bruxa | 35–36 | de noite |
| | Pearl Turtle (raro) | tartaruga | 35 | chance baixa |
| Labirinto (baixo) | Fallen Elven Warrior | esqueleto wither com espada | 36–38 | sempre |
| | Guarda Afogado | afogado com espada e escudo | 37–38 | sempre |
| Labirinto (topo) | Fallen Elven Knight | esqueleto wither de armadura | 38–39 | sempre |
| | Fallen Elven Shooter | stray | 38–39 | sempre |

- O urso e o lobo têm `agressivo: true`: o lobo ataca sozinho; o urso parte para cima do jogador
  mais próximo quando nasce (isso é novo no AincradRPG e vale para qualquer mob neutro).
- Os esqueletos wither dão o efeito de definhamento do Minecraft a cada golpe. Se pesar demais,
  troque a linha `mob:` por `SKELETON`, ou por `mythic:NomeDoMob` com o modelo de verdade.
- O **Montante do Elfo Caído** só cai dos Fallen Elven Knights e do chefe.
- O **Biceps Archelon**, chefe de campo, vive no Lago da Caldeira e volta 8 horas depois de derrotado.

## 3. NPCs

| Id | Nome sugerido | Onde | Para quê |
|---|---|---|---|
| `guarda_rovia` | Guarda do Cais | Cais de Rovia | Chegada, os canais, ronda diária |
| `construtor_rovia` | Construtor de Barcos | Rovia | A gôndola, a capa do barqueiro, o caminho de Usco |
| `anciao_usco` | Ancião | Usco | O lago, a garra do urso, a tartaruga, peixe diário |
| `visconde_yofel` | Visconde | Castelo de Yofel | O labirinto e o chefe |

## 4. Lojas

| Id | Onde | Vende |
|---|---|---|
| `mercador_rovia` | Rovia | Comida, tochas, flechas; compra peixe, escamas e conchas |
| `estaleiro_rovia` | Rovia | Gôndola (barco) e gôndola de carga; compra peles |
| `ferreiro_rovia` | Rovia | Sabre, adaga de coral, florete e lança |
| `armeiro_rovia` | Rovia | Elmo, botas e grevas de escamas |
| `mercador_usco` | Usco | Comida, tochas e flechas |
| `intendente_yofel` | Castelo de Yofel | Espada de aço azul, escudo de casco, peitoral de escamas e fruto dourado |

## 5. A sala do Wythege

`/aincrad chefe sala wythege canto1`, `canto2`, `spawn`, e `/aincrad chefe porta wythege` com o
vão da porta marcado. A sala fica fora das regiões do labirinto.

| Fase | Vida | O que muda |
|---|---|---|
| 1 | 100% | Investida e golpe em área |
| 2 | 75% | Quatro Water Wights de tempos em tempos |
| 3 | 45% | O jato d'água: um cone à frente que fere e paralisa por 4 s (a Poção Antiparalisia barra); salto; 15% mais dano |
| 4 | 15% | Maré cheia: 30% mais dano e velocidade, investida mais curta e fúria; sem afogados |

- 3400 de vida, level 40, 20 minutos de tempo limite, cristais bloqueados.
- Cada participante (4% do dano ou mais) recebe 5000 de XP e 3000 Col.
- O Last Attack dá a **Lança do Hipocampo** lendária.
- Derrotá-lo libera o andar 5.
- **A sala não enche de água.** Na história a água sobe durante a luta; o AincradBosses não mexe
  nos blocos da sala. Se quiser o efeito, construa a sala já com água rasa no chão.

## 6. A história

A primeira quest do andar pede o Nerius derrotado e o andar 4 liberado.

| # | Quest | Quem dá | Pede |
|---|---|---|---|
| 1 | A cidade dos canais | Guarda do Cais | Chegar a Rovia |
| 2 | Canais infestados | Guarda do Cais | Level 30; 12 Water Wights |
| 3 | A gôndola | Construtor | Level 32; 10 Ursos e 8 peles — dá um barco |
| 4 | Rio abaixo | Construtor | Level 34; chegar a Usco |
| 5 | O castelo no lago | Ancião | Level 35; 10 Lanceiros e chegar a Yofel |
| 6 | A torre submersa | Visconde | Level 36; 15 Warriors (party) |
| 7 | Os cavaleiros caídos | Visconde | Level 38; 12 Knights (party) |
| 8 | O Hipocampo | Visconde | Derrotar o Wythege (party) |

Fora da linha principal: **A capa do barqueiro** (16 escamas pela Capa do Barqueiro), **A garra do
urso** (25 Ursos pelo Machado de Garra de Urso), **A tartaruga de duas cabeças** (o Biceps
Archelon), as diárias Ronda dos canais e Peixe para Usco, e a semanal Limpeza da torre submersa.

## O que este pacote não tem

- Modelos 3D, falas dos NPCs, estalagem, bancadas e pontos de pesca: como no andar 1.
- Receitas, projetos de forja e fórmulas próprios do andar.
- Gôndolas com regra própria: o barco é o do Minecraft. Nada impede atravessar a nado.
- Mobs que vivem dentro da água (guardiões, peixes): as áreas de mobs nascem em terra.
