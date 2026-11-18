# AincradDuels

Duelos entre jogadores, como no anime, com aposta em Col, placar e ranking. Era parte do
AincradSocial e virou um plugin próprio. Precisa do **AincradCore**. Opcionais: AincradEconomy
(apostas), AincradFloors (duelo dentro da cidade), AincradReputation (o duelo não muda a cor do
cursor), AincradSkills (Sword Skills na cidade durante o duelo) e AincradUI (menu flutuante).

> Compila e passa nos testes de regras, mas **ainda não foi testado num servidor de verdade**.

## Como é um duelo

`/duelo` abre o menu: quem está por perto, depois o modo, depois a aposta. O desafio chega no chat
com os botões Aceitar e Recusar e expira em 30 s. Aceito, há uma contagem de 5 s e os golpes
passam a valer.

| Modo | Como acaba |
|---|---|
| `primeiro_golpe` (padrão) | Quem acerta o primeiro golpe vence. O golpe não tira vida. |
| `metade` | Vence quem deixar o outro com metade da vida ou menos. Ninguém morre. |
| `total` | Até a morte, **de verdade** — o aviso aparece no convite. `modo_total: false` desliga. |

- **Dentro de zona segura**: com o AincradFloors, os dois podem se ferir na cidade enquanto o duelo dura.
- **Cor do cursor**: com o AincradReputation, nenhum golpe do duelo muda a cor de ninguém.
- **Sword Skills**: com o AincradSkills, quem está em duelo usa skills dentro da cidade.
- Colegas de party, de guilda e aliados (AincradSocial) se acertam durante o duelo.
- Ninguém de fora acerta um duelista, e um duelista só acerta o oponente.
- Perde quem sai do servidor, quem desiste (`/duelo desistir`) e quem se afasta mais de `raio`
  blocos (30) do lugar onde o duelo começou.
- Passado o `tempo_limite_segundos` (180), vence quem tem a maior fração de vida; igual, empata.
- O resultado aparece no chat de quem está a até `raio_aviso` blocos.

## Aposta

Com o AincradEconomy, quem desafia escolhe um valor em Col. Cada um põe esse valor em custódia
ao aceitar; se um dos dois não tem o Col, o duelo não começa.

- O vencedor leva as duas apostas. No empate, cada um recebe a sua de volta.
- Se o servidor cair no meio de um duelo, as apostas voltam aos donos quando ele ligar.
- `aposta.maximo` limita o valor (0 desliga); `aposta.valores` são os botões do menu.

A aposta é só em Col. Não há aposta de itens.

## Placar e ranking

Vitórias, derrotas, empates, sequência de vitórias e a melhor sequência, gravados no banco do
core: valem para a rede inteira. `/duelo placar [jogador]` e `/duelo ranking`.

## Comandos

| Comando | O que faz |
|---|---|
| `/duelo` | Menu: quem, modo e aposta; o seu placar e o ranking |
| `/duelo <jogador> [primeiro_golpe\|metade\|total] [aposta]` | Desafia direto |
| `/duelo aceitar\|recusar [jogador]` | Responde ao desafio (sem nome, o mais recente) |
| `/duelo desistir` | Entrega o duelo em andamento |
| `/duelo placar [jogador]` · `/duelo ranking` | Placar e os que mais venceram |

O menu SAO do AincradSocial mostra "Duelar" ao clicar num membro da party ou num amigo online.

## API

`com.aincrad.duels.api.AincradDuelsAPI` (métodos estáticos; também no ServicesManager):
`isDueling(UUID)`, `inDuel(UUID, UUID)`, `challenge(Player, Player, modo, aposta)`, `getWins`,
`getLosses`, `getStreak`. Eventos: `DuelStartEvent` e `DuelEndEvent` (com vencedor, modo, motivo
e aposta).

## Compilar

```bash
./gradlew build
```

O jar sai em `build/libs/AincradDuels-0.1.0.jar`.
