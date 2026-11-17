# AincradLife — detalhes e tarefas

O AincradLife tem sete módulos que ligam e desligam na config: culinária, pesca, ferreiro, alquimia, estalagens, familiares e casamento. As quatro profissões usam a mesma regra de proficiência do anime, de 0 a 1000. Em relação ao plano principal, o ferreiro cresceu: além do reparo, ganhou **forja** e **aprimoramento**, como a oficina da Lisbeth.

Dependências: `depend: AincradCore`; `softdepend: AincradRPG, AincradEconomy, AincradFloors, AincradEffects, AincradUI`. Sem o Economy, os custos em Col viram custos em item. Sem o RPG, os itens forjados saem sem raridade.

## Proficiência das profissões

Cada profissão tem um número de 0 a 1000 por jogador, que sobe só com o uso. O jogador pode praticar as quatro; não há limite de profissões.

| Rank | Proficiência | O que libera |
| --- | --- | --- |
| Aprendiz | 0 | Receitas, peixes e serviços básicos |
| Praticante | 250 | Itens de rank C, reparo mais barato |
| Especialista | 500 | Itens de rank B, forja de armas raras, aprimoramento até +5 |
| Mestre | 750 | Itens de rank A, aprimoramento até +10 |
| Completo | 1000 | Itens de rank S, título no AincradReputation (ex.: "Mestre-Cuca") |

- Ganho: cada ação dá pontos conforme a dificuldade do item em relação à proficiência atual. Item fácil demais dá 0; item no limite dá o máximo. Valores em `professions.yml`.
- Falha também dá pontos, metade do normal.
- `/profissoes` abre o menu com as três barras, o rank e o que falta para o próximo.
- Dados: `life_professions` (uuid, profissão, proficiência). Evento `ProfessionRankUpEvent`.

**Aprender antes de fazer (vale para projetos de forja, receitas de culinária e fórmulas de alquimia)**

Ter a proficiência não basta: o artesão só produz o que já aprendeu. São duas travas separadas.

1. **Aprender**: cada projeto, receita ou fórmula existe como um item (pergaminho, livro de receitas, planta). Usá-lo ensina o conteúdo e consome o item. Só dá para aprender com a proficiência mínima de estudo, que é menor que a de produção.
2. **Produzir**: exige a proficiência mínima de produção, definida em cada item. Entre as duas, o artesão já conhece o item, mas ele aparece como "proficiência insuficiente".
3. **Onde conseguir**: lojas de NPC (os básicos), drops de mobs e de chefes, baús do labirinto, recompensas de quest e descoberta por experimento (combinações secretas).
4. **Transcrever**: no rank Mestre, o artesão copia algo que conhece para um pergaminho em branco, com custo alto em Col e materiais, e pode vender a cópia. Itens marcados como `unico: true` não podem ser transcritos.
5. **O que não se conhece** aparece como "???" nos painéis, só com a categoria.

Dados: `life_known` (uuid, tipo, id, quando aprendeu).

## Árvore de especialização

Cada profissão tem uma árvore com duas escolhas sem volta: o caminho, na proficiência 250, e a especialização, na 500. Escolher um ramo fecha os outros para sempre, então nenhum artesão faz tudo e os jogadores precisam uns dos outros.

&#91;embedded content: árvore do ferreiro · 3 caminhos, 6 especializações\]

O ferreiro como exemplo: um Armeiro de Lâminas nunca forjará armadura rara nem machado raro. As outras três árvores têm a mesma forma.

**Regras**

1. **Tronco comum** (0 a 250): nós que todo artesão pode ter. Itens do tronco continuam disponíveis para todos os caminhos.
2. **Caminho** (250): o jogador escolhe um entre três. Os outros dois ficam marcados como "caminho fechado" e seus itens exclusivos nunca poderão ser aprendidos.
3. **Especialização** (500): dentro do caminho, uma entre duas, também sem volta.
4. **Nó final** (1000): um nó único por especialização, com a técnica mais forte do ramo.
5. **Pontos de ofício**: 1 ponto a cada 50 de proficiência, 20 no total. O ramo escolhido tem mais nós do que pontos, então dois artesãos do mesmo ramo ainda ficam diferentes. Ponto gasto não volta.
6. **Tipos de nó**: passivo (janela de acerto maior, custo menor, mais porções), desbloqueio (uma categoria de projetos, receitas ou fórmulas exclusiva do ramo) e técnica ativa (ex.: "Golpe de Mestre", uma batida perfeita garantida por forja).
7. **Confirmação**: escolher caminho ou especialização pede duas confirmações, com o aviso do que será fechado. Só um admin desfaz, com `/aincrad arvore resetar <jogador> <profissão>`, e a ação fica registrada.
8. **Contratos**: o quadro de encomendas mostra o caminho e a especialização de cada artesão, e só aceita proposta de quem pode fazer o item pedido.

**Caminhos e especializações**

| Profissão | Caminho | Especializações | Exclusivo do caminho |
| --- | --- | --- | --- |
| Ferreiro | Armeiro | Lâminas; Armas pesadas | Armas de rank B ou maior |
| Ferreiro | Armoreiro | Placas; Couraças leves | Armaduras e escudos de rank B ou maior |
| Ferreiro | Refinador | Afiador; Restaurador | Aprimoramento acima de +5, reparo de itens épicos e lendários |
| Cozinheiro | Chef de banquete | Festins; Cozinha de campanha | Pratos de panela com buff para a party inteira |
| Cozinheiro | Gourmet | Carnes; Doces e bebidas | Pratos individuais de rank A e S |
| Cozinheiro | Provisionador | Conservas; Rações de viagem | Comida que não estraga e buffs longos |
| Pescador | Águas profundas | Caçador de lendários; Mergulhador | Zona funda e o Senhor do Lago |
| Pescador | Mestre de iscas | Criador de iscas; Peixeiro | Iscas próprias, frescor dobrado na banca |
| Pescador | Caça-tesouros | Baús; Materiais raros | Pescar baús, minérios e reagentes |
| Alquimista | Curandeiro | Poções; Cristais | Cura de grau alto, cristais de cura e antídoto |
| Alquimista | Toxicologista | Venenos; Óleos de arma | Venenos e óleos de grau alto |
| Alquimista | Artífice | Arremessáveis; Insumos | Frascos de área e insumos para as outras profissões |

