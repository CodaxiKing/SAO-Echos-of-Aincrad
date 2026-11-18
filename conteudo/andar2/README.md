# Pacote de conteúdo — Andar 2

O conteúdo do segundo andar, de Urbus até o trono de Asterius the Taurus King. **Este pacote já
traz tudo do andar 1**: `items.yml`, `spawns.yml`, `shops.yml` e os outros são um arquivo só por
plugin, então os pacotes somam. Quem instala o andar 2 não precisa do pacote do andar 1.

> Os arquivos carregam sem erro e os testes conferem que tudo o que um cita do outro existe
> (`Andar2PackTest` no AincradRPG, no AincradQuests e no AincradBosses). **Nada foi jogado num
> servidor**: level, dano, preço e chance são pontos de partida.

Os arquivos são gerados por `conteudo/tools/montar_andar2.py` a partir dos do andar 1. Para mudar
o andar 2, edite os blocos do script e rode-o de novo; para mudar o andar 1, edite
`conteudo/andar1` e rode o script.

## Instalar

1. Copie o conteúdo de `conteudo/andar2/plugins/` para a pasta `plugins/` do servidor,
   substituindo os arquivos (inclusive os do andar 1, que vêm iguais).
2. Apague `plugins/AincradQuests/quests/exemplo.yml`, se ainda existir.
3. Ligue o servidor e monte o mapa do andar 2 como abaixo. O do andar 1 continua como estava
   ([guia do andar 1](../andar1/README.md)).

## 1. Regiões

| Região | O que é | Level dos mobs |
|---|---|---|
| `andar2` | O andar inteiro (o `floors.yml` usa `tipo: regiao`) | — |
| `urbus` | A cidade de Urbus, na mesa de pedra (zona segura) | — |
| `marome` | A vila de Marome (zona segura) | — |
| `taran` | Taran, ao pé do labirinto (zona segura) | — |
| `planalto_urbus` | As mesas de pedra em volta de Urbus | 10 a 12 |
| `pradaria_marome` | A pradaria entre Urbus e Marome | 12 a 14 |
| `desfiladeiro_taran` | O desfiladeiro até Taran | 14 a 16 |
| `labirinto_andar2` | Os andares de baixo da torre | 16 a 18 |
| `labirinto_andar2_topo` | Os andares de cima, até a sala do trono | 18 a 19 |

- As quatro primeiras: `/aincrad varinha` e `/aincrad regiao criar <nome>`.
- As cinco de mobs: `/aincrad spawn` → **Marcar área nova** → escolha o nome na lista (no labirinto,
  "altura exata").
- No ponto de chegada: `/aincrad andar setspawn 2`; no portal de Urbus: `/aincrad gate criar 2`.
- Se o andar 2 fica num mundo próprio, troque no `floors.yml` para `tipo: mundo`.

## 2. Mobs

| Região | Mob | É um | Level | Quando |
|---|---|---|---|---|
| Planalto de Urbus | Trembling Ox | vaca | 10–11 | sempre |
| | Jagged Worm | traça | 11–12 | sempre |
| | Windwasp | phantom | 11–12 | de noite |
| | Golden Ox (raro) | vaca | 11 | chance baixa |
| Pradaria de Marome | Trembling Cow | vaca | 12–13 | sempre |
| | Lobo da Pradaria | lobo | 12–14 | sempre |
| | Arqueiro Errante | esqueleto | 13–14 | de noite |
| Desfiladeiro de Taran | Taurus Scout | piglin bruto | 14–16 | sempre |
| | Andarilho do Desfiladeiro | husk | 14–15 | sempre |
| | Aranha das Pedras | aranha | 15–16 | de noite |
| Labirinto (baixo) | Lesser Taurus Striker | piglin bruto | 16–18 | sempre |
| | Taurus Ironguard | piglin bruto com escudo | 17–18 | sempre |
| Labirinto (topo) | Taurus Ironguard | piglin bruto com escudo | 18–19 | sempre |
| | Taurus Trampler | hoglin | 18–19 | sempre |

