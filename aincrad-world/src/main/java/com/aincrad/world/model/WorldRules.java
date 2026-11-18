package com.aincrad.world.model;

import org.bukkit.configuration.ConfigurationSection;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** As regras do AincradWorld, sem servidor: jornal, calendário, dia e noite e clima. */
public final class WorldRules {

    private WorldRules() {
    }

    // ------------------------------------------------------------------ jornal

    /** A edição (semana ISO) de um instante: {@code "2026-W47"}. */
    public static String edition(long at, ZoneId zone) {
        LocalDate d = Instant.ofEpochMilli(at).atZone(zone).toLocalDate();
        return String.format(Locale.ROOT, "%d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** A segunda-feira (00:00) e a segunda seguinte de uma edição, em milissegundos. Null se o texto não é uma edição. */
    public static long[] week(String edition, ZoneId zone) {
        try {
            String[] p = edition.toUpperCase(Locale.ROOT).split("-W");
            if (p.length != 2) {
                return null;
            }
            int week = Integer.parseInt(p[1]);
            LocalDate monday = LocalDate.of(Integer.parseInt(p[0]), 1, 4)
                    .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week).with(java.time.DayOfWeek.MONDAY);
            if (week < 1 || !edition(monday.atStartOfDay(zone).toInstant().toEpochMilli(), zone).equalsIgnoreCase(edition)) {
                return null;
            }
            return new long[] {monday.atStartOfDay(zone).toInstant().toEpochMilli(),
                    monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()};
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    /** Divide as notícias em páginas de livro. */
    public static <T> List<List<T>> pages(List<T> items, int perPage) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i += perPage) {
            out.add(items.subList(i, Math.min(items.size(), i + perPage)));
        }
        return out;
    }

    /** Texto de notícia sem quebras e no tamanho da coluna. */
    public static String clip(String s, int max) {
        String t = s == null ? "" : s.replaceAll("[\\r\\n\\t]+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    // ------------------------------------------------------------------ calendário

    /**
     * Uma data marcada.
     *
     * @param real   dia do ano de verdade (24/12), ou null
     * @param season estação em que cai (quando {@code real} é null)
     * @param day    dia da estação, a partir de 1
     */
    public record DateDef(String id, String name, MonthDay real, String season, int day) {
    }

    /**
     * O calendário: as estações giram a partir de {@code start}, cada uma com
     * {@code daysPerSeason} dias de verdade.
     */
    public record Calendar(LocalDate start, int daysPerSeason, List<String> seasons, Map<String, DateDef> dates) {

        private long daysSince(LocalDate today) {
            return Math.max(0, ChronoUnit.DAYS.between(start, today));
        }

        public String season(LocalDate today) {
            return seasons.get((int) ((daysSince(today) / daysPerSeason) % seasons.size()));
        }

        /** O dia dentro da estação, a partir de 1. */
        public int seasonDay(LocalDate today) {
            return (int) (daysSince(today) % daysPerSeason) + 1;
        }

        /** O ano de Aincrad, a partir de 1: uma volta completa das estações. */
        public int year(LocalDate today) {
            return (int) (daysSince(today) / ((long) daysPerSeason * seasons.size())) + 1;
        }

        public boolean isDate(String id, LocalDate today) {
            DateDef d = dates.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
            if (d == null) {
                return false;
            }
            if (d.real() != null) {
                return d.real().equals(MonthDay.from(today));
            }
            return d.season().equals(season(today)) && d.day() == seasonDay(today);
        }

        /** As datas que caem hoje. */
        public List<DateDef> today(LocalDate today) {
            List<DateDef> out = new ArrayList<>();
            for (DateDef d : dates.values()) {
                if (isDate(d.id(), today)) {
                    out.add(d);
                }
            }
            return out;
        }

        /** O dia (de hoje em diante, até dois anos) em que a data cai. Null se não existe ou não cai. */
        public LocalDate next(String id, LocalDate today) {
            if (!dates.containsKey(id == null ? "" : id.toLowerCase(Locale.ROOT))) {
                return null;
            }
            for (int i = 0; i <= 732; i++) {
                LocalDate d = today.plusDays(i);
                if (isDate(id, d)) {
                    return d;
                }
            }
            return null;
        }
    }

    /** Lê o calendar.yml. Datas com erro ficam de fora e entram em {@code errors}. */
    public static Calendar calendar(ConfigurationSection y, List<String> errors) {
        LocalDate start;
        try {
            start = LocalDate.parse(y.getString("inicio", "2026-01-01"));
        } catch (DateTimeParseException e) {
            errors.add("'inicio' precisa ser AAAA-MM-DD; usando 2026-01-01");
            start = LocalDate.of(2026, 1, 1);
        }
        List<String> seasons = new ArrayList<>();
        for (String s : y.getStringList("estacoes")) {
            if (!s.isBlank()) {
                seasons.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (seasons.isEmpty()) {
            seasons = List.of("primavera", "verao", "outono", "inverno");
        }
        int days = Math.max(1, Math.min(366, y.getInt("dias_por_estacao", 7)));
        Map<String, DateDef> dates = new LinkedHashMap<>();
        ConfigurationSection sec = y.getConfigurationSection("datas");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                String id = key.toLowerCase(Locale.ROOT);
                if (s == null) {
                    errors.add("data " + key + ": não é uma seção");
                    continue;
                }
                String name = s.getString("nome", key);
                String season = s.getString("estacao");
                if (season == null) {
                    try {
                        dates.put(id, new DateDef(id, name, MonthDay.parse("--" + s.getString("dia", "")), null, 0));
                    } catch (DateTimeParseException e) {
                        errors.add("data " + key + ": sem 'estacao', o 'dia' precisa ser MM-DD (12-24)");
                    }
                    continue;
                }
                int day = s.getInt("dia", 1);
                if (!seasons.contains(season.toLowerCase(Locale.ROOT)) || day < 1 || day > days) {
                    errors.add("data " + key + ": estação desconhecida, ou 'dia' fora de 1 a " + days);
                    continue;
                }
                dates.put(id, new DateDef(id, name, null, season.toLowerCase(Locale.ROOT), day));
            }
        }
        return new Calendar(start, days, List.copyOf(seasons), dates);
    }

    // ------------------------------------------------------------------ dia e noite

    /** É noite no mundo? (do pôr ao nascer do sol, pelo relógio do Minecraft) */
    public static boolean night(long worldTime) {
        long t = Math.floorMod(worldTime, 24000L);
        return t >= 13000 && t < 23000;
    }

    /** O level do mob à noite. */
    public static int nightLevel(int level, double times) {
        return Math.max(level, (int) Math.round(level * Math.max(1, times)));
    }

    /**
     * Quantos itens a mais um drop rende com o multiplicador: a parte inteira é certa e o resto é
     * a chance de mais um ({@code 1.5} com 4 itens = +2).
     */
    public static int extra(int amount, double times, double roll) {
        double more = amount * (Math.max(1, times) - 1);
        int whole = (int) Math.floor(more);
        return whole + (roll < more - whole ? 1 : 0);
    }

    // ------------------------------------------------------------------ clima

    /** O clima de um andar. */
    public enum Weather { LIMPO, CHUVA, NEVE, NEVOA }

    /** As chances (somam no máximo 1; o que sobra é tempo limpo). */
    public record Chances(double rain, double snow, double fog) {

        public static final Chances NONE = new Chances(0, 0, 0);

        public Weather roll(double r) {
            if (r < rain) {
                return Weather.CHUVA;
            }
            if (r < rain + snow) {
                return Weather.NEVE;
            }
            return r < rain + snow + fog ? Weather.NEVOA : Weather.LIMPO;
        }
    }

    private static Chances chances(ConfigurationSection s) {
        if (s == null) {
            return null;
        }
        double rain = Math.max(0, s.getDouble("chuva", 0));
        double snow = Math.max(0, s.getDouble("neve", 0));
        double fog = Math.max(0, s.getDouble("nevoa", 0));
        double sum = rain + snow + fog;
        return sum <= 1 ? new Chances(rain, snow, fog) : new Chances(rain / sum, snow / sum, fog / sum);
    }

    /**
     * As chances de um andar numa estação: {@code andares.<n>.<estacao>}, senão
     * {@code andares.<n>.padrao}, senão {@code padrao}.
     */
    public static Chances chances(ConfigurationSection weather, int floor, String season) {
        if (weather == null) {
            return Chances.NONE;
        }
        ConfigurationSection f = weather.getConfigurationSection("andares." + floor);
        Chances c = f == null ? null : chances(f.getConfigurationSection(season));
        if (c == null && f != null) {
            c = chances(f.getConfigurationSection("padrao"));
        }
        if (c == null) {
            c = chances(weather.getConfigurationSection("padrao"));
        }
        return c == null ? Chances.NONE : c;
    }
}
