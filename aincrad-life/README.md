# AincradLife

A vida fora do combate. Esta versão traz **a base** (etapa 22 do plano: proficiência, "aprender
antes de fazer", árvore de especialização, bancadas com painel flutuante e estalagens) e a
**culinária** (começo da etapa 23, tarefas 23.1 a 23.3: ingredientes, receitas, preparo na bancada
e buffs de comida). Pesca, ferreiro, alquimia, barracas, pedidos, familiares e casamento ainda não
têm código.

Precisa do **AincradCore**. Opcionais: AincradEconomy (preços em Col e caixa de correio), AincradRPG
(bônus de XP do descanso), AincradFloors (volta ao andar 1), AincradEffects (envia os modelos das
bancadas no pack único).

> Compila e passa nos testes de regras, mas **ainda não foi testado num servidor de verdade**.

## Módulos (`config.yml → modulos`)

Cada módulo liga e desliga, e `/aincrad reload` aplica: os comandos de um módulo desligado somem.
Hoje têm código a base das profissões (ligada se qualquer uma das quatro estiver), `culinaria` e
`estalagens`. Os outros só aparecem no console como "ainda sem código".

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
| `/aincrad estalagem …` | Estalagens |

## API

`AincradLifeAPI` (estático, também no ServicesManager): `getProficiency`, `getRank`, `getPath`,
`getSpecialization`, `knows`, `isRested`, `hasRoom`, `getFoodBuff`, `getFishingLuck`.

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

## O que falta da etapa 23

Barraca e banca (23.4), pesca (23.5 a 23.7), ferreiro e forja (23.8 a 23.12), alquimia (23.13 a
23.16) e as encomendas (C1 a C7).

## Compilar

```bash
./gradlew build
```

O jar sai em `build/libs/AincradLife-0.1.0.jar`.
