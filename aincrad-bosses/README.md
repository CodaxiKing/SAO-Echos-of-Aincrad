# AincradBosses

Os chefes de andar (etapas 5–9 do plano): sala trancada, fases, habilidades, registro de dano,
Last Attack, liberação do próximo andar e o labirinto. Precisa do **AincradCore** e do
**AincradFloors**. Opcionais: AincradRPG (XP, itens com raridade, level dos mobs), AincradEconomy
(Col) e AincradSocial (registro da vitória para a guilda).

## O que faz

- **Chefes em YAML** (`bosses/*.yml`, um ou mais por arquivo): andar, nome, level, mob base, vida,
  dano, armadura, velocidade, resistência a empurrão, tamanho (`escala`), equipamento e fases.
  O exemplo é o Illfang (`bosses/andar1.yml`).
- **Fases por vida**: cada fase começa num percentual e pode trocar equipamento, multiplicar dano
  e velocidade, falar e mudar as habilidades. O Illfang troca o machado e o escudo por uma espada
  aos 25% e fica 40% mais rápido.
- **Habilidades com recarga**: investida, ataque em área (círculo vermelho de aviso no chão),
  lacaios, projétil, salto com impacto e fúria. Uma habilidade pronta é escolhida ao acaso, com
  um intervalo entre elas.
- **Sala do chefe**: a luta começa quando alguém pisa na sala. Há uma contagem
  (`sala.espera_segundos`) para o grupo entrar; depois a porta fecha, o chefe aparece parado e
  invulnerável enquanto a barra enche (`intro_segundos`) e a luta começa. Ninguém entra numa
  luta em andamento, e quem sai, morre ou desloga não volta.
- **Fim**: vitória quando o chefe morre; derrota quando não sobra ninguém na sala; tempo limite.
  Nos três casos a porta abre. Depois de derrota ou tempo o chefe só volta após
  `sala.recarga_derrota_segundos`.
- **Barra de vida** (BossBar) com nome, level e fase, só para quem está na sala.
- **Registro de dano e recompensas**: quem causou pelo menos `dano_minimo_pct` da vida do chefe
  é participante e recebe XP e Col; os itens são divididos em rodízio, começando por quem causou
  mais dano. O item de **Last Attack** vai para quem deu o golpe final.
- **Liberação do andar**: na primeira vitória da rede, chama
  `AincradFloorsAPI.unlockFloor(andar + 1, participantes)`. Depois o chefe volta só como desafio,
  a cada `reaparece_horas` (0 = não volta). As vitórias ficam no banco do core.
- **Labirinto**: zonas de spawn com level de mob, limite e intervalo. Crie as zonas com level
  cada vez maior conforme chegam perto da sala do chefe. Os mobs só nascem com jogador por perto,
  nunca a menos de 6 blocos de alguém, e não são salvos com o mundo.

## Extras (etapas 18 e 19)

- **Proteção contra abuso**: quem sai do servidor no meio da luta conta como morto, não recebe
  recompensa e, ao voltar, é posto para fora da sala (no ponto `entrada`, ou na chegada do andar).
  Lutador que fica `abuso.ausente_segundos` (60) sem andar nem acertar o chefe é avisado na tela e,
  se continuar parado até o fim, não recebe recompensa nem conta como participante.
- **Guia de estratégia** (`/chefe guia <id>`): um livro do chefe que começa em branco. Mostra as
  tentativas, as vitórias e a fase mais longe; cada fase (com a vida em que começa) e cada
  habilidade só aparece depois de vista em alguma tentativa do servidor. Vale para a rede.
- **Quadro de raid** (`/raid`): `/raid criar <chefe> <dd/MM> <HH:mm> [tanques] [danos] [suportes]`
  marca a raid (padrão 2/6/2 vagas); os outros se inscrevem pelo menu ou por `/raid entrar <n> <função>`.
  Os inscritos são avisados 15 min antes e na hora. Com `raid.reservar_sala`, do aviso até o fim
  da duração só os inscritos entram na sala. A raid some do quadro depois de `raid.duracao_minutos`.
- **Editor no jogo** (`/aincrad chefe editar <id>`): menus com − e + para vida, dano, level,
  armadura e velocidade; fases (vida em que começa, dano, velocidade, criar e remover);
  habilidades (tipo, recarga, dano, raio); XP, Col e dano mínimo. Em "Itens", um baú onde você
  **arrasta os itens de recompensa** (as cinco fileiras de cima são divididas entre os
  participantes; o espaço do meio de baixo é o Last Attack). Tudo é gravado no YAML do chefe e
  vale na próxima luta. Itens com nome, lore ou encanto são guardados inteiros; os do AincradRPG
  são guardados como `rpg:id:raridade` e sorteados de novo a cada entrega.
