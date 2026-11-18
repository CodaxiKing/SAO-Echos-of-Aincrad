# Plano de desenvolvimento — Plugins Aincrad (Andares + Chefes)

Nov 17, 2026 · @Diego

## Como usar este plano

O Fable 5.2 deve construir treze plugins Paper em treze pastas separadas: **aincrad-floors** (sistema de andares) e **aincrad-bosses** (chefes e dungeons). O de chefes depende do de andares e libera o próximo andar quando um chefe morre.

Antes de todos vem a lib compartilhada **aincrad-core** (etapa 0): config, mensagens, comandos, banco de dados, regiões, menus e perfil do jogador num lugar só. Todos os outros plugins dependem dela e não repetem esse código.

O terceiro, **aincrad-effects**, é o motor de efeitos visuais do **servidor inteiro**: qualquer plugin, comando, NPC ou evento pode tocar um efeito. A placa do chefe, o "Congratulations!" e os outros efeitos do Aincrad são só os primeiros usos. Ele funciona sozinho e liga as integrações com o Floors e o Bosses quando eles estão instalados.

O quarto, **aincrad-ui**, é a interface no estilo SAO: menu de ícones em anel, HUD de vida, aviso de vida crítica e tela de morte. Ele usa as artes do mod SAO MC UI (A-G Projects), com autorização do autor, entregues no pacote `aincrad-ui-assets.zip`.

Os outros quatro formam a segunda leva: **aincrad-items** (cristais), **aincrad-life** (estalagens, profissões, familiares e casamento), **aincrad-reputation** (títulos, conquistas, player killers e prisão) e **aincrad-admin** (Discord, ranking em site, registro e backup). O aincrad-bosses também ganha extras: proteção contra abuso, guia de estratégia, quadro de raid e editor de chefes dentro do jogo.

Mais cinco fecham o conjunto: **aincrad-rpg** (level, XP, atributos, raridade de itens) e **aincrad-economy** (Col, lojas, leilão, casas), feitos logo depois do core porque os outros usam level, XP e Col; e a terceira leva, **aincrad-social** (party, guilda, amigos, duelos), **aincrad-skills** (Sword Skills) e **aincrad-quests** (missões).

Instrução para colar no Fable 5.2:

> Leia este plano inteiro antes de escrever código. Crie uma pasta por projeto (`aincrad-core/`, `aincrad-rpg/`, `aincrad-economy/`, `aincrad-floors/`, `aincrad-bosses/`, `aincrad-effects/`, `aincrad-ui/`, `aincrad-items/`, `aincrad-life/`, `aincrad-reputation/`, `aincrad-admin/`, `aincrad-social/`, `aincrad-skills/`, `aincrad-quests/`), cada uma com seu próprio projeto Gradle. Siga as etapas na ordem da seção "Etapas" (0, A1 a A4, 1 a 30, 31 a 38) e só avance quando o critério de pronto da etapa estiver cumprido. Use o aincrad-core para config, mensagens, comandos, banco, regiões, menus e sincronização entre servidores; não reescreva isso em cada plugin. O servidor é uma rede Velocity com jogadores de Bedrock (Geyser): leia "Decisões do servidor" antes de começar. Todas as mensagens para o jogador ficam em português, num arquivo `messages.yml`. Quando algo não estiver definido aqui, escolha o mais simples, deixe configurável e anote a escolha no README da pasta.

Ordem de trabalho: primeiro o aincrad-core (etapa 0), depois o aincrad-rpg e o aincrad-economy (A1 a A4), depois o aincrad-floors inteiro (ele expõe a API), depois o aincrad-bosses usando essa API, em seguida o aincrad-effects e o aincrad-ui. Só depois dessas 17 etapas prontas e testadas começa a segunda leva: extras do bosses, aincrad-items, aincrad-life, aincrad-reputation e aincrad-admin.

## Visão geral e decisões

A progressão padrão é **global, como no anime**: o chefe morreu uma vez e o próximo andar abre para o servidor inteiro. Os modos "por jogador" e "misto" ficam disponíveis na config.

| Decisão | Escolha |
| --- | --- |
| Plataforma | Paper (API do Paper, não só Spigot), versão 1.21.11 (paper-api 1.21.11-R0.1-SNAPSHOT) |
| Linguagem e build | Java 21; Gradle com Kotlin DSL; `./gradlew build` gera o .jar |
| Dependências | Paper e o AincradCore (todos os plugins declaram depend: AincradCore). Libs externas ficam só dentro do core. PlaceholderAPI como opcional (softdepend) |
| Modo de progressão | `global` (padrão), `por_jogador` ou `misto` em `config.yml` |
| Andar no mapa | Cada andar é um mundo **ou** uma região (dois cantos) dentro de um mundo, configurável por andar |
| Armazenamento | Definições em YAML; estado em MySQL compartilhado pela rede, pelo banco do core. Onde o plano disser SQLite, leia "banco do core": MySQL na rede, SQLite só em teste local |
| Mensagens | `messages.yml` em português, com cores no formato MiniMessage |
| Modelos 3D | Fora do escopo. Chefes usam mobs vanilla com atributos e equipamento. O resource pack entra só para as imagens e fontes do aincrad-effects |

**Decisões do servidor**

| Tema | Decisão | O que muda no código |
| --- | --- | --- |
| Rede | Vários servidores Paper atrás de um proxy Velocity | Todo estado (andares, perfis, Col, guildas, reputação) fica em MySQL. O core tem um `MessageBus` para avisar os outros servidores (andar liberado, anúncios, convites de party): tabela de eventos no MySQL lida a cada segundo, com Redis opcional. Nenhum plugin guarda em memória algo que outro servidor precise saber sem recarregar pelo bus. Teleporte entre andares em servidores diferentes usa a troca de servidor do Velocity |
| Celular | Jogadores de Bedrock entram por Geyser + Floodgate | O core oferece `isBedrock(Player)`. Para Bedrock: menus viram formulários nativos do Floodgate (terceiro `MenuRenderer`), imagens do pack viram título e texto, números de dano e placas usam nome flutuante simples, e o gesto de abrir menu vira um item na hotbar. Toda função precisa ter caminho que funcione sem resource pack |
| Cidades | Zona segura com proteção total | Dentro da região da cidade de cada andar: nenhum dano de nenhuma fonte, sem PvP, sem fome, mobs hostis não entram nem nascem, e blocos não quebram. Implementado no Floors |
| Permissões | LuckPerms | Todos os plugins só checam nós de permissão (`aincrad.*`); nada de listas de admins em config. O README do core traz os nós de todos os plugins e grupos sugeridos (jogador, vip, moderador, admin). Títulos do Reputation são aplicados como prefixo pelo LuckPerms |

Estrutura das pastas:

```
aincrad-floors/
  build.gradle.kts
  src/main/java/.../floors/   (core, api, gates, commands, storage)
  src/main/resources/plugin.yml, config.yml, floors.yml, messages.yml
  README.md
aincrad-bosses/
  build.gradle.kts
  src/main/java/.../bosses/   (core, api, phases, abilities, arena, rewards, labyrinth, commands)
  src/main/resources/plugin.yml (depend: AincradFloors), config.yml, bosses/andar1.yml, messages.yml
  README.md
aincrad-effects/
  build.gradle.kts
  src/main/java/.../effects/  (engine, steps, api, triggers, pack, commands, integrations/aincrad)
  src/main/resources/plugin.yml (softdepend: AincradFloors, AincradBosses), config.yml, effects/*.yml, triggers.yml, images.yml, messages.yml
  resourcepack/               (PNGs, fontes e pack.mcmeta; o plugin gera o .zip)
  README.md
aincrad-ui/
  build.gradle.kts
  src/main/java/.../ui/       (api, menu, render/cursor, render/chest, hud, death, screens, commands)
  src/main/resources/plugin.yml (depend: AincradEffects), ui.yml, menu.yml, messages.yml
  resourcepack/               (texturas de pack_otimizado/ e os sons do aincrad-ui-assets.zip)
  CREDITOS.md, README.md
```

Segunda leva (mesmo padrão: `build.gradle.kts`, `plugin.yml`, `config.yml`, `messages.yml`, README):

```
aincrad-items/        (crystals, api, commands)          depend: AincradFloors; softdepend: AincradBosses, AincradEffects
aincrad-life/         (inns, professions, pets, marriage) softdepend: AincradFloors, AincradEffects, AincradUI
aincrad-reputation/   (titles, achievements, pk, prison)  softdepend: AincradFloors, AincradBosses, AincradEffects, AincradUI
aincrad-admin/        (discord, web, audit, backup)       softdepend: todos os outros
```

Lib compartilhada:

```
aincrad-core/
  build.gradle.kts            (publica no Maven local para os outros compilarem contra ela)
  src/main/java/.../core/     (config, messages, commands, storage, regions, menus, profile, util)
  src/main/resources/plugin.yml, config.yml, messages.yml
  README.md
```

## Lib compartilhada — AincradCore (pasta aincrad-core)

O AincradCore é um plugin sem funcionalidade de jogo: ele oferece as peças comuns para os outros treze. Vai na pasta `plugins/` como os demais e é carregado primeiro.

1. **Config**: carregamento de YAML com valores padrão, validação e atualização automática quando surge uma chave nova, sem apagar o que o dono editou.
2. **Mensagens**: `messages.yml` por plugin, em MiniMessage, com variáveis e prefixo comum. Um método único para enviar chat, actionbar e título.
3. **Comandos**: registro de subcomandos sob `/aincrad` com autocompletar e permissões, usando a API de comandos do Paper (Brigadier). Cada plugin acrescenta os seus.
4. **Banco de dados**: MySQL compartilhado pela rede como padrão e SQLite só para teste local, com pool (HikariCP), migrações numeradas por plugin e execução fora da thread principal.
5. **Regiões**: varinha de seleção (dois cantos), regiões nomeadas salvas, consulta "em que região está este ponto" e eventos `RegionEnterEvent` e `RegionLeaveEvent`. Usado para andares em região, salas de chefe, labirinto, cidades, prisão e gatilhos de efeito.
6. **Menus**: construtor de menus de inventário (páginas, botões, confirmação) e a interface `MenuRenderer`. O renderizador padrão é o baú; o AincradUI registra o renderizador de cursor. Os plugins pedem "abrir menu X" sem saber como ele é desenhado.
7. **Perfil do jogador**: armazenamento chave-valor por jogador e por plugin (level, andar atual, preferências), carregado ao entrar e salvo ao sair.
8. **Utilitários**: agendador (tarefas com tempo e recarga), construtor de itens, textos de tempo em português e registro de serviços entre plugins, MessageBus entre os servidores da rede e isBedrock(Player) via Floodgate.