**A árvore na tela (mesma lógica do AincradUI)**

- Abre pelo menu principal, em Skills, ou na própria estação de trabalho. Flutua na frente do jogador, com o cursor.
- Cada nó é um ícone em anel, como os do menu: cinza para disponível, laranja (versão `_on`) para comprado, cadeado para bloqueado por proficiência e riscado para caminho fechado. Linhas ligam os nós; as do ramo escolhido ficam laranja.
- Passar o cursor mostra a janela de detalhes (efeito, custo, requisito). Clicar abre a confirmação com os botões sim e não do pacote de artes e o som de confirmar.
- Sem resource pack ou no baú: uma página por nível da árvore. No Bedrock: formulário com a lista de nós por nível.

Dados: `life_tree` (uuid, profissão, nó, quando). Árvores em `trees/<profissão>.yml` (nós, posição na tela, custo, requisitos, o que fecha).

## Bancadas em 3D (modelos feitos pelo dono do servidor)

As estações de trabalho das quatro profissões serão modelos 3D próprios, criados pelo dono do servidor, e não blocos comuns do Minecraft. O plugin só posiciona e usa os modelos; a arte chega depois, e até lá tudo funciona com um bloco provisório.

**Modelos previstos**

| Profissão | Bancada | Id do modelo | Bloco provisório |
| --- | --- | --- | --- |
| Ferreiro | Bigorna de forja | `aincrad:station/forge_anvil` | Bigorna |
| Ferreiro | Fornalha com fole | `aincrad:station/forge_furnace` | Alto-forno |
| Ferreiro | Barril de têmpera | `aincrad:station/quench_barrel` | Caldeirão com água |
| Cozinheiro | Bancada de cozinha | `aincrad:station/kitchen` | Defumador |
| Alquimista | Mesa de alquimia | `aincrad:station/alchemy_table` | Suporte de poções |
| Pescador | Banca de peixe | `aincrad:station/fish_stall` | Barril |
| Todas | Quadro de encomendas | `aincrad:station/order_board` | Estante com placa |

**Como o plugin usa os modelos**

1. Cada bancada é uma entidade Item Display com o modelo, mais uma entidade Interaction para o clique e uma barreira invisível para o jogador não atravessar. `/aincrad estacao criar <tipo>` coloca o conjunto virado para o jogador; `/aincrad estacao remover` tira.
2. `stations.yml` define, por tipo: id do modelo, escala, tamanho da área de clique, bloco provisório e os **pontos de ancoragem** (onde ficam os encaixes, o holograma e a marca das batidas, em relação ao centro do modelo). Assim dá para ajustar a posição quando o modelo final chegar, sem mexer no código.
3. Estados opcionais: se existir o modelo com sufixo `_active` (ex.: fornalha acesa), o plugin troca para ele durante o uso.
4. Os arquivos vão na pasta `resourcepack/` do aincrad-life e entram no pack único pelo `registerPackFolder` do AincradEffects.
5. Sem resource pack e no Bedrock, aparece o bloco provisório no lugar do modelo, com as mesmas funções.

**O que o dono precisa entregar por modelo**

- Modelo no formato de item do Minecraft Java (feito no Blockbench, exportado como "Java Block/Item"), com a textura em PNG.
- Tamanho pensado para ocupar 1 bloco (ou 2 de largura, no caso do quadro e da banca), com a frente virada para o sul no Blockbench.
- Textura de até 64x64 por modelo, para o pack não pesar.
- Opcional: a versão `_active` e uma indicação de onde ficam os encaixes (um print marcado já serve).

Nota para a implementação: confirmar na versão 1.21.11 a forma atual de ligar um item a um modelo próprio (componente `item_model` e arquivos em `assets/aincrad/items/`) antes de montar o pack.

## Culinária

Cozinhar transforma ingredientes em pratos que dão um buff temporário. A qualidade do prato depende da proficiência, como a comida da Asuna.

1. **Bancada**: um bloco marcado como cozinha (`/aincrad cozinha criar`), nas cidades e nas casas. Clicar abre o menu de receitas.
2. **Ingredientes** em `ingredients.yml`: cada um tem rank de E a S e origem (drop de mob, pesca, loja, colheita). Ingredientes de rank S só caem de mobs raros, como o coelho do ragoû no anime.
3. **Receitas** em `recipes.yml`: ingredientes, proficiência mínima, dificuldade, tempo de preparo (3 a 15 s com barra de progresso) e o buff do prato.
4. **Qualidade**: sorteada entre queimado, comum, bom e excelente. A chance de "excelente" cresce com a proficiência acima da dificuldade. Queimado não dá buff. Excelente dá buff 50% mais longo.
5. **Buffs**: força, regeneração, velocidade, vida máxima, XP extra ou sorte na pesca, por 10 a 30 min. Só um buff de comida ativo por vez; comer outro prato troca.
6. **Receitas secretas**: algumas não aparecem no menu. O jogador descobre ao combinar os ingredientes certos no modo livre, como a Asuna recriando o molho de soja.
7. **Compartilhar**: o prato é um item comum. Pode ser dado, vendido na loja ou no leilão.

Exemplo de receita:

```yaml
ensopado_de_coelho:
  nome: "Ensopado de Coelho"
  ingredientes: {carne_coelho_raro: 1, cenoura: 2, tempero_basico: 1}
  proficiencia_minima: 500
  dificuldade: 600
  preparo_segundos: 12
  buff: {tipo: vida_maxima, valor: 20, minutos: 30}
  secreta: false
```

