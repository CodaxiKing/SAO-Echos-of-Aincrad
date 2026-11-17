package com.aincrad.bosses.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Uma raid marcada no quadro: chefe, líder, data e hora, vagas por função e os inscritos.
 * As regras (vagas, reserva da sala, leitura da data) ficam aqui, sem servidor.
 */
public final class Raid {

    /** Funções de uma raid. */
    public enum Role {
        TANQUE, DANO, SUPORTE;

        public static Role parse(String s) {
            try {
                return valueOf(String.valueOf(s).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Um inscrito. */
    public record Member(String name, Role role) {
    }

    private final long id;
    private final String boss;
    private final UUID leader;
    private final String leaderName;
    private final long at;
    private final int[] slots;
    private final Map<UUID, Member> members = new LinkedHashMap<>();

    public Raid(long id, String boss, UUID leader, String leaderName, long at, int tanks, int damage, int support) {
        this.id = id;
        this.boss = boss;
        this.leader = leader;
        this.leaderName = leaderName;
        this.at = at;
        this.slots = new int[]{Math.max(0, tanks), Math.max(0, damage), Math.max(0, support)};
    }

    public long id() {
        return id;
    }

    public String boss() {
        return boss;
    }

    public UUID leader() {
        return leader;
    }

    public String leaderName() {
        return leaderName;
    }

    /** Quando começa (milissegundos). */
    public long at() {
        return at;
    }

    public int slots(Role r) {
        return slots[r.ordinal()];
    }

    public Map<UUID, Member> members() {
        return members;
    }

    public int taken(Role r) {
        int n = 0;
        for (Member m : members.values()) {
            if (m.role() == r) {
                n++;
            }
        }
        return n;
    }

    /** Há vaga na função? (quem já está inscrito e troca de função libera a vaga antiga) */
    public boolean hasRoom(UUID player, Role r) {
        Member now = members.get(player);
        int taken = taken(r) - (now != null && now.role() == r ? 1 : 0);
        return taken < slots(r);
    }

    /** Inscreve ou troca de função. @return false se a função está cheia */
    public boolean join(UUID player, String name, Role r) {
        if (!hasRoom(player, r)) {
            return false;
        }
        members.put(player, new Member(name, r));
        return true;
    }

    public boolean leave(UUID player) {
        return members.remove(player) != null;
    }

    /** A sala está reservada para os inscritos agora? (de X minutos antes até o fim da duração) */
    public boolean reserved(long now, int minutesBefore, int durationMinutes) {
        return now >= at - minutesBefore * 60_000L && now < at + durationMinutes * 60_000L;
    }

    /** A raid já passou (começo + duração). */
    public boolean over(long now, int durationMinutes) {
        return now >= at + durationMinutes * 60_000L;
    }

    /**
     * Lê "dd/MM HH:mm" (ou "dd/MM/yyyy HH:mm", ou só "HH:mm" = hoje). Sem ano, vale a próxima vez
     * que essa data acontece.
     *
     * @return a data, ou null se não deu para ler
     */
    public static LocalDateTime parseWhen(String date, String time, LocalDateTime now) {
        try {
            String[] hm = time.trim().split(":");
            LocalTime t = LocalTime.of(Integer.parseInt(hm[0]), hm.length > 1 ? Integer.parseInt(hm[1]) : 0);
            if (date == null || date.isBlank()) {
                LocalDateTime today = LocalDateTime.of(now.toLocalDate(), t);
                return today.isBefore(now) ? today.plusDays(1) : today;
            }
            String[] dm = date.trim().split("/");
            int day = Integer.parseInt(dm[0]);
            int month = Integer.parseInt(dm[1]);
            if (dm.length > 2) {
                return LocalDateTime.of(LocalDate.of(Integer.parseInt(dm[2]), month, day), t);
            }
            LocalDateTime when = LocalDateTime.of(LocalDate.of(now.getYear(), month, day), t);
            return when.isBefore(now) ? when.plusYears(1) : when;
        } catch (RuntimeException e) {
            // DateTimeException, NumberFormatException, índice fora: data ilegível
            return null;
        }
    }
}