Libs externas, todas embutidas no core com Shadow e realocadas: HikariCP e o driver MySQL. Para desenvolvimento: MockBukkit (testes) e o plugin Gradle run-paper (servidor de teste com `./gradlew runServer`). Antes de adotar cada uma, conferir se a versão atual é compatível com o Paper 1.21.11; se não for, usar só a API do Paper e anotar no README.

Comandos: `/aincrad` (ajuda geral), `/aincrad varinha`, `/aincrad regiao criar|remover|listar` e `/aincrad reload` (aincrad.admin).

## Plugin 1 — AincradFloors (pasta aincrad-floors)

O AincradFloors guarda quais andares estão liberados, impede a entrada nos bloqueados, anuncia quando um andar abre e cuida dos Teleport Gates.

**Funções**

1. **Cadastro de andares** em `floors.yml`: número, nome (ex.: "Andar 1 — Cidade do Início"), mundo ou região, ponto de chegada e local do Teleport Gate. O andar 1 começa liberado.
2. **Bloqueio de entrada**: teleporte, portal ou caminhada para um andar bloqueado é cancelado, com mensagem. Admins com permissão passam.
3. **Liberação**: liberar o andar N grava data, hora e a lista de jogadores que participaram, e dispara o `FloorUnlockEvent`.
4. **Anúncio global**: título na tela de todos, mensagem no chat ("O andar 5 foi conquistado por …") e um som. Tudo editável em `messages.yml`.
5. **Teleport Gates**: um bloco ou NPC marcado em cada cidade principal. Ao clicar, abre um menu (inventário) com os andares liberados; os bloqueados aparecem como cadeado. No modo `misto`, só aparecem os andares que o jogador já visitou a pé.
6. **Front line**: guardar o maior andar liberado, para mostrar no `/andares` e em placeholders.

**Zona segura (função 7)**: cada andar tem a região `cidade` em `floors.yml`. Dentro dela a proteção é total: nenhum dano de nenhuma fonte, sem PvP, sem fome, mobs hostis não entram nem nascem e blocos não quebram. Aviso na tela ao entrar e ao sair ("Zona segura" e "Fora da zona segura").

**Comandos e permissões**

| Comando | O que faz | Permissão |
| --- | --- | --- |
| /andares | Lista os andares, liberados e bloqueados | aincrad.floors.list (todos) |
| /andar \<n> | Teleporta para um andar liberado (se a config permitir fora do Gate) | aincrad.floors.tp |
| /aincrad andar liberar \<n> | Libera um andar à mão | aincrad.admin |
| /aincrad andar bloquear \<n> | Volta a bloquear (para testes e reset) | aincrad.admin |
| /aincrad andar setspawn \<n> | Define o ponto de chegada na posição atual | aincrad.admin |
| /aincrad gate criar \<n> | Marca o bloco olhado como Teleport Gate do andar | aincrad.admin |
| /aincrad reload | Recarrega as configs | aincrad.admin |
| (permissão) | Entra em andar bloqueado | aincrad.floors.bypass |

**Dados**: tabela `floor_state` (andar, liberado, data, jogadores) e `player_floors` (jogador, andar, visitou) para os modos `por_jogador` e `misto`.

**Placeholders (se PlaceholderAPI estiver instalado)**: `%aincrad_frontline%` e `%aincrad_floor_atual%`.

## Plugin 2 — AincradBosses (pasta aincrad-bosses)

O AincradBosses cria os chefes de cada andar, tranca a sala durante a luta, dá o bônus de Last Attack e, quando o chefe morre, chama o AincradFloors para liberar o próximo andar.

**Funções**

1. **Definição de chefe** em `bosses/<arquivo>.yml`: andar, nome, level, mob base (ex.: zumbi, wither skeleton), vida, dano, armadura, velocidade, equipamento, resistência a empurrão e lista de fases.
2. **Fases por vida**: cada fase começa num limite de vida (ex.: 100%, 50%, 25%) e pode trocar equipamento, mudar atributos, falar uma mensagem e ativar habilidades. Exemplo obrigatório no `andar1.yml`: o Illfang troca o machado e o escudo por uma espada a 25% da vida e fica mais rápido.
3. **Habilidades** com tempo de recarga, escolhidas por tipo: investida, ataque em área com aviso de partículas no chão, chamar lacaios, projétil, salto com impacto e fúria (mais dano por alguns segundos).
4. **Sala do chefe**: região com dois cantos, porta (lista de blocos) e ponto de entrada. A luta começa quando alguém pisa na sala ou ativa um bloco, com uma introdução opcional (intro\_segundos) em que o chefe fica parado e invulnerável enquanto a barra de vida enche. A porta fecha (troca os blocos por barreira ou grade), ninguém entra, e quem sai não volta.
5. **Fim da luta**: vitória quando o chefe morre; derrota quando todos na sala morrem ou o tempo limite acaba (o chefe volta à vida cheia). Nos dois casos a porta abre.
6. **Barra de vida** (BossBar) com nome e fase, só para quem está na sala.
7. **Registro de dano** por jogador durante a luta.
8. **Recompensas**: Last Attack é um item raro, configurado por chefe, para quem deu o golpe final. Os demais itens são divididos entre os participantes com dano mínimo configurável (ex.: 5% da vida do chefe).
9. **Liberação do andar**: na primeira vitória, chama `AincradFloorsAPI.unlockFloor(andar + 1, participantes)`. Depois disso o chefe pode voltar a cada X horas, só como desafio, sem liberar nada.
10. **Labirinto**: zonas de spawn na região do labirinto de cada andar, com níveis de mob. Quanto mais perto da sala do chefe, mais forte o mob. Cada zona tem limite de mobs e intervalo.

**Comandos e permissões**

| Comando | O que faz | Permissão |
| --- | --- | --- |
| /aincrad chefe sala \<id> canto1/canto2 | Marca os cantos da sala na posição atual | aincrad.admin |
| /aincrad chefe porta \<id> | Marca os blocos da porta (selecionados com um item varinha) | aincrad.admin |
| /aincrad chefe invocar \<id> | Invoca o chefe para teste | aincrad.admin |
| /aincrad chefe resetar \<id> | Encerra a luta e abre a porta | aincrad.admin |
| /aincrad labirinto zona \<andar> \<nivel> | Cria uma zona de spawn com a seleção atual | aincrad.admin |
| /aincrad bosses reload | Recarrega configs e chefes | aincrad.admin |

**Extras do AincradBosses (segunda leva, etapas 18 e 19)**

1. **Proteção contra abuso**: quem desloga durante a luta conta como morto e não volta para a sala. Jogador sem dano, cura ou movimento por 60 s (configurável) não recebe recompensa nem conta como participante.
2. **Guia de estratégia**: `/chefe guia <id>` abre um livro do chefe. Começa em branco. Cada fase e cada habilidade aparece no livro depois de vista em alguma tentativa do servidor, com a vida em que começou. Registra também o número de tentativas e a fase mais longe alcançada.
3. **Quadro de raid**: um jogador cria a raid de um chefe com data e hora e vagas por função (tanque, dano, suporte). Outros se inscrevem por menu ou `/raid`. Aviso 15 min antes e na hora. Só inscritos entram na sala enquanto a raid estiver marcada (configurável).
4. **Editor de chefes no jogo**: `/aincrad chefe editar <id>` abre um menu para mudar vida, dano, level, fases, habilidades e drops (arrastando o item para o menu). Salva no YAML do chefe e vale na próxima luta.

## Plugin 3 — AincradEffects (pasta aincrad-effects), motor de efeitos do servidor

O AincradEffects é um plugin independente que toca efeitos visuais e sonoros no servidor inteiro. Ele só desenha: não dá itens, não libera andares e não mexe em lutas. Funciona sem o Floors e sem o Bosses; quando eles estão instalados, a integração com o Aincrad liga sozinha.

**Motor de efeitos (uso geral)**

1. **Efeitos em YAML** (`effects/*.yml`): cada efeito é uma sequência de passos com tempo. Exemplo: em 0 s imagem no centro da tela e som, em 0,5 s partículas, em 2 s abre um menu.
2. **Tipos de passo**: título e subtítulo, actionbar, imagem do pack na tela, Text Display no mundo (com fade, escala e movimento), partículas em formas (círculo, coluna, espiral, explosão, linha), som, fogos, efeito de poção curto, BossBar temporária e menu com fundo personalizado.
3. **Variáveis nos textos**, preenchidas por quem chama: {jogador}, {nome}, {level}, {andar}, {item} e outras livres. PlaceholderAPI funciona também, se instalado.
4. **Quem vê**: só o jogador, jogadores num raio, o mundo ou o servidor todo.
5. **API para outros plugins**: `play(String efeito, Player alvo, Map<String,String> variaveis)`, `play(String efeito, Location local, double raio, Map<String,String> variaveis)` e `stop(...)`. Registrada no `ServicesManager`.
6. **Comando para console, command blocks, NPCs e quests**: `/efeito tocar <efeito> [jogador | @a | raio:<n>] [variavel=valor ...]`.
7. **Gatilhos sem código** (`triggers.yml`): entrar no servidor, entrar ou sair de uma região, matar um tipo de mob, subir de nível, pegar um item, morrer. Cada gatilho toca um efeito.
8. **Limites de desempenho**: máximo de entidades e partículas por jogador e por tick, e distância máxima de visão, todos na config.
9. **Resource pack único do servidor**: todas as imagens de todos os efeitos ficam no mesmo pack, gerado e enviado pelo plugin.

**Integração com o Aincrad** (liga sozinha quando o Floors e o Bosses estão instalados; cada item abaixo é um efeito comum do motor, editável em YAML)

