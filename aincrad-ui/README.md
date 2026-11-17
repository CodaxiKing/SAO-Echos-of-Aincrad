# AincradUI

Interface estilo SAO para Paper 1.21.11: menu de ícones em anel, HUD de vida, aviso de vida crítica
e tela de morte. Faz parte do conjunto Aincrad (Floors, Bosses, Effects, UI).

Não é uma conversão de mod (mod desenha no cliente; plugin não pode): o comportamento é refeito
com resource pack, fontes e Text Displays.

## Instalação

1. `./gradlew build` → `build/libs/AincradUI-<versão>.jar` para a pasta `plugins/` do servidor.
2. Ligue o servidor. São criados `ui.yml`, `menu.yml`, `glyphs.yml`, `messages.yml` e a pasta `resourcepack/`.

Requisitos: Paper 1.21.11, Java 21+. Opcional: **AincradEffects** (pack único do servidor e efeitos).

## Artes

As artes vêm do projeto [SAO-UI/sao-assets](https://github.com/SAO-UI/sao-assets) (vetores e fonte
de darkblackswords; ver `CREDITOS.md`) e já estão no plugin:

| Arquivo (em `textures/ui/`) | Origem | Uso |
|---|---|---|
| `icons/*.png` e `*_on.png` | SAO Icons, "Ringed" | coluna principal (perfil, party, mensagens, mapa, config) |
| `under_icons/*.png` e `*_on.png` | SAO Icons, Menu-2 | submenu (grande no baú, pequeno nas linhas) |
| `buttons/*.png` e `*_on.png` | SAO Icons, Buttons | sim, não, mais, menos, lista, detalhes, iniciar, cancelar |
| `skills/skill_1..7.png` | SAO Icons, Skills | skills |
| `profile_panel.png` | SAO UI v1.100 | painel de perfil com silhueta e pontos de equipamento |
| `person.png` | SAO Man | silhueta sozinha |
| `hud/frame.png`, `hud/fill_00..19.png` | SAO Life Gauge | moldura e fatias da barra de vida |
| `effects/you_are_dead.png` | SAO HEX | tela de morte |
| `window_blank.png`, `extra/*` | desenhados no mesmo estilo | janelas, setas, cursor, cadeado, fundo das linhas |
| `font/sao_ui.ttf` | SAO UI (fonte) | textos do menu e nome na HUD |

Para atualizar a partir do repositório de artes:

```bash
git clone --depth 1 https://github.com/SAO-UI/sao-assets.git ../../sao-assets
```

```bash
python tools/importar_sao_assets.py ../../sao-assets
```

Sons: coloque os `.ogg` em `plugins/AincradUI/resourcepack/assets/aincrad/sounds/ui/` com os nomes
de `ui.yml → sons → arquivo` (`open`, `move`, `confirm`, `back`, `close`, `critical`, `death`) e use
`/aincrad ui reload`. Enquanto o `.ogg` não existe, o pack aponta para um som vanilla de reserva.

O plugin nunca sobrescreve arquivos que você trocou, com uma exceção: quando uma versão nova traz
artes novas (`.versao_artes` diferente), a pasta `resourcepack/` antiga é guardada em
`resourcepack_antigo_<versão>/` e as novas são copiadas.

## Resource pack

- **Com o AincradEffects**: o AincradUI não gera pack próprio. Registra a pasta `resourcepack/`
  no Effects (`registerPackFolder`), que junta tudo num pack único do servidor.
- **Sem o AincradEffects**: gera `plugins/AincradUI/aincrad-ui-pack.zip`. Para enviar aos jogadores,
  defina `pack.url` (hospedagem própria) ou ligue `pack.servidor_embutido` com o `endereco_publico`.
  Para testar sem enviar, instale o .zip no cliente e ligue `pack.assumir_carregado: true`.

