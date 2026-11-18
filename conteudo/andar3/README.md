# Pacote de conteúdo — Andar 3

O conteúdo do terceiro andar, de Zumfut até a sala de Nerius the Evil Treant. **Este pacote já
traz tudo dos andares 1 e 2**: `items.yml`, `spawns.yml`, `shops.yml` e os outros são um arquivo só
por plugin, então os pacotes somam. Quem instala o andar 3 não precisa dos outros dois pacotes.

> Os arquivos carregam sem erro e os testes conferem que tudo o que um cita do outro existe
> (`Andar3PackTest` no AincradRPG, no AincradQuests e no AincradBosses). **Nada foi jogado num
> servidor**: level, dano, preço e chance são pontos de partida.

Os arquivos são gerados por `conteudo/tools/montar_andar3.py` a partir dos do pacote do andar 2.
Para mudar o andar 3, edite os blocos do script e rode-o de novo. Para mudar o andar 1 ou o 2,
edite a origem deles, rode `montar_andar2.py` e depois `montar_andar3.py`.

## Instalar

1. Copie o conteúdo de `conteudo/andar3/plugins/` para a pasta `plugins/` do servidor,
   substituindo os arquivos (inclusive os dos andares 1 e 2, que vêm iguais).
2. Apague `plugins/AincradQuests/quests/exemplo.yml`, se ainda existir.
3. Ligue o servidor e monte o mapa do andar 3 como abaixo. Os dos andares 1 e 2 continuam como
   estavam ([guia do andar 1](../andar1/README.md), [guia do andar 2](../andar2/README.md)).

## 1. Regiões

| Região | O que é | Level dos mobs |
|---|---|---|
| `andar3` | O andar inteiro (o `floors.yml` usa `tipo: regiao`) | — |
| `zumfut` | A cidade de Zumfut, dentro das árvores gigantes (zona segura) | — |
| `acampamento_elfico` | O acampamento dos elfos escuros (zona segura) | — |
| `posto_avancado` | O posto da linha de frente, ao pé do labirinto (zona segura) | — |
| `floresta_da_nevoa` | A floresta em volta de Zumfut | 20 a 22 |
| `bosque_elfico` | A terra disputada pelos elfos | 22 a 24 |
| `caverna_das_aranhas` | A caverna da Nephila Regina | 24 a 26 |
| `labirinto_andar3` | Os andares de baixo da torre | 26 a 28 |
| `labirinto_andar3_topo` | Os andares de cima, até a sala do Nerius | 28 a 29 |

- As quatro primeiras: `/aincrad varinha` e `/aincrad regiao criar <nome>`.
- As cinco de mobs: `/aincrad spawn` → **Marcar área nova** → escolha o nome na lista (na caverna e
  no labirinto, "altura exata").
- No ponto de chegada: `/aincrad andar setspawn 3`; no portal de Zumfut: `/aincrad gate criar 3`.
- Se o andar 3 fica num mundo próprio, troque no `floors.yml` para `tipo: mundo`.
- O `floors.yml` já traz o andar 4 (Rovia) como reserva: é ele que o Nerius libera.

## 2. Mobs

| Região | Mob | É um | Level | Quando |
|---|---|---|---|---|
| Floresta da Névoa | Treant Sapling | creaking | 20–21 | sempre |
| | Lobo da Névoa | lobo | 20–22 | sempre |
| | Thicket Spider | aranha das cavernas | 21–22 | de noite |
| | Jade Fox (raro) | raposa | 21 | chance baixa |
| Bosque Élfico | Forest Elven Scout | vindicator com espada | 22–23 | sempre |
| | Forest Elven Archer | pillager | 23–24 | sempre |
| | Espectro da Névoa | stray | 23–24 | de noite |
| Caverna das Aranhas | Thicket Spider | aranha das cavernas | 24–25 | sempre |
| | Aranha Tecelã | aranha | 25–26 | sempre |
| Labirinto (baixo) | Elder Treant | creaking | 26–28 | sempre |
| | Evil Treant Seedling | bogged | 27–28 | sempre |
| Labirinto (topo) | Forest Elven Knight | vindicator com espada e elmo | 28–29 | sempre |
| | Evil Treant Seedling | bogged | 28–29 | sempre |

- Os Treants são **creakings**: só andam quando nenhum jogador olha para eles. Se isso atrapalhar,
  troque a linha `mob:` por outro tipo, ou por `mythic:NomeDoMob` com o modelo de verdade (o menu
  `/aincrad spawn` lista os do MythicMobs).
