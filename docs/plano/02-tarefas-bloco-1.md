# Tarefas de implementação — bloco 1 (core, rpg, economy, floors)

Esta aba quebra as 9 primeiras etapas do plano (0, A1 a A4 e 1 a 4) em 47 tarefas pequenas, cada uma com os arquivos a criar e um teste que diz quando está pronta. Os blocos seguintes são detalhados quando este estiver rodando.

**Regras para todas as tarefas**

- Pacote base `dev.aincrad.<plugin>` (ex.: `dev.aincrad.core`). API pública sempre em `...<plugin>.api`.
- Uma tarefa por commit, na ordem da tabela. A tarefa só fecha com `./gradlew build` passando e o teste da coluna "Pronto quando" conferido.
- Nada de banco, arquivo ou rede na thread principal: usar o executor do core e voltar à thread principal para mexer no mundo.
- Todo dado de jogador vale para a rede inteira: gravar no MySQL do core e avisar os outros servidores pelo `MessageBus` quando mudar algo que eles guardam em cache.
- Todo menu passa pelo construtor do core, para funcionar em baú, cursor e formulário do Bedrock.
- Toda ação de admin dispara `AincradAdminActionEvent`. Ajuste em relação ao plano: esse evento fica no `api` do **core**, não do Floors, porque o rpg e o economy vêm antes.

## Etapa 0 — aincrad-core

São 11 tarefas. Ao fim delas o core liga sozinho, e os outros plugins compilam contra ele pelo Maven local.

| ID | Tarefa | Arquivos e classes | Pronto quando |
| --- | --- | --- | --- |
| 0.1 | Projeto Gradle | `build.gradle.kts` (paper-api 1.21.11-R0.1-SNAPSHOT como compileOnly, Java 21, Shadow, run-paper, maven-publish), `plugin.yml` (api-version 1.21, softdepend Floodgate, Vault, PlaceholderAPI, LuckPerms), `AincradCore` | `./gradlew build` gera o .jar e `./gradlew runServer` liga sem erro |
| 0.2 | Config | `ConfigFile`, `ConfigLoader`: copia o padrão do .jar, acrescenta chaves novas sem apagar as editadas, valida tipos com erro legível no console | Teste: uma chave nova aparece no arquivo e um valor editado continua igual |
| 0.3 | Mensagens | `Messages`: `messages.yml` em MiniMessage, variáveis, prefixo; envio por chat, actionbar e título | Teste: `{jogador}` é trocado e a cor aparece |
| 0.4 | Comandos | `CommandRegistry` sobre a API de comandos do Paper (Brigadier): raiz `/aincrad`, subcomandos por plugin, ajuda automática, permissão por nó | `/aincrad` lista os subcomandos que o jogador pode usar; sem permissão o subcomando não aparece |
| 0.5 | Banco | `Database` (HikariCP; MySQL ou SQLite pela config), `Migrations` (arquivos `V1__nome.sql` por plugin e tabela `schema_version`), executor assíncrono com `CompletableFuture` | Teste em SQLite: a migração roda uma vez só; uma consulta não bloqueia a thread principal |
| 0.6 | MessageBus | Tabela `core_bus` (id, canal, payload JSON, servidor de origem, data); `publish` e `subscribe`; leitura a cada 1 s; limpeza do que tem mais de 1 h; `server-id` na config; implementação Redis opcional atrás da mesma interface | Dois servidores de teste: uma mensagem publicada no A chega ao B em até 2 s e não volta para o A |
| 0.7 | Regiões | `Region` (dois cantos, mundo, servidor), `RegionStore` (tabela `core_regions`), varinha, `RegionEnterEvent` e `RegionLeaveEvent` (checagem só quando o jogador muda de bloco, índice por chunk), `/aincrad varinha` e `/aincrad regiao criar, remover, listar` | Criar a região `teste` e atravessá-la dispara um evento de entrada e um de saída, uma vez cada |
| 0.8 | Menus | `Menu`, `MenuButton`, `MenuRenderer`; `ChestRenderer` (páginas e confirmação); `BedrockFormRenderer` com formulários do Floodgate, ativo só se o Floodgate estiver instalado | O mesmo menu de exemplo abre como baú no Java e como formulário no Bedrock, e o clique chama a mesma ação |
| 0.9 | Perfil do jogador | `PlayerProfile` chave-valor (tabela `core_profile`: uuid, plugin, chave, valor, versão); carrega no pré-login, salva ao sair e a cada 5 min; na troca de servidor, o destino espera o salvamento da origem (campo de versão) | Um valor gravado no servidor A é lido no B logo depois da troca |
| 0.10 | Utilitários | `Cooldowns`, `ItemBuilder`, `TimeFormat` (português), `Platform.isBedrock(Player)`, `Services` (atalho para o `ServicesManager`), `AincradAdminActionEvent` | Testes unitários de recarga e de formatação de tempo passam |
| 0.11 | Fechamento | Testes com MockBukkit, `publishToMavenLocal`, README com instalação, config do MySQL e a lista de permissões com grupos sugeridos do LuckPerms; plugin de exemplo em `example/` | O plugin de exemplo abre um menu e grava um valor no perfil usando só a API do core |