1. **Resource pack**: a pasta `resourcepack/` guarda os PNGs (placas, "Congratulations!", fundo da janela de resultado, "Floor Cleared") e as fontes que transformam cada imagem num caractere. O plugin gera o .zip, calcula o hash e envia ao jogador quando ele entra (ou usa uma URL definida na config). Quem recusar o pack vê a versão simples: título e texto, sem imagem.
2. **Placa do chefe na porta**: uma Text Display na frente da porta mostra a imagem da placa e, embaixo, o nome e o level do chefe lidos do YAML ("Illfang the Kobold Lord · Lv. 10"). Aparece quando o jogador chega a 8 blocos (configurável), com fade e aumento de tamanho, só para quem chegou perto. Some quando a luta começa. Depois da primeira vitória, mostra "Derrotado por …". Opção `estilo: mapa` usa imagem em quadros (map art) no lugar da Text Display.
3. **Entrada do chefe**: durante a introdução, o nome do chefe aparece grande na tela de quem está na sala, com som.
4. **Números de dano**: cada golpe no chefe solta um número que sobe e some em 1 segundo. Golpe crítico em outra cor. Limite de números na tela para não pesar.
5. **Troca de fase**: aviso "Fase 2" na tela, som e um efeito curto na visão (configurável ou desligado).
6. **Congratulations!**: quando o chefe morre, cada participante vê a imagem "Congratulations!" no centro da tela, com som e fogos. Depois de 2 segundos abre a **janela de resultado** (menu com fundo próprio do pack) com XP, Col (só quando existir a economia), os itens recebidos e o **Last Attack em destaque** para quem deu o golpe final. A janela só mostra: os itens já foram entregues pelo AincradBosses.
7. **Chegada no andar**: título "Andar 2 — Urbus" quando o jogador chega a um andar.
8. **Teleport Gate**: partículas e luz azul no jogador na hora do teleporte.
9. **Floor Cleared**: quando um andar é liberado, todos veem a imagem "Floor Cleared" e o número do andar. Com o aincrad-effects ligado, o título simples do Floors é desligado na config dele, e a mensagem no chat continua.

**Comandos e permissões**

| Comando | O que faz | Permissão |
| --- | --- | --- |
| /efeito tocar \<efeito> \[alvo\] \[variaveis\] | Toca um efeito em alguém, num raio ou em todos | aincrad.effects.play |
| /efeito lista | Lista os efeitos carregados | aincrad.effects.play |
| /efeito parar \[jogador\] | Para os efeitos em andamento | aincrad.effects.play |
| /efeito pack | Reenvia o resource pack para você | aincrad.effects.pack (todos) |
| /efeito reload | Recarrega efeitos, gatilhos, imagens e regera o pack | aincrad.admin |

**Imagens que o usuário vai desenhar** (lista em `images.yml`, com tamanho em pixels): placa da porta, "Congratulations!", fundo da janela de resultado, "Floor Cleared" e o brasão de cada andar. O plugin precisa vir com imagens provisórias simples para tudo funcionar antes das artes finais.

## Plugin 4 — AincradUI (pasta aincrad-ui), interface estilo SAO

O AincradUI recria no servidor a interface do anime: menu de ícones em anel, HUD de vida, aviso de vida crítica e tela de morte. Ele não é uma conversão do mod SAO MC UI (mod desenha direto no cliente; plugin não pode). Usa só as **artes e os sons** do mod, com autorização do autor, e refaz o comportamento com resource pack, fontes e Text Displays.

**Artes (pacote `aincrad-ui-assets.zip`)**

- `pack_otimizado/textures/`: ícones e botões em 128x128, janela em 512 px, "You are dead" em 1024 px. Usar estes no pack. Cada ícone tem a versão normal e a `_on` (cursor em cima).
- `originais/`: os arquivos como vieram do mod (2000x2000), só para referência ou para gerar outros tamanhos.
- Grupos: `icons/` (menu principal: perfil, party, mensagens, mapa, config), `under_icons/` (submenu: itens, equipamento, skills, amigos, guilda, quests, mapa, opções, ajuda, logout, chamada), `buttons/` (sim, não, mais, menos, lista, detalhes, iniciar, cancelar), `skills/` (7 skills), `hotbar/`, `window_blank.png`, `person.png` e `effects/you_are_dead.png`.
- Sons: abrir menu, confirmar, voltar, fechar, vida crítica e morte. Registrar no namespace `aincrad` (ex.: `aincrad:ui.open`).
- O modelo 3D da barra de vida (`health_bar.glb`, de Sockona, CC BY 4.0) fica para depois: precisa ser convertido para modelo do Minecraft.
- `CREDITOS.md` vai junto no README e no comando `/creditos`.

**Funções**

1. **Pack único**: o AincradUI não gera pack próprio. Ele registra a pasta `resourcepack/` no AincradEffects (`registerPackFolder`), que junta tudo num pack só do servidor.
2. **Abrir o menu**: agachar + clique esquerdo com a mão vazia (gesto configurável) ou `/menu`. Toca o som de abrir.
3. **Menu principal**: coluna vertical com os 5 ícones em anel. Ao passar o cursor, troca para a versão `_on` e toca o som de mover. Clicar abre o submenu ao lado com os ícones daquele grupo.
4. **Dois modos de desenho**: modo **cursor** (o menu flutua na frente do jogador em Text Displays, a câmera trava e o cursor segue o movimento do mouse) e modo **baú** (menu de inventário com fundo e ícones do pack), usado como reserva e para quem preferir. Escolha do jogador em Opções; padrão na config.
5. **Janelas**: perfil (com `person.png` e o equipamento), detalhes de item e confirmação sim/não, todas sobre `window_blank.png`.
6. **HUD de vida**: barra no canto superior esquerdo no estilo SAO, com nome e level, desenhada com fonte do pack. Verde, amarela abaixo de 50% e vermelha abaixo de 25%.
7. **Vida crítica**: abaixo de 25% a barra pisca e toca o som de alerta (com intervalo, para não repetir sem parar).
8. **Morte**: estilhaços azuis (efeito do AincradEffects), imagem "You are dead" na tela e o som de morte. Mobs também se desfazem em estilhaços, configurável.
9. **Entradas para plugins futuros**: party, guilda, amigos, quests, mapa e skills aparecem com cadeado ("em breve") até algum plugin registrar a ação pela API `registerMenuEntry(id, icone, acao)`. Itens e equipamento já funcionam com o inventário do jogo.
10. **Logout**: pede confirmação e desconecta. Opção `modo_death_game` troca por uma mensagem dizendo que o botão de logout sumiu, como no anime.

**Comandos e permissões**

| Comando | O que faz | Permissão |
| --- | --- | --- |
| /menu | Abre o menu principal | aincrad.ui.menu (todos) |
| /creditos | Mostra os créditos das artes | todos |
| /aincrad ui teste \<tela> | Abre uma tela ou HUD para conferir (menu, perfil, confirmar, morte, critico) | aincrad.admin |
| /aincrad ui reload | Recarrega `ui.yml` e `menu.yml` | aincrad.admin |

## Plugin 5 — AincradItems (pasta aincrad-items), cristais

O AincradItems adiciona os cristais consumíveis do anime. Cada cristal é um item com modelo próprio no pack único, definido em `crystals.yml` (nome, descrição, recarga, se é consumido).

1. **Cristal de Cura**: recupera toda a vida na hora. Recarga compartilhada entre cristais de cura.
2. **Cristal de Antídoto**: remove veneno, wither e lentidão.
3. **Cristal de Teleporte**: abre a lista de andares liberados e leva à cidade do andar escolhido depois de 3 s parado. Cancela se levar dano.
4. **Cristal de Ressurreição**: ao morrer, o jogador fica 10 s "se desfazendo" (configurável). Nesse tempo, outro jogador pode usar o cristal nele e cancelar a morte. Item raríssimo, só por drop configurado.
5. **Cristal de Gravação**: guarda uma mensagem de texto de até 256 caracteres com autor e data. Quem clicar com ele lê a mensagem. Pode ser entregue a outro jogador.
6. **Áreas anti-cristal**: o Bosses marca salas com `anti_cristal: true`. Dentro delas nenhum cristal funciona e o jogador recebe um aviso.

Comandos: `/aincrad cristal dar <jogador> <tipo> [qtd]` e `/aincrad items reload` (aincrad.admin). API: `giveCrystal(Player, tipo, qtd)` e `CrystalUseEvent` (cancelável).

## Plugin 6 — AincradLife (pasta aincrad-life), vida no Aincrad

O AincradLife cuida do que o jogador faz fora das lutas: estalagens, profissões, familiares e casamento. Cada módulo liga e desliga na config.

Regras completas e tarefas: AincradLife — detalhes. Onde esta seção e a aba divergirem, vale a aba.

1. **Estalagens**: um bloco ou NPC marcado como estalagem em cada cidade. Descansar custa um valor configurável (item, ou Col quando existir economia), dá o bônus "Descansado" (ex.: +10% de vida por 30 min) e define o ponto de retorno ao morrer.
2. **Culinária**: receitas em `recipes.yml` feitas numa bancada de cozinha. A comida dá buffs temporários (força, regeneração, velocidade). O nível de culinária sobe com o uso e libera receitas melhores.
3. **Pesca**: tabela de peixes por andar em `fishing.yml`, com peixes comuns, raros e um peixe lendário por andar. Nível de pesca aumenta a chance de raro.
4. **Ferreiro**: reparo de durabilidade numa bigorna especial, com custo em materiais. Nível de ferreiro reduz o custo. Item com durabilidade zerada quebra de vez (configurável).
5. **Familiares**: alguns mobs pequenos (lista em `pets.yml`) podem ser domados com a comida certa, com chance baixa. O familiar segue o dono, tem nome e level e dá um bônus leve (ex.: cura pequena a cada 30 s). Se morrer, deixa um item que permite revivê-lo em até 3 dias. Um familiar por jogador.
6. **Casamento**: `/casar <jogador>` envia o pedido; aceito, os dois ganham um baú compartilhado acessível pelo menu, veem a vida um do outro e podem se teleportar um ao outro uma vez por dia. `/divorciar` devolve os itens do baú a quem os colocou.

