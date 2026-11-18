package com.aincrad.quests.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.Set;

/** As contas das quests, sem servidor: progresso, renovação e requisitos. */
public final class QuestRules {

    private QuestRules() {
    }

    /** Onde o jogador está numa quest. */
    public enum State { ATIVA, FEITA }

    /**
     * O progresso de um jogador numa quest.
     *
     * @param stage  índice da etapa atual
     * @param count  quanto já fez da etapa atual
     * @param doneAt quando concluiu pela última vez (0 = nunca)
     * @param times  quantas vezes concluiu
     */
    public record Progress(State state, int stage, int count, long doneAt, int times) {

        public static Progress started(Progress before) {
            return new Progress(State.ATIVA, 0, 0, before == null ? 0 : before.doneAt(), before == null ? 0 : before.times());
        }
    }

    /** O que mudou depois de um avanço. */
    public enum Step { NADA, AVANCOU, ETAPA_CONCLUIDA, PRONTA }

    /** O resultado de um avanço: o progresso novo e o que aconteceu. */
    public record Advance(Progress progress, Step step) {
    }

    /**
     * Soma {@code amount} à etapa atual. Ao encher, passa para a próxima; na última, a quest fica
     * pronta para entregar (a etapa passa do fim).
     */
    public static Advance advance(QuestDef def, Progress p, int amount) {
        if (p == null || p.state() != State.ATIVA || p.stage() >= def.stages().size() || amount <= 0) {
            return new Advance(p, Step.NADA);
        }
        int need = def.stages().get(p.stage()).amount();
        int count = Math.min(need, p.count() + amount);
        if (count < need) {
            return new Advance(new Progress(State.ATIVA, p.stage(), count, p.doneAt(), p.times()), Step.AVANCOU);
        }
        int next = p.stage() + 1;
        return new Advance(new Progress(State.ATIVA, next, 0, p.doneAt(), p.times()),
                next >= def.stages().size() ? Step.PRONTA : Step.ETAPA_CONCLUIDA);
    }

    /** Põe a contagem da etapa num valor (para "coletar", que olha o inventário). */
    public static Advance setCount(QuestDef def, Progress p, int count) {
        if (p == null || p.state() != State.ATIVA || p.stage() >= def.stages().size()) {
            return new Advance(p, Step.NADA);
        }
        int need = def.stages().get(p.stage()).amount();
        if (count >= need) {
            return advance(def, new Progress(State.ATIVA, p.stage(), 0, p.doneAt(), p.times()), need);
        }
        int c = Math.max(0, count);
        return new Advance(new Progress(State.ATIVA, p.stage(), c, p.doneAt(), p.times()), c == p.count() ? Step.NADA : Step.AVANCOU);
    }

    /** Todas as etapas feitas: falta só entregar ao NPC. */
    public static boolean ready(QuestDef def, Progress p) {
        return p != null && p.state() == State.ATIVA && p.stage() >= def.stages().size();
    }

    public static Progress completed(Progress p, long now) {
        return new Progress(State.FEITA, 0, 0, now, (p == null ? 0 : p.times()) + 1);
    }

    /**
     * Uma quest concluída pode ser feita de novo? História nunca; diária no dia seguinte; semanal
     * na semana seguinte (segunda-feira), no fuso do servidor.
     */
    public static boolean renewed(QuestDef.Kind kind, long doneAt, long now, ZoneId zone) {
        if (kind == QuestDef.Kind.HISTORIA) {
            return false;
        }
        LocalDate done = Instant.ofEpochMilli(doneAt).atZone(zone).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        if (kind == QuestDef.Kind.DIARIA) {
            return today.isAfter(done);
        }
        int doneWeek = done.get(IsoFields.WEEK_BASED_YEAR) * 100 + done.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int thisWeek = today.get(IsoFields.WEEK_BASED_YEAR) * 100 + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return thisWeek > doneWeek;
    }

    /** Por que o jogador não pode pegar a quest (null = pode). */
    public enum Block { ATIVA, JA_FEITA, LEVEL, ANDAR, QUEST_ANTERIOR }

    /**
     * @param level     level do jogador
     * @param frontline o andar mais alto liberado
     * @param done      quests que o jogador já concluiu alguma vez
     */
    public static Block blocked(QuestDef def, Progress p, long now, ZoneId zone, int level, int frontline, Set<String> done) {
        if (p != null && p.state() == State.ATIVA) {
            return Block.ATIVA;
        }
        if (p != null && p.state() == State.FEITA && !renewed(def.kind(), p.doneAt(), now, zone)) {
            return Block.JA_FEITA;
        }
        if (level < def.minLevel()) {
            return Block.LEVEL;
        }
        if (def.floor() > 0 && frontline < def.floor()) {
            return Block.ANDAR;
        }
        if (def.after() != null && !done.contains(def.after())) {
            return Block.QUEST_ANTERIOR;
        }
        return null;
    }

    /** {@code "BREAD 3"} → {"BREAD", "3"}; {@code "rpg:espada"} → {"rpg:espada", "1"}. Null se inválido. */
    public static String[] item(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String[] p = spec.trim().split("\\s+");
        if (p.length > 2) {
            return null;
        }
        int amount = 1;
        if (p.length == 2) {
            try {
                amount = Integer.parseInt(p[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return amount < 1 || amount > 6400 ? null : new String[] {p[0], String.valueOf(amount)};
    }
}
