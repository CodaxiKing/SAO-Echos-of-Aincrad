# AincradUI

Interface estilo SAO para Paper 1.21.11: menu de ícones em anel, HUD de vida, aviso de vida crítica
e tela de morte. Faz parte do conjunto Aincrad (Floors, Bosses, Effects, UI).

Não é uma conversão do mod SAO MC UI (mod desenha no cliente; plugin não pode). Usa só as artes e
os sons do mod, com autorização do autor, e refaz o comportamento com resource pack, fontes e
Text Displays.

## Instalação

1. `./gradlew build` → `build/libs/AincradUI-<versão>.jar` para a pasta `plugins/` do servidor.
2. Ligue o servidor uma vez. São criados `ui.yml`, `menu.yml`, `glyphs.yml`, `messages.yml` e a pasta `resourcepack/`.
3. Instale as artes (próxima seção) e use `/aincrad ui reload`.

Requisitos: Paper 1.21.11, Java 21+. Opcional: **AincradEffects** (pack único do servidor e efeitos).

## Artes (aincrad-ui-assets.zip)

O plugin já vem com imagens provisórias simples (círculos com siglas), para tudo funcionar antes das
artes finais. Para usar as artes do mod:

1. Copie o conteúdo de `pack_otimizado/textures/` para
   `plugins/AincradUI/resourcepack/assets/aincrad/textures/ui/`, substituindo os arquivos.
2. Confira em `glyphs.yml` se os nomes batem (`nomes:` de cada grupo). Cada ícone de `icons/`,
   `under_icons/` e `buttons/` precisa também da versão `_on`.
3. Sons: coloque os `.ogg` em `plugins/AincradUI/resourcepack/assets/aincrad/sounds/ui/` com os
   nomes de `ui.yml → sons → arquivo` (`open`, `move`, `confirm`, `back`, `close`, `critical`, `death`).
   Enquanto o `.ogg` não existe, o pack aponta para um som vanilla de reserva.
4. `/aincrad ui reload`.

Os arquivos do plugin nunca sobrescrevem os seus: só são copiados os que faltam.

### Imagens provisórias a trocar

| Arquivo (em `textures/ui/`) | Uso |
|---|---|
| `icons/*.png` e `*_on.png` | menu principal (perfil, party, mensagens, mapa, config) |
| `under_icons/*.png` e `*_on.png` | submenu |
| `buttons/*.png` e `*_on.png` | sim, não, mais, menos, lista, detalhes, iniciar, cancelar |
| `skills/skill_1..7.png` | skills |
| `window_blank.png` | fundo das janelas e do menu de baú |
| `person.png` | silhueta do perfil |
| `effects/you_are_dead.png` | tela de morte |
| `extra/cursor.png`, `extra/hud_segment.png`, `extra/lock.png` | do próprio AincradUI (não vêm do mod) |

## Resource pack

- **Com o AincradEffects**: o AincradUI não gera pack próprio. Registra a pasta `resourcepack/`
  no Effects (`registerPackFolder`), que junta tudo num pack único do servidor.
- **Sem o AincradEffects**: gera `plugins/AincradUI/aincrad-ui-pack.zip`. Para enviar aos jogadores,
  defina `pack.url` (hospedagem própria) ou ligue `pack.servidor_embutido` com o `endereco_publico`.
  Para testar sem enviar, instale o .zip no cliente e ligue `pack.assumir_carregado: true`.

Gerado automaticamente (não edite): `font/ui.json`, `font/space.json`, `models/ui/`, `items/ui/`,
`sounds.json` e as texturas transparentes da BossBar branca.

Quem recusa o pack vê a versão simples: menu de baú com itens comuns, HUD como BossBar colorida e
"You are dead" em texto.

## Como funciona

- **Abrir**: agachar + clique esquerdo com a mão vazia, ou `/menu`.
- **Modo cursor** (padrão): o menu flutua na frente do jogador em Text Displays que só ele vê. O
  jogador fica parado (velocidade e pulo zerados), o cursor segue o olhar, o ícone troca para `_on`
  com som. Clique escolhe; agachar volta; levar dano fecha.
- **Modo baú**: menu de inventário com `window_blank` de fundo e ícones do pack (`item_model`).
  É usado por quem escolher em Opções e sempre por quem está sem o pack.
- **HUD de vida**: no alto da tela, desenhada no título de uma BossBar com a fonte do pack (o
  deslocamento para a esquerda é `hud.deslocamento_x`). Verde, amarela abaixo de 50% e vermelha
  abaixo de 25%, quando pisca e toca o alerta (com intervalo).
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

## Ficou para depois

- Modelo 3D da barra de vida (health_bar.glb, Sockona, CC BY 4.0): precisa de conversão.
- Ações de party, guilda, amigos, quests, mapa, skills e chamada: aparecem com cadeado até um plugin registrar.
- Grupo `hotbar/` do pacote de artes: ainda sem uso.

## Desenvolvimento

```bash
./gradlew build
```

```bash
python tools/gerar_placeholders.py
```

Testes (JUnit) cobrem a geometria do cursor, slots do baú, faixas de vida, deslocamento da HUD,
leitura de glifos e o redirecionamento de comando.
