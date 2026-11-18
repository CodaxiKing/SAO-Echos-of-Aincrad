# AincradSkills

O sistema de habilidades do anime: **proficiência por tipo de arma**, **Sword Skills ativas** (com
recarga e a pausa depois do uso), **atalhos**, **skills passivas** e a **skill única** Duas Espadas.
Etapas 34 a 36 do plano.

Precisa do **AincradCore**. Opcionais: AincradRPG (o level libera os espaços de skill) e
AincradFloors (skills bloqueadas em zona segura).

> Compila e passa nos testes de regras, mas **ainda não foi testado num servidor de verdade**.

## Proficiência

Cada tipo de arma tem a sua, de 0 a 1000: espada, florete, adaga, machado, lança, espada de duas
mãos (e Duas Espadas, para o dono da skill única).

- Sobe com o uso: cada golpe que acerta tem chance de render 1 ponto. A chance começa em 50% e cai
  conforme a proficiência sobe, até 5% (`proficiencia` no `config.yml`).
- Libera as skills da arma e aumenta o dano delas (+50% com 1000, por padrão).
- **Como o plugin reconhece a arma** (`config.yml → armas`): pelo nome do item e pelo material. O
  nome vale mais: uma `IRON_SWORD` chamada "Florete de Ferro" é um florete. Assim os itens do
  AincradRPG e do ferreiro do AincradLife viram florete, adaga ou lança só pelo nome.

## Sword Skills (`skills/*.yml`)

Um arquivo por arma, três skills em cada um, 21 no total. Uma skill:

```yaml
corte_inclinado:
  nome: "Slant"
  arma: espada
  proficiencia: 0      # mínima para equipar e usar
  dano: 1.6            # x o dano de ataque do jogador, por golpe
  golpes: 1
  alcance: 3.5
  forma: alvo          # alvo, linha (todos à frente) ou area (todos em volta)
  recarga: 5           # segundos
  pausa: 0.4           # segundos travado depois do último golpe
  avanco: 0            # blocos que o jogador avança
```

O jogador fica travado durante a `pausa`: lento, sem golpe comum e sem outra skill. Trocar de arma
no meio interrompe a skill. Arquivos novos na pasta `skills/` são lidos no `/aincrad reload`.

## Usar

| Como | O que faz |
|---|---|
| `/skills` | Menu: proficiências, skills (clique equipa ou tira) e passivas |
| **F** (troca de mão) com a arma na mão | Mostra os atalhos; a tecla da hotbar indicada usa a skill |
| Agachado + **F** | Troca de mão de verdade |
| `/skill <1 a 4>` | Usa a skill do atalho (serve para macro e para o Bedrock) |
| `/skills atalho` | Dá o item de atalho: botão direito abre as skills equipadas (Bedrock) |
| `/rastrear` | Marca o alvo na mira (passiva Rastreio) |

As teclas dos atalhos pulam a tecla do item na mão — apertá-la não faria nada. Com a arma na tecla
1, os atalhos são 2, 3, 4 e 5.

**Espaços**: até 4 ativas e 3 passivas, liberadas pelo level do AincradRPG (`config.yml → espacos`).

## Passivas

| Passiva | O que faz |
|---|---|
| Cura em Batalha | Recupera vida a cada 10 s, em batalha ou fora dela |
| Aparar | Com a arma na mão, 20% de chance de anular um golpe que vem de frente |
| Busca | Marca os monstros num raio de 24 blocos; só o jogador vê |
| Rastreio | `/rastrear` segue o alvo na mira: seta e distância na tela |
| Arremesso de Lâminas | Botão direito com uma lâmina (`IRON_NUGGET`) arremessa uma |

## Skill única: Duas Espadas

Existe para **um jogador só na rede inteira** (a chave primária do banco garante). O dono, com uma
espada em cada mão, usa as skills do `skills/duas_espadas.yml` (Double Circular, Starburst Stream de
16 golpes e The Eclipse de 27). Os outros jogadores nem veem essas skills no menu.

- À mão: `/aincrad skill unica <jogador>`.
- Pelo teste de reação: `/aincrad skill teste`. Depois de uma espera sorteada aparece "AGORA!" e o
  primeiro a se agachar ganha. Quem se agacha antes está fora da rodada.
- `/aincrad skill unica_limpar` deixa a skill sem dono.

O servidor só é avisado de que a skill encontrou um dono.

### Mais skills únicas, com critério secreto (`unique-skills.yml`, etapa 56)

Além de Duas Espadas, o arquivo aceita quantas skills únicas o dono quiser. Cada uma vai para o
**primeiro jogador que cumprir o critério**, e o critério não aparece em comando nem em mensagem —
nem no console.

| `criterio` | Cumpre quem |
|---|---|
| `manual` | Recebe do admin |
| `reacao` | Vence `/aincrad skill teste <id>` |
| `aparados:300` | Apara 300 golpes com a passiva Aparar |
| `golpes:5000` | Acerta 5000 golpes com uma arma |
| `skills:1000` | Usa 1000 Sword Skills |
| `proficiencia:florete:1000` | Chega à proficiência na arma |

Os contadores ficam no banco (`skill_counters`) e valem para a rede. O que a skill única dá: as
Sword Skills de `skills/*.yml` marcadas com `unica: <id>` passam a existir só para o dono. Para uma
skill única nova, crie um arquivo em `skills/` com a `arma:` que ela usa e `unica: <id>`.

## Admin

| Comando | O que faz |
|---|---|
| `/aincrad skill dar <jogador> <arma> <0 a 1000>` | Define a proficiência |
| `/aincrad skill unica <jogador> [id]` · `unica_limpar [id]` | Dá ou tira a skill única |
| `/aincrad skill teste` | Começa o teste de reação |
| `/aincrad skill lista` | Skills carregadas e os erros dos arquivos |

Permissão `aincrad.admin`. As ações entram no registro do AincradAdmin.

## API

`com.aincrad.skills.api.AincradSkillsAPI` (estático, também no ServicesManager): `castSkill(Player,
id)`, `getProficiency(Player, arma)`, `getWeaponType(Player)`, `hasUniqueSkill(Player, id)` e
`getUniqueHolder(id)`. Evento `SkillCastEvent` (cancelável).

## Banco

`skill_prof` (proficiência por arma), `skill_slots` (skills equipadas) e `skill_unique` (o dono de
cada skill única), no banco do core.

## Escolhas diferentes do plano

- Com o AincradEffects, usar uma skill toca o efeito `aincrad_skill_<id>`, `aincrad_skill_<arma>`
  ou `aincrad_skill` (o primeiro que existir; o último já vem pronto). As partículas e o som de cada
  golpe, definidos na skill, continuam.
- Em zona segura as skills são bloqueadas, menos para quem está num duelo do AincradSocial.
- A Cura em Batalha cura sempre, não só fora de combate.

## Compilar

```bash
./gradlew build
```

O jar sai em `build/libs/AincradSkills-0.1.0.jar`.