## Etapas A1 e A2 — aincrad-rpg

São 12 tarefas: A1 entrega level, XP e atributos; A2 entrega level dos mobs e raridade dos itens.

| ID | Tarefa | Arquivos e classes | Pronto quando |
| --- | --- | --- | --- |
| A1.1 | Projeto e dados | Projeto Gradle com `depend: AincradCore`; migração `rpg_players` (uuid, level, xp, pontos livres, força, agilidade, vitalidade); `RpgPlayer`, `RpgPlayerStore` com cache por jogador online | O plugin liga e cria a tabela; um jogador novo começa no level 1 |
| A1.2 | Curva e ganho de XP | `levels.yml` (fórmula `base * level ^ expoente` ou tabela, level máximo), `LevelCurve`, `LevelService.addXp` (sobe vários levels de uma vez se preciso), `XpGainEvent` (cancelável), `PlayerLevelUpEvent` | Teste: XP suficiente para 3 levels sobe os 3 e dispara 3 eventos |
| A1.3 | Barra de XP | A barra do Minecraft mostra level e progresso do RPG; XP vanilla desligado (orbes não contam, bigorna e encantamento usam custo fixo configurável) | Pegar um orbe não muda a barra; ganhar XP do RPG muda |
| A1.4 | Atributos | `AttributeService`: força, agilidade e vitalidade viram modificadores de dano, velocidade de ataque, velocidade de movimento e vida máxima, com chave própria para não acumular; pontos por level em `levels.yml`; menu `/status` pelo core | Gastar 1 ponto em vitalidade aumenta a vida máxima e continua valendo depois de relogar |
| A1.5 | Rede | Salvar ao sair e carregar ao entrar pelo perfil do core; level e atributos iguais nos dois servidores de teste | Subir de level no servidor A e trocar para o B mostra o mesmo level |
| A1.6 | Comandos | `/level [jogador]`, `/aincrad rpg xp dar ou tirar`, `/aincrad rpg level definir`, com `AincradAdminActionEvent` | Os comandos de admin mudam o level e aparecem como evento |
| A2.1 | Level dos mobs | `mob-levels.yml` (faixa de level por região do core ou por mundo), `MobLevelService`: define o level ao nascer, grava no mob, escala vida e dano, mostra "Lv. 5 Lobo" no nome | Um mob nascido na região de teste tem level dentro da faixa e a vida escalada |
| A2.2 | XP por mob | Ao morrer, o mob dá `xp_base * level`, com redução quando o jogador está muitos levels acima; interface `XpSplitter` para o Social dividir entre a party depois | Matar mob de level 5 dá o XP da fórmula; com 10 levels a mais, dá o valor reduzido |
| A2.3 | Raridade e itens | `Rarity` (comum, incomum, raro, épico, lendário), `items.yml` (id, material, faixas de bônus por raridade, level mínimo), `ItemFactory` grava os bônus no item e gera nome colorido e descrição | `/aincrad rpg item gerar espada_ferro raro` cria item com bônus dentro da faixa |
| A2.4 | Bônus ao equipar | `EquipmentListener`: aplica e remove os bônus ao equipar, trocar de mão e desequipar; item com level mínimo acima do jogador não dá bônus e avisa | Equipar a espada soma o dano; jogador abaixo do level não recebe o bônus |
| A2.5 | Morte | Penalidade configurável: perde uma parte do XP do level atual, nunca o level | Morrer com 50% do level e penalidade de 10% deixa 40% |
| A2.6 | API e fechamento | `RpgAPI` no `ServicesManager` (`getLevel`, `addXp`, `getAttribute`, `getMobLevel`, `createItem`), testes com MockBukkit, README | Um plugin de teste dá XP pela API e ouve o `PlayerLevelUpEvent` |