Comandos de jogador: `/profissoes`, `/familiar`, `/casar`, `/divorciar`. Admin: `/aincrad life reload`, `/aincrad estalagem criar`. Dados em SQLite. API: `getProfessionLevel(Player, profissao)` e eventos `PlayerRestEvent`, `PetTameEvent`, `MarriageEvent`.

## Plugin 7 — AincradReputation (pasta aincrad-reputation), títulos e player killers

O AincradReputation registra o que cada jogador fez e mostra isso aos outros: títulos, conquistas e a cor do cursor.

1. **Conquistas** em `achievements.yml`, ligadas a eventos dos outros plugins: primeiro a entrar num andar, participou da primeira vitória de um chefe, deu um Last Attack, pescou um lendário, domou um familiar.
2. **Títulos**: cada conquista pode liberar um título ("Beater", "Linha de Frente", "Primeiro no Andar 5"). O jogador escolhe um em `/titulos`; ele aparece antes do nome no chat, na tab e sobre a cabeça.
3. **Cor do cursor**: verde (normal), laranja por 24 h para quem atacou um jogador verde, vermelho para quem matou um. O tempo de vermelho aumenta a cada morte causada. A cor aparece como marcador sobre a cabeça (via AincradEffects quando instalado, senão como cor do nome).
4. **Consequências**: laranja e vermelho não entram nas cidades (guardas os expulsam). Atacar um laranja ou vermelho não muda a cor de quem atacou.
5. **Prisão**: uma região marcada como prisão. Vermelho derrotado por outro jogador vai preso por um tempo proporcional às mortes causadas (configurável), sem comandos de teleporte e sem cristais. Cumprido o tempo, volta laranja.
6. **PvP por andar**: a config define andares ou regiões com PvP desligado.

Comandos: `/titulos`, `/conquistas`, `/reputacao [jogador]`. Admin: `/aincrad prisao definir`, `/aincrad reputacao limpar <jogador>`, `/aincrad titulo dar <jogador> <titulo>`. API: `getCursorColor(Player)`, `grantAchievement(Player, id)`.

## Plugin 8 — AincradAdmin (pasta aincrad-admin), ferramentas do dono

O AincradAdmin não muda o jogo: ele registra, publica e protege os dados dos outros plugins.

1. **Discord por webhook** (sem bot): mensagens automáticas de andar liberado, chefe derrotado (com Last Attack e top 3 de dano), raid marcada e conquista rara. Um webhook por tipo de mensagem, textos em `discord.yml`.
2. **Ranking em site**: um servidor web pequeno embutido (porta configurável, desligado por padrão) com uma página de rankings (linha de frente, maiores danos por chefe, tempo de cada andar, Last Attacks) e os mesmos dados em JSON em `/api/ranking`. Só leitura.
3. **Registro de admin**: toda ação administrativa dos plugins Aincrad (liberar andar, dar cristal, dar título, editar chefe, limpar reputação) e toda entrega de item raro ficam gravadas com quem, quando e o quê. Consulta por `/aincrad log [jogador] [tipo]`.
4. **Backup**: cópia diária dos bancos SQLite e YAMLs de todos os plugins Aincrad numa pasta com data, guardando os últimos 14 dias. `/aincrad backup agora` e `/aincrad backup restaurar <data>` (com confirmação e servidor sem jogadores).
5. **Painel de estado**: `/aincrad status` mostra a versão de cada plugin Aincrad, se as integrações estão ligadas e o tamanho do resource pack.

Todos os comandos exigem aincrad.admin. O webhook e a porta do site ficam só na config, nunca em mensagens ou logs.

## Plugin 9 — AincradRPG (pasta aincrad-rpg), level e atributos

O AincradRPG é o dono do level, do XP e dos atributos do jogador, e do level dos mobs e da raridade dos itens. Feito logo depois do core (etapas A1 e A2), porque o HUD, as recompensas de chefe e as profissões usam esses números.

1. **Level e XP**: curva de XP em `levels.yml` (fórmula ou tabela), level máximo configurável. A barra de XP do Minecraft mostra o level e o progresso do RPG. O XP vanilla é desligado.
2. **Atributos**: força (dano), agilidade (velocidade de ataque e de movimento) e vitalidade (vida máxima). Cada level dá pontos para distribuir em `/status` ou no menu.
3. **Level dos mobs**: mobs nascem com level conforme o andar e a zona, com vida e dano escalados. Dão XP conforme o level, com redução quando o jogador está muito acima.
4. **Raridade de itens**: comum, incomum, raro, épico e lendário, com cor no nome e bônus sorteados dentro de faixas em `items.yml`. Item pode exigir level mínimo para equipar.
5. **Morte**: penalidade configurável (perda de parte do XP do level atual; nunca perde level por padrão).

Comandos: `/status`, `/level [jogador]`; admin: `/aincrad rpg xp dar|tirar`, `/aincrad rpg level definir`, `/aincrad rpg item gerar <id> <raridade>`. API: `getLevel(Player)`, `addXp(Player, qtd, motivo)`, `getAttribute(Player, atributo)`, `getMobLevel(Entity)`, `createItem(id, raridade)` e eventos `PlayerLevelUpEvent` e `XpGainEvent`. O Bosses, o Life e o Quests dão XP por essa API.

## Plugin 10 — AincradEconomy (pasta aincrad-economy), Col

O AincradEconomy cria a moeda Col e tudo o que se compra com ela. Feito nas etapas A3 e A4.

1. **Col**: saldo por jogador no MySQL, válido em toda a rede. Registrado como provedor do Vault, para plugins de terceiros também usarem.
2. **Ganho**: mobs soltam Col conforme o level; chefes e quests pagam pela API.
3. **Lojas de NPC** em `shops.yml`: compra e venda com preços fixos, abertas por NPC ou bloco.
4. **Troca entre jogadores**: `/pagar` e janela de troca segura, em que os dois confirmam.
5. **Leilão**: anunciar item com preço e prazo, comprar pelo menu, taxa configurável, itens não vendidos voltam por caixa de retirada.
6. **Casas**: regiões à venda em cada andar. O dono pode construir dentro, trancar portas e baús e dar acesso a outros jogadores. Uma casa por jogador por padrão.

Comandos: `/col`, `/pagar`, `/trocar`, `/leilao`, `/casa`; admin: `/aincrad col dar|tirar|definir`, `/aincrad loja criar`, `/aincrad casa criar <preço>`. API: `getBalance`, `deposit`, `withdraw` (com motivo, para o registro do Admin) e `TransactionEvent`. A estalagem e o ferreiro do Life passam a cobrar em Col.

## Plugin 11 — AincradSocial (pasta aincrad-social), party, guilda, amigos e duelos

O AincradSocial liga os jogadores entre si e funciona entre servidores da rede pelo `MessageBus` do core.

1. **Party**: até 6 jogadores (configurável). XP dividido entre membros próximos, regra de drops (livre, por vez ou do líder), vida dos membros no HUD e chat da party.
2. **Guilda**: como no anime — cargos com permissões, tag e emblema no nome, uniforme, casa, cofre, tesouro, nível, alianças e ranking da linha de frente. Detalhes em "Guilda — estrutura como no anime", logo abaixo.
3. **Amigos**: lista, pedido e aceite, aviso de entrada, e teleporte até o amigo só dentro de zona segura.
4. **Mensagens**: mensagem privada e caixa de mensagens para quem está offline.
5. **Duelos**: desafio com modo "primeiro golpe", "metade da vida" ou "total". Ninguém morre nos dois primeiros. Funciona dentro de zona segura e não altera a cor do cursor do Reputation.

Comandos: `/party`, `/guilda`, `/amigo`, `/msg`, `/duelo`. As telas entram no menu do AincradUI por `registerMenuEntry`. API: `getParty(Player)`, `getGuild(Player)`, `areAllies(a, b)` e eventos de party, guilda e duelo.

### Guilda — estrutura como no anime

A guilda é o grupo permanente do jogador, como os Cavaleiros do Juramento de Sangue ou o Fuurinkazan: dura entre sessões, tem hierarquia, símbolo, base própria e disputa a linha de frente. A party continua sendo o grupo de uma caçada; a guilda é quem você é em Aincrad.

**Inspiração**: cada recurso vem de uma guilda do anime. Nenhuma delas é criada pelo plugin; servem de modelo para a config e para os testes.

| Guilda do anime | O que traz para o sistema |
| --- | --- |
| Cavaleiros do Juramento de Sangue (KoB) | Comandante e vice-comandante, uniforme branco e vermelho, sede própria, topo do ranking da linha de frente |
| Divine Dragon Alliance (DDA) | Guilda grande de linha de frente que disputa o Last Attack com a KoB: ranking por chefes derrotados |
| Aincrad Liberation Force ("o Exército") | Muitos membros, base no andar 1, oficiais que comandam grupos: limite alto de membros e cargo de oficial |
| Fuurinkazan | Guilda pequena de amigos com tag e chat próprios: criar e manter uma guilda pequena tem que ser barato |
| Gatos Negros do Luar | Juntam Col para comprar uma casa: tesouro da guilda e compra de casa com o tesouro |
| Laughing Coffin | Guilda de jogadores vermelhos: a tag fica vermelha e a guilda sai do ranking quando o líder tem cursor vermelho |

**Cargos**: cinco cargos fixos, do comandante ao recruta. Os nomes são editáveis em `messages.yml`; as permissões são editáveis por guilda pelo líder, exceto as marcadas como só do líder.

| Permissão | Líder (Comandante) | Sub-líder (Vice) | Oficial | Membro | Recruta |
| --- | --- | --- | --- | --- | --- |
| Convidar e aceitar candidaturas | sim | sim | sim | não | não |
| Expulsar (só cargos abaixo do seu) | sim | sim | sim | não | não |
| Promover e rebaixar (até um cargo abaixo do seu) | sim | sim | não | não | não |
| Depositar no cofre e no tesouro | sim | sim | sim | sim | não |
| Retirar do cofre | sim | sim | sim | sim (limite por dia) | não |
| Sacar do tesouro e comprar com ele | sim | sim | não | não | não |
| Entrar na casa da guilda | sim | sim | sim | sim | sim |
| Mudar tag, emblema, uniforme e regras | sim | não | não | não | não |
| Propor e aceitar alianças | sim | sim | não | não | não |
| Passar a liderança e desfazer a guilda | só o líder | não | não | não | não |