**Preparo sobre a bancada (substitui a barra de preparo simples)**

Como na forja, cozinhar acontece num painel flutuante sobre a bancada, sem menu de baú.

1. **Encaixes do prato**: Base, Proteína, Vegetal, Tempero e Especial (opcional). O que entra em cada encaixe define o prato. Combinação conhecida mostra o nome da receita; combinação nova mostra "Prato desconhecido" e pode revelar uma receita secreta.
2. **Etapas**: o holograma mostra as etapas da receita, uma por vez. Receita simples tem 2 etapas; prato de rank S tem 5.
   - Cortar: clicar no ritmo das marcas que passam pela tábua.
   - Fogo: manter a chama na faixa certa, colocando lenha (sobe) ou abafando (desce), pelo tempo pedido.
   - Mexer: girar o olhar em volta da panela no sentido indicado.
   - Temperar: uma marca aparece e some; adicionar o tempero enquanto ela está acesa.
   - Empratar: um último clique no tempo certo, que pode dar o bônus "excelente".
3. **Qualidade**: a soma dos acertos decide entre queimado, comum, bom e excelente. Errar a etapa do fogo por muito tempo queima o prato na hora.
4. **Porções**: receitas de panela rendem de 2 a 6 porções iguais, para a party inteira.

**Ecossistema da culinária**

- **Barraca ou restaurante**: numa casa do Economy, o cozinheiro monta um cardápio com preços e estoque. Os clientes compram mesmo com ele offline.
- **Banquete de raid**: o líder de uma raid marcada no quadro do AincradBosses encomenda pratos de buff para todos os inscritos. Vira um contrato com prazo até a hora da raid.
- **Fornecimento recorrente**: contrato diário ou semanal (ex.: 10 pratos por dia para uma guilda), cobrado a cada entrega.
- **Fornecedores**: caçadores entregam carne e drops, pescadores entregam peixe, pelo mesmo sistema de pedidos de material.

## Pesca

Pescar dá peixes que servem de ingrediente, de item de venda e, raramente, de desafio. Inspirado no lago do andar 22.

1. **Pontos de pesca**: regiões do core marcadas por andar em `fishing.yml`. Fora delas, a pesca é a normal do Minecraft.
2. **Vara e isca**: varas com rank (compradas ou forjadas) e iscas consumíveis. Cada uma aumenta a chance de peixe raro.
3. **Fisgada**: quando o peixe morde, toca um som e aparece um aviso. O jogador tem uma janela curta para puxar (clique). Peixe mais raro, janela menor. No Bedrock a janela é maior, por causa do toque.
4. **Tabela por andar**: comum, incomum, raro e um **lendário por andar**. A chance do lendário exige rank Mestre e a isca certa.
5. **Senhor do Lago**: fisgar o lendário não entrega o peixe. Ele sai da água como um mini-chefe (mob com level do RPG) que precisa ser derrotado. Quem fisgou ganha o item e a conquista.
6. **Uso**: peixes entram em receitas, vendem bem nas lojas e têm um registro em `/profissoes` com o maior de cada espécie.

**A pescaria em três fases (substitui a fisgada simples do item 3)**

Um holograma sobre a boia acompanha as fases.

1. **Lançar**: segurar o clique enche uma barra de força. A distância do lançamento escolhe a zona do ponto de pesca: raso (peixes comuns), meio e fundo (raros). Zonas fundas exigem vara de rank maior.
2. **Fisgar**: som e aviso na mordida, com a janela de tempo por raridade.
3. **Cansar o peixe**: aparece a barra de tensão da linha. Puxar sobe a tensão; soltar desce. O jogador mantém a tensão na faixa verde enquanto a barra de força do peixe esvazia. Tensão no vermelho arrebenta a linha (perde a isca); tensão baixa demais solta o peixe.
   - Peixe raro dá arrancadas para um lado: o jogador vira o olhar para o lado oposto, senão a tensão dispara.
   - A proficiência alarga a faixa verde; a vara aumenta a resistência da linha.
4. **Bedrock**: só as fases de fisgar e de tensão, com botões de puxar e soltar.

**Ecossistema da pesca**

- **Pescador como fornecedor**: cozinheiros abrem pedidos de peixe no quadro de coleta; o pescador reserva e entrega.
- **Banca de peixe**: como a barraca do cozinheiro, com estoque e preço. Peixe tem frescor: vale o preço cheio por 24 h e metade depois (configurável).
- **Torneio semanal**: inscrição paga em Col, vence o maior peixe da semana por andar. O prêmio é o total das inscrições menos a taxa, e o vencedor ganha um título.
- **Caçada ao Senhor do Lago**: o pescador que tem a isca certa contrata aventureiros pelo quadro para derrotar o mini-chefe quando ele sair da água. O contrato define como o pagamento e os drops são divididos.

## Ferreiro

O ferreiro faz três serviços numa oficina (bigorna e fornalha marcadas com `/aincrad oficina criar`): reparo, forja e aprimoramento. Os três dão proficiência.

**Reparo (manutenção)**

- Restaura a durabilidade de arma ou armadura. Custo em Col e em material do mesmo tipo do item, proporcional ao desgaste.
- A proficiência reduz o custo em até 50% no rank Completo.
- Item com durabilidade zerada quebra de vez (configurável). Aviso na tela quando a durabilidade passa de 10%.
- NPCs ferreiros das cidades também reparam, pelo preço cheio.

**Forja**

A forja não usa menu de baú. Tudo acontece em cima da bigorna, num painel flutuante no estilo do AincradUI, e quem está na oficina vê o trabalho acontecer.

1. **Abrir a mesa de forja**: clicar na bigorna da oficina com um martelo de ferreiro na mão. Sobe um anel de ícones com as categorias: espada de uma mão, florete, adaga, machado, lança, espada de duas mãos, escudo e armadura.
2. **Escolher o projeto**: cada categoria lista os projetos que aquele ferreiro conhece. Projetos vêm da proficiência, de lojas, de drops e de quests. Projeto desconhecido aparece como "???".
3. **Encaixes**: o projeto abre os encaixes daquele tipo de item, flutuando ao redor da bigorna. O ferreiro clica num encaixe com o material na mão, e o material fica visível ali.

