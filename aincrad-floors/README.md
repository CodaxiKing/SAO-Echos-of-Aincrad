# AincradFloors

Os andares do Aincrad (etapas 1–4 do plano): quais estão liberados, quem entra, zona segura nas
cidades, anúncio quando um andar abre e os Teleport Gates. Precisa do **AincradCore**.

## O que faz

- **Cadastro** em `floors.yml`: número, nome, servidor da rede, tipo (um mundo inteiro ou uma
  região do core), ponto de chegada e a região da cidade. Os números vão de 1 em diante, sem
  pular; erros de cadastro aparecem no console com o andar. O andar 1 está sempre liberado.
- **Bloqueio de entrada**: teleporte (portais e troca de mundo incluídos), caminhada para dentro
  da região e entrar no servidor já dentro de um andar bloqueado. Quem tem `aincrad.floors.bypass` passa.
- **Liberação**: grava data e participantes no banco do core, dispara o `FloorUnlockEvent`
  (cancelável) e avisa os outros servidores pelo MessageBus.
- **Anúncio global**: título, chat e som para todos os jogadores da rede. Cita a guilda quando
  todos os participantes são da mesma (AincradSocial). O título desliga em `anuncio.titulo`
  (para o AincradEffects assumir).
- **Zona segura** (a região `cidade` de cada andar): nenhum dano, sem PvP (nem de dentro para
  fora), sem fome, mobs hostis não miram, não nascem e são removidos; blocos não quebram nem com
  explosão. Aviso ao entrar e sair. Com `aincrad.floors.bypass` dá para construir.
- **Teleport Gates**: `/aincrad gate criar <n>` marca o bloco olhado. Clicar abre o menu de
  andares; o teleporte é local ou, se o andar fica em outro servidor, o destino vai no perfil do
  jogador e ele troca de servidor (canal `BungeeCord` do Velocity).
- **Modos** (`config.yml → modo`): `global` (liberado vale para todos), `por_jogador` (só quem
  participou da vitória ou recebeu `/aincrad andar conceder`) e `misto` (aberto a todos, mas o
  Gate só lista os andares que o jogador já visitou a pé). Trocar o modo não perde dados.
- **Linha de frente**: o maior andar liberado em sequência, no `/andares` e nos placeholders.

## Como criar um andar

1. Marque a área com `/aincrad varinha` e crie a região: `/aincrad regiao criar andar2`
   (ou use um mundo inteiro com `tipo: mundo`).
2. Faça o mesmo para a cidade: `/aincrad regiao criar urbus`.
3. Acrescente o andar em `plugins/AincradFloors/floors.yml` e rode `/aincrad reload`.
4. No ponto de chegada: `/aincrad andar setspawn 2`.
5. Olhando para o bloco do portal na cidade: `/aincrad gate criar 2`.

## Comandos

| Comando | O que faz | Permissão |
|---|---|---|
| `/andares` | Lista os andares, liberados e bloqueados | `aincrad.floors.list` |
| `/andar <n>` | Vai para um andar liberado, com espera (se `teleporte.comando: true`) | `aincrad.floors.tp` |
| `/aincrad andar liberar\|bloquear <n>` | Libera ou volta a bloquear | `aincrad.admin` |
| `/aincrad andar setspawn <n>` | Ponto de chegada na posição atual (grava em `floors.yml`) | `aincrad.admin` |
| `/aincrad andar conceder\|revogar <jogador> <n>` | Acesso no modo `por_jogador` | `aincrad.admin` |
| `/aincrad andar info <n>` | Estado, servidor, tipo, cidade e chegada | `aincrad.admin` |
| `/aincrad gate criar <n>` · `remover` · `listar` | Teleport Gates | `aincrad.admin` |
| (permissão) | Entra em andar bloqueado e mexe nas zonas seguras | `aincrad.floors.bypass` |

As ações de admin disparam o `AincradAdminActionEvent` do core.

## API

`com.aincrad.floors.api.AincradFloorsAPI` (registrada no ServicesManager; métodos estáticos):

```java
boolean isUnlocked(int andar)        int getFrontline()            int getFloorCount()
int getFloorAt(Location)             int getFloor(Player)          String getFloorName(int)
boolean unlockFloor(int, Collection<UUID> participantes)           boolean lockFloor(int)
boolean canEnter(Player, int)        boolean isSafeZone(Location)  Location getSpawn(int)
boolean teleport(Player, int)
```

Eventos: `FloorUnlockEvent` (cancelável), `PlayerEnterFloorEvent` (de, para, primeira visita) e
`GateTeleportEvent` (cancelável).

Placeholders (com o PlaceholderAPI): `%aincrad_frontline%`, `%aincrad_floor_atual%`, `%aincrad_floor_nome%`.

## Banco

`floor_state` (andar, liberado, data, participantes), `player_floors` (jogador, andar, visitou,
acesso) e `floor_gates` (bloco → andar). Criadas pelas migrações do core.

## Compilar

```bash
./gradlew build
```

Compila com o `../aincrad-core` (build composto). Precisa do AincradCore desta mesma versão no
servidor (ele ganhou o `AincradAdminActionEvent`). Testes: `FloorRulesTest` (cadastro e erros, linha
de frente e os três modos).

## Ainda não testado em servidor

Tudo compila e a lógica dos modos e do cadastro tem teste, mas bloqueio, zona segura, Gates e a
troca de servidor ainda não rodaram num servidor Paper nem numa rede Velocity.
