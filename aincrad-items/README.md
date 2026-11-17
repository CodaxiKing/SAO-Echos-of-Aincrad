# AincradItems

Os cristais consumíveis do anime (etapas 20–21 do plano). Precisa do **AincradCore**. Opcionais:
AincradFloors (cristal de teleporte), AincradBosses (salas anti-cristal), AincradEffects (envia os
modelos no pack único) e AincradRPG (level dos mobs para os drops).

## Os cristais (`crystals.yml`)

Clique direito com o cristal na mão. Cada um tem nome, descrição, recarga, grupo de recarga e se é consumido.

| Cristal | O que faz |
|---|---|
| **Cura** | Recupera toda a vida na hora (`cura: 1.0` = fração da vida máxima). Cristais do mesmo `grupo_recarga` dividem a recarga. Com a vida cheia não é gasto. |
| **Antídoto** | Remove veneno, wither e lentidão (`remove:`). Sem nenhum desses efeitos não é gasto. |
| **Teleporte** | Abre a lista dos andares em que o jogador pode entrar e leva à chegada do andar escolhido depois de 3 s parado. Andar, levar dano ou sair cancela, e o cristal não é gasto. |
| **Ressurreição** | Clique num jogador que está "se desfazendo" para cancelar a morte dele; ele volta com metade da vida (`vida: 0.5`). |
| **Gravação** | Em branco: clique e digite no chat a mensagem (até 256 letras; não vai para o chat). Gravado: clique para ouvir a mensagem, com autor e data. Pode ser entregue a outro jogador. |

Você pode criar outros cristais com os mesmos tipos (ex.: um `meia_cura` com `cura: 0.5`).

## Ressurreição: a janela "se desfazendo"

Um golpe fatal não mata na hora: o jogador fica `ressurreicao.janela_segundos` (10) parado, sem
poder agir, usar comandos ou teleportar, e imune a mais dano. Quem está por perto é avisado. Se
ninguém usar o cristal nele, morre de verdade ao fim do tempo — os outros plugins veem a morte
normal, e a morte conta para o jogador que deu o golpe.

- Só abre a janela se houver outro jogador a até `precisa_alguem_a` blocos (24). Sozinho, morre na hora.
- Vazio do mundo, `/kill` e quem está com um Totem da Imortalidade na mão não passam pela janela.
- Sair do servidor durante a janela mata na hora.
- `janela_segundos: 0` desliga tudo isso.

## Áreas anti-cristal

Nenhum cristal funciona (e o jogador é avisado) nas salas de chefe com `anti_cristal: true` do
AincradBosses e nas regiões do core listadas em `config.yml → anti_cristal.regioes`. Usar o cristal
de ressurreição também é bloqueado para quem está dentro da área.

## Modelos e resource pack

Cada cristal é um item base (`material`) com um modelo próprio (`modelo`). Os modelos e as artes
ficam em `plugins/AincradItems/resourcepack/` e são entregues ao pack único do AincradEffects.
As artes que vêm no plugin são **provisórias** (um losango colorido por tipo): desenhe as finais
com o mesmo nome em `assets/aincrad/textures/crystal/` e use `/efeito reload`.

`config.yml → modelos`: `auto` (padrão) usa os modelos só com o AincradEffects no servidor; `sim`
sempre; `nao` nunca. Sem o modelo, o cristal aparece como o material base, brilhando.

## Drops

Com `drops: true`, cada cristal com `drop: { chance, level_mob }` pode cair de mobs mortos por
jogadores, a partir do level indicado (o level é o do AincradRPG; sem ele, só caem os de `level_mob: 0`).
O de ressurreição vem com chance 0,02% em mobs de level 10 ou mais.

## Comandos

| Comando | O que faz | Permissão |
|---|---|---|
| `/aincrad cristal dar <jogador> <tipo> [qtd]` | Dá cristais | `aincrad.admin` |
| `/aincrad cristal lista` | Lista os cristais carregados | `aincrad.admin` |
| `/aincrad reload` | Recarrega config, mensagens e cristais | `aincrad.admin` |
| (permissão) | Usar cristais | `aincrad.items.use` (todos) |

## API

`com.aincrad.items.api.AincradItemsAPI` (registrada no ServicesManager; métodos estáticos):

```java
boolean   giveCrystal(Player, String tipo, int qtd)   ItemStack createCrystal(String tipo, int qtd)
String    getCrystalId(ItemStack)                     List<String> getCrystals()
boolean   isDying(Player)
```

`tipo` é o id de `crystals.yml` ou o nome do tipo (`cura`, `antidoto`, `teleporte`, `ressurreicao`,
`gravacao`). Evento: `CrystalUseEvent` (cancelável; jogador, cristal, tipo e alvo).

## Escolhas feitas onde o plano não definia

- O cristal de teleporte leva ao **ponto de chegada** do andar (`/aincrad andar setspawn`), que é
  onde a cidade deve ficar.
- A janela de ressurreição só abre com alguém por perto, para quem joga sozinho não esperar 10 s a cada morte.
- A mensagem do cristal de gravação é digitada no chat (funciona igual no Bedrock).
- `crystals.yml` não é completado sozinho com os cristais de exemplo: um cristal apagado não volta.
- A recarga fica em memória: continua valendo se o jogador sair e voltar, mas zera ao reiniciar o servidor.

## Compilar

```bash
./gradlew build
```

Compila com `../aincrad-core` e `../aincrad-floors` (build composto). Testes: `CrystalDefTest` (os
cinco cristais de exemplo, os modelos deles no pack, padrões e erros de cadastro, corte da mensagem).
`tools/gerar_cristais.py` refaz as artes provisórias e os modelos.

## Ainda não testado em servidor

O cadastro tem teste. O uso dos cristais, a janela de ressurreição e os modelos no pack ainda não
rodaram num servidor Paper.