| Tipo de item | Encaixes | Batidas base |
| --- | --- | --- |
| Adaga | Lâmina, Cabo, Catalisador (opcional) | 10 |
| Espada de uma mão, florete | Lâmina, Núcleo, Guarda, Cabo, Catalisador (opcional) | 20 |
| Machado, lança | Cabeça, Núcleo, Haste, Catalisador (opcional) | 25 |
| Espada de duas mãos | Lâmina x2, Núcleo, Guarda, Cabo, Catalisador (opcional) | 35 |
| Escudo, peça de armadura | Placa x2, Forro, Rebite, Catalisador (opcional) | 30 |

4. **O material define o item**, como num crafting. O mesmo projeto com materiais diferentes dá armas diferentes:
   - Lâmina, cabeça ou placa (lingote): material base e teto de raridade.
   - Núcleo (lingote ou cristal): atributo dominante, entre afiação, rapidez, precisão e peso.
   - Guarda, haste, forro e rebite: durabilidade e defesa.
   - Cabo (couro, madeira, tecido): velocidade de ataque.
   - Catalisador (drop raro ou de chefe): efeito especial e nome próprio.
   - Combinações secretas em `forge.yml` geram **armas nomeadas**, únicas por combinação, que o ferreiro descobre testando.
5. **Holograma de batidas**: com os encaixes obrigatórios cheios, aparece sobre a bigorna o painel da forja, com o nome do item, as batidas feitas e as necessárias, a barra de calor e a barra de qualidade. As batidas necessárias são a base do tipo, multiplicada pelo rank do lingote.

```
        Espada de Aço Negro
     Batidas  12 / 30
     Calor    [#######---]  ideal
     Qualidade [######----] 61%
```

6. **Calor**: o lingote precisa estar na faixa ideal. O calor cai a cada batida e com o tempo. Para subir, o ferreiro usa o fole da fornalha (cliques). Batida fora da faixa conta como ruim. Itens grandes pedem reaquecer duas ou três vezes.
7. **Batidas (o desafio)**: a cada batida surge uma marca brilhante num ponto da peça (esquerda, centro ou direita) e um anel que se fecha sobre ela. O ferreiro mira na marca e bate quando o anel fecha.
   - Perfeita (no ponto e no tempo): sobe bastante a qualidade, com faísca e som agudo.
   - Boa (ponto certo, tempo quase): sobe pouco.
   - Ruim (ponto errado, fora do tempo ou calor errado): baixa a qualidade e soma uma trinca.
   - Três trincas quebram a peça: a forja falha e metade dos materiais se perde.
   - A velocidade do anel cresce com o rank do lingote. A proficiência alarga a janela da batida perfeita.
8. **Têmpera** (só para itens raros ou melhores): ao fim das batidas, a peça brilha e o ferreiro tem 3 s para mergulhá-la no barril de água. No tempo certo, bônus de durabilidade; atrasado, perde 10% de qualidade.
9. **Resultado**: o item surge sobre a bigorna. A qualidade final decide a raridade dentro do teto do lingote e o número de tentativas de aprimoramento (qualidade acima de 90% dá 2 tentativas extras). O nome do ferreiro fica gravado no item.
10. **Bedrock e sem resource pack**: os encaixes viram um formulário, o holograma vira texto flutuante simples e a batida usa só o tempo, sem a mira.

**Aprimoramento**

1. Cada arma tem um número fixo de tentativas (ex.: 8), definido ao ser criada. Cada tentativa, com sucesso ou não, gasta uma.
2. O jogador escolhe um de cinco atributos: Afiação (dano), Rapidez (velocidade de ataque), Precisão (crítico), Peso (empurrão e quebra de guarda) e Durabilidade.
3. A chance base depende da proficiência e do nível atual da arma. Materiais extras do atributo aumentam a chance até o teto de 95%.
4. Sucesso: +1 no atributo; o nome mostra o total (ex.: "Espada de Aço +4").
5. Falha, sorteada entre três resultados: só perde os materiais; o atributo cai 1; ou o +1 vai para outro atributo. A arma nunca é destruída.
6. Limite: +5 no rank Especialista e +10 no Mestre.

O aprimoramento usa o mesmo painel sobre a bigorna, com uma sequência curta de 5 batidas: cada batida perfeita soma 2% à chance de sucesso.

**Serviço para outros jogadores**: reparo, forja e aprimoramento podem ser vendidos pelo sistema de encomendas e contratos, descrito abaixo.

Dados gravados no próprio item: tentativas restantes, níveis por atributo e nome do ferreiro.

## Alquimia

O alquimista produz poções, antídotos, venenos, óleos de arma e frascos arremessáveis numa mesa de alquimia (`/aincrad alquimia criar`). Poção cura aos poucos; cristal cura na hora e custa muito mais. É essa diferença que dá valor aos dois.

**Fórmulas**

- Toda produção parte de uma fórmula aprendida, pela regra de "aprender antes de fazer". Cada fórmula em `formulas.yml` tem proficiência de estudo, proficiência de produção, reagentes, ordem de adição e resultado.
- A fórmula aprendida fica no livro do alquimista (`/alquimia livro`), que mostra a ordem dos reagentes e as cores esperadas em cada etapa.

**Encaixes da mesa**

| Encaixe | O que recebe | O que define |
| --- | --- | --- |
| Frasco | Frasco de vidro, cristal ou reforçado | Número de doses e teto de potência |
| Base | Água pura, óleo ou álcool | Tipo: poção de beber, óleo de arma ou frasco arremessável |
| Reagente principal | Erva, cogumelo ou parte de mob | O efeito |
| Reagente secundário | Erva ou mineral | Duração ou intensidade |
| Catalisador (opcional) | Pó de cristal ou drop raro | Efeito extra ou versão superior |