## Etapas A3 e A4 — aincrad-economy

São 10 tarefas: A3 entrega o Col, as lojas e a troca; A4 entrega o leilão e as casas.

| ID | Tarefa | Arquivos e classes | Pronto quando |
| --- | --- | --- | --- |
| A3.1 | Projeto e saldo | Projeto com `depend: AincradCore`, `softdepend: AincradRPG, Vault`; migrações `eco_balances` (uuid, saldo em inteiro) e `eco_transactions` (extrato: quem, valor, motivo, data); débito atômico no SQL (`UPDATE ... WHERE saldo >= valor`) | Dois débitos simultâneos maiores que o saldo: só um passa e o saldo nunca fica negativo |
| A3.2 | API e Vault | `EconomyAPI` (`getBalance`, `deposit`, `withdraw`, `transfer`, todos com motivo), `TransactionEvent`, provedor do Vault quando instalado | Um plugin de terceiros que usa Vault lê e altera o Col |
| A3.3 | Ganho por mob | `col-drops.yml` (Col por level do mob, variação), usando `RpgAPI.getMobLevel`; aviso na actionbar | Matar mob de level 5 credita o valor da config e grava no extrato |
| A3.4 | Lojas de NPC | `shops.yml` (itens, preço de compra e de venda), `ShopMenu` pelo core, vínculo a um bloco ou NPC com `/aincrad loja criar <id>` | Comprar desconta e entrega o item; com inventário cheio a compra é recusada sem cobrar |
| A3.5 | Pagamento e troca | `/col`, `/pagar <jogador> <valor>`, `/trocar <jogador>`: `TradeSession` em que os dois colocam itens e Col, os dois confirmam, e qualquer alteração desfaz as confirmações; a entrega acontece de uma vez só | Alterar a oferta depois da confirmação do outro cancela a confirmação; sair no meio devolve tudo |
| A3.6 | Admin | `/aincrad col dar, tirar, definir` com `AincradAdminActionEvent`; `/aincrad col extrato <jogador>` | O extrato mostra a ação do admin com autor e hora |
| A4.1 | Leilão | Migrações `eco_auctions` e `eco_mailbox`; `/leilao` abre o menu com busca e páginas; anunciar com preço e prazo; compra atômica válida em toda a rede; taxa configurável | Um item anunciado no servidor A é comprado no B; duas compras ao mesmo tempo: só uma leva |
| A4.2 | Caixa de retirada | Itens vendidos, expirados e Col de vendas ficam em `eco_mailbox`; aviso ao entrar; retirada pelo menu | Um anúncio expirado volta para a caixa do dono |
| A4.3 | Casas | Migração `eco_houses` (região do core, preço, dono, servidor); `/aincrad casa criar <preço>` sobre a seleção; compra por placa ou menu; proteção de blocos, portas e baús; convidados; `/casa`; limite de uma por jogador | Só o dono e os convidados abrem a porta e os baús; quem não é dono não quebra blocos |
| A4.4 | API e fechamento | `HouseAPI` (`getHouse`, `transferHouse`, `setHouseMembers`, para a guilda usar depois), testes com MockBukkit, README | Um plugin de teste transfere uma casa pela API |

## Etapas 1 a 4 — aincrad-floors

São 14 tarefas. Ao fim delas os andares bloqueiam, liberam, anunciam e teleportam em toda a rede.

