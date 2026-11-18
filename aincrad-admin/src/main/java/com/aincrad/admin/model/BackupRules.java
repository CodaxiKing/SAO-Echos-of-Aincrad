package com.aincrad.admin.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Regras do backup: nome das pastas, o que entra, quando o diário roda e o que já venceu. */
public final class BackupRules {

    /** Arquivos maiores que isto ficam de fora (não são config nem dados de plugin). */
    public static final long MAX_FILE = 20L * 1024 * 1024;

    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");
    private static final Pattern VALID = Pattern.compile("\\d{4}-\\d{2}-\\d{2}_\\d{4}(-\\d{1,3})?");
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Set<String> EXTENSIONS = Set.of("yml", "yaml", "json");
    /** Pastas que não são dados: os próprios backups, o resource pack gerado e caches. */
    private static final Set<String> SKIP_DIRS = Set.of("backups", "pack", "resourcepack", "cache", "logs");

    private BackupRules() {
    }

    /** Nome da pasta de um backup feito neste instante: {@code 2026-11-18_0400}. */
    public static String name(LocalDateTime at) {
        return NAME.format(at);
    }

    /** O nome, ou o nome com {@code -2}, {@code -3}... se já existe um do mesmo minuto. */
    public static String unique(String base, Collection<String> existing) {
        if (!existing.contains(base)) {
            return base;
        }
        int n = 2;
        while (existing.contains(base + "-" + n)) {
            n++;
        }
        return base + "-" + n;
    }

    public static boolean valid(String name) {
        return name != null && VALID.matcher(name).matches() && date(name) != null;
    }

    /** O dia do backup, tirado do nome da pasta. Null se o nome não é de backup. */
    public static LocalDate date(String name) {
        if (name == null || name.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(name.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * A pasta que o admin pediu: o nome inteiro, ou só o dia ({@code 2026-11-18}), que vale pelo
     * backup mais novo daquele dia. Null se não existe.
     */
    public static String resolve(Collection<String> names, String arg) {
        if (arg == null) {
            return null;
        }
        if (valid(arg)) {
            return names.contains(arg) ? arg : null;
        }
        if (!DAY.matcher(arg).matches()) {
            return null;
        }
        String best = null;
        for (String n : names) {
            if (valid(n) && n.startsWith(arg + "_") && (best == null || compare(n, best) > 0)) {
                best = n;
            }
        }
        return best;
    }

    /** Ordem do mais velho para o mais novo ({@code _0400} antes de {@code _0400-2}). */
    public static int compare(String a, String b) {
        int c = a.substring(0, 15).compareTo(b.substring(0, 15));
        if (c != 0) {
            return c;
        }
        return Integer.compare(suffix(a), suffix(b));
    }

    private static int suffix(String name) {
        return name.length() > 16 ? Integer.parseInt(name.substring(16)) : 1;
    }

    /** As pastas com mais de {@code keepDays} dias. 0 = guarda para sempre. */
    public static List<String> expired(Collection<String> names, LocalDate today, int keepDays) {
        List<String> out = new ArrayList<>();
        if (keepDays <= 0) {
            return out;
        }
        LocalDate limit = today.minusDays(keepDays);
        for (String n : names) {
            LocalDate d = valid(n) ? date(n) : null;
            if (d != null && !d.isAfter(limit)) {
                out.add(n);
            }
        }
        return out;
    }

    /** O backup diário roda quando já passou da hora marcada e ainda não há backup de hoje. */
    public static boolean dailyDue(Collection<String> names, LocalDateTime now, LocalTime at) {
        if (now.toLocalTime().isBefore(at)) {
            return false;
        }
        LocalDate today = now.toLocalDate();
        for (String n : names) {
            if (valid(n) && today.equals(date(n))) {
                return false;
            }
        }
        return true;
    }

    /** Lê {@code "04:00"}. Null se inválido. */
    public static LocalTime time(String text) {
        try {
            return text == null ? null : LocalTime.parse(text.trim(), DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** O arquivo entra no backup? Só YAML e JSON de tamanho razoável (o banco SQLite é copiado à parte). */
    public static boolean include(String fileName, long size) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && size <= MAX_FILE && EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static boolean skipDir(String dirName) {
        return SKIP_DIRS.contains(dirName.toLowerCase(Locale.ROOT));
    }

    /** {@code 1536} → {@code "1,5 KB"}. */
    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB"};
        double v = bytes;
        int i = -1;
        do {
            v /= 1024;
            i++;
        } while (v >= 1024 && i < units.length - 1);
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',') + " " + units[i];
    }
}