Há sempre um líder e no máximo um sub-líder. Recruta vira membro sozinho depois de `guilda.recruta_dias` (padrão 3) ou antes, se um oficial promover. Líder sem entrar por `guilda.lider_inativo_dias` (padrão 30) passa a liderança para o sub-líder ou, sem ele, para o oficial mais antigo.

**Criação e entrada**: criar uma guilda é uma conquista, como em SAO Progressive, onde as guildas só surgem depois de uma quest no andar 3.

1. Requisitos (todos configuráveis): andar 3 liberado, quest "Selo da Guilda" concluída pelo fundador (AincradQuests), level mínimo 10 e 5.000 Col. Sem o Quests instalado, vale só level e Col.
2. O fundador escolhe nome (3 a 24 letras, único), tag (2 a 4 letras, única) e emblema (lista de emblemas do pack). Nome e tag passam por um filtro de palavras.
3. A guilda nasce no nível 1 com o fundador como líder. Ela precisa de 3 membros em 7 dias, senão é desfeita e metade do Col volta.
4. Recrutamento em três modos: só convite, candidatura (o jogador pede e um oficial aceita) ou aberta. Quem entra começa como recruta.
5. Um jogador só tem uma guilda. Quem sai ou é expulso espera `guilda.espera_nova_horas` (padrão 24) para entrar em outra.
6. Desfazer: só o líder, com confirmação. O tesouro vai para o líder, os itens do cofre vão para a caixa de entrada dele e a casa volta à venda.

**Funções da guilda**

1. **Identidade**: a tag aparece antes do nome no chat, na lista de jogadores e acima da cabeça (`[KoB] Asuna`). O emblema (imagem do pack) aparece ao lado do nome na barra de vida do AincradUI e no painel de perfil. Membros da mesma guilda veem a tag em verde; aliados, em azul.
2. **Uniforme**: duas cores escolhidas pelo líder (ex.: branco e vermelho da KoB). `/guilda uniforme` entrega peças de couro tingidas com nome e emblema; são cosméticas e não podem ser vendidas.
3. **Casa da guilda**: uma casa do AincradEconomy comprada com o tesouro vira sede. Todos os membros entram; `/guilda casa` teleporta para lá, dentro das regras de zona segura.
4. **Cofre**: baú compartilhado (9 espaços no nível 1, até 54 no nível 5), aberto pelo menu ou na casa. Cada depósito e retirada fica no histórico (quem, o quê, quando).
5. **Tesouro**: Col da guilda. Membros depositam à vontade e o líder define uma taxa automática de 0 a 10% sobre o Col que os membros ganham caçando. Todo movimento fica no extrato.
6. **Nível**: a guilda ganha XP quando membros derrotam chefes de andar (mais com Last Attack), concluem quests e caçam no labirinto. O nível libera mais vagas e mais cofre (tabela abaixo).
7. **Linha de frente**: ranking por chefes de andar derrotados, Last Attacks e andar mais alto liberado. O anúncio do Floors cita a guilda ("O andar 5 foi conquistado pelos Cavaleiros do Juramento de Sangue") e a guilda do Last Attack ganha um título no AincradReputation.
8. **Alianças**: até 3 guildas aliadas para os raids da linha de frente. Aliados têm canal de chat comum, não se ferem e aparecem em azul.
9. **Guilda vermelha**: se o líder tem cursor vermelho no AincradReputation, a tag fica vermelha, a guilda sai do ranking e não pode ter casa em cidade, como a Laughing Coffin.
10. **Chat da guilda**: `/g <mensagem>` ou o prefixo `#` no chat. Chega nos outros servidores da rede pelo `MessageBus`.

| Nível | XP total | Vagas | Cofre | Libera |
| --- | --- | --- | --- | --- |
| 1 | 0 | 10 | 9 | Tag, emblema, chat, tesouro |
| 2 | 1.000 | 20 | 18 | Uniforme, comprar casa |
| 3 | 3.000 | 30 | 27 | Alianças |
| 4 | 7.000 | 40 | 36 | Emblema animado no HUD |
| 5 | 15.000 | 50 | 54 | Banner da guilda na casa e na sala do chefe |

Todos os valores da tabela ficam em `guilds.yml`.

**Comandos, menu, dados e API da guilda**

- Comandos: `/guilda criar <nome> <tag>`, `info [guilda]`, `convidar`, `aceitar`, `recusar`, `candidatar <guilda>`, `sair`, `expulsar`, `promover`, `rebaixar`, `lider`, `cofre`, `tesouro [depositar|sacar] <valor>`, `casa`, `uniforme`, `alianca [propor|aceitar|desfazer] <guilda>`, `ranking`, `desfazer`; `/g <mensagem>`. Admin: `/aincrad guilda [renomear|desfazer|dar-xp] <guilda>`.
- Menu (AincradUI): a entrada "Guilda" do grupo Party abre a lista da guilda com botões Membros, Cofre, Tesouro, Candidaturas, Alianças, Ranking e Opções. Sem guilda, mostra "Criar guilda" (com os requisitos) e as guildas abertas para candidatura.
- Dados (MySQL): `guilds` (nome, tag, emblema, cores, nível, XP, tesouro, modo de recrutamento), `guild_members` (jogador, cargo, desde), `guild_permissions`, `guild_vault`, `guild_ledger` (extrato e histórico do cofre), `guild_alliances`, `guild_applications`.
- API: `getGuild(Player)`, `getRank(Player)`, `hasGuildPermission(Player, permissão)`, `areGuildAllies(a, b)`, `addGuildXp(guilda, valor, motivo)` e os eventos `GuildCreateEvent`, `GuildJoinEvent`, `GuildLeaveEvent`, `GuildRankChangeEvent`, `GuildLevelUpEvent` e `GuildAllianceEvent`. O Bosses chama `addGuildXp` ao derrotar um chefe; o Floors lê `getGuild` para o anúncio.

## Plugin 12 — AincradSkills (pasta aincrad-skills), Sword Skills

O AincradSkills dá ao combate o sistema de habilidades do anime: proficiência por tipo de arma, Sword Skills ativas e skills passivas.

1. **Proficiência**: cada tipo de arma (espada de uma mão, florete, adaga, machado, lança, espada de duas mãos) tem nível próprio, que sobe com o uso.
2. **Sword Skills ativas** em `skills/*.yml`: nome, arma, proficiência mínima, dano, alcance, número de golpes, recarga e a pausa depois do uso (o jogador fica travado por um instante, como no anime). Efeito visual e som pelo AincradEffects.
3. **Ativação**: até 4 skills em atalhos (troca de mão + número da hotbar, configurável). No Bedrock, por um item de atalho.
4. **Skills passivas**: cura em batalha, aparar, busca (mostra mobs próximos), rastreio (segue um alvo marcado) e arremesso de lâminas. Os ícones dessas skills já estão no pacote de artes.
5. **Espaços de skill**: o número de skills equipadas cresce com o level do RPG.
6. **Skill única**: Duas Espadas, dada a um único jogador por critério configurável (ex.: o mais rápido num teste de reação) ou à mão pelo admin.

Comandos: `/skills`; admin: `/aincrad skill dar`, `/aincrad skill unica <jogador>`. API: `castSkill`, `getProficiency` e `SkillCastEvent`. Skills são bloqueadas em zona segura, fora de duelo.

## Plugin 13 — AincradQuests (pasta aincrad-quests), missões

O AincradQuests traz as missões de NPC de cada andar, definidas em `quests/*.yml`.

1. **Objetivos**: matar mobs, coletar ou entregar itens, falar com NPC, chegar a um lugar, derrotar um chefe, pescar, cozinhar. Uma quest pode ter várias etapas em sequência.
2. **Recompensas**: XP, Col, itens, título ou skill, pelas APIs dos outros plugins.
3. **Tipos**: história do andar (uma vez), diárias e semanais (renovam), e quest de party (progresso compartilhado).
4. **NPCs**: ícone sobre a cabeça de quem tem quest disponível ou pronta para entregar; diálogo em janela com opções.
5. **Diário**: lista de quests ativas e concluídas no menu do AincradUI, com rastreio da quest escolhida no HUD.
6. **Requisitos**: level mínimo, andar liberado ou quest anterior.

Comandos: `/quests`; admin: `/aincrad quest dar|concluir|resetar`, `/aincrad npc criar`. NPCs próprios e simples por padrão, com integração opcional com Citizens ou FancyNpcs se instalados.

## Contrato entre os plugins

Os três plugins só falam entre si por esta API pública. Nenhum acesso direto ao banco ou às classes internas de outro plugin. A API é registrada no `ServicesManager` do Bukkit.

| Item | Tipo | Quem oferece | Quem usa |
| --- | --- | --- | --- |
| `isUnlocked(int andar)` | Método | Floors | Bosses (não invoca chefe de andar bloqueado) |
| `getFrontline()` | Método | Floors | Bosses, Effects e placeholders |
| `getFloorAt(Location)` | Método | Floors | Bosses (descobre o andar da sala) |
| `getFloorName(int andar)` | Método | Floors | Effects (título "Andar 2 — Urbus") |
| `unlockFloor(int andar, Collection<UUID> participantes)` | Método | Floors | Bosses (na primeira vitória) |
| `FloorUnlockEvent` | Evento (cancelável) | Floors | Effects (Floor Cleared) e plugins futuros |
| `PlayerEnterFloorEvent` | Evento | Floors | Modo misto e Effects (chegada no andar) |
| `GateTeleportEvent` (jogador, de, para) | Evento | Floors | Effects (animação do Gate) |
| `getBosses()` (id, nome, level, andar, porta, sala, já derrotado, quem derrotou) | Método | Bosses | Effects (placa da porta) |
| `BossFightStartEvent` (chefe, jogadores, intro\_segundos) | Evento | Bosses | Effects (entrada do chefe, esconder a placa) |
| `BossDamageEvent` (chefe, jogador, dano, crítico) | Evento | Bosses | Effects (números de dano) |
| `BossPhaseChangeEvent` (chefe, fase nova) | Evento | Bosses | Effects (aviso de fase) |
| `BossDefeatEvent` (andar, chefe, Last Attack, participantes, dano, itens e XP de cada um) | Evento | Bosses | Effects (Congratulations e janela de resultado) |
| `BossFightEndEvent` (chefe, motivo: derrota ou tempo) | Evento | Bosses | Effects (mostrar a placa de novo) |