| ID | Tarefa | Arquivos e classes | Pronto quando |
| --- | --- | --- | --- |
| 1.1 | Projeto | Projeto com `depend: AincradCore`, `softdepend: PlaceholderAPI, AincradSocial`; `AincradFloors`; `config.yml` com `modo: global` | O plugin liga depois do core |
| 1.2 | Cadastro de andares | `floors.yml`: número, nome, servidor da rede, tipo (mundo ou região do core), ponto de chegada, região `cidade`, local do Gate; `Floor`, `FloorRegistry` com validação (números sem buraco, região existente) | 3 andares de exemplo carregam; um erro no arquivo aparece no console com a linha |
| 1.3 | Listagem e reload | `/andares` (menu pelo core com liberados e bloqueados), `/aincrad floors reload` | O menu mostra o andar 1 liberado e os outros com cadeado |
| 2.1 | Estado na rede | Migrações `floor_state` (andar, liberado, data, participantes) e `player_floors` (jogador, andar, visitou); `FloorStateService` com cache e canal `floors.state` no `MessageBus` para atualizar os outros servidores | Liberar o andar 2 no servidor A aparece no B em até 2 s e continua após reiniciar os dois |
| 2.2 | Liberar e bloquear | `unlockFloor` e `lockFloor`, `FloorUnlockEvent` (cancelável), `/aincrad andar liberar, bloquear <n>` com `AincradAdminActionEvent` | Cancelar o evento num plugin de teste impede a liberação |
| 2.3 | Bloqueio de entrada | `EntryGuard`: cancela teleporte, troca de mundo e entrada em região de andar bloqueado, devolve o jogador ao ponto anterior com mensagem; `aincrad.floors.bypass` | Sem bypass não entra no andar 2 bloqueado por nenhum dos três caminhos; com bypass entra |
| 2.4 | Zona segura | `SafeZoneListener` na região `cidade`: cancela todo dano, PvP, fome, alvo e nascimento de mobs hostis, quebra e colocação de blocos; mob hostil que entra é removido; aviso ao entrar e sair; `isSafeZone(Location)` | Lava, queda, mob e outro jogador não tiram vida dentro da cidade; fora dela tiram |
| 3.1 | Anúncio global | `UnlockAnnouncer`: título, chat e som para todos os jogadores da rede pelo `MessageBus`; cita a guilda quando o Social estiver instalado; título desligável na config (para o Effects assumir) | Liberar no servidor A mostra o anúncio a um jogador no B |
| 3.2 | Teleport Gates | `/aincrad gate criar <n>` marca o bloco olhado; clique abre o menu de andares; teleporte local ou, se o andar está em outro servidor, grava o destino no perfil e troca de servidor pelo canal de plugin do Velocity; `GateTeleportEvent` | Do Gate do andar 1 no servidor A o jogador chega ao ponto de chegada do andar 2 no servidor B |
| 3.3 | Modos por jogador e misto | `PlayerEnterFloorEvent` grava a visita; `por_jogador`: só entra quem participou da vitória ou foi liberado; `misto`: andar aberto a todos, mas o Gate lista só os visitados a pé | Trocar o modo na config muda o que o Gate mostra, sem perder dados |
| 3.4 | Comandos de andar | `/andar <n>` (se a config permitir fora do Gate, com espera de 3 s), `/aincrad andar setspawn <n>` | `/andar 2` só funciona com o andar liberado |
| 4.1 | API e eventos | `FloorsAPI` no `ServicesManager`: `isUnlocked`, `getFrontline`, `getFloorAt`, `getFloorName`, `unlockFloor`, `isSafeZone`; eventos documentados | Um plugin de teste chama `unlockFloor` e ouve `FloorUnlockEvent` |
| 4.2 | Placeholders | `%aincrad_frontline%` e `%aincrad_floor_atual%` quando o PlaceholderAPI estiver instalado | Os dois placeholders retornam o valor certo num scoreboard de teste |
| 4.3 | Fechamento | Testes com MockBukkit (estado, três modos, bloqueio, zona segura), README com instalação, comandos, permissões e como criar um andar | Os critérios das etapas 1 a 4 do plano passam na rede de teste, com um jogador Java e um Bedrock |

## Próximos blocos

Cada bloco ganha uma aba como esta quando o anterior estiver rodando, porque as tarefas dependem do que o código anterior virou de verdade.

| Bloco | Etapas | Plugins |
| --- | --- | --- |
| 2 | 5 a 13 | aincrad-bosses e aincrad-effects |
| 3 | 14 a 17 | aincrad-ui (fim da versão jogável) |
| 4 | 18 a 30 | Extras do bosses, items, life, reputation, admin |
| 5 | 31 a 38 | social (com a guilda), skills, quests |
