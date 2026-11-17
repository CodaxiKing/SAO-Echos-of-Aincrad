# AincradLife

A vida fora do combate. Esta versão traz **a base** (etapa 22 do plano): proficiência, "aprender
antes de fazer", árvore de especialização, bancadas com painel flutuante e estalagens. As quatro
profissões em si (receitas, minigames), os pedidos, os familiares e o casamento ainda não têm código.

Precisa do **AincradCore**. Opcionais: AincradEconomy (preços em Col e caixa de correio), AincradRPG
(bônus de XP do descanso), AincradFloors (volta ao andar 1), AincradEffects (envia os modelos das
bancadas no pack único).

> Compila e passa nos testes de regras, mas **ainda não foi testado num servidor de verdade**.

## Módulos (`config.yml → modulos`)

Cada módulo liga e desliga, e `/aincrad reload` aplica: os comandos de um módulo desligado somem.
Hoje têm código a base das profissões (ligada se qualquer uma das quatro estiver) e `estalagens`.
`familiares` e `casamento` só aparecem no console como "ainda sem código".

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

As profissões registram os próprios itens por `ProfessionService.register`; os de
`professions.yml → exemplos_de_aprendizado` servem para testar agora.

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
| `/aincrad estalagem …` | Estalagens |

## API

`AincradLifeAPI` (estático, também no ServicesManager): `getProficiency`, `getRank`, `getPath`,
`getSpecialization`, `knows`, `isRested`, `hasRoom`.

## Escolhas diferentes do plano

- Os dados ficam no banco do AincradCore (não num SQLite próprio), para funcionar em rede.
- Pacote `com.aincrad.life`, como os outros plugins.

## Compilar

```bash
./gradlew build
```

O jar sai em `build/libs/AincradLife-0.1.0.jar`.