A API fica num pacote `api` separado dentro do aincrad-floors. O aincrad-bosses compila contra o .jar do aincrad-floors (`compileOnly`) e declara `depend: [AincradFloors]` no `plugin.yml`.

O Bosses também tem um pacote `api` e registra sua API no `ServicesManager`. O aincrad-effects compila contra os dois .jar (`compileOnly`), mas declara só `softdepend: [AincradFloors, AincradBosses]`: liga sem eles e ativa a integração quando eles estão presentes. O Floors e o Bosses não dependem do Effects; se ele for desligado, tudo continua funcionando, só sem os efeitos. Qualquer outro plugin futuro do servidor (quests, NPCs, economia) usa a API `play(...)` do Effects para seus próprios efeitos.

O AincradEffects oferece `registerPackFolder(Plugin, pasta)` para outros plugins colocarem arquivos no pack único. O AincradUI oferece `registerMenuEntry(id, icone, acao)`, `openScreen(Player, tela)` e `isMenuOpen(Player)` para os plugins futuros (party, guilda, quests, skills).

Segunda leva: o Bosses passa a oferecer `isCrystalBlocked(Location)` (usado pelo Items) e os eventos `RaidScheduledEvent` e `BossEditedEvent`. O Items, o Life e o Reputation expõem as APIs e os eventos citados nas seções deles. O Admin só ouve: todo plugin Aincrad dispara um `AincradAdminActionEvent` (quem, ação, alvo, detalhes) em cada ação administrativa, definido no pacote `api` do core, para o registro funcionar sem dependência direta.

## Etapas de desenvolvimento

São 43 etapas, nesta ordem: 0 (aincrad-core), A1 a A4 (aincrad-rpg e aincrad-economy), 1 a 30 e, por fim, 31 a 38 (terceira leva). Das numeradas: 1 a 4 no aincrad-floors, 5 a 9 no aincrad-bosses e 10 a 13 no aincrad-effects (a 10 é o motor geral do servidor; 11 a 13 são a integração com o Aincrad) 14 a 17 no aincrad-ui e 18 a 30 na segunda leva (tabelas logo abaixo). Cada uma termina com o servidor de teste ligando sem erro no console.

| # | Pasta | Entrega | Pronto quando |
| --- | --- | --- | --- |
| 0 | aincrad-core | Config, mensagens, comandos, banco, regiões com varinha, menus com `MenuRenderer`, perfil do jogador, utilitários, testes com MockBukkit, publicação no Maven local, README | O core liga sozinho no Paper 1.21.11; `/aincrad regiao criar teste` salva a seleção e entrar nela dispara o evento; um plugin de exemplo abre um menu e grava um valor no perfil |

| # | Pasta | Entrega | Pronto quando |
| --- | --- | --- | --- |
| A1 | aincrad-rpg | Level, XP, atributos, `/status`, barra de XP, dados no MySQL | Ganhar XP sobe de level, dá pontos, e o level é o mesmo em dois servidores da rede |
| A2 | aincrad-rpg | Level dos mobs, raridade de itens, penalidade de morte, API, README | Mob do andar 1 nasce com o level da zona e dá o XP certo; um item raro gerado tem bônus dentro da faixa |
| A3 | aincrad-economy | Col, provedor Vault, ganho por mob, lojas de NPC, `/pagar`, troca segura | Matar mob dá Col; comprar na loja desconta; o saldo é o mesmo em toda a rede |
| A4 | aincrad-economy | Leilão, casas, API, README | Um item anunciado é comprado por outro jogador; uma casa comprada só abre para o dono e convidados |

| # | Pasta | Entrega | Pronto quando |
| --- | --- | --- | --- |
| 1 | aincrad-floors | Projeto Gradle, `plugin.yml`, configs de exemplo, carregamento e reload | O plugin liga, `/aincrad reload` funciona e os 3 andares de exemplo aparecem em `/andares` |
| 2 | aincrad-floors | Estado salvo (SQLite), liberar e bloquear andares, bloqueio de entrada | Andar 2 bloqueado não deixa entrar; depois de `/aincrad andar liberar 2` deixa, e continua liberado após reiniciar o servidor |
| 3 | aincrad-floors | Anúncio global, Teleport Gates com menu, modos `por_jogador` e `misto` | O anúncio aparece para todos; o Gate mostra só os andares certos em cada modo |
| 4 | aincrad-floors | API pública, eventos (inclusive `GateTeleportEvent`), placeholders, README | Um plugin de teste simples consegue chamar `unlockFloor` e ouvir os eventos |
| 5 | aincrad-bosses | Projeto ligado ao Floors, leitura dos chefes em YAML (com level), `/aincrad chefe invocar` | O Illfang de exemplo aparece com nome, level, vida e equipamento do YAML |
| 6 | aincrad-bosses | Fases e habilidades com recarga | A 25% da vida o Illfang troca de arma e usa as habilidades da fase nova |
| 7 | aincrad-bosses | Sala do chefe: introdução, porta trancando, BossBar, vitória, derrota e tempo limite | A porta fecha ao começar e abre nos três finais; quem saiu não volta |
| 8 | aincrad-bosses | Registro de dano, Last Attack, recompensas, liberação do próximo andar, API e eventos | Matar o chefe do andar 1 dá o item de Last Attack a quem matou, libera o andar 2 para todos e dispara os eventos da tabela do contrato |
| 9 | aincrad-bosses | Labirinto com zonas e níveis de mob, README | Os mobs perto da sala são mais fortes que os da entrada e respeitam o limite da zona |
| 10 | aincrad-effects | Projeto independente e motor de efeitos: passos em YAML, variáveis, quem vê, API, comando /efeito, gatilhos, limites de desempenho, resource pack com imagens provisórias e modo sem pack | Liga sem o Floors e o Bosses; /efeito tocar teste @a mostra imagem, som e partículas; um gatilho de entrar no servidor funciona; quem recusa o pack vê só texto |
| 11 | aincrad-effects | Placa do chefe na porta (Text Display e opção map art) | A 8 blocos a placa aparece com fade, nome e level do YAML; some quando a luta começa e mostra "Derrotado por …" depois da vitória |
| 12 | aincrad-effects | Entrada do chefe, números de dano e aviso de fase | Na luta de teste aparecem o nome na introdução, um número por golpe e o aviso "Fase 2" |
| 13 | aincrad-effects | Congratulations, janela de resultado, chegada no andar, Gate, Floor Cleared, `/efeito tocar`, README | Matar o Illfang mostra o Congratulations e a janela com itens e Last Attack em destaque; o andar 2 mostra Floor Cleared para todos |

| # | Pasta | Entrega | Pronto quando |
| --- | --- | --- | --- |
| 14 | aincrad-ui | Projeto ligado ao Effects, artes de `pack_otimizado/` e sons registrados no pack único, `CREDITOS.md`, `/creditos` | `/aincrad ui teste` mostra cada ícone (normal e `_on`) e toca os 6 sons |
| 15 | aincrad-ui | Menu principal e submenu nos modos cursor e baú, gesto de abrir, `/menu` | O gesto abre o menu; o ícone troca para `_on` com som ao passar o cursor; Itens abre o inventário; o modo baú funciona igual |
| 16 | aincrad-ui | HUD de vida, aviso de vida crítica e tela de morte | Abaixo de 25% a barra fica vermelha, pisca e toca o alerta; ao morrer aparecem os estilhaços e "You are dead" |
| 17 | aincrad-ui | Janelas de perfil, detalhes e confirmação, logout, API `registerMenuEntry`, README | Um plugin de teste adiciona uma entrada no menu e ela funciona; logout pede confirmação |

Segunda leva. Só começa com as etapas 1 a 17 prontas e testadas no servidor.

| # | Pasta | Entrega | Pronto quando |
| --- | --- | --- | --- |
| 18 | aincrad-bosses | Proteção contra abuso e guia de estratégia | Quem desloga na luta não volta e não recebe item; o guia mostra só as fases já vistas |
| 19 | aincrad-bosses | Quadro de raid e editor de chefes no jogo | Uma raid marcada avisa os inscritos na hora; mudar a vida do chefe pelo menu vale na próxima luta e fica no YAML |
| 20 | aincrad-items | Projeto, `crystals.yml`, cristais de cura, antídoto e teleporte, modelos no pack único | Os três cristais funcionam com recarga; o de teleporte lista só andares liberados e cancela com dano |
| 21 | aincrad-items | Ressurreição, gravação, áreas anti-cristal, API, README | Um jogador é salvo dentro dos 10 s; a mensagem gravada é lida por outro jogador; nenhum cristal funciona na sala anti-cristal |
| 22 | aincrad-life | Projeto, dados em SQLite, estalagens | Descansar dá o bônus e, ao morrer, o jogador volta à estalagem |
| 23 | aincrad-life | Culinária, pesca e ferreiro com níveis | Uma receita dá o buff configurado; o peixe lendário do andar 1 pode ser pescado; o reparo consome os materiais |
| 24 | aincrad-life | Familiares | Domar funciona com a comida certa; o familiar segue, dá o bônus e pode ser revivido com o item |
| 25 | aincrad-life | Casamento, API, README | O pedido aceito cria o baú compartilhado; o divórcio devolve os itens a quem colocou |
| 26 | aincrad-reputation | Projeto, conquistas e títulos | Matar o chefe do andar 1 pela primeira vez dá a conquista e o título aparece no chat e sobre a cabeça |
| 27 | aincrad-reputation | Cor do cursor, bloqueio nas cidades, prisão, PvP por andar, API, README | Atacar um verde deixa laranja; matar deixa vermelho; vermelho derrotado vai preso pelo tempo configurado |
| 28 | aincrad-admin | Projeto, `AincradAdminActionEvent` nos outros plugins, registro e `/aincrad log` | Liberar um andar à mão aparece no log com autor e hora |
| 29 | aincrad-admin | Discord por webhook e ranking em site | Derrotar um chefe publica a mensagem no canal de teste; a página e o JSON mostram a linha de frente |
| 30 | aincrad-admin | Backup, restauração, `/aincrad status`, README | O backup diário é criado; restaurar uma data traz de volta o estado dos andares |

