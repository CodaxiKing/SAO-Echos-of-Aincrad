# AincradCore

Plugin sem funcionalidade de jogo: as peças comuns dos outros plugins Aincrad (etapa 0 do plano).
Vai em `plugins/` como os demais, liga primeiro (`load: STARTUP`) e todos declaram
`depend: [AincradCore]`.

## Instalação

1. `./gradlew build` → `build/libs/AincradCore-<versão>.jar` (e `exemplo/build/libs/AincradExemplo-<versão>.jar`, só para teste).
2. Primeira vez: o Paper baixa HikariCP, o driver MySQL e o SQLite do Maven Central (`libraries:` no
   `plugin.yml`), então o servidor precisa de internet nesse momento.
3. `config.yml`: `servidor` (nome único na rede) e `banco` (`mysql` na rede; `sqlite` para teste local).

## O que oferece (`AincradCore.get()`)

| Peça | Uso |
|---|---|
| Config | `core.config(plugin, "x.yml")`: padrões do .jar e chaves novas acrescentadas sozinhas, sem mexer no que o dono editou; leitura com limites (`integer`, `decimal`, `enumValue`) |
| Mensagens | `core.messages(plugin)`: messages.yml em MiniMessage com prefixo comum; `send` (chat), `actionBar`, `title`, `Messages.vars("andar", 5)` |
| Comandos | `core.commands().register(plugin, subcomando)` pendura em `/aincrad` (Brigadier do Paper, com autocompletar e permissão) |
| Banco | `core.database()`: MySQL ou SQLite com pool HikariCP; `async(...)`, `update`, `query`, `upsert(...)` e `migrate(dono, lista)` com migrações numeradas; `{AUTO_ID}` vira a coluna de id do banco em uso |
| Regiões | `core.regions()`: varinha, regiões nomeadas no banco (compartilhadas pela rede), `at(local)`, `isIn(jogador, nome)`, eventos `RegionEnterEvent` e `RegionLeaveEvent` |
| Menus | `core.menus().open(jogador, Menu.of(...))`: o plugin descreve o menu; um `MenuRenderer` desenha (baú no core; o AincradUI pode registrar o de cursor, um plugin de Bedrock o de formulário). `confirm(...)` para sim/não |
| Perfil | `core.profile(jogador).set(plugin, "andar", 3)`: chave-valor por jogador e por plugin, carregado antes de entrar, gravado ao sair e a cada `perfil.salvar_segundos`; `profiles().load(uuid)` para offline |
| MessageBus | `core.publish("floors:unlock", "5")` e `core.subscribe(...)`: avisos entre servidores pela tabela `aincrad_bus`, lida a cada segundo |
| Utilitários | `ItemBuilder`, `TimeFormat` ("2 h 5 min", "2h30m" → ms), `Cooldowns`, `core.isBedrock(jogador)` (Floodgate), `provide`/`service` (APIs entre plugins), `AincradReloadEvent` |

## Comandos

| Comando | O que faz | Permissão |
|---|---|---|
| `/aincrad` | Ajuda com os subcomandos de todos os plugins | — |
| `/aincrad varinha` | Varinha: esquerdo marca o canto 1, direito o canto 2 | `aincrad.admin` |
| `/aincrad regiao criar\|remover\|listar\|info [nome]` | Regiões nomeadas | `aincrad.admin` |
| `/aincrad reload` | Recarrega o core e os plugins que escutam o `AincradReloadEvent` | `aincrad.admin` |

## Permissões de todos os plugins (LuckPerms)

| Nó | Plugin | Grupo sugerido |
|---|---|---|
| `aincrad.admin` | todos (administração) | admin |
| `aincrad.ui.menu` | AincradUI (abrir o menu) | jogador |
| `aincrad.effects.play` | AincradEffects (tocar efeitos) | moderador |
| `aincrad.effects.pack` | AincradEffects (pedir o pack) | jogador |
| `aincrad.social.use` | AincradSocial (amigos, party, guilda, mensagens) | jogador |

Os plugins só checam nós de permissão; não há listas de admins em config. Os próximos plugins
acrescentam seus nós aqui.

## Plugin de exemplo (`exemplo/`)

`/aincrad exemplo` abre um menu cujo botão soma 1 num contador guardado no perfil (continua
depois de sair e entrar); entrar e sair de regiões mostra uma mensagem na actionbar. É o
critério de pronto da etapa 0 e o modelo para os próximos plugins.

## Para outros plugins compilarem contra o core

```bash
./gradlew publishToMavenLocal
```

Depois, no `build.gradle.kts` do outro plugin: `repositories { mavenLocal() }` e
`compileOnly("com.aincrad:aincrad-core:0.1.0")` (ou build composto com `includeBuild("../aincrad-core")`).

## Decisões tomadas (diferente do plano)

- HikariCP e os drivers vêm pelo carregador de bibliotecas do Paper (`libraries:`), não embutidos
  com Shadow: sem risco de incompatibilidade do plugin Shadow com o Gradle 9 e sem conflito de versões.
- Testes com JUnit e SQLite de verdade, não MockBukkit (ainda não confirmado para o Paper 1.21.11).
- Sem o run-paper por enquanto (servidor de teste); entra quando for testar no jogo.
- Redis opcional do MessageBus fica para depois; a tabela no banco já funciona na rede.
- Os plugins já feitos (UI, Effects, Social) ainda não usam o core; migram para ele aos poucos
  (dados do Social e da guilda vão para o MySQL nessa migração).

## Desenvolvimento

```bash
./gradlew build
```

Testes: regiões e índice (inclusive coordenadas negativas e regiões enormes), tempo em português,
recargas, junção de config, migrações (ordem, erro sem marcar), upsert e MessageBus entre dois servidores.