- Os elfos da floresta são illagers do Minecraft com nome e arma. Os elfos escuros são NPCs.
- O **Manto de Seda de Aranha** só cai das Aranhas Tecelãs e da rainha delas.
- A **Nephila Regina**, chefe de campo, vive na Caverna das Aranhas e volta 8 horas depois de derrotada.

## 3. NPCs

| Id | Nome sugerido | Onde | Para quê |
|---|---|---|---|
| `guarda_zumfut` | Guarda de Zumfut | Entrada de Zumfut | Chegada, os Treants, os elfos, ronda diária |
| `lenhador_zumfut` | Lenhador | Zumfut | Troca troncos pelo Machado do Lenhador |
| `escrivao_zumfut` | Escrivão | Zumfut | A quest Selo da Guilda |
| `kizmel` | Kizmel | Acampamento élfico | A chave de jade, o sabre, a rainha das aranhas |
| `comandante_elfico` | Comandante | Acampamento élfico | Os fragmentos, o caminho da torre, seda diária |
| `capitao_posto` | Capitão | Posto avançado | O labirinto e o chefe |

## 4. Lojas

| Id | Onde | Vende |
|---|---|---|
| `mercador_zumfut` | Zumfut | Comida, tochas, flechas; compra troncos, ossos e fragmentos de jade |
| `ferreiro_zumfut` | Zumfut | Espada élfica, adaga, florete e escudo |
| `armeiro_zumfut` | Zumfut | Elmo, botas e grevas élficas |
| `intendente_elfico` | Acampamento élfico | Lança, montante, cota élfica e fruto dourado; compra seda e teia |
| `mercador_posto` | Posto avançado | Comida, tochas e flechas; compra resina e musgo |

## 5. A sala do Nerius

`/aincrad chefe sala nerius canto1`, `canto2`, `spawn`, e `/aincrad chefe porta nerius` com o vão
da porta marcado. A sala fica fora das regiões do labirinto.

| Fase | Vida | O que muda |
|---|---|---|
| 1 | 100% | Espinhos (flechas) de longe e golpe em área |
| 2 | 70% | Três Evil Treant Seedlings de tempos em tempos; espinhos mais rápidos |
| 3 | 40% | O pólen: um cone à frente que fere e paralisa por 5 s (a Poção Antiparalisia barra); 15% mais dano |
| 4 | 15% | Arranca as raízes: 30% mais dano, 50% mais velocidade, salto e fúria; sem sementes |

- 2400 de vida, level 30, 20 minutos de tempo limite, cristais bloqueados.
- O Nerius é um golem de ferro bem lento até a última fase: a luta é de desviar e cuidar das sementes.
- Cada participante (4% do dano ou mais) recebe 3600 de XP e 2200 Col.
- O Last Attack dá as **Roots of Nerius** lendárias (botas).
- Derrotá-lo libera o andar 4.

## 6. A história

A primeira quest do andar pede o Asterius derrotado e o andar 3 liberado.

| # | Quest | Quem dá | Pede |
|---|---|---|---|
| 1 | A cidade nas árvores | Guarda de Zumfut | Chegar a Zumfut |
| 2 | A floresta que anda | Guarda de Zumfut | Level 20; 12 Treant Saplings |
| 3 | A cavaleira na névoa | Guarda de Zumfut | Level 21; 6 Scouts e chegar ao acampamento |
| 4 | A chave de jade | Kizmel | Level 23; 10 Archers e 5 fragmentos de jade (esmeraldas) |
| 5 | A linha de frente | Comandante | Level 25; chegar ao posto avançado |
| 6 | Raízes da torre | Capitão | Level 26; 15 Elder Treants (party) |
| 7 | Os cavaleiros da floresta | Capitão | Level 28; 12 Knights (party) |
| 8 | O Treant maligno | Capitão | Derrotar o Nerius (party) |

Fora da linha principal: **Selo da Guilda** (level 20; 15 Lobos da Névoa e 3 fragmentos de jade —
é a quest que o AincradSocial pede para fundar uma guilda), **Lenha para Zumfut** (16 troncos pelo Machado do Lenhador), **O sabre da
cavaleira** (20 Aranhas Tecelãs pelo Sabre da Cavaleira), **A rainha das aranhas** (a Nephila
Regina), as diárias Ronda da floresta e Seda para o acampamento, e a semanal Limpeza da torre oca.

A guerra dos elfos aqui é uma versão curta: o jogador sempre fica do lado dos elfos escuros. Não há
escolha de lado nem a campanha que segue pelos andares seguintes.

## O que este pacote não tem

- Modelos 3D, falas dos NPCs, estalagem, bancadas e pontos de pesca: como no andar 1.
- Receitas, projetos de forja e fórmulas próprios do andar.
- Veneno: o chefe paralisa, mas não envenena (o AincradBosses não tem essa habilidade).
