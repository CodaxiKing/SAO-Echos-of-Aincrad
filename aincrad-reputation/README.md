# AincradReputation

Registra o que cada jogador fez e mostra isso aos outros: **conquistas**, **títulos** e a **cor do
cursor** (verde, laranja, vermelho), com as consequências dela — cidades fechadas, prisão e PvP
desligado por andar. Etapas 26 e 27 do plano.

Precisa do **AincradCore**. Opcionais: AincradFloors (andares, cidades e PvP por andar),
AincradBosses (conquistas de chefe), AincradLife (conquistas de pesca, familiar, profissão e
casamento; veneno em jogador), AincradItems (cristais bloqueados na prisão), LuckPerms (título
como prefixo).

> Compila e passa nos testes de regras, mas **ainda não foi testado num servidor de verdade**.

## Conquistas (`achievements.yml`)

Cada conquista é liberada por um acontecimento de outro plugin Aincrad:

| Tipo | Quando | Vem de |
|---|---|---|
| `primeiro_no_andar` | O primeiro jogador da rede a entrar num andar (não vale para o andar 1) | AincradFloors |
| `andar_visitado` | Qualquer jogador, na primeira vez num andar | AincradFloors |
| `chefe_primeira_vitoria` | Participou da primeira vitória sobre o chefe de um andar | AincradBosses |
| `chefe_derrotado` | Participou de uma vitória sobre um chefe | AincradBosses |
| `last_attack` | Deu o golpe final num chefe | AincradBosses |
| `peixe_lendario` | Pescou um lendário | AincradLife |
| `torneio_de_pesca` | Venceu o torneio semanal de pesca | AincradLife |
| `familiar_domado` | Domou um familiar | AincradLife |
| `profissao_completa` | Chegou a 1000 numa profissão | AincradLife |
| `casamento` | Casou | AincradLife |

- `andar:` e `profissao:` restringem a conquista; `por_andar: true` cria uma para cada andar
  ("Primeiro no Andar 5").
- `titulo:` é o título que ela libera; `anunciar: true` avisa o servidor.
- `/conquistas` mostra todas, as que o jogador tem em destaque.
- Um participante de chefe que está offline recebe a conquista do mesmo jeito, sem aviso.

## Títulos

- `/titulos` lista os títulos que o jogador ganhou; clicar escolhe o que fica em uso. O primeiro
  título ganho já entra em uso.
- O título aparece **antes do nome no chat** e **na lista de jogadores** (tab).
- **Sobre a cabeça**: o plugin usa os times do placar, que é o mesmo lugar onde o AincradSocial
  põe a tag da guilda. Por isso `nome.sobre_a_cabeca` vem em `auto`: o Reputation só mexe no nome
  sobre a cabeça quando o AincradSocial **não** está no servidor.
- Com `titulos.luckperms: true`, o plugin não mexe no chat: põe o título como prefixo do jogador
  no LuckPerms, e o seu plugin de chat mostra.

## Cor do cursor

| Cor | Como se ganha | Dura |
|---|---|---|
| Verde | Normal | — |
| Laranja | Atacou um jogador verde | 24 h (renova a cada ataque) |
| Vermelho | Matou um jogador verde | 48 h na primeira morte; cada morte já causada aumenta 50% |

- Atacar ou matar um laranja ou um vermelho **não muda** a cor de quem atacou.
- Só conta o golpe que de fato tirou vida: na zona segura do AincradFloors, onde ninguém se fere,
  ninguém muda de cor.
- Flecha conta para quem atirou. Veneno de alquimia em jogador conta como ataque.
- A cor aparece no nome na tab (e sobre a cabeça, na condição acima). `/reputacao [jogador]`
  mostra a cor, quanto falta, as mortes causadas, o título e as conquistas.
- **Cidades**: laranja e vermelho não entram nas zonas seguras — o plugin barra a passagem e o
  teleporte. Quem já está dentro (renasceu, entrou no servidor) pode sair.
- **PvP por andar**: `pvp.andares_sem_pvp` e `pvp.regioes_sem_pvp` desligam o PvP.

## Prisão

- Defina com a varinha do core: marque os dois cantos, fique **dentro** da seleção e use
  `/aincrad prisao definir`. Onde você está é onde os presos aparecem.
- Um **vermelho derrotado por outro jogador** vai preso por 30 minutos por morte que causou, até
  24 h. Ele renasce na prisão.
- Preso não sai da região, não se teleporta, não usa cristais e só usa os comandos de
  `prisao.comandos_permitidos`.
- Cumprido o tempo, é levado à chegada do andar 1 (ou ao spawn do mundo) e volta **laranja** por
  24 h. As mortes causadas continuam contando.

## Comandos

| Comando | O que faz | Permissão |
|---|---|---|
| `/titulos` | Escolhe o título em uso | todos |
| `/conquistas` | Lista as conquistas | todos |
| `/reputacao [jogador]` | Cor do cursor, tempo, mortes, título | todos |
| `/aincrad prisao definir` | Define a prisão com a seleção da varinha | `aincrad.admin` |
| `/aincrad reputacao limpar <jogador>` | Cursor verde, sem mortes, fora da prisão | `aincrad.admin` |
| `/aincrad titulo dar <jogador> <título>` | Dá um título avulso | `aincrad.admin` |
| `/aincrad conquista dar <jogador> <id>` | Dá uma conquista | `aincrad.admin` |

`aincrad.reputation.bypass`: não muda de cor e entra nas cidades com qualquer cor.

## API

`com.aincrad.reputation.api.AincradReputationAPI` (estático, também no ServicesManager):
`getCursorColor(Player)`, `getTitle(Player)`, `getKills(Player)`, `isJailed(Player)`,
`grantAchievement(Player, id)` e `exemptPvp(UUID, UUID, até)`.

Eventos: `AchievementGrantEvent` e `CursorColorChangeEvent`.

`exemptPvp` é para os duelos: o AincradSocial deve chamá-lo ao começar um duelo, para os dois se
ferirem sem mudar de cor. **Enquanto ele não chamar, um duelo deixa o atacante laranja.**

## Banco

`rep_players` (título em uso, laranja até, vermelho até, mortes, preso até), `rep_achievements`
(conquistas e os títulos que liberaram) e `rep_firsts` (quem foi o primeiro em cada coisa). Tudo no
banco do core, então vale para a rede.

## Escolhas diferentes do plano

- O título aparece pelo próprio plugin (chat e tab); o prefixo do LuckPerms é opcional, e não o
  único caminho, para o plugin funcionar sem ele.
- O marcador da cor é a cor do nome; não usa um efeito do AincradEffects.
- "Guardas" são uma barreira na entrada da cidade (a passagem é cancelada), não NPCs.
- O vermelho que vence o prazo sem ser preso volta direto a verde; só quem cumpre prisão volta
  laranja.
- A guilda de quem deu o Last Attack não ganha título por aqui: fica para a ligação com o
  AincradSocial.
- O tempo de prisão é em tempo real, conte o jogador online ou não.

## Compilar

```bash
./gradlew build
```

O jar sai em `build/libs/AincradReputation-0.1.0.jar`.