Gerado automaticamente (não edite): `font/ui.json`, `font/space.json`, `font/sao.json` (TTF da
fonte SAO com reserva na fonte padrão), `font/hud_low.json` (números embaixo da barra),
`models/ui/`, `items/ui/`, `sounds.json` e as texturas transparentes da BossBar branca.

Quem recusa o pack vê a versão simples: menu de baú com itens comuns, HUD como BossBar colorida e
"You are dead" em texto.

## Como funciona

- **Abrir**: agachar + clique esquerdo com a mão vazia, ou `/menu`.
- **Modo cursor** (padrão), como no anime: o menu flutua na frente do jogador em Text Displays que
  só ele vê. No centro, a coluna de ícones em anel (o escolhido fica laranja). À esquerda, o painel
  de perfil com o nome, a silhueta e o equipamento flutuando sobre os pontos (clique abre os
  detalhes da peça), com uma seta apontando para a coluna. À direita do ícone escolhido, uma seta e
  as linhas do submenu (ícone + nome na fonte SAO sobre fundo branco; laranja com o cursor em
  cima). O jogador fica parado (velocidade e pulo zerados), o cursor segue o olhar e cada troca de
  item faz som. Clique escolhe; agachar volta; levar dano fecha. Janelas (detalhes, confirmação,
  opções, ajuda) são painéis brancos com título, texto escuro e botões redondos.
- **Itens** (Perfil → Itens): a mochila e a hotbar em lista, com o item em 3D, filtro por
  categoria (armas, armaduras, consumíveis, blocos, outros) e páginas. Clicar num item abre a janela
  com Equipar, Segurar (vai para a mão), Jogar fora (com confirmação) e os detalhes.
- **Equipamento** (painel de perfil): clicar num ponto ocupado abre a peça com Desequipar e Trocar;
  num ponto vazio, lista o que da mochila serve ali.
- **O inventário padrão continua existindo**: a tela do inventário (tecla E), a hotbar e o chat são
  desenhados pelo próprio jogo e um plugin não consegue substituí-los. O menu SAO cobre tudo o que
  se faz nelas; a entrada com `acao: inventario` em menu.yml abre o inventário padrão, se quiser.
- **Modo baú**: menu de inventário com `window_blank` de fundo e ícones do pack (`item_model`).
  É usado por quem escolher em Opções e sempre por quem está sem o pack.
- **Party na HUD**: com um plugin de party (AincradSocial), cada membro aparece embaixo da barra
  principal com uma barra menor e o nome (`hud.party`).
- **HUD de vida**: moldura no canto de cima com o nome, a barra do SAO (com o degrau no meio) e
  "vida/máx LV:n" embaixo, desenhada no título de uma BossBar (o deslocamento para a esquerda é
  `hud.deslocamento_x`). A barra é feita de 20 fatias pintadas na cor da vida: verde, amarela abaixo
  de 50% e vermelha abaixo de 25%, quando pisca e toca o alerta (com intervalo).
- **Morte**: estilhaços azuis (efeito do AincradEffects se instalado, senão partículas próprias),
  imagem "You are dead" e som. Mobs também se desfazem (configurável).
- **Logout**: pede confirmação. `logout.modo_death_game: true` troca por uma mensagem, como no anime.

## Comandos e permissões

| Comando | O que faz | Permissão |
|---|---|---|
| `/menu` | Abre o menu principal | `aincrad.ui.menu` (todos) |
| `/creditos` | Mostra os créditos das artes | todos |
| `/aincrad ui teste <tela>` | Abre uma tela ou HUD: `menu`, `perfil`, `detalhes`, `confirmar`, `opcoes`, `ajuda`, `icones`, `morte`, `critico`, `sons` | `aincrad.admin` |
| `/aincrad ui reload` | Recarrega configs e regera o pack | `aincrad.admin` |

`/aincrad` é compartilhado entre os plugins do Aincrad: o AincradUI registra `/aincradui` e
redireciona `/aincrad ui ...` para ele, com ou sem o AincradFloors instalado.

