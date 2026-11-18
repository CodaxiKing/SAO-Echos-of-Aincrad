# SAO — Echoes of Aincrad

Plugins Paper (1.21.11, Java 21) que recriam o Aincrad de Sword Art Online num servidor Minecraft.
Cada plugin fica na sua própria pasta, com seu próprio projeto Gradle.

| Pasta | Plugin | Estado |
|---|---|---|
| `aincrad-core/` | **AincradCore** — config, mensagens, /aincrad, banco, regiões, menus, perfil, MessageBus | pronto (etapa 0) |
| `aincrad-ui/` | **AincradUI** — menu de ícones estilo SAO, HUD em barras, telas planas com cursor (loja), vida crítica, tela de morte | pronto (etapas 14–17), com as artes do SAO-UI/sao-assets |
| `aincrad-social/` | **AincradSocial** — amigos, party (XP e drops divididos), guilda (como no anime), duelos e mensagens | código pronto (etapas 31–33), sem teste em servidor; dados no banco do core (a rede inteira vê os mesmos) ou em arquivos |
| `aincrad-rpg/` | **AincradRPG** — level, XP, atributos, level dos mobs, mobs por região (Minecraft e MythicMobs), itens com raridade | pronto (etapas A1–A2); os mobs por região, os mobs raros (etapa 45) e os itens únicos (etapa 51) têm código, sem teste em servidor |
| `aincrad-economy/` | **AincradEconomy** — Col, Vault, lojas de NPC, /pagar, troca segura, leilão, casas e custódia | pronto (etapas A3–A4, mais a custódia das encomendas); casas por leilão (etapa 50) com código, sem teste em servidor |
| `aincrad-floors/` | **AincradFloors** — andares, bloqueio de entrada, zona segura, anúncio, Teleport Gates, o dia do anúncio e o fim de jogo | pronto (etapas 1–4); etapas 40, 43 e 54 (mapa por exploração) com código, sem teste em servidor |
| `aincrad-bosses/` | **AincradBosses** — chefes com fases e habilidades, sala, Last Attack, liberação do andar, labirinto | pronto (etapas 5–9, 18–19: proteção contra abuso, guia, raid, editor); chefes de campo e de evento e informantes (etapas 41–42), reunião de estratégia (49), dungeons escondidas, chaves e mapas (52–53) com código, sem teste em servidor |
| `aincrad-items/` | **AincradItems** — cristais de cura, antídoto, teleporte, ressurreição e gravação | pronto (etapas 20–21) |
| `aincrad-life/` | **AincradLife** — profissões (proficiência, aprender antes de fazer, árvore), bancadas 3D com painel, culinária, pesca, ferreiro, alquimia, encomendas, barracas, familiares, casamento e estalagens | base pronta (etapa 22), as quatro profissões da etapa 23 e as encomendas, as barracas, os familiares e o casamento: etapas 22 a 25 e os contratos de escolta (48) com código, sem teste em servidor |
| `aincrad-reputation/` | **AincradReputation** — conquistas, títulos, cor do cursor, cidades fechadas, prisão, PvP por andar, o Monumento da Vida, recompensa por cabeça e túmulos | código pronto (etapas 26–27, 39, 46, 47 e 55: guildas de crime), sem teste em servidor |
| `aincrad-admin/` | **AincradAdmin** — registro das ações de administração (`/aincrad log`), Discord por webhook, ranking em site, backup diário com restauração e `/aincrad status` | etapas 28–30 com código, sem teste em servidor |
| `aincrad-skills/` | **AincradSkills** — proficiência por arma, Sword Skills ativas e passivas, atalhos e a skill única Duas Espadas | código pronto (etapas 34–36 e 56: skills únicas com critério secreto), sem teste em servidor |
| `aincrad-quests/` | **AincradQuests** — quests em etapas, diárias e semanais, quest de party, NPCs com diálogo e diário | código pronto (etapas 37–38), sem teste em servidor |
| `aincrad-world/` | **AincradWorld** — jornal semanal, calendário com estações e datas, dia e noite e clima por andar | código pronto (etapas 44, 57–59), sem teste em servidor |
| `aincrad-effects/` | **AincradEffects** — motor de efeitos do servidor e resource pack único | pronto (etapas 10–13): motor geral e integração com andares e chefes |

## Plano

O plano de desenvolvimento (cópia do documento em https://claude.ai/artifact/A23oCqBsYC54PhuHKenyhW, de 17/11/2026):

- [docs/plano/01-plano.md](docs/plano/01-plano.md) — plano geral dos plugins e das etapas
- [docs/plano/02-tarefas-bloco-1.md](docs/plano/02-tarefas-bloco-1.md) — tarefas do bloco 1 (core, rpg, economy, floors)
- [docs/plano/03-aincrad-life-detalhes.md](docs/plano/03-aincrad-life-detalhes.md) — detalhes do AincradLife

## Compilar

Em cada pasta (o `aincrad-social` compila junto com o `aincrad-ui` e o `aincrad-core`, de quem usa as APIs):

```bash
./gradlew build
```

O `.jar` sai em `build/libs/`.
