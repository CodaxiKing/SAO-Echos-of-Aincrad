# AincradEffects

Motor de efeitos visuais e sonoros do servidor inteiro, para Paper 1.21.11. Qualquer plugin,
comando, NPC, quest ou evento pode tocar um efeito. Ele só desenha: não dá itens, não libera andares
e não mexe em lutas. Funciona sozinho; a placa do chefe, o "Congratulations!" e os outros efeitos do
Aincrad são só os primeiros usos.

Também é dono do **resource pack único** do servidor: junta as próprias imagens e as pastas que
outros plugins registram (o AincradUI, por exemplo), gera o .zip e envia aos jogadores.

## Instalação

1. `./gradlew build` → `build/libs/AincradEffects-<versão>.jar` em `plugins/`.
2. Ligue o servidor. São criados `config.yml`, `messages.yml`, `images.yml`, `triggers.yml`,
   `effects/exemplos.yml` e `resourcepack/` (imagens provisórias).
3. Em `config.yml → pack.servidor_embutido.endereco_publico`, coloque o endereço que os jogadores
   usam para baixar o pack (ex.: `http://123.45.67.89:8163`) e libere a porta. Para teste local,
   pode deixar vazio. Se preferir hospedar o .zip em outro lugar, use `pack.url`.
4. `/efeito tocar teste` para conferir.

Requisitos: Paper 1.21.11, Java 21+. Opcionais: PlaceholderAPI, AincradFloors, AincradBosses.

## Efeitos (effects/*.yml)

Cada chave de primeiro nível é um efeito; um arquivo pode ter vários.

```yaml
meu_efeito:
  publico: jogador          # jogador | raio:<n> | mundo | servidor
  passos:
    - em: 0                 # segundos depois do início
      tipo: imagem
      imagem: teste
      subtitulo: "<gray>Olá, {jogador}"
    - em: 0.5
      tipo: particulas
      particula: END_ROD
      forma: espiral
      repetir: 3            # repete mais 3 vezes...
      intervalo: 0.25       # ...a cada 0,25 s
    - em: 2
      tipo: menu
      fundo: fundo_menu
      itens: [{ slot: 13, material: NETHER_STAR, nome: "<aqua>OK", fechar: true }]
```

### Tipos de passo

