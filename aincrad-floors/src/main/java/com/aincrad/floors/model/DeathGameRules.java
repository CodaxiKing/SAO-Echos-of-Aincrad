package com.aincrad.floors.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Regras do jogo da morte: as falas do dia do anúncio e o que acontece quando o último chefe cai. */
public final class DeathGameRules {

    private DeathGameRules() {
    }

    /**
     * Uma fala do discurso.
     *
     * @param atTicks quando aparece, em ticks desde o começo
     */
    public record Line(long atTicks, String title, String subtitle, String chat, String sound) {
    }

    /** O que fazer com o mundo depois da vitória. */
    public enum Outcome {
        /** O mundo continua aberto; o logout volta. */
        CONTINUAR,
        /** Nova temporada: os andares voltam a ser bloqueados e o anúncio pode ser feito de novo. */
        REINICIAR,
        /** A entrada fecha: depois de um prazo, só os admins entram. */
        FECHAR;

        public static Outcome parse(String s) {
            try {
                return s == null ? CONTINUAR : valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return CONTINUAR;
            }
        }
    }

    /** Lê a lista de falas ({@code em} em segundos). As sem texto nenhum ficam de fora. Saem em ordem de tempo. */
    public static List<Line> lines(List<Map<?, ?>> raw) {
        List<Line> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            double at = m.get("em") instanceof Number n ? n.doubleValue() : 0;
            String title = text(m.get("titulo"));
            String sub = text(m.get("subtitulo"));
            String chat = text(m.get("chat"));
            String sound = text(m.get("som"));
            if (title == null && sub == null && chat == null && sound == null) {
                continue;
            }
            out.add(new Line(Math.max(0, Math.round(at * 20)), title, sub, chat, sound));
        }
        out.sort((a, b) -> Long.compare(a.atTicks(), b.atTicks()));
        return out;
    }

    private static String text(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString();
    }

    /** Quanto dura o discurso: a última fala mais {@code tail} segundos. */
    public static long durationTicks(List<Line> lines, double tailSeconds) {
        long last = lines.isEmpty() ? 0 : lines.get(lines.size() - 1).atTicks();
        return last + Math.max(0, Math.round(tailSeconds * 20));
    }

    /**
     * O chefe deste andar era o último? {@code finalFloor} 0 = o último andar do floors.yml.
     */
    public static boolean isFinal(int clearedFloor, int finalFloor, int lastFloor) {
        int end = finalFloor > 0 ? finalFloor : lastFloor;
        return end > 0 && clearedFloor == end;
    }

    /** Os nomes dos créditos numa linha só, cortados em {@code max} com "e mais N". */
    public static String credits(List<String> names, int max, String more) {
        if (names.isEmpty()) {
            return "";
        }
        List<String> shown = names.size() <= max ? names : names.subList(0, max);
        String out = String.join(", ", shown);
        return names.size() <= max ? out : out + " " + more.replace("<n>", String.valueOf(names.size() - max));
    }
}