Terceira leva. Começa depois da etapa 30.

| # | Pasta | Entrega | Pronto quando |
| --- | --- | --- | --- |
| 31 | aincrad-social | Party: convite, XP dividido, regra de drops, vida no HUD, chat | Dois jogadores em party dividem o XP de um mob e veem a vida um do outro |
| 32 | aincrad-social | Guilda como no anime: criação com quest, 5 cargos, tag e emblema, uniforme, casa, cofre, tesouro com taxa, nível, alianças, ranking da linha de frente, guilda vermelha, chat | Criar exige a quest do andar 3 e desconta o Col; a tag aparece no chat e acima da cabeça; o cofre e o tesouro respeitam os cargos; derrotar um chefe dá XP à guilda e o anúncio do andar cita o nome dela; o chat chega em outro servidor da rede |
| 33 | aincrad-social | Amigos, mensagens, duelos, telas no menu, API, README | Um duelo "primeiro golpe" termina sem morte dentro da zona segura; mensagem offline é entregue ao entrar |
| 34 | aincrad-skills | Proficiência por arma e motor de Sword Skills em YAML | Uma skill de exemplo causa o dano configurado, entra em recarga e trava o jogador pelo tempo definido |
| 35 | aincrad-skills | Atalhos, espaços de skill, efeitos, 3 skills por tipo de arma | Equipar e ativar pelos atalhos funciona no Java e no Bedrock |
| 36 | aincrad-skills | Skills passivas, skill única, API, README | Cura em batalha recupera vida fora de combate; Duas Espadas só existe para um jogador |
| 37 | aincrad-quests | Motor de quests, objetivos, recompensas, NPCs com diálogo | A quest de exemplo "matar 5 lobos" é aceita, concluída e paga XP e Col |
| 38 | aincrad-quests | Diárias, quest de party, diário no menu, rastreio no HUD, README | A diária renova no dia seguinte; o progresso de party conta para todos os membros |

## O que ainda falta

Estado em 18/11/2026. O AincradAdmin (etapas 28–30) e o AincradSkills (etapas 34–36) já têm código, nas pastas `aincrad-admin/` e `aincrad-skills/`, sem teste em servidor; a descrição de cada um está na seção dele, mais acima, e os critérios de pronto estão nas tabelas de etapas.

| Etapas | Plugin | O que é |
| --- | --- | --- |
| 28–30 | AincradAdmin | Registro das ações de admin, Discord por webhook, ranking em site, backup e restauração, `/aincrad status` |
| 34–36 | AincradSkills | Proficiência por arma, Sword Skills ativas e passivas, atalhos, skill única |
| 37–38 | AincradQuests | Motor de quests, NPCs com diálogo, diárias, quest de party, diário no menu |

O AincradReputation (etapas 26–27) já tem código, na pasta `aincrad-reputation/`; como o AincradLife, ainda não foi testado num servidor de verdade.

Fora das etapas, o AincradRPG ganhou os **mobs por região** (`spawns.yml`): cada região do core pode ter a sua lista de mobs, do Minecraft ou do MythicMobs, com level e peso. É a base dos mobs de cada pacote de andar (A1, A2…).

**O que cada um dos três traz**

- **AincradSkills**: é o que mais faz falta no combate. Sem ele, lutar é o combate normal do Minecraft com números maiores. Inclui a pausa depois do golpe, a proficiência por arma e a Dual Blades.
- **AincradQuests**: NPCs com missão, diárias e a quest "Selo da Guilda" do andar 3, que o AincradSocial exige para criar uma guilda.
- **AincradAdmin**: não muda a experiência do jogador, mas o dono precisa do backup e do registro das ações.

### Quarta leva — o mundo de Aincrad

Os plugins dão os sistemas; para o servidor parecer o Aincrad do anime ainda faltam o conteúdo de cada andar e algumas regras do jogo da morte. Esta leva começa depois da etapa 38, porque usa as Sword Skills e as quests.

**Conteúdo de cada andar**

Hoje os arquivos trazem só exemplos. Cada andar novo é um pacote de conteúdo, sem código, com quatro partes:

1. **Chefe**: existe o Illfang, do andar 1. Cada andar precisa do seu em `bosses/andarN.yml`, com fases e habilidades.
2. **Mobs**: hoje são mobs do Minecraft com level e nome (um Kobold é um zumbi renomeado), porque o plano deixou os modelos 3D de fora. Cada andar define os seus em `mob-levels.yml` e nas zonas do labirinto: tipo, level, equipamento e drops. Modelos próprios continuam fora do escopo; se entrarem, vão pelo pack único do AincradEffects.
3. **Itens**: armas, armaduras, drops e receitas próprias do andar, em `items.yml` (RPG), `forge.yml` e `ingots.yml` (ferreiro), `recipes.yml` (culinária), `formulas.yml` (alquimia) e `fishing.yml` (pesca).
4. **Quests**: a história do andar e as diárias, em `quests/andarN.yml`. Depende do AincradQuests.

O mapa de cada andar (terreno, cidade, labirinto, sala do chefe) é construído pelo dono do servidor; os plugins só delimitam as regiões.

**Regras e eventos do jogo da morte**

1. **Monumento da Vida** (AincradReputation): um mural, no Palácio de Ferro Negro, com o nome de todos os jogadores que já entraram. O nome é riscado quando o jogador morre, com a data e a causa (mob, chefe, jogador, queda). `/monumento` mostra a mesma lista em menu, com busca. Vale para a rede inteira.
2. **O dia do anúncio** (AincradEffects, com o AincradFloors): um evento que o dono dispara uma vez, com `/aincrad anuncio iniciar`. Todos os jogadores online são levados à praça da Cidade do Início, o céu fica vermelho e o discurso passa em sequência, em efeitos de YAML. No fim, o `modo_death_game` do AincradUI é ligado e o botão de logout some. Quem entrar depois vê uma versão curta.
3. **Fim de jogo** (AincradFloors, com o AincradBosses): hoje o plano não diz o que acontece quando o último chefe cai. A config ganha `andar_final` (75, como no anime, ou 100) e `ao_vencer`: anúncio para a rede, créditos com os nomes de quem sobreviveu e de quem participou da última luta, e o que fazer com o mundo (continuar aberto, reiniciar a temporada ou fechar a entrada). A escolha entre esses três fica para o dono.
4. **Chefes de campo e de evento** (AincradBosses): chefes fora das salas de andar. O de campo fica numa região do mapa e volta de tempos em tempos; o de evento aparece numa data marcada e some depois. Nenhum dos dois libera andar. O primeiro de evento é o Nicholas, o Renegado, na noite de Natal, que dá o Cristal de Ressurreição do AincradItems — o cristal já existe, o chefe não.
5. **Informantes** (AincradBosses, com o AincradEconomy): o guia de estratégia dos chefes já cobre parte do papel da Argo. Falta o comércio de informação: o guia deixa de ser aberto a todos e passa a ser vendido. Um NPC informante em cada cidade vende, em Col, as páginas que o servidor já descobriu; e um jogador que viu uma fase ou habilidade primeiro pode vender a página dele a outro, como item.

| # | Pasta | Entrega | Pronto quando |
| --- | --- | --- | --- |
| 39 | aincrad-reputation | Monumento da Vida: registro de entradas e mortes, mural e `/monumento` | Um jogador que morre aparece riscado, com a causa, no mural e no menu, também para quem está em outro servidor |
| 40 | aincrad-effects, aincrad-floors | O dia do anúncio: teleporte à praça, sequência do discurso, logout escondido | `/aincrad anuncio iniciar` leva todos à praça, toca a sequência inteira e, no fim, o menu não mostra mais o logout |
| 41 | aincrad-bosses | Chefes de campo (região e intervalo) e de evento (data marcada) | O chefe de campo volta no intervalo da config sem liberar andar; o Nicholas aparece na data marcada e dá o Cristal de Ressurreição a quem der o Last Attack |
| 42 | aincrad-bosses, aincrad-economy | Informantes: páginas do guia vendidas por NPC e entre jogadores | Sem comprar, o guia de um chefe aparece em branco; comprar a página no informante desconta o Col e mostra a fase |
| 43 | aincrad-floors, aincrad-bosses | Fim de jogo: `andar_final`, anúncio, créditos e o que fazer com o mundo | Derrotar o chefe do andar final anuncia a vitória para a rede e aplica a opção escolhida em `ao_vencer` |
| A1, A2… | conteúdo | Um pacote por andar (A1 é o andar 1): chefe, mobs, itens e quests | No andar novo, o chefe luta com as fases do YAML, os mobs têm os levels e drops definidos, os itens podem ser forjados ou dropados e a história do andar pode ser concluída |

Os pacotes de andar não entram na numeração das etapas: são feitos um a um, ao longo de todo o resto. O andar 1 é o primeiro (A1): a Cidade do Início, os campos, o labirinto, o Illfang com os Sentinelas e a quest do primeiro dia.

### Quinta leva — o mundo vivo

Dezesseis ideias para o mundo deixar de ser cenário. Nenhuma é necessária para jogar; a ordem das etapas vai do que custa menos e usa o que já existe para o que mexe no equilíbrio de tudo. Calendário, clima, dia e noite e o jornal são de um plugin novo, o **AincradWorld** (pasta `aincrad-world`, `softdepend` em todos os outros); o resto são extras dos plugins que já existem.

**O mundo reage aos jogadores (AincradWorld)**