**Preparo sobre a mesa**

1. **Moer**: cliques no ritmo sobre o almofariz. Quanto mais regular, mais fino o pó e maior a pureza inicial.
2. **Infusão**: o líquido muda de cor. O alquimista adiciona cada reagente quando o líquido chega à cor que a fórmula pede. Reagente na cor errada reduz a pureza; ordem errada estraga a mistura.
3. **Destilar**: manter a temperatura na faixa certa enquanto a barra de pureza enche. Quente demais por 3 s e a mistura explode: dano leve em quem está perto e perda dos reagentes.
4. **Engarrafar**: o líquido brilha por um instante. Engarrafar nesse momento dá o bônus "perfeita".

O holograma mostra a etapa, a cor atual, a temperatura e a pureza.

**Pureza**

| Pureza | Resultado |
| --- | --- |
| Impura | Efeito pela metade e um efeito colateral leve (ex.: lentidão por 5 s) |
| Comum | Efeito normal |
| Pura | Efeito 25% mais forte ou mais longo |
| Perfeita | Efeito 50% mais forte e uma dose extra no frasco |

**O que o alquimista produz**

- **Poções de cura**: recuperam vida ao longo de 10 s, em quatro graus. Recarga compartilhada de 5 s entre poções.
- **Antídotos e resistências**: veneno, paralisia, fogo, frio.
- **Poções de buff**: força, defesa, velocidade, visão. O jogador pode ter um buff de comida e um de poção ao mesmo tempo, não dois de poção.
- **Óleos de arma**: aplicados na lâmina, dão dano extra por um número de golpes.
- **Venenos**: aplicados na arma, causam veneno ou paralisia em mobs. Usar em jogador conta como ataque no AincradReputation.
- **Arremessáveis**: frasco de fumaça, de luz e de lentidão em área.
- **Insumos para as outras profissões**: isca avançada de pesca, tempero raro de culinária e pó de têmpera, que alarga a janela da têmpera na forja.
- **Cristais básicos**: no rank Mestre, o alquimista transforma cristal bruto em cristal de cura ou de antídoto do AincradItems. Teleporte e ressurreição não são fabricáveis.

**Reagentes e coleta**: ervas e cogumelos nascem em pontos de coleta por andar (`reagents.yml`, regiões do core com tempo de renascimento). Qualquer jogador colhe; quem tem proficiência em alquimia colhe mais e identifica reagentes raros. Partes de mobs vêm de drops.

**Ecossistema da alquimia**

- **Botica**: barraca com estoque e preço, como a do cozinheiro.
- **Kit de raid**: uma guilda encomenda um conjunto por membro (ex.: 5 poções de cura e 2 antídotos) com prazo até a raid.
- **Fornecimento recorrente** para guildas da linha de frente.
- **Coletores**: aventureiros entregam ervas e partes de mobs pelos pedidos de material.
- **Venda de fórmulas**: o alquimista Mestre transcreve fórmulas e vende as cópias.
- **Sob medida**: o cliente encomenda uma poção com pureza mínima, e o contrato confere a pureza na entrega.

## Encomendas e contratos (o ecossistema das profissões)

Um único sistema liga quem quer um item, quem sabe fazê-lo e quem busca os materiais. Vale para ferreiro, cozinheiro, pescador e alquimista. Todo Col e todo item de um contrato ficam em custódia até o fim, para ninguém sair no prejuízo.

&#91;embedded content: fluxo da encomenda · 4 papéis, 7 passos\]

Os números seguem a ordem dos passos. Cliente e artesão nunca trocam Col ou itens direto: tudo entra e sai pelo contrato, e os fornecedores recebem a cada entrega.

**Papéis**

| Papel | Quem é | O que ganha |
| --- | --- | --- |
| Cliente | Qualquer jogador ou guilda que quer um item ou serviço | O item feito sob medida |
| Artesão | Ferreiro, cozinheiro, pescador ou alquimista | Mão de obra em Col, proficiência e reputação |
| Fornecedor | Aventureiro ou membro de guilda que entrega materiais | Col por unidade entregue e reputação |
| Servidor | O Quadro de Encomendas | Taxa sobre cada contrato, que tira Col de circulação |

**Caminho 1: encomenda pública**

1. O cliente vai ao Quadro de Encomendas da cidade (ou `/encomenda`) e abre um pedido: projeto ou tipo de item, exigências mínimas (raridade, atributo dominante, qualidade), orçamento máximo, prazo e se vai fornecer materiais.
2. O orçamento máximo sai do saldo do cliente e fica em custódia.
3. Artesãos que conhecem o projeto enviam propostas: preço da mão de obra, preço dos materiais que eles mesmos providenciam e prazo. O cliente vê a reputação de cada um.
4. O cliente aceita uma proposta. Nasce o contrato, e a diferença entre o orçamento e o preço volta para o cliente.

**Caminho 2: contrato direto**

1. O cliente vai à oficina do artesão (balcão numa casa do Economy) ou usa `/contratar <artesão>`. Funciona com o artesão offline.
2. Ele escolhe no catálogo do artesão: projetos oferecidos, preço da mão de obra e prazo padrão, definidos pelo artesão.
3. O cliente deposita o Col e, se quiser, os materiais que já tem, direto na caixa do contrato.
4. O artesão aceita ou recusa em até 24 h. Recusado ou sem resposta, tudo volta ao cliente.

**Materiais: guilda e aventureiros**

1. Com o contrato aceito, o artesão vê a lista do que falta para os encaixes do projeto.
2. Ele abre pedidos de material, com preço por unidade e prazo, em dois lugares:
   - No quadro da própria guilda: só membros veem; pago por ele ou pelo tesouro (se o cargo permitir); cada entrega dá XP à guilda.
   - No quadro público de coleta: qualquer aventureiro vê.
