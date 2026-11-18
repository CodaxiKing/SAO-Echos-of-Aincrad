# SAO — Echoes of Aincrad

Plugins Paper (1.21.11, Java 21) que recriam o Aincrad de Sword Art Online num servidor Minecraft.
Cada plugin fica na sua própria pasta, com seu próprio projeto Gradle.

| Pasta | Plugin | Estado |
|---|---|---|
| `aincrad-core/` | **AincradCore** — config, mensagens, /aincrad, banco, regiões, menus, perfil, MessageBus | pronto (etapa 0) |
| `aincrad-ui/` | **AincradUI** — menu de ícones estilo SAO, HUD em barras, telas planas com cursor (loja), vida crítica, tela de morte | pronto (etapas 14–17), com as artes do SAO-UI/sao-assets |
| `aincrad-social/` | **AincradSocial** — amigos, party, guilda (como no anime) e mensagens | pronto (dados em YAML até o aincrad-core) |
| `aincrad-rpg/` | **AincradRPG** — level, XP, atributos, level dos mobs, mobs por região (Minecraft e MythicMobs), itens com raridade | pronto (etapas A1–A2); os mobs por região têm código, sem teste em servidor |
| `aincrad-economy/` | **AincradEconomy** — Col, Vault, lojas de NPC, /pagar, troca segura, leilão, casas e custódia | pronto (etapas A3–A4, mais a custódia das encomendas) |
| `aincrad-floors/` | **AincradFloors** — andares, bloqueio de entrada, zona segura, anúncio, Teleport Gates, o dia do anúncio e o fim de jogo | pronto (etapas 1–4); etapas 40 e 43 com código, sem teste em servidor |
| `aincrad-bosses/` | **AincradBosses** — chefes com fases e habilidades, sala, Last Attack, liberação do andar, labirinto | pronto (etapas 5–9, 18–19: proteção contra abuso, guia, raid, editor); chefes de campo e de evento e informantes (etapas 41–42) com código, sem teste em servidor |
| `aincrad-items/` | **AincradItems** — cristais de cura, antídoto, teleporte, ressurreição e gravação | pronto (etapas 20–21) |
| `aincrad-life/` | **AincradLife** — profissões (proficiência, aprender antes de fazer, árvore), bancadas 3D com painel, culinária, pesca, ferreiro, alquimia, encomendas, barracas, familiares, casamento e estalagens | base pronta (etapa 22), as quatro profissões da etapa 23 e as encomendas, as barracas, os familiares e o casamento: etapas 22 a 25 com código, sem teste em servidor |
| `aincrad-reputation/` | **AincradReputation** — conquistas, títulos, cor do cursor, cidades fechadas, prisão, PvP por andar e o Monumento da Vida | código pronto (etapas 26–27 e 39), sem teste em servidor |
| `aincrad-admin/` | **AincradAdmin** — registro das ações de administração (`/aincrad log`), Discord por webhook, ranking em site, backup diário com restauração e `/aincrad status` | etapas 28–30 com código, sem teste em servidor |
| `aincrad-skills/` | **AincradSkills** — proficiência por arma, Sword Skills ativas e passivas, atalhos e a skill única Duas Espadas | código pronto (etapas 34–36), sem teste em servidor |
| `aincrad-quests/` | **AincradQuests** — quests em etapas, diárias e semanais, quest de party, NPCs com diálogo e diário | código pronto (etapas 37–38), sem teste em servidor |
| `aincrad-effects/` | **AincradEffects** — motor de efeitos do servidor e resource pack único | pronto (etapas 10–13): motor geral e integração com andares e chefes |

## Plano

O plano de desenvolvimento (cópia do documento em https://claude.ai/artifact/A23oCqBsYC54PhuHKenyhW, de 17/11/2026):

- [docs/plano/01-plano.md](docs/plano/01-plano.md) — plano geral dos plugins e das etapas
- [docs/plano/02-tarefas-bloco-1.md](docs/plano/02-tarefas-bloco-1.md) — tarefas do bloco 1 (core, rpg, economy, floors)
- [docs/plano/03-aincrad-life-detalhes.md](docs/plano/03-aincrad-life-detalhes.md) — detalhes do AincradLife

## Compilar

Em cada pasta (o `aincrad-social` compila junto com o `aincrad-ui`, de quem usa a API):

```bash
./gradlew build
```

O `.jar` sai em `build/libs/`.
