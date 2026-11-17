package com.aincrad.life.inn;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Uma estalagem de inns.yml e as regras de aluguel (sem servidor).
 *
 * @param server servidor da rede onde ela fica ("" = este)
 * @param price  Col por noite (um dia real)
 * @param rooms  id do quarto → quarto
 */
public record Inn(String id, String name, int floor, String server, long price, Map<String, Room> rooms) {

    /** Maior aluguel de uma vez, em dias reais. */
    public static final int MAX_DAYS = 7;
    public static final long DAY = 24L * 60 * 60 * 1000;

    /**
     * Um quarto.
     *
     * @param region região do AincradCore que delimita o quarto (com a porta, o baú e a cama)
     * @param bed    onde o hóspede aparece: "mundo,x,y,z,yaw,pitch" (null = meio da região)
     */
    public record Room(String id, String region, String bed) {
    }

    /** Um quarto alugado. */
    public static final class Rental {
        public final String inn;
        public final String room;
        public final UUID tenant;
        public final String tenantName;
        public long until;
        public final Set<UUID> guests = new LinkedHashSet<>();

        public Rental(String inn, String room, UUID tenant, String tenantName, long until, String guests) {
            this.inn = inn;
            this.room = room;
            this.tenant = tenant;
            this.tenantName = tenantName;
            this.until = until;
            if (guests != null) {
                for (String g : guests.split(",")) {
                    try {
                        if (!g.isBlank()) {
                            this.guests.add(UUID.fromString(g.trim()));
                        }
                    } catch (IllegalArgumentException e) {
                        // lixo no banco: ignora
                    }
                }
            }
        }

        public boolean active(long now) {
            return until > now;
        }

        /** O hóspede e quem ele convidou abrem a porta e o baú. */
        public boolean canUse(UUID player, long now) {
            return active(now) && player != null && (player.equals(tenant) || guests.contains(player));
        }

        public String guestsText() {
            StringBuilder b = new StringBuilder();
            for (UUID g : guests) {
                if (!b.isEmpty()) {
                    b.append(',');
                }
                b.append(g);
            }
            return b.toString();
        }

        /** Falta uma hora ou menos (e ainda não acabou)? */
        public boolean endingSoon(long now) {
            return active(now) && until - now <= 60L * 60 * 1000;
        }
    }

    public static Inn parse(String id, ConfigurationSection s, List<String> errors) {
        Map<String, Room> rooms = new LinkedHashMap<>();
        ConfigurationSection rs = s.getConfigurationSection("quartos");
        if (rs != null) {
            for (String rid : rs.getKeys(false)) {
                ConfigurationSection r = rs.getConfigurationSection(rid);
                String region = r == null ? null : r.getString("regiao");
                if (region == null || region.isBlank()) {
                    errors.add("estalagem " + id + ": o quarto " + rid + " precisa de \"regiao\".");
                    continue;
                }
                String bed = r.getString("cama");
                rooms.put(rid.toLowerCase(Locale.ROOT), new Room(rid.toLowerCase(Locale.ROOT), region.toLowerCase(Locale.ROOT),
                        bed == null || bed.isBlank() ? null : bed));
            }
        }
        if (rooms.isEmpty()) {
            errors.add("estalagem " + id + ": nenhum quarto válido.");
        }
        return new Inn(id.toLowerCase(Locale.ROOT), s.getString("nome", id), Math.max(1, s.getInt("andar", 1)),
                s.getString("servidor", ""), Math.max(0, s.getLong("preco_noite", 50)), rooms);
    }

    public boolean isOn(String thisServer) {
        return server == null || server.isEmpty() || server.equalsIgnoreCase(thisServer);
    }

    /** Preço de {@code days} noites. */
    public long cost(int days) {
        return price * clampDays(days);
    }

    public static int clampDays(int days) {
        return Math.max(1, Math.min(MAX_DAYS, days));
    }

    /**
     * Até quando vai o aluguel depois de pagar {@code days}: a partir de agora, ou somado ao que
     * ainda resta — mas nunca mais que {@link #MAX_DAYS} dias à frente.
     */
    public static long extend(long currentUntil, long now, int days) {
        long from = Math.max(now, currentUntil);
        return Math.min(now + MAX_DAYS * DAY, from + clampDays(days) * DAY);
    }
}