| tipo | Campos (todos opcionais, menos os marcados) |
|---|---|
| `titulo` | `titulo`, `subtitulo`, `entrada`, `fica`, `saida` (s) |
| `imagem` | **`imagem`** (id de images.yml), `subtitulo`, `reserva` (texto para quem está sem pack), `entrada`, `fica`, `saida` |
| `actionbar` | **`texto`**, `duracao` |
| `bossbar` | `texto`, `cor` (PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE), `estilo` (PROGRESS, NOTCHED_6/10/12/20), `progresso_inicial`, `progresso_final`, `duracao` |
| `som` | **`som`** (ex.: `minecraft:entity.player.levelup`), `volume`, `tom`, `categoria`, `no_jogador` (toca na posição de cada espectador) |
| `particulas` | **`particula`**, `forma` (ponto, circulo, coluna, espiral, explosao, linha), `quantidade`, `raio`, `altura`, `voltas`, `comprimento`, `direcao`, `espalhar` [x,y,z], `velocidade`, `deslocamento`, e dados: `cor`, `cor_final`, `tamanho`, `bloco`, `item` |
| `texto_mundo` | **`texto`** (aceita `<img:id>`), `deslocamento`, `escala`, `escala_final`, `subir`, `duracao`, `fade_entrada`, `fade_saida`, `billboard`, `sombra`, `fundo` (#AARRGGBB) |
| `fogos` | `forma_fogo` (BALL, BALL_LARGE, STAR, BURST, CREEPER), `cores`, `cores_fade`, `rastro`, `brilho`, `potencia`, `quantidade`, `espalhar`, `deslocamento`, `explodir` |
| `pocao` | **`efeito`** (ex.: `blindness`), `duracao`, `nivel`, `particulas`, `alvo` (espectadores ou jogador) |
| `menu` | `titulo`, `fundo` (imagem), `linhas` (1–6), `itens`: `slot`, `material`, `nome`, `lore`, `modelo` (item_model), `quantidade`, `comando`, `fechar` |

Campos comuns: `em`, `repetir`, `intervalo`. Tempos sempre em segundos (aceita `0,5` ou `0.5`).

### Textos e variáveis

- MiniMessage (`<aqua>`, `<bold>`, `<gradient:...>`).
- Variáveis: `{jogador}`, `{nome}`, `{level}`, `{mundo}`, `{x}`, `{y}`, `{z}`, `{efeito}`, `{espectador}`
  e qualquer outra que quem chamou mandar (`{andar}`, `{item}`...). Variável sem valor fica como está.
- PlaceholderAPI (`%player_health%`), se instalado.
- `<img:id>` desenha uma imagem de images.yml no meio do texto; quem está sem o pack vê a `reserva`.

### Quem vê

`publico` no efeito (ou `publico_padrao` na config): `jogador` (só o alvo), `raio:<n>`, `mundo` ou
`servidor`. Partículas, textos no mundo, fogos e sons respeitam também `limites.distancia_visao`.

## Imagens (images.yml)

Cada imagem vira um caractere da fonte `aincrad:effects`. As que vêm no plugin são **provisórias**:
desenhe as finais com o mesmo nome em `plugins/AincradEffects/resourcepack/assets/aincrad/textures/`
e use `/efeito reload`. O plugin nunca sobrescreve seus arquivos.

| id | Arquivo (em `textures/`) | Tamanho sugerido | Uso |
|---|---|---|---|
| `teste` | `effects/teste.png` | 512×256 | efeito de teste |
| `bem_vindo` | `effects/bem_vindo.png` | 1024×256 | entrada no servidor |
| `fundo_menu` | `effects/fundo_menu.png` | 512×512 | fundo de menus |
| `placa_chefe` | `effects/aincrad/placa_chefe.png` | 512×256 | placa na porta do chefe |
| `congratulations` | `effects/aincrad/congratulations.png` | 1024×256 | vitória sobre o chefe |
| `janela_resultado` | `effects/aincrad/janela_resultado.png` | 512×512 | fundo da janela de resultado |
| `floor_cleared` | `effects/aincrad/floor_cleared.png` | 1024×256 | andar liberado |
| `brasao_andar_1..3` | `effects/aincrad/brasao_andar_N.png` | 128×128 | brasão de cada andar |

## Gatilhos (triggers.yml)

Sem código: `entrar_servidor`, `entrar_regiao`, `sair_regiao`, `matar_mob`, `subir_nivel`,
`pegar_item`, `morrer`. Cada gatilho toca um efeito, com `atraso`, `permissao`, `chance`, `publico`
e filtros (`mobs`, `itens`, `nivel`, `regiao`). O de entrada espera o jogador carregar o pack
(até 10 s), para a imagem aparecer de verdade.

## Comandos e permissões

| Comando | O que faz | Permissão |
|---|---|---|
| `/efeito tocar <efeito> [alvo] [var=valor ...]` | Toca um efeito. Alvo: jogador, `@s`, `@a` ou `raio:<n>` (a partir de quem executa ou do bloco de comando). Em `var=valor`, `_` vira espaço | `aincrad.effects.play` |
| `/efeito lista` | Lista os efeitos carregados | `aincrad.effects.play` |
| `/efeito parar [jogador]` | Para os efeitos em andamento (no console, sem jogador, para todos) | `aincrad.effects.play` |
| `/efeito pack` | Reenvia o resource pack para você | `aincrad.effects.pack` (todos) |
| `/efeito reload` | Recarrega efeitos, gatilhos, imagens e regera o pack | `aincrad.admin` |

Aliases: `/efeitos`, `/fx`.

## Resource pack único

- Camadas, nesta ordem: fontes geradas → `plugins/AincradEffects/resourcepack/` → pastas registradas
  por outros plugins (`registerPackFolder`).
- Arquivos repetidos: `font/*.json` juntam os `providers`, `atlases/*.json` juntam os `sources`,
  `sounds.json` e `lang/*.json` juntam as chaves. Qualquer outro repetido: vale o último, com aviso no console.
- O .zip fica em `plugins/AincradEffects/pack/aincrad-pack.zip`. O hash só muda quando o conteúdo
  muda; quando muda, quem está online recebe o pack novo.
- Quem recusa o pack vê a versão simples: imagens viram o texto `reserva`, menus ficam sem fundo.

## Limites de desempenho (config.yml → limites)

- `particulas_por_jogador_por_tick`: o excedente daquele tick é descartado.
- `entidades_por_jogador`: Text Displays e fogos visíveis ao mesmo tempo.
- `distancia_visao`: quem está mais longe não recebe partículas, textos, fogos e sons do efeito.
- `efeitos_simultaneos_por_jogador`: os mais antigos com o mesmo alvo param.

## API para outros plugins

```java
AincradEffectsAPI fx = Bukkit.getServicesManager().load(AincradEffectsAPI.class);
fx.play("congratulations", player, Map.of("item", "Coat of Midnight"));
fx.play("aincrad_estilhacos", location, 32, Map.of());
fx.play("floor_cleared", location, Bukkit.getOnlinePlayers(), Map.of("andar", "2"));
fx.registerPackFolder(this, new File(getDataFolder(), "resourcepack"));
fx.stop(player);
```

No `plugin.yml` do outro plugin: `softdepend: [AincradEffects]` e `compileOnly` contra este .jar.
O AincradUI já se liga sozinho (procura `com.aincrad.effects.api.AincradEffectsAPI`).

## Ficou para depois (etapas 11–13 do plano)

A integração com o Floors e o Bosses (placa do chefe na porta, entrada do chefe, números de dano,
aviso de fase, Congratulations com janela de resultado, chegada no andar, animação do Teleport
Gate e Floor Cleared) depende das APIs desses plugins, que ainda não existem. As imagens
provisórias dela já estão em images.yml.

## Decisões tomadas (não definidas no plano)

- Um arquivo de efeitos pode conter vários efeitos (cada chave é um efeito).
- Todos os tempos do YAML são em segundos.
- Textos no mundo são criados um por espectador (cada um vê suas variáveis e a versão com/sem pack).
- `/efeito tocar ... @a` toca o efeito para cada jogador, na posição dele.
- O passo `menu` aceita `comando` por item (executado pelo jogador que clicou).
- Fogos de efeito não causam dano.

## Desenvolvimento

```bash
./gradlew build
```

```bash
python tools/gerar_placeholders.py
```

Os testes (JUnit) cobrem a junção do pack, as formas de partícula, o fade, o público, as variáveis,
os limites, as regiões, a leitura de efeitos e a validade dos YAML de exemplo.