- **Sala anti-cristal**: `anti_cristal: true` no chefe (padrão) e `AincradBossesAPI.isCrystalBlocked(Location)`
  para o AincradItems.

## Como montar a sala de um chefe

1. Em um canto da sala: `/aincrad chefe sala illfang canto1`; no canto oposto: `canto2`
   (conta a altura: vá de um canto de baixo a um canto de cima).
2. Onde o chefe nasce: `/aincrad chefe sala illfang spawn`.
3. Deixe o vão da porta aberto (ar), marque-o com `/aincrad varinha` e use
   `/aincrad chefe porta illfang`. Durante a luta o vão é tapado com `sala.porta_material`.
4. Teste com `/aincrad chefe invocar illfang` e encerre com `/aincrad chefe resetar illfang`.

Sem sala, `invocar` solta o chefe onde você está, com quem estiver por perto.

## Comandos (`aincrad.admin`)

| Comando | O que faz |
|---|---|
| `/aincrad chefe lista` · `info <id>` | Chefes carregados e o estado de cada um |
| `/aincrad chefe sala <id> canto1\|canto2\|spawn\|entrada` | Marca a sala na posição atual |
| `/aincrad chefe porta <id>` | O ar da seleção da varinha vira o vão da porta |
| `/aincrad chefe invocar <id>` | Começa a luta agora (teste) |
| `/aincrad chefe resetar <id>` | Encerra a luta e abre a porta |
| `/aincrad chefe esquecer <id>` | Apaga as vitórias: a próxima volta a liberar o andar |
| `/aincrad chefe editar <id>` | Abre o editor do chefe |
| `/chefe guia <id>` | Guia de estratégia (`aincrad.bosses.guia`, todos) |
| `/raid` · `criar` · `entrar` · `sair` · `cancelar` | Quadro de raid (`aincrad.bosses.raid`, todos) |
| `/aincrad labirinto zona <andar> <nivel>` | Cria uma zona com a seleção da varinha |
| `/aincrad labirinto listar` · `remover <id>` | Zonas |
| `/aincrad reload` | Recarrega configs, chefes, salas e zonas |

As ações disparam o `AincradAdminActionEvent` do core.

## API

`com.aincrad.bosses.api.AincradBossesAPI` (registrada no ServicesManager; métodos estáticos):
`getBosses()` (id, nome, level, andar, porta, sala, derrotado, quem derrotou, em luta),
`isDefeated(andar)`, `isBossRoom(Location)`, `isCrystalBlocked(Location)`, `isFighting(Player)`, `getFight(Player)`.

Eventos: `BossFightStartEvent` (jogadores, segundos de introdução), `BossDamageEvent` (jogador,
dano, crítico), `BossPhaseChangeEvent`, `BossDefeatEvent` (Last Attack e o item dele, participantes, dano, itens,
XP e Col de cada um, primeira vitória) e `BossFightEndEvent` (derrota, tempo ou reset).

`AincradBossesAPI.getRaid(long id)` devolve uma raid marcada no quadro (chefe, líder, hora e
inscritos), ou null. O AincradLife usa para o banquete de raid.

## Arquivos

- `config.yml`, `messages.yml`, `bosses/*.yml` — você edita.
- `salas.yml` e `labirinto.yml` — criados pelos comandos (mobs, limite e intervalo de cada zona
  podem ser ajustados em `labirinto.yml`).
- Banco do core: `boss_state` (vitórias, datas, Last Attack, participantes), `boss_guide` (guia) e
  `boss_raids` / `boss_raid_members` (quadro de raid).

## Escolhas feitas onde o plano não definia

- A porta é o **vão** (blocos de ar) e fecha com um material da config. Assim, se o servidor cair
  no meio da luta, basta abrir os vãos ao ligar (o plugin faz isso).
- XP e Col vão inteiros para cada participante; só os itens são divididos.
- A introdução em que "a barra enche" é a BossBar indo de 0 a 100%.
- Uma luta por chefe por vez, neste servidor; a sala fica no servidor onde foi marcada.
- O editor grava o YAML com a biblioteca do servidor: os comentários dentro das listas de fases se perdem ao editar pelo jogo.
- As raids usam o relógio e o fuso do servidor.

## Compilar

```bash
./gradlew build
```

Compila com `../aincrad-core`, `../aincrad-floors`, `../aincrad-rpg` e `../aincrad-economy` (build
composto). Testes: `BossLogicTest` (o Illfang de exemplo, a fase dos 25%, erros de cadastro, dano
mínimo, rodízio de itens, caixa da sala).

## Ainda não testado em servidor

As regras de fases, dano, recompensas, guia e raid têm teste (`BossLogicTest`, `ExtrasTest`). A luta
em si (spawn, porta, habilidades, barra, labirinto), os menus do editor e do quadro de raid ainda
não rodaram num servidor Paper: os números do Illfang são um ponto de partida.