3. O Col de cada pedido fica em custódia no momento em que o pedido é aberto.
4. O aventureiro aceita o pedido inteiro ou uma parte (reserva a quantidade por um tempo), busca o material e entrega em qualquer quadro. O pagamento cai na hora, por unidade entregue.
5. O material entregue vai para a caixa do contrato, não para o inventário do artesão.
6. Reserva vencida sem entrega libera a quantidade e baixa a reputação do fornecedor.

**Produção e entrega**

1. O artesão trabalha na estação (bigorna, bancada) escolhendo o contrato: os encaixes puxam os materiais da caixa do contrato.
2. O item pronto vai para a caixa do contrato e é conferido com as exigências do cliente.
3. Atendeu: o item vai para a caixa de retirada do cliente, o artesão recebe a mão de obra menos a taxa do quadro (5%, configurável), e o cliente é avisado.
4. Não atendeu (qualidade ou raridade abaixo): o artesão pode tentar de novo com novos materiais, por conta dele, ou oferecer o item com desconto. O cliente aceita ou recusa.
5. Prazo vencido ou cancelamento: o Col em custódia volta ao cliente, os materiais não usados voltam a quem os forneceu, e quem causou o cancelamento perde reputação. Material já consumido em tentativa falha não volta.

**Reputação**

- Ao fim do contrato, o cliente dá de 1 a 5 estrelas ao artesão.
- O perfil do artesão mostra média de estrelas, contratos concluídos, cancelados e atrasados.
- Fornecedores têm taxa de entrega (reservas cumpridas sobre reservas feitas).
- `/artesaos` lista o ranking por profissão. O primeiro de cada profissão ganha um título no AincradReputation.

**Dados e pré-requisitos**

- Tabelas: `life_orders`, `life_bids`, `life_contracts`, `life_contract_box`, `life_material_requests`, `life_reservations`, `life_reviews`.
- O AincradEconomy precisa ganhar custódia na `EconomyAPI`: `hold`, `release` e `refund`. É uma tarefa extra no economy (C1 na tabela de tarefas).
- Tudo vale na rede inteira: avisos pelo `MessageBus` e pela caixa de mensagens.
- Admin: `/aincrad contrato ver, cancelar, devolver <id>` para resolver disputas, com registro.

## Estalagens

A estalagem aluga quartos e é onde o jogador descansa e define para onde volta ao morrer.

1. **Cadastro** em `inns.yml`: nome, andar, preço por noite e a lista de quartos (regiões do core com porta e baú).
2. **Aluguel**: de 1 a 7 dias reais, pago em Col no balcão (NPC ou bloco). Quarto ocupado não aparece.
3. **Privacidade**: só o hóspede e quem ele convidar abrem a porta e o baú do quarto.
4. **Descanso**: deitar na cama do quarto por 10 s dá o bônus "Descansado" (ex.: +10% de vida máxima e +5% de XP por 30 min).
5. **Ponto de retorno**: ao morrer, o jogador volta ao quarto, mesmo que esteja em outro servidor da rede. Sem quarto, volta à cidade do andar 1.
6. **Fim do aluguel**: aviso 1 h antes. Os itens do baú vão para a caixa de retirada do Economy.

## Familiares

Um familiar é um mob pequeno domado que acompanha o dono, como a Pina.

1. **Quem pode ser domado**: lista em `pets.yml` (mob, comida favorita, chance, habilidade). A tentativa só vale se o mob não foi atacado pelo jogador. A chance é baixa (1 a 5%).
2. **Um por jogador**, com nome escolhido. `/familiar` abre o painel com level, vínculo e comandos (seguir, ficar, guardar).
3. **Level**: o familiar ganha uma parte do XP do dono e fica mais resistente.
4. **Habilidades**, uma por espécie: cura pequena a cada 30 s, bolha que absorve um golpe, alerta de mob hostil próximo ou busca de item no chão.
5. **Vínculo**: cai se o familiar ficar sem comer e sobe ao ser alimentado. Com vínculo baixo a habilidade para.
6. **Morte**: o familiar deixa o item "Coração" com o nome dele. O dono tem 3 dias reais para revivê-lo com um item de ressurreição de familiar (flor obtida por quest ou num andar definido na config). Depois disso o coração vira lembrança.
7. **Rede e zonas**: o familiar acompanha o dono na troca de servidor, não entra em sala de chefe por padrão e não sofre dano em zona segura.

## Casamento

O casamento liga dois jogadores e dá a eles um inventário em comum, como no anime.

1. **Pedido**: `/casar <jogador>` com um anel na mão (item de loja). O outro aceita ou recusa por menu. Os dois precisam estar no mesmo lugar, em zona segura.
2. **Anúncio** opcional para o servidor, com efeito do AincradEffects.
3. **Baú do casal**: 27 espaços compartilhados, aberto pelo menu de qualquer lugar. Com trava entre servidores, para os dois não alterarem ao mesmo tempo.
4. **Status**: cada um vê a vida, o level e o andar do outro no HUD.
5. **Teleporte**: uma vez por dia até o cônjuge, fora de sala de chefe.
6. **Casa**: a casa de um passa a abrir para o outro, pela `HouseAPI`.
7. **Divórcio**: `/divorciar`, com confirmação. Cada item do baú volta para quem o colocou (o baú registra o autor de cada depósito), pela caixa de retirada. Espera de 7 dias para casar de novo.

## Tarefas (etapas 22 a 25)

São 38 tarefas. A etapa 23 virou a maior do plugin, e o sistema de encomendas entrou como um grupo próprio (C1 a C7), feito depois das três profissões. Valem as mesmas regras da aba de tarefas do bloco 1.