- Os Taurus são piglins brutos do Minecraft com nome e arma. Para os modelos de verdade, troque a
  linha `mob:` por `mythic:NomeDoMob` (o menu `/aincrad spawn` lista os do MythicMobs).
- O **Bullbous Bow**, chefe de campo, aparece na Pradaria de Marome e volta 8 horas depois de derrotado.

## 3. NPCs

| Id | Nome sugerido | Onde | Para quê |
|---|---|---|---|
| `guarda_urbus` | Guarda de Urbus | Portão de Urbus | Chegada, bois do planalto, ronda diária |
| `curtidor_urbus` | Curtidor | Urbus | Troca couro por uma couraça |
| `cozinheira_marome` | Cozinheira | Marome | Carne diária, o touro da pradaria, o desfiladeiro |
| `eremita` | Eremita | Perto de Marome | A quest da Lâmina do Eremita |
| `capitao_taran` | Capitão | Taran | O labirinto e o chefe |

## 4. Lojas

| Id | Onde | Vende |
|---|---|---|
| `mercador_urbus` | Urbus | Comida, tochas, flechas; compra o que os mobs do planalto soltam |
| `ferreiro_urbus` | Urbus | Espada de aço, adaga, florete, lança e escudo |
| `armeiro_urbus` | Urbus | Couraça de couro de boi, elmo e botas de ferro |
| `ferreiro_taran` | Taran | Montante de aço, peitoral e grevas de ferro |
| `mercador_taran` | Taran | Comida e tochas |

## 5. A sala do Asterius

`/aincrad chefe sala asterius canto1`, `canto2`, `spawn`, e `/aincrad chefe porta asterius` com o
vão da porta marcado. A sala fica fora das regiões do labirinto.

| Fase | Vida | O que muda |
|---|---|---|
| 1 | 100% | Baran the General Taurus (um golem) e os Nato the Colonel Taurus; pisão em área |
| 2 | 70% | O Rei desce do trono: investida, e mais Coronéis |
| 3 | 40% | O sopro: um cone à frente que fere e paralisa por 4 s (a Poção Antiparalisia barra); 15% mais dano |
| 4 | 15% | Fúria: 30% mais dano e velocidade, salto; sem lacaios |

- 1600 de vida, level 20, 20 minutos de tempo limite, cristais bloqueados.
- Cada participante (4% do dano ou mais) recebe 2400 de XP e 1500 Col.
- O Last Attack dá a **Crown of the Taurus King** lendária.
- Derrotá-lo libera o andar 3.

## 6. A história

A primeira quest do andar pede o Illfang derrotado e o andar 2 liberado.

| # | Quest | Quem dá | Pede |
|---|---|---|---|
| 1 | A cidade na mesa de pedra | Guarda de Urbus | Chegar a Urbus |
| 2 | Os bois do planalto | Guarda de Urbus | Level 10; 10 Trembling Oxen |
| 3 | O caminho para Marome | Guarda de Urbus | Level 12; chegar a Marome |
| 4 | O desfiladeiro | Cozinheira | Level 14; 8 Taurus Scouts e chegar a Taran |
| 5 | Os golpeadores | Capitão | Level 16; 15 Strikers (party) |
| 6 | A guarda do Rei | Capitão | Level 18; 12 Ironguards (party) |
| 7 | O Rei Taurus | Capitão | Derrotar o Asterius (party) |

Fora da linha principal: **Couro para Urbus** (12 couros por uma couraça), **A rocha do eremita**
(25 Jagged Worms pela Lâmina do Eremita), **O touro da pradaria** (o Bullbous Bow), as diárias
Ronda do planalto e Carne para Marome, e a semanal Limpeza da torre dos Taurus.

## O que este pacote não tem

- Modelos 3D, falas dos NPCs, estalagem, bancadas e pontos de pesca: como no andar 1.
- Receitas, projetos de forja e fórmulas próprios do andar.
