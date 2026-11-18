package com.aincrad.bosses.model;

import org.bukkit.configuration.ConfigurationSection;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Regras dos chefes fora das salas (roaming.yml): o de campo fica numa região e volta de tempos em
 * tempos; o de evento aparece numa data marcada e some depois. Nenhum dos dois libera andar.
 */
public final class RoamingRules {

    private RoamingRules() {
    }

    /**
     * Um chefe de campo ou de evento.
     *
     * @param boss          id do chefe em bosses/*.yml
     * @param region        região do core onde ele aparece
     * @param spot          "mundo,x,y,z" (ou null = o meio da região)
     * @param intervalMs    campo: tempo para voltar depois de derrotado
     * @param date          evento: o dia do ano (null = chefe de campo)
     * @param time          evento: a hora em que aparece
     * @param durationMs    evento: quanto tempo fica
     * @param announce      avisa o servidor quando aparece
     * @param calendar      evento: id de uma data do calendário do AincradWorld (ou null)
     */
    public record Entry(String id, String boss, String region, String spot, long intervalMs, MonthDay date, LocalTime time,
                        long durationMs, boolean announce, String calendar) {

        public boolean event() {
            return date != null || calendar != null;
        }
    }

    /** A janela de um chefe de evento: de quando a quando ele pode estar no mundo. */
    public record Window(long start, long end) {

        public boolean contains(long now) {
            return now >= start && now < end;
        }
    }

    /** Lê as seções {@code campo} e {@code evento}. Linhas com erro ficam de fora e entram em {@code errors}. */
    public static Map<String, Entry> parse(ConfigurationSection root, List<String> errors) {
        Map<String, Entry> out = new LinkedHashMap<>();
        if (root == null) {
            return out;
        }
        section(root.getConfigurationSection("campo"), false, out, errors);
        section(root.getConfigurationSection("evento"), true, out, errors);
        return out;
    }

    private static void section(ConfigurationSection sec, boolean event, Map<String, Entry> out, List<String> errors) {
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            String id = key.toLowerCase(Locale.ROOT);
            if (s == null) {
                errors.add(key + ": não é uma seção");
                continue;
            }
            if (out.containsKey(id)) {
                errors.add(key + ": já existe em 'campo'");
                continue;
            }
            String region = s.getString("regiao");
            if (region == null || region.isBlank()) {
                errors.add(key + ": 'regiao' faltando");
                continue;
            }
            MonthDay date = null;
            LocalTime time = LocalTime.MIDNIGHT;
            String calendar = null;
            if (event) {
                calendar = s.getString("calendario");
                if (calendar != null && calendar.isBlank()) {
                    calendar = null;
                }
                try {
                    // Com "calendario", a "data" é opcional: só vale se o AincradWorld não estiver ligado.
                    if (calendar == null || s.getString("data") != null) {
                        date = MonthDay.parse("--" + s.getString("data", ""));
                    }
                    time = LocalTime.parse(s.getString("hora", "00:00"), DateTimeFormatter.ofPattern("H:mm"));
                } catch (DateTimeParseException e) {
                    errors.add(key + ": 'data' precisa ser MM-DD (12-24) e 'hora' HH:MM");
                    continue;
                }
            }
            String spot = s.getString("local");
            out.put(id, new Entry(id, s.getString("chefe", id).toLowerCase(Locale.ROOT), region.toLowerCase(Locale.ROOT),
                    spot == null || spot.isBlank() ? null : spot,
                    Math.max(60_000L, Math.round(s.getDouble("intervalo_horas", 6) * 3_600_000L)), date, time,
                    Math.max(60_000L, Math.round(s.getDouble("duracao_minutos", 60) * 60_000L)), s.getBoolean("avisar", true),
                    calendar == null ? null : calendar.toLowerCase(Locale.ROOT)));
        }
    }

    /** O chefe de campo pode aparecer? {@code nextAt} 0 = nunca apareceu. */
    public static boolean fieldDue(long now, long nextAt) {
        return now >= nextAt;
    }

    /**
     * A janela do evento que vale agora: a deste ano ou, se ela atravessa a virada (31/12 às 23h
     * por 2 horas), a que começou no ano passado e ainda não acabou. Null em 29/02 de ano comum.
     */
    public static Window window(Entry e, long now, ZoneId zone) {
        if (e.date() == null) {
            return null;
        }
        int year = Instant.ofEpochMilli(now).atZone(zone).getYear();
        Window last = windowOf(e, year - 1, zone);
        if (last != null && last.contains(now)) {
            return last;
        }
        return windowOf(e, year, zone);
    }

    private static Window windowOf(Entry e, int year, ZoneId zone) {
        try {
            if (!e.date().isValidYear(year)) {
                return null;
            }
            long start = LocalDate.of(year, e.date().getMonth(), e.date().getDayOfMonth()).atTime(e.time()).atZone(zone)
                    .toInstant().toEpochMilli();
            return new Window(start, start + e.durationMs());
        } catch (DateTimeException ex) {
            return null;
        }
    }

    /** A janela a partir do começo do dia que o calendário do AincradWorld deu para a data. */
    public static Window windowOn(Entry e, long dayStart) {
        long start = dayStart + e.time().toSecondOfDay() * 1000L;
        return new Window(start, start + e.durationMs());
    }

    /** O chefe de evento deve estar no mundo agora? Não, se já foi derrotado nesta janela. */
    public static boolean eventActive(Window w, long now, long lastKilled) {
        return w != null && w.contains(now) && lastKilled < w.start();
    }
}