| ID | Tarefa | Arquivos e classes | Pronto quando |
| --- | --- | --- | --- |
| 22.1 | Projeto e módulos | Projeto Gradle, `AincradLife`, `config.yml` com liga e desliga por módulo, interface `LifeModule` | Desligar `casamento` na config remove os comandos dele sem erro |
| 22.2 | Proficiência | Migração `life_professions`; `professions.yml`; `ProfessionService`, `ProfessionRankUpEvent`, menu `/profissoes` | Ação no limite da dificuldade dá o ganho máximo, ação fácil demais dá 0; mesmo valor nos dois servidores |
| 22.3 | Estalagens: cadastro e aluguel | `inns.yml`, migração `life_inn_rooms`; balcão; cobrança pela `EconomyAPI`; porta e baú só do hóspede | Alugar desconta o Col; outro jogador não abre a porta |
| 22.4 | Estalagens: descanso e retorno | Bônus "Descansado"; ponto de retorno ao morrer, inclusive em outro servidor; fim do aluguel envia o baú à caixa de retirada | Morrer no servidor B devolve o jogador ao quarto no servidor A |
| 22.5 | Painel de estação | `StationPanel`: painel flutuante preso a um bloco, com encaixes clicáveis, itens visíveis, holograma de progresso e marcas de tempo e mira; usa o AincradUI e o AincradEffects; reserva em formulário para Bedrock e texto simples sem pack; visível a quem está perto | Um painel de teste com 3 encaixes aceita itens pelo clique, mostra uma barra e detecta um clique "no tempo" no Java e no Bedrock |
| 23.1 | Culinária: ingredientes e combinações | `ingredients.yml`, `recipes.yml` com os 5 encaixes, `RecipeRegistry`, receitas secretas, porções | Uma combinação conhecida mostra o nome; uma secreta é revelada ao ser feita pela primeira vez |
| 23.2 | Culinária: etapas de preparo | `CookingSession` sobre o `StationPanel`: cortar, fogo, mexer, temperar, empratar; pontuação e qualidade | Com acertos forçados em teste sai "excelente"; errar o fogo queima o prato |
| 23.3 | Culinária: buffs | `FoodBuffService`: um buff por vez, duração por qualidade, continua após relogar e trocar de servidor | Comer um segundo prato troca o buff; prato queimado não dá nada |
| 23.4 | Barraca e banca | `StallService`: cardápio com preço e estoque numa casa do Economy, venda com o dono offline, frescor do peixe | Um cliente compra da barraca com o dono offline e o Col chega ao dono |
| 23.5 | Pesca: pontos, zonas e tabela | `fishing.yml` (regiões, zonas raso, meio e fundo, tabela por andar, varas, iscas); `FishingService` | Lançar no fundo com vara fraca é recusado; cada zona sorteia da própria tabela |
| 23.6 | Pesca: lançar, fisgar e cansar | `FishingSession`: barra de força, janela de fisgada, barra de tensão com arrancadas; registro do maior peixe; proficiência | Tensão no vermelho arrebenta a linha e gasta a isca; manter no verde entrega o peixe |
| 23.7 | Pesca: Senhor do Lago e torneio | Mini-chefe do lendário com level do RPG; torneio semanal com inscrição, ranking por andar e prêmio | O item do lendário vai para quem fisgou; o torneio paga o vencedor na virada da semana |
| 23.8 | Ferreiro: oficina e reparo | `/aincrad oficina criar`; reparo pelo painel; custo e desconto por proficiência; quebra em durabilidade zero | Reparar restaura a durabilidade e cobra o valor da fórmula |
| 23.9 | Forja: projetos e encaixes | `forge.yml` (projetos, encaixes por tipo, efeito de cada material, armas nomeadas), `ingots.yml`; `Blueprint`, `ForgeRecipeResolver`; projetos conhecidos por jogador | O mesmo projeto com dois núcleos diferentes dá atributos dominantes diferentes; a combinação secreta dá a arma nomeada |
| 23.10 | Forja: calor e batidas | `ForgeSession`: batidas necessárias por tipo e rank, barra de calor com fole, marca e anel de tempo, perfeita, boa e ruim, trincas, reaquecer, têmpera | 3 batidas ruins quebram a peça e devolvem metade dos materiais; o holograma mostra "12 / 30" |
| 23.11 | Forja: resultado | Qualidade final, raridade dentro do teto pelo `RpgAPI.createItem`, tentativas de aprimoramento, nome do ferreiro, efeito de conclusão | Qualidade acima de 90% dá 2 tentativas extras; a raridade nunca passa do teto do lingote |
| 23.12 | Aprimoramento | `UpgradeService`: tentativas, cinco atributos, chance com materiais e com as 5 batidas, três tipos de falha, limite por rank | Com sorteio fixo: sucesso dá +1 e gasta uma tentativa; sem tentativas a arma é recusada |
| C1 | Custódia | No AincradEconomy: `hold`, `release`, `refund` na `EconomyAPI` e tabela `eco_holds`. No Life: `life_contract_box` (itens em custódia por contrato) | Col em custódia não pode ser gasto; `refund` devolve o valor exato |
| C2 | Quadro de encomendas | `life_orders`, `life_bids`; quadro nas cidades e `/encomenda`; abrir pedido com exigências, propostas, aceite | Um pedido aberto no servidor A recebe proposta de um artesão no B; aceitar devolve a sobra do orçamento |
| C3 | Contrato direto e catálogo | `life_contracts`; catálogo do artesão no balcão da oficina; `/contratar`; depósito de Col e materiais; aceite em 24 h | Contratar com o artesão offline funciona; sem resposta em 24 h tudo volta ao cliente |
| C4 | Pedidos de material | `life_material_requests`, `life_reservations`; quadro da guilda (pelo AincradSocial) e quadro público; reserva parcial, entrega, pagamento por unidade, XP de guilda | Entregar 4 de 10 unidades paga 4 e deixa 6 em aberto; reserva vencida libera a quantidade |
| C5 | Produção e entrega | A estação puxa materiais da caixa do contrato; conferência das exigências; entrega, taxa, nova tentativa, desconto, cancelamento e prazo | Item abaixo da exigência não é entregue sozinho; cancelar devolve Col e materiais não usados |
| C6 | Reputação de artesão | `life_reviews`; estrelas, contadores, taxa de entrega do fornecedor, `/artesaos`, título do primeiro colocado | Uma avaliação muda a média; o ranking ordena por média e volume |
| C7 | Contratos especiais e admin | Banquete de raid (ligado ao quadro de raid), fornecimento recorrente, caçada ao Senhor do Lago; `/aincrad contrato ver, cancelar, devolver`; avisos pela rede | O banquete entrega um prato a cada inscrito antes da raid; o admin cancela um contrato e tudo é devolvido |
| 24.1 | Familiares: domar | `pets.yml`, migração `life_pets`; tentativa com a comida favorita; um por jogador; nome | Com chance 100% em teste, o mob vira familiar; um segundo é recusado |
| 24.2 | Familiares: comportamento | Seguir, ficar e guardar; level; habilidade por espécie; vínculo; painel `/familiar` | O familiar de cura recupera vida do dono a cada 30 s; com vínculo baixo, para |
| 24.3 | Familiares: morte, rede e zonas | "Coração" com prazo de 3 dias; ressurreição; troca de servidor; fora de sala de chefe; sem dano em zona segura | Reviver dentro do prazo traz o mesmo nome e level; depois do prazo, não |
| 25.1 | Casamento: pedido | Anel, `/casar`, aceite, zona segura, espera de 7 dias, `life_marriages`, anúncio, `MarriageEvent` | Pedido aceito grava o casal; sem anel o pedido é recusado |
| 25.2 | Casamento: baú, status e teleporte | Baú com autor por item e trava entre servidores; status no HUD; teleporte diário; acesso à casa | Em servidores diferentes os dois não abrem o baú ao mesmo tempo; nenhum item duplica |
| 25.3 | Divórcio e fechamento | `/divorciar` com devolução por autor; `LifeAPI`; entradas no menu do AincradUI; testes com MockBukkit; README | Cada item volta ao autor; os critérios das etapas 22 a 25 passam com um jogador Java e um Bedrock |

