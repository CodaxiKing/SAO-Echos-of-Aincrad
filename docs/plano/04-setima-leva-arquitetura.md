# Sétima leva — arquitetura

Como serão feitos os 34 sistemas do Sword Art Online que o servidor ainda não tem. Escrito em
18/11/2026. **Nada daqui tem código.** O resumo e a tabela de etapas (67 a 94) estão no
[plano geral](01-plano.md#sétima-leva--o-que-falta-do-sword-art-online).

## 1. O que já existe e muda de forma

Antes de planejar, o código foi conferido. Sete itens da lista já existem em parte; o plano é
ampliar, não refazer.

| Pedido | O que já existe | O que falta |
|---|---|---|
| Slots de skill | AincradSkills: 4 ativas e 3 passivas, liberadas pelo level | Nada de estrutura; os slots passam a valer também para as passivas novas |
| Skills de apoio | Cura em Batalha, Aparar, Busca, Rastreio, Arremesso | Furtividade, Escuta, Corrida, Acrobacia, Visão Noturna |
| Penalidade de morte | AincradRPG: perde 10% do XP do level, nunca desce de level | Col, durabilidade e (opcional) um item |
| Venenos | AincradLife: veneno de alquimia aplicado na arma | Virar um estado de verdade, que mobs e chefes também causam |
| Coleta | AincradLife: pontos de coleta de reagentes da alquimia | Minério, madeira e fibra, com ferramenta e proficiência |
| Peso e força | AincradRPG: atributo Força (dano por ponto) | Peso dos itens, limite de carga e força mínima da arma |
| Sala segura | AincradFloors: `vilas:` são zonas seguras extras | Um tipo próprio, que não aparece como vila e onde mob não nasce nem entra |

Já existem inteiros e **não entram** nesta leva: troca segura (`/trocar`), aprimoramento de arma
(+1, +2…), quadro de raid e reunião de estratégia.

## 2. Onde cada sistema mora

Regra: um sistema vai para o plugin que já é dono dos dados dele. Plugin novo só quando o sistema
não tem dono e é grande o bastante.

| Plugin | Sistemas |
|---|---|
| **AincradCombat** (novo) | Ameaça e Switch; estados (veneno, sangramento, atordoamento, queda, paralisia); cor do cursor dos mobs; quebra de arma |
| **AincradCasino** (novo) | Fichas, jogos de mesa, loja de prêmios, arena de monstros |
| AincradSkills | Passivas novas; mods de skill; encadear skills; Artes Marciais; Destrancar e Desarmar |
| AincradRPG | Farm que cansa; penalidade de morte ampliada; peso e força; posse e durabilidade no chão; itens não identificados |
| AincradItems | Cristal de corredor; cristais de gravação e de mensagem |
| AincradLife | Coleta geral; alfaiate, carpinteiro, músico e mercador; refundir arma; mercenários e aprendizes |
| AincradQuests | Quests do Cardinal; campanhas entre andares |
| AincradBosses | Baús de labirinto; rolagem de dados no drop |
| AincradReputation | Guardas das cidades |
| AincradSocial | Mensagens com limite |
| AincradEconomy | Armazém pessoal |
| AincradFloors | Sala segura; portal que abre sozinho; dados do mapa como item (com AincradUI e AincradItems) |
| AincradWorld | Festivais; caça a relíquias; enigmas |

Regras que valem para todos, as mesmas do resto do projeto:

- Um plugin só fala com outro pela API pública ou por evento. Plugin opcional entra por
  `softdepend` e uma classe ponte carregada só se ele estiver instalado.
- O que precisa valer na rede vai para o banco do core ou para o perfil do core; o que é do
  servidor (mobs vivos, tabelas de ameaça, portais abertos) fica em memória.
- Tudo o que o jogador faz é por menu. Só se digita onde o texto é o próprio conteúdo (o cristal
  de mensagem).
- Cada sistema liga e desliga na config do plugin dono, e as regras puras ficam em classes sem
  Bukkit, com teste JUnit.

---

## 3. AincradCombat (pasta `aincrad-combat`) — plugin novo

O combate em grupo do SAO. Precisa do AincradCore. Opcionais: AincradRPG (level dos mobs),
AincradSkills (pausa depois da skill), AincradSocial (party), AincradBosses, AincradLife,
AincradItems, AincradReputation, AincradDuels, AincradUI, AincradEffects, MythicMobs, PacketEvents.

```
com.aincrad.combat
├── AincradCombatPlugin        liga os módulos que a config pede
├── threat/
│   ├── ThreatTable            ameaça de cada jogador num mob (regra pura)
│   ├── ThreatRules            ganho, decaimento e troca de alvo (regra pura)
│   ├── ThreatService          as tabelas dos mobs vivos, o relógio de 10 ticks
│   ├── ThreatListener         dano, cura, morte, mob que sai do alcance
│   └── SwitchService          a janela do Switch
├── status/
│   ├── StatusDef              um estado lido do estados.yml
│   ├── StatusRegistry         os estados carregados
│   ├── ActiveStatus           estado num alvo: potência, acúmulos, fim
│   ├── StatusRules            resistência, acúmulo, imunidade depois do fim (regra pura)
│   ├── StatusService          aplica, conta o tempo, cura, grava ao sair
│   └── StatusListener         bloqueios (andar, atacar, skill, cristal), dano por tempo
├── cursor/
│   ├── CursorRules            cor pela diferença de level (regra pura)
│   └── MobCursorService       HUD do alvo; nome por jogador se houver PacketEvents
├── breakage/
│   └── WeaponBreakService     golpe no ponto fraco da arma
├── api/                       AincradCombatAPI e os eventos
└── hook/                      pontes para os outros plugins
```

### 3.1 Ameaça (aggro)

Hoje o mob do Minecraft ataca quem está mais perto, e o chefe também (`FightManager.nearest`).
No SAO, quem segura o mob é quem ele mais odeia.

- **Tabela por mob, em memória.** Nasce no primeiro golpe e some quando o mob morre, é removido
  ou fica 15 s sem ninguém a menos de 32 blocos. Nada vai para o banco.
- **O que gera ameaça**: dano causado (1 ponto por ponto de dano); cura em aliado (metade,
  dividida entre os mobs que odeiam o curado); skill de provocação (valor fixo do `skills/*.yml`,
  campo novo `ameaca`); `ameaca_x` de item ou passiva multiplica tudo (escudo e armadura pesada
  acima de 1, adaga e Furtividade abaixo).
- **Troca de alvo com folga**: o mob só larga o alvo atual quando outro passa de **110%** da
  ameaça dele em corpo a corpo, ou **130%** à distância. Sem isso, o mob fica pulando entre dois
  jogadores. A conta roda a cada 10 ticks e quando alguém toma a ponta.
- **Decaimento**: quem sai de 24 blocos perde 10% por segundo; quem morre zera.
- **Como o alvo é imposto**: `Mob.setTarget` no relógio, e o `EntityTargetLivingEntityEvent` é
  cancelado quando o mob tenta escolher sozinho alguém que não é o primeiro da tabela.
- **Chefes**: com o AincradCombat instalado, o AincradBosses pergunta à API quem é o alvo em vez
  de usar o mais próximo; as habilidades com alvo (investida, salto, sopro) vão no primeiro da
  ameaça. Uma habilidade pode pedir `alvo: aleatorio` ou `alvo: mais_longe`, para o chefe punir
  quem fica atrás.
- **MythicMobs**: mob com tabela de ameaça própria do Mythic fica de fora (`mythic: respeitar`).
- **O jogador vê**: quem tem a ameaça do mob que ele mira aparece no HUD do alvo, e um aviso
  quando passa de 90% do primeiro ("você vai puxar o mob").

### 3.2 Switch

- **Quando acontece**: o jogador A acerta um mob com uma Sword Skill e entra na pausa
  (`AincradSkillsAPI.getDelayTicks`). Se um colega de party B acerta **o mesmo mob** dentro da
  janela (`switch.janela_ticks`, padrão 30), é um Switch.
- **O que faz**: B passa a ser o alvo (ameaça de B = a do primeiro + 10%); a ameaça de A cai 30%;
  o mob fica atordoado por 10 ticks; o golpe de B sai com +20% de dano; os dois veem "Switch!"
  (AincradEffects) e ouvem o som.
- **Limites**: mesma party (AincradSocial); recarga de 6 s por dupla; chefes aceitam Switch mas
  não o atordoamento (`chefes.atordoar: false`).
- **Sem o AincradSkills** não há pausa, então o Switch fica desligado. A ameaça funciona sozinha.
- Evento `SwitchEvent(quemSaiu, quemEntrou, mob)`, para conquistas e quests.

### 3.3 Estados

A paralisia de hoje é lentidão + fadiga dadas direto pelo sopro do chefe, e o veneno de alquimia
é um efeito do AincradLife. Os dois passam a ser estados do AincradCombat, e ganham companhia.

`estados.yml`, um bloco por estado, montado com as mesmas peças:

```yaml
veneno:
  nome: "<dark_green>Veneno"
  icone: "☠"
  dano_por_segundo_pct: 1.5      # da vida máxima, por acúmulo
  max_acumulos: 3
  bloqueia: [regeneracao]
  nao_mata: true                  # para em 1 de vida; o SAO mata, este servidor escolhe
  cura: [antidoto, cristal_antidoto]
sangramento:
  dano_por_segundo_pct: 1.0
  dobra_ao: [correr, atacar]      # parado, sangra a metade
  cura: [atadura, cristal_cura]
atordoamento:
  bloqueia: [mover, atacar, skill, item, cristal]
  duracao_maxima_segundos: 3
  imunidade_depois_x: 2           # imune pelo dobro do tempo que ficou atordoado
queda:
  bloqueia: [mover, atacar, skill]
  dano_recebido_x: 1.25
  duracao_maxima_segundos: 2
paralisia:
  bloqueia: [mover, atacar, skill, item]
  permite: [cristal]              # como no anime: paralisado, só o cristal salva
  cura: [pocao_antiparalisia, cristal_antidoto]
```

- **Quem aplica**: qualquer plugin, por `AincradCombatAPI.apply(alvo, estado, potencia, ticks,
  origem)`. Nos arquivos:
  - `spawns.yml` (AincradRPG): `estados: [{ id: veneno, chance: 0.3, segundos: 6 }]` na linha do
    mob — o golpe dele aplica.
  - `bosses/*.yml`: campo `estado:` em qualquer habilidade de dano, e `imune: [atordoamento, queda]`
    no chefe. O `sopro` passa a aplicar `paralisia` pelo motor.
  - `skills/*.yml`: campo `estado:` na skill (o golpe que atordoa, o que derruba).
  - AincradLife: o veneno e o óleo de arma chamam a API em vez de darem o efeito direto.
- **Resistência**: de 0 a 100% por estado, somando poções (as de resistência do AincradLife já
  existem), bônus de item (`resistencia_veneno` no `items.yml`) e passivas. Reduz a duração; com
  100%, o estado nem entra.
- **Cura**: cada estado lista o que o tira. O cristal de antídoto (AincradItems) e as poções
  chamam `AincradCombatAPI.cure(alvo, causa)`.
- **Mobs também sofrem**: o motor vale para qualquer `LivingEntity`, então o jogador envenena e
  atordoa mobs. Chefes têm a lista `imune`.
- **Sair não cura**: ao sair, os estados do jogador vão para o perfil do core com o tempo que
  falta e voltam ao entrar, em qualquer servidor.
- **PvP**: aplicar estado num jogador conta como ataque para o AincradReputation (mesma regra do
  `PoisonAttackEvent` de hoje) e não funciona em zona segura, fora de duelo.
- **Na tela**: o AincradUI mostra os ícones com o tempo ao lado da vida, do jogador e dos colegas
  de party (`AincradUIAPI.setStatusProvider`). Sem o AincradUI, action bar.
- Eventos: `StatusApplyEvent` (cancelável), `StatusExpireEvent`, `StatusCureEvent`.

### 3.4 Cor do cursor dos mobs

A cor depende de **quem olha**, e o Minecraft só tem um nome por entidade. Por isso são dois níveis:

1. **HUD do alvo** (sempre): o mob que o jogador mira ou em que bateu aparece na barra do
   AincradUI com o nome na cor do cursor. Não precisa de nada além do que já existe.
2. **Nome sobre a cabeça por jogador** (opcional): com o PacketEvents instalado, o plugin troca a
   cor do nome no pacote que vai para cada jogador. Sem ele, o nome fica como hoje.

| Level do mob − level do jogador | Cor (padrão, editável) |
|---|---|
| −10 ou menos | rosa pálido |
| −9 a −3 | rosa |
| −2 a +2 | vermelho |
| +3 a +6 | vermelho escuro |
| +7 ou mais | carmesim quase preto |

Chefes ficam sempre na cor de chefe. `CursorRules` é uma função pura, testada com a tabela.

### 3.5 Quebra de arma

- **Quando**: o golpe acerta um oponente armado **enquanto ele prepara ou executa uma skill, ou
  bloqueia** (janela dada pelo AincradSkills). Em vez de vida, o golpe tira durabilidade da arma
  dele, multiplicada por `quebra.dano_x`.
- **Contra quem**: jogadores em duelo ou PvP, e mobs com `arma_quebra: true` no `spawns.yml`
  (o mob desarmado perde dano).
- **Ao zerar**: por padrão a arma fica **inutilizada** (não bate, não usa skill) até um ferreiro
  reparar; `quebra.ao_zerar: destruir` faz como no anime. Itens únicos nunca são destruídos.
- Evento `WeaponBreakEvent`. A skill única "Arms Blast" pode ser uma das skills únicas da etapa 56.

### 3.6 API e dados

```
AincradCombatAPI
  Player  topThreat(LivingEntity mob)            double getThreat(LivingEntity mob, Player p)
  void    addThreat(LivingEntity mob, Player p, double v)     void clearThreat(LivingEntity mob)
  boolean apply(LivingEntity alvo, String estado, int potencia, int ticks, Entity origem)
  boolean has(LivingEntity alvo, String estado)   void cure(LivingEntity alvo, String causa)
  double  getResistance(Player p, String estado)
  String  cursorColor(Player quemOlha, LivingEntity mob)
```

Banco: nenhuma tabela. Perfil do core: `combat.status` (estados ao sair). Arquivos: `config.yml`,
`estados.yml`, `messages.yml`.

---

## 4. AincradSkills — passivas, mods, encadear e Artes Marciais

### 4.1 Passivas novas

Entram no `PassiveService`, ocupam os mesmos slots de passiva e sobem de 0 a 1000 com o uso, como
as armas.

| Passiva | O que faz | Sobe quando |
|---|---|---|
| **Furtividade** | Agachado e parado ou andando devagar: os mobs só notam o jogador de perto (alcance × 0,3 a 1000) e o nome dele some para outros jogadores. Atacar ou tomar dano revela. | Passa por mobs sem ser notado |
| **Busca** (já existe) | Ganha o confronto contra a Furtividade: quem tem Busca maior que a Furtividade do outro o vê | — |
| **Escuta** | Ativa (recarga): por 5 s, jogadores e mobs atrás de paredes num raio aparecem com contorno, só para quem usou | Usa perto de alguém |
| **Corrida** | Mais velocidade fora de combate; desliga 5 s depois de bater ou apanhar | Corre |
| **Acrobacia** | Menos dano de queda e pulo mais alto; a 500, um impulso no ar | Cai de alto, pula |
| **Visão Noturna** | Visão no escuro, ligada e desligada no menu; o alcance cresce com a proficiência | Fica em lugar escuro |
| **Carga Ampliada** | Mais limite de peso (seção 5.3) | Anda carregado |
| **Destrancar** e **Desarmar** | Abrem baús trancados e desarmam armadilhas (seção 9.1) | Tenta |

O contorno da Escuta e o sumiço do nome usam o que o Paper já oferece por jogador
(`Player.hideEntity`, time de scoreboard por jogador para o brilho), sem pacote.

### 4.2 Mods de skill

- A cada marco de proficiência (`mods.marcos`: 50, 100, 200, 350, 500, 750, 1000) o jogador
  escolhe **um entre dois ou três** mods, num menu. Valem para armas e para passivas.
- `mods.yml`: por arma ou passiva, a lista de mods de cada marco (recarga −10%, alcance +1, pausa
  −15%, ameaça +20%, chance de sangramento…). Um mod é só uma lista de modificadores que o
  `SkillEngine` soma ao calcular a skill.
- A escolha fica no `SkillStore` (vale na rede). Refazer custa Col, num NPC (`mods.refazer_col`).
- Marco alcançado sem escolha fica pendente e o `/skills` avisa.

### 4.3 Encadear skills

- No fim de uma skill há uma janela (`encadear.janela_ticks`, padrão 6). Iniciar **outra** skill
  dentro dela cancela a pausa da primeira.
- Cada elo estreita a janela em 20% e a pausa final cresce com o tamanho da corrente. Errar a
  janela dobra a pausa: é arriscado de propósito.
- Só encadeia o que o arquivo deixa (`encadeia_com: [punho]` na skill). O par clássico é espada
  → Artes Marciais → espada.
- A janela aparece como um flash na barra de recarga do AincradUI.

### 4.4 Artes Marciais

- Um tipo de arma novo, `punho`: mão principal vazia. Arquivo `skills/punho.yml` com três skills.
- **Não vem liberada**: é recompensa de quest (`recompensas: skill: artes_marciais`, que o
  AincradQuests já entende). No pacote do andar 2, a quest "A rocha do eremita" passa a dar a
  skill além da lâmina.
- Dano baixo, pausa curta, e é a que encadeia com todas as outras.

---

## 5. AincradRPG — itens e progressão

### 5.1 Farm que cansa

- Cada área do `spawns.yml` guarda, por jogador (e a party conta junto), os abates da última hora.
- Passado `fadiga.livre` abates (padrão 60), o XP e a chance de drop daquela área caem em
  degraus até `fadiga.minimo` (40%). Voltam ao normal com o tempo longe da área.
- O jogador vê no action bar quando entra num degrau ("Esta área está esgotada para você").
- Em memória por servidor, com uma cópia no perfil do core para não zerar trocando de servidor.
- As quests do Cardinal (seção 8.1) puxam para as áreas menos caçadas: as duas coisas juntas
  espalham os jogadores.

### 5.2 Penalidade de morte ampliada

Hoje: 10% do XP do level. Passa a ter quatro partes, cada uma com a sua chave em `morte:`.

| Parte | Padrão sugerido | Observação |
|---|---|---|
| XP | 10% do XP do level (como hoje) | Nunca desce de level |
| Col | 5% do que está na carteira | O do banco e o do armazém não |
| Durabilidade | 10% de cada peça equipada | Peça que zera fica inutilizada, não some |
| Item | desligado | Ligado: um item não equipado cai no chão, com posse (seção 5.4) |

Duelos não cobram nada. As conquistas "sem cair" da etapa 66 usam o mesmo evento de morte.

### 5.3 Peso e força

- Todo item tem peso: o do `items.yml` (`peso: 12`) ou o padrão por material em `pesos.yml`.
- **Limite de carga** = `base + força × por_ponto` (+ a passiva Carga Ampliada).
- Até 100%: normal. De 100% a 150%: lentidão crescente e sem Corrida. Acima de 150%: não pega mais
  nada do chão.
- **Força mínima** (`forca_minima: 20` no item): abaixo dela a arma bate com a mesma
  `penalidade_arma` que já existe para level. As duas não se somam; vale a pior.
- A barra de peso aparece no menu de inventário do AincradUI e no `/status`.
- O peso é recalculado só quando o inventário muda (eventos de pegar, largar, clicar), nunca por
  relógio.

### 5.4 Posse e durabilidade no chão

- Item largado ou caído na morte leva no próprio item (PDC) o dono e a hora.
- Por `posse.segundos` (padrão 300) só o dono e a party dele pegam. Depois, qualquer um.
- Equipamento do AincradRPG no chão perde durabilidade por minuto (`posse.desgaste_por_minuto`).
  Ao zerar, some (itens únicos voltam para o dono pelo correio).
- O nome flutuante do item mostra o tempo de posse que falta.

### 5.5 Itens não identificados

- Drop de raridade `identificar.a_partir_de` (padrão: raro) para cima cai **selado**: os bônus já
  foram sorteados e estão no item, mas a descrição mostra "???" e ele não pode ser equipado.
- Identifica: o NPC avaliador, por Col; ou um jogador com a profissão Mercador (seção 7.2), que
  cobra o que quiser.
- Itens de loja, de quest, de forja e de Last Attack nunca vêm selados.
- `AincradRPGAPI.isSealed(item)` e `identify(item)`.

---

## 6. AincradItems — cristais novos

### 6.1 Cristal de corredor

- **Dois estados no item**: em branco e gravado. Agachar e usar grava o lugar (servidor, mundo,
  posição, andar). Não grava em sala de chefe, área anti-cristal, casa dos outros nem dungeon.
- **Usar** (gravado): abre um portal onde o jogador está, por `corredor.duracao_segundos` (60) ou
  até passarem `corredor.max_pessoas` (48, uma raid). O cristal é gasto ao abrir.
- **Quem passa**: `corredor.quem: todos | party | raid`. Cada um que entra é conferido: andar
  liberado para ele, não está em combate, não está preso.
- **Entre servidores**: o portal aberto é anunciado pelo barramento do core; quem entra é levado
  pelo mesmo caminho do `/casa ir` (o destino fica guardado e o jogador aparece nele ao chegar).
- **Visual**: anel de partículas e um texto flutuante com o destino; com o AincradEffects, o
  efeito `aincrad_corredor`.
- **Portais abertos** ficam em memória; se o servidor reinicia, somem (o cristal já foi gasto —
  o README avisa).
- **Uso contra jogadores** (a captura da Laughing Coffin): ninguém é empurrado para dentro. O
  que existe é um destino especial, `prisao`, só nos cristais dados pelos guardas (seção 10): um
  jogador **paralisado** ou atordoado, laranja ou vermelho, pode ser mandado por quem o derrubou.

### 6.2 Cristal de gravação

- Usar começa a gravar por até 60 s tudo o que é dito no chat local a `gravacao.raio` blocos,
  com quem falou e quando. Usar de novo para.
- Depois de gravado não regrava: é prova. Quem segura o cristal ouve a gravação no chat, com as
  falas no ritmo em que foram ditas.
- A gravação vai dentro do item (PDC), com limite de tamanho. Pode ser vendido, trocado e
  entregue em quest (`tipo: entregar, item: "items:gravacao"`).
- Precisa de um chat local. Se o servidor não usa o do AincradSocial, o raio vale sobre o chat comum.

### 6.3 Cristal de mensagem

- É o único lugar em que o jogador digita: escolhe o destinatário no menu (amigos, party,
  guilda), e escreve o texto no chat (capturado, não enviado) ou escolhe uma frase pronta.
- **Chega onde a mensagem comum não chega**: dentro de labirinto e dungeon (seção 11) e em outro
  servidor. Offline, fica no correio.
- Gasto ao enviar.

---

## 7. AincradLife — coleta, profissões e contratos

### 7.1 Coleta geral

Os pontos de coleta da alquimia viram um módulo só, `coleta`, com quatro tipos.

| Tipo | Ferramenta | Dá | Quem usa |
|---|---|---|---|
| Ervas (já existe) | mão | reagentes | alquimia |
| Minério | picareta | minérios e pedras, por nível do veio | ferreiro |
| Madeira | machado | madeira comum e rara | carpinteiro, ferreiro (cabos) |
| Fibra | tesoura | linho, algodão, seda | alfaiate |

- **Ponto**: um bloco marcado pelo admin (`/aincrad coleta`, por menu, olhando para o bloco). O
  bloco não quebra; o ponto "esgota" e renasce depois.
- **Esgota por jogador** (`coleta.por_jogador: true`, padrão): dois jogadores não brigam pelo
  mesmo veio. `false` faz como no anime, um veio para todos.
- **Proficiência de coleta** (uma por tipo, 0 a 1000): mais rendimento, veios de nível maior e a
  chance do material raro do ponto.
- Os materiais são ingredientes do AincradLife (`life:<id>`), que as receitas, a forja e as
  lojas já aceitam.
- Os pacotes de andar passam a listar os pontos de cada região.

### 7.2 Profissões novas

As quatro seguem o molde que já existe (`ProfessionService`, árvore, bancada, saberes): cada uma
é um arquivo de árvore, um de receitas e uma bancada.

| Profissão | Faz | Bancada | Particular |
|---|---|---|---|
| **Alfaiate** | Armaduras leves, capas, bolsas (mais limite de peso) | Mesa de corte | Qualidade pelo minijogo de costura, como as batidas da forja |
| **Carpinteiro** | Móveis, baús (armazém da casa), barcos, cabos de arma | Bancada de marcenaria | Os móveis só podem ser postos na casa do dono; com o ItemsAdder, usam os modelos de móvel dele |
| **Músico** | Toca num lugar: bônus de área para quem ouve (regeneração, XP de profissão) | Instrumento na mão | Só em zona segura e acampamento; um bônus por vez por ouvinte |
| **Mercador** | Avalia itens selados, vende melhor ao NPC, mais vagas na barraca | — | A Avaliação é a habilidade que identifica (seção 5.5) |

O teste `TreeEffectsTest`, que falha se um nó de árvore não tem efeito, passa a cobrir as quatro.

### 7.3 Refundir arma

- Na bigorna, opção nova **Refundir**: a arma vira um **Lingote de \<arma\>**.
- O lingote carrega uma parte dela: `refundir.heranca` (padrão 50%) dos aprimoramentos,
  arredondando para baixo, e a raridade.
- Usado como núcleo na forja, a arma nova já nasce com os aprimoramentos herdados e com tentativas
  de aprimoramento a mais.
- Itens únicos e de Last Attack não podem ser refundidos, salvo `refundir.permitir_unicos`.

### 7.4 Mercenários e aprendizes

Dois contratos novos no quadro de encomendas, com a mesma custódia de Col que já existe.

- **Guarda-costas**: o inverso da escolta. O cliente contrata alguém por tempo ou até sair de uma
  região. O contratado recebe se o cliente **não morrer** e os dois ficarem a até N blocos; se o
  cliente morre, o Col volta. A reputação de encomendas conta.
- **Aprendiz**: um mestre de profissão (750 ou mais) aceita um aprendiz por um prazo. O aprendiz
  ganha XP de profissão mais rápido perto do mestre; o mestre recebe uma parte do que o aprendiz
  produzir e vender.
- Mercenário NPC fica fora desta leva: NPC que luta ao lado do jogador é um sistema à parte.

---

## 8. AincradQuests — Cardinal e campanhas

### 8.1 Quests do Cardinal

No SAO o Cardinal cria quests sem parar. Aqui ele resolve um problema concreto: os andares 5 a 100
não têm quest escrita.

- **De onde vêm os dados**: do que o andar já tem. Os mobs e levels do `spawns.yml`, os drops
  deles, as regiões, os chefes de campo do `roaming.yml`, os peixes e receitas do AincradLife. O
  gerador **não inventa nada**: só combina o que existe, então nunca pede um mob que não nasce.
- **Moldes** em `cardinal.yml`: matar N de um mob; coletar N de um drop; entregar a um NPC;
  chegar a uma região; derrotar o chefe de campo; pescar; cozinhar. Cada molde tem as suas faixas
  de quantidade e os seus textos, com variáveis.
- **Sorteio com semente**: a semente é a data mais o número do andar. Todos os servidores da rede
  geram **as mesmas** quests do dia sem trocar mensagem nenhuma.
- **Equilíbrio**: com `cardinal.equilibrar: true`, os mobs menos mortos nas últimas 24 horas
  pesam mais no sorteio (o AincradRPG já conta os abates). Junto com o farm que cansa, puxa os
  jogadores para as áreas vazias.
- **Recompensa por fórmula**: XP e Col saem do level do alvo e do esforço (quantidade × level),
  com um teto por andar. A fórmula fica em `cardinal.yml` e num teste, para nenhuma quest gerada
  pagar mais que uma escrita à mão do mesmo andar.
- **Quadro de missões**: um bloco ou NPC por cidade (`/aincrad quadro`). Mostra as
  `cardinal.por_dia` quests do andar (padrão 6); o jogador pega até `cardinal.por_jogador` (3).
  Valem até a virada do dia.
- **Por dentro**: uma quest gerada é um `QuestDef` como os outros, com id
  `cardinal:<andar>:<data>:<n>`. O motor, o diário, o rastreador e a API não mudam.
- **Raras**: com chance baixa o dia traz uma quest rara (um mob raro do andar, ou uma entrega
  longa) com recompensa de item. É o gancho para o jogador olhar o quadro todo dia.

### 8.2 Campanhas entre andares

- `campanhas/*.yml`: uma campanha tem capítulos; cada capítulo é de um andar e é uma lista de
  quests comuns (as que já existem), mais o que ele pede: capítulo anterior concluído, andar
  liberado e, se for o caso, uma **marca**.
- **Marcas** (etapa 63) são as escolhas: `lado_dos_elfos_escuros` ou `lado_dos_elfos_da_floresta`
  decide quais quests e quais NPCs o jogador vê nos capítulos seguintes.
- **Diário**: uma aba "Campanhas" com os capítulos, o que falta e o andar em que cada um se passa.
- **Capítulo de andar que ainda não existe** aparece como "continua no andar N".
- **Companheiro**: o NPC da campanha (Kizmel) é um NPC comum em cada andar. NPC que anda e luta
  com o jogador fica fora desta leva.
- **Conteúdo**: a Guerra dos Elfos, capítulos dos andares 3 e 4, entra nos pacotes desses andares
  (o 3 ganha a escolha de lado; hoje é sempre o dos elfos escuros). Os capítulos 5 a 9 vêm com
  os pacotes de cada andar.

---

## 9. AincradBosses — baús e rolagem

### 9.1 Baús de labirinto

- `chests.yml`: por região, os pontos de baú (marcados por menu) e a tabela de saque do andar.
  A cada reinício do labirinto (`baus.renascer_minutos`) uma parte dos pontos ganha baú.
- **Tipos**, sorteados por peso:
  - **Comum**: abre e pronto.
  - **Trancado**: precisa da passiva Destrancar (confronto de proficiência contra o nível da
    tranca, com um minijogo de tempo no menu) ou de uma chave.
  - **Armadilha**: alarme (chama mobs, como o baú das dungeons escondidas), gás (aplica um estado
    do AincradCombat), teleporte (leva o grupo a outro ponto do labirinto) ou explosão. A passiva
    Desarmar detecta e desarma; a Busca só detecta.
  - **Mímico**: vira um mob do andar, com drop melhor que o do baú.
- **Saque por jogador** (padrão): cada um que abre recebe o seu sorteio, uma vez por
  renascimento. `por_grupo: true` faz um saque só, que vai para a rolagem (9.2).
- O baú-armadilha das dungeons escondidas (etapa 52) passa a ser um caso deste módulo.

### 9.2 Rolagem de dados no drop

- Vale para os itens do chefe de raridade `rolagem.a_partir_de` (padrão: raro) para cima. O Last
  Attack nunca entra: é de quem deu o golpe.
- Derrotado o chefe, cada participante com direito a recompensa vê o item num menu com três
  botões: **Preciso**, **Quero**, **Passo**. Trinta segundos; quem não responde passa.
- "Preciso" ganha de "Quero". Dentro do mesmo grupo, vence o maior dado (1 a 100), e os dados de
  todos aparecem no chat da raid.
- O **líder da raid** escolhe o modo antes da luta, no quadro de raid: `sorteio` (o de cima),
  `lider` (ele distribui) ou `cada_um` (como hoje, um sorteio por participante).
- Quem saiu do servidor depois da luta ainda participa: o item vai pelo correio.
- Fica no AincradBosses, que já entrega as recompensas; a party e a raid vêm do AincradSocial e do
  quadro de raid.

---

## 10. AincradReputation — guardas das cidades

- `guards.yml`: por cidade (região de zona segura), os postos de guarda, marcados por menu.
- **O guarda** é uma entidade do servidor (padrão: golem de ferro com nome; `mythic:` ou
  ModelEngine para o modelo de verdade). Não toma dano, não sai da região da cidade.
- **Regra por cor**, em `guards.yml`:

| Cor | Padrão sugerido | Opções |
|---|---|---|
| Verde | nada | — |
| Laranja | aviso de 10 s para sair; depois, ataque | `ignorar`, `avisar`, `atacar` |
| Vermelho | ataque imediato | `atacar`, `prender` |

- **O ataque** tira uma fração da vida máxima por golpe (`guardas.dano_pct`), aplicado pelo plugin
  — a zona segura bloqueia o PvP, não o guarda. O guarda alcança: se o alvo foge, ele reaparece
  ao lado dentro da cidade.
- **Derrotado pelos guardas**, o jogador vai para a prisão que já existe, com a pena da cor dele.
- **Exceções**: quem está em duelo; a entrada da prisão; cidades marcadas `sem_guardas` (uma
  cidade sem lei num andar alto é conteúdo).
- **Detecção**: no evento de entrar na região (o core já dispara) e num relógio de 2 s para quem
  mudou de cor dentro da cidade.
- Evento `GuardAttackEvent`. Na rede, cada servidor cuida das suas cidades.

---

## 11. AincradSocial — mensagens com limite

- O AincradFloors ganha uma marca de região: `sem_mensagens: [labirinto_andar1, ...]`.
- `/msg` e mensagem de amigo para quem está numa dessas regiões **não chegam na hora**: ficam na
  caixa de entrada que já existe e são entregues quando ele sai. Quem mandou é avisado ("está num
  labirinto").
- Chat de party e de guilda continuam funcionando (`limite.party`, `limite.guilda` mudam isso).
- O cristal de mensagem (6.3) atravessa.
- Padrão **desligado**: é fiel ao anime, mas incomoda. O dono liga se quiser.

## 12. AincradEconomy — armazém pessoal

- Páginas de 54 espaços por jogador. A primeira é de graça; as outras custam Col, com preço que
  cresce (`armazem.preco_pagina`).
- **Acesso**: no NPC de estalagem (AincradLife), no baú de armazém da própria casa (feito pelo
  carpinteiro) e, com permissão, por `/armazem`.
- **Na rede**: tabela `eco_storage` no banco do core, com a mesma trava de uso do cofre da
  guilda — a página aberta num servidor fica travada nos outros até fechar.
- O casamento compartilha uma página, não o armazém inteiro.
- O Col do armazém não existe: Col fica na carteira ou no banco.

## 13. AincradFloors — sala segura, portal e dados do mapa

### 13.1 Sala segura

- `salas_seguras: [descanso_labirinto1, ...]` por andar: zona segura como as `vilas`, mas não
  aparece na lista de cidades nem vale como destino de cristal de teleporte.
- O AincradRPG não cria mob dentro dela e os mobs que entram perdem o alvo e voltam.
- Sair do servidor ali é seguro; o cristal de corredor pode ser gravado ali (é para isso que a
  raid usa).

### 13.2 Portal que abre sozinho

- Hoje, derrotar o chefe libera o andar seguinte na hora. Com `portal.ativacao: toque`, o andar é
  liberado mas o **portal da cidade nova fica apagado**.
- Os jogadores sobem a pé pela escada da sala do chefe. O primeiro que toca o portal o ativa para
  todos, é anunciado e ganha a conquista.
- Se ninguém toca em `portal.abre_sozinho_horas` (2), ele ativa sozinho.
- `portal.ativacao: imediata` mantém o comportamento de hoje (padrão, para não mudar servidores
  já configurados).

### 13.3 Dados do mapa como item

- O mapa que se revela a pé (etapa 54) guarda, por jogador, os setores visitados.
- No menu do mapa, **Copiar dados** cria um item "Dados do Mapa — Andar N" com os setores que o
  jogador conhece naquele momento (AincradItems cria o item; os setores vão no PDC).
- Usar o item revela esses setores para quem usou e o gasta. Pode ser vendido, trocado, posto na
  barraca e vendido pelos informantes.
- O item mostra quantos setores tem e de quem veio: mapa de quem só andou pela cidade vale pouco.

---

## 14. AincradWorld — festivais, relíquias e enigmas

### 14.1 Festivais

- `festivais.yml`: um festival tem um gatilho — uma data do calendário (Ano Novo) ou um
  acontecimento (andar liberado → festa de 24 horas na cidade nova).
- **O que um festival liga**, enquanto dura: uma região de decoração (blocos trocados e
  devolvidos no fim, de um esquema salvo), NPCs vendedores que só aparecem nele, fogos em horas
  marcadas, um bônus (XP, pesca) e quests próprias.
- A contagem de Ano Novo é uma sequência do AincradEffects disparada pelo calendário.
- O jornal (etapa 44) anuncia o festival na edição da semana.

### 14.2 Caça a relíquias

- `reliquias.yml`: por andar, os pontos escondidos (marcados por menu) e a tabela de troca.
- Cada ponto é achado **uma vez por jogador** por temporada; alguns só aparecem com a Busca alta.
- As relíquias são trocadas num NPC por bônus temporários (como as do andar 5) ou por itens.
- Reinicia por estação do calendário, para a caça voltar.

### 14.3 Enigmas

- Peças prontas em `enigmas.yml`, cada uma com o seu estado e a sua solução:
  - **Sequência**: alavancas ou placas na ordem certa.
  - **Luzes**: uma grade no menu em que clicar inverte os vizinhos.
  - **Deslizar**: o quebra-cabeça de peças num menu.
  - **Charada**: uma pergunta com as respostas em botões.
- **O que resolver faz**: abre uma porta (uma região de blocos, como a porta da sala do chefe),
  conta o objetivo `tipo: enigma` de uma quest, ou dá um saque.
- Enigma de grupo: o estado é do lugar, não do jogador; volta ao início depois de um tempo.
- A API (`AincradWorldAPI.isSolved`, evento `PuzzleSolvedEvent`) é o que o AincradQuests e o
  AincradBosses usam.

---

## 15. AincradCasino (pasta `aincrad-casino`) — plugin novo

O cassino e a arena de monstros do andar 7. Precisa do AincradCore e do AincradEconomy. Opcionais:
AincradRPG (os mobs da arena), AincradFloors, AincradUI, AincradEffects, AincradReputation.

```
com.aincrad.casino
├── chips/      ChipService, ChipStore        as fichas de cada jogador
├── games/      Game, Roulette, Dice, Blackjack, Slots, GameRules (regra pura)
├── arena/      ArenaMatch, Odds (regra pura), ArenaService, BetPool
├── prizes/     PrizeShop                     a loja de prêmios
├── api/        AincradCasinoAPI e os eventos
└── hook/
```

- **Fichas**: compradas com Col no caixa. **Não voltam a ser Col**: só compram prêmios, como no
  cassino de Volupta. Isso impede o cassino de virar máquina de fazer Col.
- **Só dinheiro do jogo**: fichas não são vendidas por dinheiro de verdade nem entram em loja de
  VIP. As regras da Mojang proíbem jogo de azar com dinheiro real; o README diz isso com todas as
  letras.
- **Jogos**, todos por menu: roleta, dados (maior ou menor), vinte-e-um e caça-níquel. Cada um é
  uma classe de regra pura com a vantagem da casa na config e um teste que confere o retorno
  esperado em um milhão de rodadas.
- **Arena de monstros**: lutas marcadas entre dois mobs do `spawns.yml`. As chances saem dos
  levels e atributos; a aposta é rateada (o que os perdedores apostaram é dividido entre os
  vencedores, menos a parte da casa). A luta acontece de verdade numa região de arena, com os
  dois mobs mandados um contra o outro; se a arena não está montada, o resultado é sorteado
  pelas chances.
- **Limites**: máximo de fichas compradas por dia e por aposta, para ninguém perder tudo numa
  noite.
- **Onde funciona**: só nas regiões marcadas como cassino.
- **Registro**: toda rodada vai para `casino_log` (quem, jogo, aposta, resultado), para o dono
  auditar; o AincradAdmin já mostra registros assim.
- Banco: `casino_chips`, `casino_bets`, `casino_log`. Vale na rede.

---

## 16. Etapas, ordem e dependências

A tabela completa (67 a 94) está no [plano geral](01-plano.md#sétima-leva--o-que-falta-do-sword-art-online).
A ordem vai do combate, que muda mais a experiência, para o que é conteúdo de andar alto.

- **Primeiro o AincradCombat** (67 a 72): estados e ameaça são usados por chefes, mobs, skills,
  baús e guardas. Fazer antes evita voltar em cinco plugins depois.
- **67 → 68**: o Switch precisa da ameaça. **69 → 70**: as ligações precisam do motor de estados.
- **74 → 86**: os baús trancados usam as passivas Destrancar e Desarmar.
- **78 → 79**: itens selados e refundir usam o peso e a posse.
- **83 → 84 e 85**: as profissões novas usam os materiais da coleta.
- **80 → 73**: o cristal de corredor com destino `prisao` é dado pelos guardas; os guardas
  funcionam sem ele.
- **89** (Cardinal) não depende de nada novo e pode subir na fila se a prioridade for conteúdo.
- **93 e 94** (cassino) são as últimas: são do andar 7, e o servidor tem pacotes até o 4.

## 17. Decisões que são suas

Cada uma tem um padrão sugerido, que é o que o plano assume até você mudar.

1. **Veneno mata?** Sugerido: não (para em 1 de vida). No anime mata.
2. **Laranja entra na cidade?** Sugerido: os guardas avisam e depois atacam.
3. **Arma quebrada some?** Sugerido: fica inutilizada até reparar.
4. **O que morrer custa**: sugerido 10% do XP do level, 5% do Col da carteira, 10% de
   durabilidade, sem perder item.
5. **Nome colorido sobre a cabeça dos mobs** pede o PacketEvents instalado. Sem ele, a cor aparece
   só na barra do alvo.
6. **Mensagens que não chegam no labirinto**: sugerido desligado.
7. **Portal que abre sozinho**: sugerido manter a liberação imediata e deixar o toque como opção.
8. **Veio de minério por jogador ou um para todos?** Sugerido: por jogador.
9. **Cassino**: sugerido fazer por último, com fichas que não voltam a ser Col.
