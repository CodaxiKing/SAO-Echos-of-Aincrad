package com.aincrad.core.util;

/** Durações em português curto: "2 h 5 min", "45 s", "3 d 4 h". */
public final class TimeFormat {

    private TimeFormat() {
    }

    /**
     * @param millis duração
     * @param parts  quantas unidades mostrar no máximo (1 = "2 h", 2 = "2 h 5 min")
     */
    public static String duration(long millis, int parts) {
        if (millis < 1000) {
            return "agora";
        }
        long s = millis / 1000;
        long[] values = {s / 86_400, (s / 3_600) % 24, (s / 60) % 60, s % 60};
        String[] units = {"d", "h", "min", "s"};
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < values.length && shown < parts; i++) {
            if (values[i] == 0 && shown == 0) {
                continue;
            }
            if (values[i] == 0) {
                break; // não mostra "2 h 0 min"
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(values[i]).append(' ').append(units[i]);
            shown++;
        }
        return sb.toString();
    }

    public static String duration(long millis) {
        return duration(millis, 2);
    }

    /** Lê "1d", "2h30m", "45s", "90" (segundos). @return milissegundos, ou -1 se inválido */
    public static long parse(String text) {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.isEmpty()) {
            return -1;
        }
        if (t.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(t) * 1000;
        }
        long total = 0;
        long num = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c)) {
                num = (num < 0 ? 0 : num * 10) + (c - '0');
                continue;
            }
            if (num < 0) {
                return -1;
            }
            long mult = switch (c) {
                case 'd' -> 86_400_000L;
                case 'h' -> 3_600_000L;
                case 'm' -> 60_000L;
                case 's' -> 1_000L;
                default -> -1;
            };
            if (mult < 0) {
                return -1;
            }
            total += num * mult;
            num = -1;
        }
        return num >= 0 ? -1 : total;
    }
}