1. **Notícias do castelo**: um jornal semanal montado sozinho com o que aconteceu — andar liberado, quem deu o Last Attack, o torneio de pesca, casamentos, quem foi preso, conquistas anunciadas. O plugin só ouve os eventos que os outros já disparam e guarda no banco. Sai como um livro, vendido por NPC nas cidades e lido em `/jornal`; as edições antigas ficam guardadas.
2. **Calendário de Aincrad**: datas e estações próprias do servidor, em `calendar.yml` (quantos dias reais dura uma estação, e as datas marcadas). Dá chão para os eventos: o Nicholas no Natal, festivais nas cidades, peixes e ervas de estação. Os outros plugins perguntam pela API `getSeason()` e `isDate(id)`, e ouvem `SeasonChangeEvent` e `CalendarDateEvent`.
3. **Clima por andar**: cada andar com o seu tema, como no anime — o 4 coberto de água, o 22 de floresta calma, o 35 com a Floresta da Perdição. `weather.yml` define, por andar e por estação, a chance de chuva, neve e névoa. O clima muda a visibilidade (névoa com os efeitos do AincradEffects) e quais mobs aparecem (o RPG e o labirinto do Bosses consultam o clima).
4. **Dia e noite que importam**: mobs mais fortes e com drops melhores à noite, e as cidades como abrigo de verdade. Multiplicadores de level, XP, Col e chance de drop por período, em `world.yml`. Aviso na tela ao anoitecer fora de zona segura.

**Lugares com história**

5. **Dungeons escondidas** (AincradBosses): como a do andar 27, onde os Gatos Negros caíram na armadilha. Uma região fora do caminho do labirinto, com mobs acima do level do andar, área anti-cristal e um baú-armadilha: abrir fecha a porta e dispara o alarme, que chama mobs até todos morrerem ou o tempo acabar. Definidas em `dungeons/*.yml`.
6. **Zonas fora do mapa** (AincradFloors, com o AincradUI): áreas sem marcação, onde o mapa do jogador fica em branco até ele explorar a pé. O andar é dividido em setores; cada setor visitado fica gravado por jogador. O mapa do menu mostra só os setores vistos, e os dados podem ser vendidos como item ("dados de mapa"), como no anime.
7. **Túmulos e memoriais** (AincradReputation): quando um jogador morre, fica uma lápide no lugar, com o nome e a data. Combina com o Monumento da Vida. As lápides não podem ser quebradas, somem depois de um prazo configurável (ou nunca) e têm um limite por pedaço de mapa. Outro jogador pode deixar uma flor.
8. **Casas com vista** (AincradEconomy): a cabana do andar 22. Casas raras, uma por andar, marcadas com `leilao: true`. Não têm preço fixo: vão a leilão com lance mínimo e prazo, e o maior lance leva. Quando o dono vende ou abandona, a casa volta a leilão.

**Coisas que os jogadores fazem entre si**

9. **Reunião de estratégia** (AincradBosses, com os informantes da etapa 42): antes de um chefe, o líder da raid convoca a reunião numa região da cidade, como a do Diabel em Tolbana. Os inscritos são avisados; quem está presente recebe a função, vê as páginas do guia que o líder comprou e fica marcado como "informado" na lista da raid.
10. **Contratos de escolta** (AincradLife, nas encomendas): um jogador paga outro para atravessar um labirinto ou chegar a um lugar. O Col fica em custódia e é pago quando o contratante chega vivo ao destino (uma região), com o escolta por perto. Se o contratante morre, o Col volta e o escolta perde reputação.
11. **Recompensa por cabeça** (AincradReputation, com o AincradEconomy): a vítima de um jogador vermelho, ou qualquer um, põe Col pela prisão dele. O valor fica em custódia e vai para quem o derrotar e mandar para a prisão. `/recompensas` lista as cabeças a prêmio. Se o vermelho voltar a verde sem ser preso, o Col volta a quem pôs.
12. **Guildas de crime** (AincradSocial, com o AincradReputation): o plano já tem a guilda vermelha. Falta o que a Laughing Coffin fazia: uma base escondida (uma casa que não aparece em lista nenhuma e só entra no mapa de quem a encontrou a pé) e emboscadas (dentro de uma região marcada pela guilda, as vítimas não usam cristal de teleporte por alguns segundos depois do primeiro golpe). Derrotar o líder na base dele rende a recompensa de todos os membros.

**Itens e segredos**

13. **Mobs raros de andar** (AincradRPG, com o AincradLife): mobs com chance baixa de aparecer num andar, que fogem do jogador e dão um drop raro. O primeiro é o coelho do ragu: a receita do Ensopado de Coelho já espera a carne dele como ingrediente de rank S, que hoje só existe por comando. Definidos em `rare-mobs.yml` (mob, andar, chance, clima ou período, drop).
14. **Itens únicos do servidor** (AincradRPG): a Elucidator e a Dark Repulser existem uma vez só. Um item marcado como `unico: true` só é criado se ainda não existe na rede; fica registrado quem o tem; não pode ser destruído (lava, vazio, despawn) e, se o dono some por um prazo configurável, volta a poder cair. `/unicos` lista os que já foram encontrados.
15. **Skills únicas** (AincradSkills, ampliando a etapa 36): além da Dual Blades, o anime cita dez. Cada uma com um critério secreto em `unique-skills.yml` (o tempo de reação mais rápido, mais golpes aparados, mais chefes sem morrer…), dada ao primeiro jogador que o cumprir. O critério não aparece em lugar nenhum; o servidor só anuncia que a skill encontrou um dono.
16. **Chaves e mapas** (AincradItems, com o AincradBosses): drops que abrem uma dungeon escondida para um grupo, uma vez. A chave abre a porta para a party de quem a usa e é gasta. O mapa mostra a entrada no mapa do menu e some depois de usado.

| # | Pasta | Entrega | Pronto quando |
| --- | --- | --- | --- |
| 44 | aincrad-world | Projeto, registro dos acontecimentos e o jornal semanal | Liberar um andar e prender um jogador aparecem na edição da semana; `/jornal` abre a edição atual e as antigas |
| 45 | aincrad-rpg, aincrad-life | Mobs raros de andar; o coelho do ragu | Com chance 100% em teste, o coelho aparece no andar dele, foge do jogador e solta a carne que a receita de rank S aceita |
| 46 | aincrad-reputation, aincrad-economy | Recompensa por cabeça | O Col posto na cabeça de um vermelho vai para quem o prender; se ele volta a verde antes, o Col volta a quem pôs |
| 47 | aincrad-reputation | Túmulos e memoriais | Morrer deixa uma lápide com nome e data que não quebra e some no prazo da config |
| 48 | aincrad-life | Contratos de escolta | O escolta recebe quando o contratante entra vivo na região de destino; se o contratante morre, o Col volta |
| 49 | aincrad-bosses | Reunião de estratégia | O líder convoca, os inscritos são avisados e quem está na região vê as páginas do guia do líder |
| 50 | aincrad-economy | Casas com vista: venda por leilão | A casa marcada vai para o maior lance no fim do prazo e volta a leilão quando o dono a vende |
| 51 | aincrad-rpg | Itens únicos do servidor | O segundo pedido de criar um item único é recusado em qualquer servidor da rede; jogá-lo na lava não o destrói |
| 52 | aincrad-bosses | Dungeons escondidas e baú-armadilha | Abrir o baú fecha a porta, bloqueia os cristais e chama mobs até o grupo vencer ou o tempo acabar |
| 53 | aincrad-items, aincrad-bosses | Chaves e mapas | A chave abre a dungeon para a party de quem usou e é gasta; sem chave, a porta não abre |
| 54 | aincrad-floors, aincrad-ui | Zonas fora do mapa | Um setor nunca visitado aparece em branco no mapa do menu e é revelado ao entrar nele a pé |
| 55 | aincrad-social, aincrad-reputation | Guildas de crime: base escondida e emboscada | A base não aparece em lista nenhuma até ser encontrada; na região de emboscada, o cristal de teleporte da vítima falha pelo tempo da config |
| 56 | aincrad-skills | Skills únicas com critério secreto | Com um critério de teste, a skill vai para o primeiro que o cumpre e não pode ser dada a um segundo |
| 57 | aincrad-world | Calendário: estações, datas marcadas, API e eventos | Mudar a estação dispara o evento; um plugin de teste pergunta se hoje é a data "natal" e recebe a resposta certa |
| 58 | aincrad-world | Dia e noite que importam | À noite, fora de zona segura, um mob do mesmo tipo nasce com level maior e dá o drop multiplicado da config |
| 59 | aincrad-world | Clima por andar | O andar com névoa na config reduz a visão e troca a tabela de mobs enquanto a névoa dura |

**Dependências entre as etapas**: a 49 precisa dos informantes (42); a 53 precisa das dungeons (52); a 56 precisa do AincradSkills (34 a 36); o Nicholas da etapa 41 passa a usar o calendário quando a 57 existir, e até lá usa uma data fixa na config. As etapas 57 a 59 mexem no equilíbrio de mobs, drops e economia: só depois do andar 1 (A1) pronto e jogado.

## Testes e entrega

A entrega final são catorze .jar (o core e os treze plugins) que funcionam juntos numa rede de teste com Velocity, dois servidores Paper 1.21.11, MySQL, LuckPerms e Geyser + Floodgate, com configs de exemplo e um README em português em cada pasta.

**Testes**

- Testes automáticos com MockBukkit para a lógica sem mundo: estado dos andares, os três modos de progressão, divisão de recompensas por dano e troca de fase por vida.
- Teste manual num servidor Paper local seguindo os critérios de pronto da tabela de etapas.
- Casos difíceis que precisam ser cobertos: servidor reiniciado no meio de uma luta (a porta tem que abrir ao ligar), dois jogadores dando o golpe final no mesmo tick, jogador desconectando dentro da sala e AincradFloors desligado (o Bosses não liga e avisa no console), jogador que recusou o resource pack (o Effects troca as imagens por texto) e muitos jogadores batendo no chefe ao mesmo tempo (os números de dano não podem derrubar o TPS).

**Entrega por pasta**

- [ ] Código-fonte e `./gradlew build` gerando o .jar sem avisos
- [ ] `config.yml`, `messages.yml` e exemplos (`floors.yml` com 3 andares; `bosses/andar1.yml` com o Illfang e 4 lacaios)
- [ ] README com instalação, comandos, permissões e como criar um andar e um chefe novos
- [ ] Lista curta do que ficou para depois (ex.: modelos 3D, Sword Skills, interface SAO) e, no aincrad-effects, a lista das imagens provisórias que o usuário precisa trocar pelas artes finais
