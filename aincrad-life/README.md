# AincradLife

A vida fora do combate. Esta versão traz **a base** (etapa 22 do plano: proficiência, "aprender
antes de fazer", árvore de especialização, bancadas com painel flutuante e estalagens) e, da
etapa 23, a **culinária** (tarefas 23.1 a 23.3: ingredientes, receitas, preparo na bancada e buffs
de comida), a **pesca** (23.5 a 23.7: pontos com zonas, lançar, fisgar e cansar, Senhor do Lago e
torneio semanal), o **ferreiro** (23.8 a 23.12: reparo, forja com calor e batidas, e
aprimoramento), a **alquimia** (23.13 a 23.16: coleta, preparo na mesa, poções, óleos, venenos,
frascos e insumos) e as **encomendas** (C1 a C7: custódia, quadro com pedidos e propostas, contrato
direto com catálogo, pedidos de material, entrega conferida, reputação e os contratos especiais) e
as **barracas** (23.4), os **familiares** (etapa 24) e o **casamento** (etapa 25). Todos os módulos do
plano têm código.

Precisa do **AincradCore**. Opcionais: AincradEconomy (preços em Col, caixa de correio, torneio de
pesca, custódia das encomendas e casas para as barracas), AincradRPG (bônus de XP do descanso e da comida, level do Senhor do Lago, base dos itens
forjados), AincradFloors
(volta ao andar 1), AincradItems (cristais feitos pelo alquimista), AincradSocial (pedidos de
material só para a guilda, com XP de guilda), AincradBosses (banquete de raid), AincradEffects (envia os
modelos das bancadas no pack único).

> Compila e passa nos testes de regras, mas **ainda não foi testado num servidor de verdade**.

## Módulos (`config.yml → modulos`)

Cada módulo liga e desliga, e `/aincrad reload` aplica: os comandos de um módulo desligado somem.
Hoje têm código a base das profissões (ligada se qualquer uma das quatro estiver), `culinaria`,
`pesca`, `ferreiro`, `alquimia`, `encomendas`, `barracas`, `familiares`, `casamento` e `estalagens`. Os outros só aparecem no console como "ainda sem código".

## Proficiência (`professions.yml`)

Quatro profissões — culinária, pesca, ferreiro e alquimia — de 0 a 1000, com os ranks Aprendiz (0),
Praticante (250), Especialista (500), Mestre (750) e Completo (1000).

- O ganho é maior quando o trabalho está no limite do artesão e cai até zero quando é fácil demais
  (`faixa_facil`, 200 pontos abaixo). Falhar dá uma parte do ganho (`fator_falha`).
- Subir de rank dispara o `ProfessionRankUpEvent`.
- `/profissoes` mostra as quatro barras, o que o jogador conhece e a árvore.

## Aprender antes de fazer

Nada é produzido sem ter sido aprendido. Um projeto, receita ou fórmula tem a proficiência para
**estudar** e outra, maior, para **produzir**. O que o jogador ainda não conhece aparece como "???".

- Pergaminho de conhecimento: clique direito para aprender (é gasto).
- Pergaminho em branco: quem conhece pode transcrever para outro jogador, se o item permitir.
- Um item ligado a um caminho da árvore é recusado para quem escolheu outro.

As profissões registram os próprios itens por `ProfessionService.register` (a culinária registra
as receitas como tipo `receita`); os de `professions.yml → exemplos_de_aprendizado` servem para testar.

## Árvore de especialização (`trees/<profissão>.yml`)

Cada profissão tem 5 nós gerais, 3 caminhos (aos 250) com 4 nós cada e, dentro de cada caminho,
2 especializações (aos 500) com 4 nós, a última uma técnica aos 1000. São 41 nós por árvore.

- Um ponto de ofício a cada 50 de proficiência: 20 no total. Um ramo inteiro custa mais que isso,
  então dois artesãos do mesmo ramo acabam diferentes.
- Escolher um caminho **fecha os outros dois para sempre**; a escolha pede confirmação dupla.
- Os efeitos dos nós somam e as profissões leem por `ProfessionService.modifier`.
- As árvores são geradas por `tools/gerar_arvores.py`; pode editar os YAML à mão.

Por enquanto a árvore abre em páginas de baú. A versão flutuante do AincradUI fica para depois.

## Bancadas (`stations.yml`)

Sete tipos: bigorna, fornalha, barril de têmpera, cozinha, mesa de alquimia, banca de peixe e
quadro de pedidos. Cada bancada é um modelo 3D (ItemDisplay) com uma área de clique e uma barreira
invisível; sem o pack, vira um bloco provisório do Minecraft.

- `/aincrad estacao criar <tipo>` no bloco para onde você olha; `remover`, `listar`, `teste`.
- O painel flutua sobre a bancada, com espaços de item e marcas de tempo (perfeito / bom / erro).
- Bedrock e quem prefere usam o modo simples, em menu.
- Os modelos do pack são **provisórios** (apontam para blocos do Minecraft). Para trocar, substitua
  `resourcepack/assets/aincrad/models/station/<id>.json`. Um `items/station/<id>_active.json`, se
  existir, é usado enquanto a bancada está em uso. `tools/gerar_modelos_provisorios.py` refaz os provisórios.

## Culinária (`ingredients.yml`, `recipes.yml`, `config.yml → culinaria`)

Cozinhar acontece em cima da bancada de cozinha (`/aincrad cozinha criar`), sem menu de baú.

1. **Montar o prato**: clique na bancada. Aparecem cinco encaixes — Base, Proteína, Vegetal,
   Tempero e Especial (opcional). Clique num encaixe com o ingrediente na mão. O holograma mostra o
   nome da receita, se você a conhece, ou "Prato desconhecido".
2. **Cozinhar**: clique de novo na bancada. Agachado + clique desiste (antes de começar, os
   ingredientes voltam; depois, se perdem).
3. **Etapas**, na ordem da receita:
   - *Cortar* e *Empratar*: clique no ponto dourado quando o anel fecha.
   - *Mexer*: as marcas andam para um lado; siga a sequência.
   - *Temperar*: a marca acende de repente e some logo.
   - *Fogo*: a chama cai sozinha. Clique na direita para pôr lenha e na esquerda para abafar, e
     mantenha a chama na faixa verde. Quente demais por 3 s queima o prato na hora.
4. **Qualidade**: a soma dos acertos dá queimado, comum, bom ou excelente. A chance de "excelente"
   cresce com a proficiência acima da dificuldade da receita, com o empratar perfeito e com um
   ingrediente a mais no encaixe Especial.

Regras:

- **Ingredientes**: os comuns são itens do Minecraft (cenoura, trigo, peixe...). Os de
  `item_proprio: true` só valem com a marca do plugin (`/aincrad cozinha ingrediente`, e depois
  drops e lojas).
- **Receitas**: precisam ser aprendidas pelo livro (`/aincrad saber dar <jogador> receita <id>`) e
  pedem a proficiência de produção. As de `caminho:` são exclusivas de um ramo da árvore.
- **Receitas secretas** não têm livro: quem acerta a combinação e termina o prato sem queimar
  aprende a receita.
- **Combinação errada** (ou receita que você não conhece) gasta os ingredientes e vira "gororoba".
  Para só avisar, sem gastar: `mistura_errada_consome: false`.
- **Pratos de panela** (`porcoes` maior que 1) rendem várias porções, até `porcoes_maximo`.
- **Buffs**: força, regeneração, velocidade, vida máxima, XP (precisa do AincradRPG) e sorte na
  pesca (guardada para o módulo de pesca). Só um buff de comida por vez — comer outro prato
  troca. O buff fica no perfil do core: continua depois de relogar e de trocar de servidor.
  Queimado não dá nada; excelente dura 50% mais.
- Os nós da árvore de culinária já valem: janela de corte, de mexer e de tempero, faixa do fogo,
  chance de excelente, porções, duração e força do buff, ingrediente poupado e "nunca queima".
- No Bedrock (modo simples) os encaixes viram um menu, as marcas contam só o tempo e, no fogo,
  cada batida põe lenha.

## Pesca (`fishing.yml`, `config.yml → pesca`)

Dentro de um **ponto de pesca** a pescaria é a do Aincrad; fora, é a pesca normal do Minecraft.
Um ponto é uma região do core em volta do lago e da margem: `/aincrad varinha`, marque os dois
cantos, `/aincrad regiao criar <nome>` e ponha o nome em `fishing.yml → pontos → <id> → regiao`.

1. **Lançar**: o primeiro clique com a vara abre a barra de força, que sobe e desce; o segundo
   lança. A força escolhe a zona — raso, meio ou fundo — e cada zona tem a sua tabela de peixes.
   Vara fraca não alcança as zonas fundas: o lançamento é recusado.
2. **Fisgar**: quando o peixe morde, toca um som e aparece o aviso. Clique dentro da janela; quanto
   mais raro o peixe, menor a janela. Passou da janela, o peixe vai embora e a linha fica na água.
3. **Cansar o peixe**: aparece a tensão da linha. Cada clique puxa (segurar o botão também serve);
   sem clicar, a tensão cai. Mantenha na faixa verde até a força do peixe acabar.
   - No vermelho por tempo demais, a linha **arrebenta** e leva a isca. A vara define quanto aguenta.
   - Frouxa por tempo demais, o peixe **se solta**.
   - Peixes incomuns e raros dão **arrancadas** para um lado: vire o olhar para o lado oposto, senão
     a tensão dispara.
4. **Bedrock**: sem barra de força (a linha vai para a zona mais funda que a vara alcança) e sem
   arrancadas; a janela da fisgada é maior.

Regras:

- **Varas** (`/aincrad pesca vara`): rank (zona que alcança), resistência da linha e sorte. A vara
  comum do Minecraft conta como a `vara_padrao`. A vara vai na mão principal.
- **Iscas** (`/aincrad pesca isca`): vão na outra mão e dão sorte. Uma é gasta por peixe pego e
  quando a linha arrebenta.
- **Sorte** (vara + isca + proficiência + comida + nós da árvore) aumenta o peso dos peixes
  incomuns e raros da tabela.
- **Peixes** saem com tamanho próprio. `/pesca` mostra o maior de cada espécie; as que você nunca
  pescou aparecem como "???". O peixe continua servindo de ingrediente na cozinha.
- **Senhor do Lago**: o lendário do andar só morde na zona funda, com a isca certa e rank de Mestre
  (750). Fisgar não entrega o peixe: o chefe sai da água (com level do AincradRPG, se instalado) e
  precisa ser derrotado em alguns minutos. O peixe vai para **quem fisgou**, não importa quem dê o
  último golpe; se ele saiu do servidor, vai para a caixa de retirada. Dispara o `LegendaryFishEvent`.
- **Torneio semanal** (precisa do AincradEconomy): `/pesca torneio entrar [andar]` cobra a
  inscrição; vale o maior peixe da semana naquele andar; `/pesca torneio ranking [andar]` mostra a
  tabela. Na virada da semana (segunda-feira) o vencedor recebe o total das inscrições menos a
  taxa, mesmo offline, e dispara o `FishingTournamentEndEvent`. Se ninguém pescou, as inscrições
  voltam. O pagamento sai uma vez só na rede. Para testar: `/aincrad pesca torneio encerrar`.
- Nós da árvore de pesca que já valem: força do lançamento, tempo e janela da mordida, faixa
  verde, cansar o peixe, resistência da linha, arrancada, chances de incomum, raro e lendário,
  isca poupada e ver o peixe antes de pegar.

## Ferreiro (`ingots.yml`, `forge.yml`, `config.yml → ferreiro`)

A oficina são três bancadas: a **bigorna de forja**, a **fornalha com fole** e o **barril de
têmpera** (`/aincrad forja criar bigorna|fornalha|barril`). Com o **martelo de ferreiro** na mão
(`/aincrad forja martelo <jogador>`), clicar na bigorna abre a oficina: Forjar, Reparar e Aprimorar.

**Reparo**: o item gasto vai na outra mão. A oficina mostra o custo — Col e o material do item
(ferro para item de ferro, diamante para item de diamante...), proporcionais ao desgaste — e pede
confirmação. A proficiência dá até 50% de desconto no Col. Quando a durabilidade passa de 10%, o
dono do item recebe um aviso na tela.

**Forja**:

1. **Projeto**: a lista mostra os projetos que o ferreiro aprendeu
   (`/aincrad saber dar <jogador> projeto <id>`); os outros aparecem como "???".
2. **Encaixes**: sobem sobre a bigorna os encaixes do tipo de item — adaga: lâmina, cabo e
   catalisador; espada de uma mão e florete: lâmina, núcleo, guarda, cabo e catalisador; machado
   e lança: cabeça, núcleo, haste e catalisador; espada de duas mãos: duas lâminas, núcleo, guarda,
   cabo e catalisador; escudo e armadura: duas placas, forro, rebite e catalisador. O catalisador
   é opcional. Clique num encaixe com o material na mão.
3. **O material define o item**: o material base (lâmina, cabeça, placa) dá o bônus principal e
   o **teto de raridade**; o núcleo dá o **atributo dominante** (afiação, rapidez, precisão ou
   peso); guarda, haste, forro, rebite e cabo dão os bônus de apoio; o catalisador, um bônus
   especial. Combinações secretas (`forge.yml → nomeadas`) dão **armas nomeadas**.
4. **Batidas**: clique na bigorna para começar. A cada batida surge uma marca num ponto e um anel
   que se fecha: mire na marca e bata quando o anel fechar. Perfeita sobe bastante a qualidade,
   boa sobe pouco, ruim baixa e soma uma **trinca**. Três trincas quebram a peça e metade dos
   materiais se perde. Material de rank maior pede mais batidas e fecha o anel mais rápido.
5. **Calor**: o lingote esfria com o tempo e a cada batida. Fora da faixa ideal, toda batida
   conta como ruim. Clique na **fornalha** para usar o fole (sem fornalha por perto: agachado +
   clique na bigorna).
6. **Têmpera**: se a peça está saindo rara ou melhor e há um barril a até 8 blocos, o ferreiro
   tem 3 s para clicar no **barril**. No tempo, +15% de durabilidade; atrasado, −10 de qualidade.
7. **Resultado**: a qualidade final decide a raridade (nunca acima do teto do material base) e
   escala os bônus (de 60% a 140%). Qualidade de 90% para cima dá 2 tentativas de aprimoramento a
   mais. O item sai com a qualidade, as tentativas e o nome do ferreiro na descrição.

**Aprimoramento**: o item vai na outra mão. O ferreiro escolhe um de cinco atributos — Afiação
(dano), Rapidez (velocidade de ataque), Precisão (golpe crítico), Peso (empurrão) e Durabilidade —
põe materiais extras nos encaixes (cada um soma 10% de chance) e dá cinco batidas (cada perfeita
soma 2%). Cada tentativa gasta uma das tentativas do item, com sucesso ou não. Sucesso: +1 no
atributo. Falha, sorteada entre três: só os materiais se perdem; o atributo cai 1; ou o +1 vai
para outro atributo. O item nunca é destruído. Limite: +5 no rank Especialista e +10 no Mestre;
acima de +5, só o caminho Refinador. Um item que não veio da forja ganha 8 tentativas no primeiro
aprimoramento.

Com o AincradRPG, um projeto com `item_rpg` usa o modelo dele como base (raridade, bônus e level
mínimo) e soma os bônus dos materiais por cima. Sem o RPG, o item sai do material do projeto.

Nós da árvore do ferreiro que já valem: batidas, janelas perfeita e boa, calor estável, trincas
extras, materiais na falha, material poupado, janela da têmpera, qualidade de armas e de
armaduras, atributo dominante, tentativas extras, custo e material do reparo, chance de
aprimoramento e de afiação e falha sem queda.

## Alquimia (`reagents.yml`, `formulas.yml`, `config.yml → alquimia`)

**Coleta**: um admin marca blocos como pontos de coleta (`/aincrad alquimia ponto <tipo>`, olhando
para o bloco). Qualquer jogador colhe clicando no ponto; depois ele leva um tempo para renascer
(os prontos soltam um brilho). Quem tem proficiência em alquimia colhe mais e pode achar o
reagente raro do ponto.

**Livro**: `/alquimia` abre o livro do alquimista, só com as fórmulas aprendidas
(`/aincrad saber dar <jogador> formula <id>`): os reagentes, a ordem de adição e a cor esperada
em cada passo. A mesa não mostra essas cores — é o livro que ensina.

**Preparo** na mesa de alquimia (`/aincrad alquimia criar`):

1. **Encaixes**: Frasco (define as doses e o teto de pureza), Base (água: poção de beber; óleo:
   óleo de arma; álcool: frasco arremessável), Reagente principal (o efeito), Reagente secundário
   e Catalisador (opcional: mais um passo na infusão, mais pureza).
2. **Moer**: marcas em ritmo regular. Quanto mais regular, maior a pureza inicial.
3. **Infusão**: o líquido passa pelas cores em roda. Clique no ponto do reagente (esquerda:
   principal; centro: secundário; direita: catalisador) quando o líquido estiver na cor que a
   fórmula pede. Cor errada baixa a pureza; reagente **fora de ordem estraga a mistura**.
4. **Destilar**: mantenha a temperatura na faixa verde (direita: aquecer; esquerda: esfriar).
   Quente demais por 3 s e a mistura **explode**: dano em quem está perto e os reagentes se perdem.
5. **Engarrafar**: uma marca só; no instante certo dá o bônus exigido para a pureza "perfeita".

**Pureza**: impura (efeito pela metade e um efeito colateral leve), comum, pura (25% mais forte
ou mais longa) e perfeita (50% mais e uma dose extra). Nunca passa do teto do frasco.

**Produtos**:

- **Poção de cura**: recupera a vida ao longo de 10 s, não de uma vez. Recarga de 5 s
  compartilhada entre as poções de cura.
- **Antídoto**: tira os efeitos ruins listados na fórmula.
- **Poção de buff** (força, velocidade, visão...): só uma ativa por vez; beber outra troca. Vale
  junto com o buff de comida.
- **Óleo de arma**: dano extra por um número de golpes. Beba com a arma na outra mão para aplicar.
- **Veneno**: efeito no alvo por um número de golpes, aplicado como o óleo. Acertar um jogador
  dispara o `PoisonAttackEvent`, para o AincradReputation contar como ataque.
- **Frascos arremessáveis**: fumaça, lentidão... em área, sem atingir quem arremessou.
- **Insumos**: isca avançada (pesca), tempero raro (culinária) e pó de têmpera — com ele no
  inventário, o ferreiro tem o dobro do tempo na têmpera (gasta um por peça).
- **Cristais de cura e de antídoto**: rank Mestre, caminho Curandeiro, entregues pelo
  AincradItems. Teleporte e ressurreição não são fabricáveis.

Nós da árvore da alquimia que já valem: janela de moer, faixa de temperatura, tempo de preparo,
pureza, doses e dose extra, reagente poupado, explosão perdoada, sem colateral, colheita, força
da cura e do óleo, duração do veneno e do frasco, área do frasco, golpes extras, insumos extras e
cristal duplo.

## Encomendas (`config.yml → encomendas`)

O quadro liga quem quer um item a quem sabe fazê-lo. Precisa do AincradEconomy: todo o Col de um
contrato fica em **custódia** até o fim, e cliente e artesão nunca trocam Col ou itens direto.
Abre com `/encomenda` ou clicando numa bancada "quadro de encomendas"
(`/aincrad estacao criar order_board`). Vale na rede inteira.

1. **Abrir o pedido** (cliente):
   `/encomenda abrir <profissão> <produto> <orçamento> <dias> [quantidade] [nota mínima]`.
   O orçamento sai do saldo e vai para a custódia. A nota mínima é a exigência: qualidade de 0 a
   100 (ferreiro), comum/bom/excelente (culinária), comum/pura/perfeita (alquimia) ou o tamanho em
   centímetros (pesca).
2. **Propor** (artesão): no quadro aparecem os pedidos abertos. Só quem aprendeu o item e tem a
   proficiência pode propor: `/encomenda propor <id> <preço> <dias>`, com preço até o orçamento.
3. **Aceitar**: `/encomenda propostas <id>` mostra cada proposta com a reputação do artesão
   (estrelas, contratos feitos, cancelados e atrasados) e o caminho e a especialização dele.
   Aceitar cria o contrato e **devolve a diferença** entre o orçamento e o preço.
4. **Entregar**: com o item na mão, `/encomenda entregar <id>`. O item é conferido:
   - Atende: vai para a caixa de retirada do cliente, o artesão recebe o preço menos a taxa do
     quadro (5%, que sai de circulação).
   - Abaixo da exigência: **não é entregue sozinho**. O artesão faz outro ou oferece este com
     desconto (`/encomenda oferecer <id> <preço>`); o item fica na caixa do contrato e o cliente
     responde com `/encomenda oferta <id> aceitar|recusar`. Recusado, o item volta ao artesão.
5. **Cancelar e prazo**: o cliente cancela a qualquer momento antes da entrega, e o artesão depois
   de contratado (`/encomenda cancelar <id>`); quem cancela um contrato fica com isso no histórico.
   Pedido que vence sem contrato e contrato que passa do prazo são encerrados sozinhos. Em todos os
   casos o Col em custódia volta ao cliente.
6. **Avaliar**: `/encomenda avaliar <id> <1-5>`, uma vez por encomenda entregue. `/artesaos
   <profissão>` mostra o ranking, pela média de estrelas e, no empate, pelo número de contratos.

`/encomenda minhas` lista as suas encomendas, como cliente e como artesão, com o próximo passo de
cada uma. Quem está em outro servidor da rede recebe os avisos pelo MessageBus.

**Contrato direto** (sem passar pelo quadro):

- O artesão monta o **catálogo** dele: `/catalogo adicionar <profissão> <produto> <preço> <dias>`
  (só o que ele sabe fazer), `/catalogo remover`, `/catalogo` para ver.
- O cliente usa `/contratar <artesão>`, que abre o catálogo dele — **mesmo com ele offline** ou
  em outro servidor — e escolhe um item. O preço vai para a custódia.
- O cliente pode deixar na **caixa do contrato** os materiais que já tem: `/encomenda material
  <id>` com o item na mão.
- O artesão tem 24 h para responder: `/encomenda aceitar <id>` ou `recusar <id>`. Recusado ou
  **sem resposta, tudo volta ao cliente** — o Col e os materiais.
- Aceito, segue como qualquer contrato: `/encomenda caixa <id>` retira os materiais para
  trabalhar e `/encomenda entregar <id>` entrega.

**Contrato de serviço** (`/contrato`): para o artesão trabalhar num item **seu** — aprimorar ou
reparar a sua arma, por exemplo.

- Com o item na mão: `/contrato <profissão> <artesão> <preço> [horas]`. O item e o Col ficam
  guardados. Funciona com o artesão offline, desde que ele já tenha entrado neste servidor.
- Sem item na mão, `/contrato <profissão> <artesão>` abre o catálogo dele, para **criar** algo novo
  (é o mesmo que `/contratar <artesão>`).
- O artesão tem 24 h para `/encomenda aceitar <id>` ou `recusar <id>`. Aceito, o item vai para o
  inventário dele, **preso**:
  - não vai para baú, baú do fim, baú do casal, moldura, suporte de armadura, trouxinha, nem
    para nenhuma outra tela (loja, troca, bigorna comum);
  - não pode ser largado, não cai se ele morrer e não gasta;
  - com ele na mão, não rodam o leilão, a troca, a barraca nem os outros comandos de
    `encomendas.servico.comandos_bloqueados`.
- Ele trabalha no item (na oficina, com o item na outra mão) e devolve com
  `/encomenda entregar <id>`, com o item na mão. O item vai para a caixa de retirada do dono e o
  artesão recebe o preço menos a taxa.
- **Se o serviço não for feito no prazo** (48 h por padrão, contadas do aceite), o contrato
  encerra sozinho: o dono recebe de volta o item **como o entregou** e o Col; o item que estava
  com o artesão some do inventário dele. O mesmo vale se o artesão recusar, não responder, ou se
  um dos dois cancelar.

**Pedidos de material** (`/material`):

- O artesão de um contrato em produção pede o que falta:
  `/material pedir <contrato> <material> <quantidade> <preço por unidade> <dias> [guilda]`. O Col
  do pedido inteiro fica em custódia. Com `guilda` no fim, só os membros da guilda dele veem (e
  cada entrega dá XP à guilda); sem, é o quadro público de coleta.
- O aventureiro vê o quadro em `/material`, **reserva uma parte** por um tempo
  (`/material reservar <id> <qtd>`) e entrega com o material na mão (`/material entregar <id>`).
  Dá para entregar só uma parte: entregar 4 de 10 paga 4 e deixa 6 em aberto.
- O pagamento cai **na hora, por unidade**. O material vai para a caixa do contrato, não para o
  inventário do artesão.
- **Reserva vencida** sem entrega libera a quantidade para os outros e baixa a taxa de entrega do
  fornecedor (`/material taxa`).
- O artesão fecha o pedido com `/material cancelar <id>`; fechado ou vencido, o Col do que
  faltava volta para ele.
- Quando um contrato é cancelado ou vence, o que ainda está na caixa volta para quem pôs: os
  materiais do cliente para o cliente, os comprados pelo artesão para o artesão.

**Contratos especiais**:

- **Fornecimento recorrente**:
  `/encomenda recorrente <profissão> <produto> <orçamento por entrega> <vezes> <dia|semana> [quantidade] [nota]`.
  A custódia guarda todas as entregas. Os artesãos propõem o preço **por entrega**; aceita a
  proposta, a primeira vence depois de um intervalo e cada uma seguinte, um intervalo depois da
  anterior. Cada entrega é paga na hora; a última fecha o contrato. Se o artesão perde um prazo
  ou alguém cancela, o Col das entregas que faltavam volta ao cliente.
- **Banquete de raid** (precisa do AincradBosses): o líder de uma raid marcada no quadro usa
  `/encomenda banquete <raid> <receita> <orçamento> [nota]`. O pedido é de um prato por inscrito e
  o prazo é a hora da raid. Na entrega, **cada inscrito recebe um prato** na caixa de retirada; se
  alguém saiu da raid, o prato a mais vai para quem encomendou.
- **Caçada ao Senhor do Lago**: o pescador que vai fisgar o lendário abre
  `/encomenda cacada abrir <andar> <pagamento por caçador> <vagas> <dias>`, com o Col de todas as
  vagas em custódia. Os aventureiros entram com `/encomenda cacada entrar <id>`. Quando o chefe
  fisgado por ele cai, recebe **cada inscrito que feriu o chefe**; o Col das vagas que sobraram
  volta ao pescador. O peixe continua sendo de quem fisgou.

## Barracas (`config.yml → barracas`)

Barraca do cozinheiro, banca de peixe e botica são a mesma coisa: um bloco onde o dono põe itens
à venda e os clientes compram **mesmo com ele offline** ou em outro servidor. Precisa do
AincradEconomy.

- `/barraca criar`, olhando para um bloco **dentro de uma casa sua**. Uma por jogador.
- `/barraca por <preço>`, olhando para a barraca com a pilha na mão: a pilha inteira vai para o
  estoque, pelo preço por unidade. Até 27 itens diferentes.
- Clicar na barraca: o cliente vê a loja e compra uma unidade por clique; o dono vê o estoque e
  tira um item clicando nele.
- O Col da venda cai na conta do dono na hora, esteja ele onde estiver.
- `/barraca remover` tira a barraca e devolve o estoque ao dono.
- **Frescor do peixe**: um peixe pescado num ponto de pesca vale o preço cheio por 24 h,
  contadas da pescaria (não de quando foi posto à venda), e metade depois. O nó "frescor" da
  árvore do pescador alonga esse tempo. Pratos, poções e outros itens não passam.

A casa do AincradEconomy tranca portas e baús para quem não é convidado. Para os clientes
alcançarem a barraca, deixe-a num balcão aberto para a rua, e use um bloco que não seja porta nem
baú (uma mesa de flechas, um compostor, um atril).

O **kit de raid** da alquimia usa as encomendas: o líder de uma raid marcada pede
`/encomenda kit <raid> <fórmula> <por inscrito> <orçamento> [nota]`. Na entrega, cada inscrito
recebe o conjunto dele na caixa de retirada; o que sobrar vai para quem encomendou.

## Familiares (`pets.yml`, `config.yml → familiares`)

Um familiar é um mob pequeno domado que acompanha o dono. Um por jogador.

- **Domar**: clique num mob selvagem de uma espécie de `pets.yml` com a comida favorita dele na
  mão. Cada tentativa gasta uma unidade e tem chance baixa (2 a 5%). O mob **não aceita quem o
  atacou**. Dê um nome com `/familiar nome <nome>`.
- **Comandos**: `/familiar` abre o painel (level, vínculo, habilidade); `/familiar seguir`,
  `ficar` e `guardar` (guardado, ele some do mundo até ser chamado de volta); `/familiar libertar`.
- **Level**: o familiar ganha uma parte do XP do dono (20%, pelo AincradRPG; sem ele, um pouco
  por mob morto) e fica com mais vida a cada level.
- **Habilidade**, uma por espécie: cura pequena a cada 30 s; bolha que absorve um golpe (recarga
  de 60 s); alerta de mob hostil por perto; busca dos itens caídos por perto. Só funciona com o
  familiar por perto do dono.
- **Vínculo** (0 a 100): cai 1 a cada 10 minutos com o familiar fora, e sobe 15 a cada comida
  favorita (clique nele com a comida). Abaixo de 30, **a habilidade para**.
- **Morte**: o familiar deixa o **Coração**, com o nome dele. O dono tem 3 dias reais para
  revivê-lo: com o Coração no inventário, clique com a **Flor da Ressurreição** na mão
  (`/aincrad familiar flor <jogador>`; depois, quest ou drop). Ele volta com o mesmo nome e level.
  Passado o prazo, o Coração fica como lembrança e o jogador pode domar outro.
- **Rede**: os dados ficam no banco do core; ao trocar de servidor, o familiar aparece ao lado
  do dono no destino.
- **Zonas**: não entra em sala de chefe do AincradBosses (some na porta e volta na saída). Na
  zona segura do AincradFloors ninguém sofre dano, o familiar incluído. O dono nunca fere o
  próprio familiar, e os outros jogadores só se `outros_jogadores_ferem` estiver ligado.

## Casamento (`config.yml → casamento`)

- **Pedido**: `/casar <jogador>` com o **anel de casamento** na mão
  (`/aincrad casamento anel <jogador>`). Os dois precisam estar juntos (até 8 blocos) e em zona
  segura do AincradFloors. O outro aceita ou recusa num menu. Aceito, o anel é gasto e o casamento
  é anunciado no servidor. Sem anel, o pedido é recusado.
- **Baú do casal** (`/casal bau`): 27 espaços, aberto de qualquer lugar. Enquanto um dos dois está
  com ele aberto, **em qualquer servidor da rede**, o outro não abre. O baú guarda quem colocou
  cada item.
- **Barra do par**: uma barra no alto da tela mostra o nome, o level, o andar e a vida do par,
  mesmo que ele esteja em outro servidor. `/casal hud` liga e desliga.
- **Teleporte** (`/casal ir`): uma vez por dia até o par, se ele está no mesmo servidor e
  nenhum dos dois está numa sala de chefe.
- **Casa**: as casas de um (AincradEconomy) passam a abrir para o outro.
- **Divórcio** (`/divorciar`, com confirmação): cada item do baú volta para **quem o colocou**,
  pela caixa de retirada; as casas deixam de abrir; os dois esperam 7 dias reais para casar de
  novo. Funciona com o par offline.

`/casal` abre o menu com tudo isso.

## Menu do AincradUI

Com o AincradUI no servidor, o AincradLife põe quatro entradas no menu dele: Profissões,
Encomendas, Familiar e Casal (as dos módulos que estiverem ligados).

## Estalagens (`inns.yml`)

- Balcão: aluga um quarto de 1 a 7 dias; renovar soma ao que resta, até 7 dias à frente.
- Só o hóspede e os convidados abrem a porta. `/estalagem`, `/estalagem convidar|remover <jogador>`, `/estalagem sair`.
- Deitar na cama do quarto por alguns segundos dá "Descansado": mais vida máxima e mais XP por um tempo.
- Quem tem quarto renasce nele, mesmo em outro servidor da rede. Sem quarto, volta à chegada do andar 1.
- Aviso uma hora antes de vencer; ao vencer, o que ficou no quarto vai para a caixa de correio do AincradEconomy.
- Admin: `/aincrad estalagem balcao <id>`, `removerbalcao`, `cama <estalagem> <quarto>`, `listar`, `despejar <jogador>`.

Sem o AincradEconomy, os preços viram itens (`sem_economia`).

## Comandos de admin (`aincrad.admin`)

| Comando | O que faz |
|---|---|
| `/aincrad estacao criar\|remover\|listar\|teste` | Bancadas |
| `/aincrad saber dar\|ensinar\|esquecer <jogador> <tipo> <id>`, `pergaminho <jogador> [qtd]`, `lista` | Conhecimento |
| `/aincrad arvore resetar <jogador> <profissão>` | Devolve os pontos e reabre os caminhos |
| `/aincrad profissao definir <jogador> <profissão> <0-1000>`, `ver <jogador>` | Proficiência |
| `/aincrad cozinha criar`, `ingrediente <jogador> <id> [qtd]`, `prato <jogador> <receita> [qualidade]`, `lista` | Culinária |
| `/aincrad pesca vara <jogador> <id>`, `isca <jogador> <id> [qtd]`, `peixe <jogador> <id> [cm]`, `lista`, `torneio encerrar` | Pesca |
| `/aincrad forja criar <bigorna\|fornalha\|barril>`, `martelo <jogador>`, `material <jogador> <id> [qtd]`, `lista` | Ferreiro |
| `/aincrad alquimia criar`, `ponto <tipo>`, `removerponto`, `reagente <jogador> <id> [qtd]`, `pocao <jogador> <fórmula> [pureza]`, `lista` | Alquimia |
| `/aincrad casamento anel <jogador> [qtd]`, `divorciar <jogador>` | Casamento |
| `/aincrad familiar dar <jogador> <espécie>`, `remover <jogador>`, `level <jogador> <n>`, `flor <jogador> [qtd]`, `lista` | Familiares |
| `/aincrad contrato ver <id>`, `cancelar <id>` | Encomendas: resolve disputas (cancelar devolve o Col ao cliente e o item da caixa ao artesão) |
| `/aincrad estalagem …` | Estalagens |

## API

`AincradLifeAPI` (estático, também no ServicesManager): `getProficiency`, `getRank`, `getPath`,
`getSpecialization`, `knows`, `isRested`, `hasRoom`, `getFoodBuff`, `getFishingLuck`, `hasPet`,
`getPetLevel`, `isMarried`, `getSpouse`.

Eventos: `ProfessionRankUpEvent`, `LegendaryFishEvent` (Senhor do Lago derrotado),
`FishingTournamentEndEvent` (torneio pago), `PoisonAttackEvent` (veneno de alquimia em jogador) e
`PetTameEvent` (familiar domado) e `MarriageEvent` (casamento e divórcio).

## Escolhas diferentes do plano

- Os dados ficam no banco do AincradCore (não num SQLite próprio), para funcionar em rede.
- Pacote `com.aincrad.life`, como os outros plugins.
- Culinária: toda receita usa os quatro encaixes obrigatórios; o `preparo_segundos` do exemplo do
  plano deu lugar à lista de `etapas`.
- "Mexer" é uma sequência de marcas para um lado, e não girar o olhar em volta da panela (girando,
  o jogador perderia o painel de vista).
- Comum dura 80% do tempo da receita, bom 100% e excelente 150% (`culinaria.duracao`); o plano só
  define o excelente.
- Os buffs específicos de ramo (ex.: `forca_buff_carne`, `festim_regeneracao`) e as técnicas dos
  nós finais ainda não têm efeito; chegam com as receitas de cada ramo.
- Pesca: a barra de força é de dois cliques (um abre, outro lança), porque o servidor não vê o
  jogador "segurando" o clique com a vara.
- O ponto de pesca vale pelo lugar onde o pescador está, não por onde a boia cai.
- O registro do maior peixe fica em `/pesca` (e no perfil do core), não no menu `/profissoes`.
- O torneio é por andar: a inscrição vale para um andar e o prêmio é o das inscrições daquele andar.
- Da árvore de pesca, ainda não têm efeito: pescar baús, tesouros e materiais (Caça-tesouros),
  iscas próprias, mergulho, frescor e banca (chegam com a tarefa 23.4) e as técnicas dos nós finais.
  A zona funda depende só da vara, não do caminho "Águas profundas".
- Ferreiro: o reparo é um menu de confirmação (com o item na outra mão), não um painel sobre a
  bigorna. A forja e o aprimoramento usam o painel.
- A lista de projetos é um menu, não um anel de ícones por categoria.
- O martelo abre a oficina; para pôr um material num encaixe, o ferreiro troca para ele na mão.
- A raridade vem da qualidade (40, 60, 75 e 90%), cortada pelo teto do material base.
- Ainda não têm efeito: NPCs ferreiros nas cidades, a regra de que só o Refinador repara itens
  épicos e lendários, os bônus específicos de ramo (ex.: `defesa_placas`) e as técnicas dos nós
  finais. Os projetos de caminho usam `caminho:` no `forge.yml`, não os nós "desbloqueia".
- Alquimia: os pontos de coleta são blocos marcados por um admin, não regiões do core.
- O livro do alquimista é um menu (`/alquimia`), não `/alquimia livro`.
- Óleos e venenos são aplicados "bebendo" o frasco com a arma na outra mão.
- A água da base é uma garrafa de água; qualquer poção comum do Minecraft conta.
- O buff de poção vale junto com o de comida, mas não fica guardado como o de comida: ao trocar
  de servidor, o jogador recebe só o tempo que restava.
- Ainda não têm efeito: a venda de fórmulas (a transcrição de pergaminhos já existe), as
  resistências a paralisia e frio, e as técnicas dos nós finais.
- Encomendas: o pedido é aberto por comando (`/encomenda abrir ...`); o quadro e as listas são menus.
- A proposta tem um preço só (mão de obra e materiais juntos); o artesão compra o que falta
  pelos pedidos de material.
- O artesão retira os materiais da caixa do contrato por comando (`/encomenda caixa`); as
  estações ainda não puxam direto da caixa.
- O catálogo é um comando (`/catalogo`, `/contratar`), não um balcão numa casa do Economy.
- O pedido de material é pago pelo artesão; o tesouro da guilda ainda não entra.
- Um pedido de material aceita só o item comum do Minecraft (sem nome nem marca), então não
  serve para ingredientes e materiais próprios dos plugins.
- Na caçada, o contrato divide só o pagamento em Col; os drops do chefe não entram (o peixe é de
  quem fisgou).
- O banquete conta os inscritos na hora do pedido; quem entrar na raid depois só recebe se
  sobrar prato.
- A oferta com desconto vale só para o pedido comum, não para os contratos especiais.
- Contrato de serviço: quando o prazo vence, o dono recebe a cópia do item guardada na hora do
  pedido, não o item que estava com o artesão. Assim ele recebe o item mesmo que o artesão nunca
  mais entre; em troca, um trabalho feito pela metade se perde.
- O serviço não confere se o artesão de fato aprimorou ou reparou o item: o cliente avalia
  depois, com as estrelas.
- O artesão do `/contrato` é procurado entre quem já entrou neste servidor, não na rede inteira.
- Barraca: é um bloco marcado por comando, não um menu de cardápio montado numa bancada; vende
  qualquer item, não só os das profissões.
- O kit de raid é de uma fórmula por pedido (ex.: 5 poções de cura por membro). Para cura e
  antídoto juntos, são dois pedidos.
- Familiares: "guardar" tira o familiar do mundo (não é um modo de vigia).
- A Flor da Ressurreição é um item do plugin, dado por comando; ligar a uma quest ou a um andar
  fica para o AincradQuests.
- O familiar não ataca nem serve de montaria; as quatro habilidades são as do plano.
- Casamento: o estado do par aparece numa barra no alto da tela (BossBar), não dentro do HUD do
  AincradUI.
- O teleporte até o par só funciona com os dois no mesmo servidor da rede.
- O anúncio do casamento é uma mensagem com som e partículas; não usa um efeito do AincradEffects.
- Se alguém põe mais unidades numa pilha que o par tinha deixado no baú, a pilha inteira passa a
  ser de quem a aumentou (o baú guarda um autor por espaço, não por unidade).
- A casa abre para o par nas casas do servidor onde o casamento (ou o divórcio) aconteceu.
- A exigência é uma nota mínima por profissão (qualidade, pureza ou tamanho), não raridade nem
  atributo dominante.
- A taxa do quadro é cobrada sobre o que o artesão recebe, na entrega.
- O título do primeiro colocado de cada profissão fica para o AincradReputation.

## O que falta

As etapas 22 a 25 têm código e testes das regras, mas **nada disso foi testado num servidor de
verdade**, que é o próximo passo. Das profissões, ficaram para depois as técnicas dos
nós finais das árvores e os bônus específicos de ramo.

## Compilar

```bash
./gradlew build
```

O jar sai em `build/libs/AincradLife-0.1.0.jar`.