## API para outros plugins

Além das entradas do menu, outros plugins montam telas SAO com listas, janelas e confirmações, e
podem mostrar membros de party embaixo da barra de vida (o AincradSocial usa tudo isso):

```java
ui.openList(player, UiList.of(Component.text("Amigos"))
        .row(UiList.Row.item(cabeca, Component.text("Asuna"), Component.text("online"), p -> abrirAmigo(p)))
        .button(new UiList.Button("buttons/plus", Component.text("Adicionar"), p -> adicionar(p))));
ui.replaceList(player, listaAtualizada);   // atualiza a lista atual
ui.openWindow(player, titulo, linhas, botoes);
ui.setPartyHudProvider(viewer -> membrosOnline(viewer));
```

A ação de uma entrada do menu roda com o menu aberto: ela pode abrir uma lista por cima (agachar
volta) ou chamar `closeMenu`.

```java
AincradUIAPI ui = Bukkit.getServicesManager().load(AincradUIAPI.class);

// Tira o cadeado da entrada "guilda" de menu.yml:
ui.registerMenuEntry("guilda", null, player -> abrirGuilda(player));

// Entrada nova no grupo "party":
ui.registerMenuEntry("convite", "under_icons/call", Component.text("Convidar"), "party", p -> convidar(p));

ui.openScreen(player, "perfil");
ui.openConfirm(player, Component.text("Sair da party?"), p -> sair(p), null);
boolean aberto = ui.isMenuOpen(player);
```

No `plugin.yml` do outro plugin: `softdepend: [AincradUI]` e `compileOnly` contra o .jar do AincradUI.

### Contrato esperado do AincradEffects

O AincradUI não compila contra o Effects: procura o serviço `com.aincrad.effects.api.AincradEffectsAPI`
e chama por reflexão `registerPackFolder(Plugin, File)` e
`play(String efeito, Location local, double raio, Map<String,String> variaveis)`. O efeito de
estilhaços tem o id de `morte.efeito_estilhacos` (padrão `aincrad_estilhacos`).

## Decisões tomadas (não definidas no plano)

- `/aincrad ui` funciona por redirecionamento para `/aincradui`, para não disputar `/aincrad` com o Floors.
- O jogador fica parado no modo cursor por modificadores transitórios de atributo (não são salvos se o servidor cair).
- A HUD usa uma BossBar branca com a barra transparente pelo pack; outros plugins devem usar outras cores de BossBar.
- Sem o AincradEffects, o AincradUI gera e (opcionalmente) serve o próprio pack, para poder ser testado sozinho.
- A escolha cursor/baú e a HUD ligada/desligada ficam no próprio jogador (PersistentDataContainer).
- O menu é desenhado no mundo (Text Displays num plano na frente do jogador), não na tela: é como os
  menus aparecem no anime e não depende de shaders, que quebram a cada versão do Minecraft.
- O painel de perfil aparece junto com o menu principal (`menu.painel_perfil`), como nas referências.

## Ficou para depois

- Modelo 3D da barra de vida (health_bar.glb, Sockona, CC BY 4.0): precisa de conversão.
- Guilda, quests, mapa e skills: aparecem com cadeado até um plugin registrar (party, amigos e
  mensagens já são ligados pelo AincradSocial).
- Relógio e data no canto de baixo (como no SAO Utils).
- Menu desenhado na tela (2D) com shaders próprios, como alternativa ao menu no mundo.

## Desenvolvimento

```bash
./gradlew build
```

Testes (JUnit) cobrem a geometria do cursor, slots do baú, faixas de vida, deslocamento da HUD,
leitura de glifos, a medição da fonte SAO, o posicionamento por pixel, as cores sobre os painéis
brancos, os pontos do painel de perfil, o redirecionamento de comando e se todo glifo e ícone do
menu tem imagem no pack.