Tarefas da alquimia e do aprendizado. A 22.6 vem logo depois da 22.5; as 23.13 a 23.16 vêm depois da 23.12 e antes da C1.

| ID | Tarefa | Arquivos e classes | Pronto quando |
| --- | --- | --- | --- |
| 22.6 | Aprender antes de fazer | Migração `life_known`; `KnowledgeService` (aprender por item, proficiência de estudo e de produção, "???" nos painéis); itens de pergaminho, livro de receitas e planta; transcrição no rank Mestre; flag `unico` | Usar um pergaminho sem a proficiência de estudo é recusado e não consome o item; com ela, o item some e o conteúdo aparece no painel |
| 23.13 | Alquimia: fórmulas e reagentes | `formulas.yml`, `reagents.yml`; `Formula`, `FormulaRegistry`; pontos de coleta com renascimento; bônus de colheita por proficiência; `/alquimia livro` | Colher num ponto entrega o reagente e o ponto renasce no tempo configurado; o livro lista só as fórmulas aprendidas |
| 23.14 | Alquimia: mesa e preparo | `/aincrad alquimia criar`; `AlchemySession` sobre o `StationPanel`: cinco encaixes, moer, infusão por cor, destilar com temperatura, engarrafar; pureza; explosão | Adicionar o reagente na cor errada baixa a pureza; superaquecer por 3 s explode e consome os reagentes |
| 23.15 | Alquimia: produtos | `PotionService`: cura ao longo do tempo, recarga compartilhada, um buff de poção por vez, óleos e venenos aplicados na arma (contagem de golpes gravada no item), arremessáveis em área | A poção de cura recupera a vida em 10 s, não de uma vez; veneno em jogador dispara o evento que o Reputation ouve |
| 23.16 | Alquimia: ligações | Insumos para pesca, culinária e forja; cristal de cura e de antídoto pelo `giveCrystal` do AincradItems no rank Mestre; botica pela `StallService`; kit de raid como contrato especial | O pó de têmpera alarga a janela na forja; o kit de raid entrega o conjunto a cada membro |

Tarefas da árvore, feitas depois da 22.6.

| ID | Tarefa | Arquivos e classes | Pronto quando |
| --- | --- | --- | --- |
| 22.7 | Árvore: regras | `trees/*.yml`, migração `life_tree`; `TreeService` (pontos de ofício, compra de nó, caminho e especialização sem volta, nó final); `ProfessionModifier` aplica os efeitos dos nós nas sessões de forja, preparo, pesca e alquimia; o `KnowledgeService` recusa itens de caminho fechado; reset por admin | Escolher Armeiro fecha Armoreiro e Refinador; um projeto de armadura rara é recusado; o reset do admin devolve os pontos e aparece no registro |
| 22.8 | Árvore: painel | `TreePanel` pelo AincradUI: nós em anel, linhas, quatro estados, janela de detalhes, confirmação dupla, sons; reserva em baú e formulário do Bedrock; caminho e especialização visíveis no quadro de encomendas | Comprar um nó troca o ícone para laranja; o caminho fechado aparece riscado; no Bedrock a mesma compra funciona pelo formulário |

Tarefa das bancadas em 3D, feita junto com a 22.5.

| ID | Tarefa | Arquivos e classes | Pronto quando |
| --- | --- | --- | --- |
| 22.9 | Bancadas em 3D | `stations.yml`; `StationRegistry`, `PlacedStation` (Item Display, Interaction e barreira, salvos na tabela `life_stations`); `/aincrad estacao criar, remover, listar`; pontos de ancoragem usados pelo `StationPanel`; troca para o modelo `_active`; bloco provisório sem pack e no Bedrock; modelos provisórios simples no pack | Colocar uma bigorna de forja mostra o modelo no Java com pack e a bigorna comum sem pack; trocar o arquivo do modelo e recarregar muda a aparência sem recolocar a estação; mudar um ponto de ancoragem no YAML move o holograma |
