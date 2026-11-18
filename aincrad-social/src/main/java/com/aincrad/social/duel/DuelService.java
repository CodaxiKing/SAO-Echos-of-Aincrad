package com.aincrad.social.duel;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Desafios e duelos em andamento, em memória. Só lógica: quem chama cuida das mensagens, da
 * contagem e do dano.
 */
public final class DuelService {

    public enum Ask { SENT, SELF, BUSY, TARGET_BUSY, ALREADY }

    /** Um desafio ainda sem resposta. */
    public record Challenge(UUID from, UUID to, DuelMode mode, long expires) {
    }

    /** Um duelo aceito: em contagem até {@code startAt}, depois valendo até {@code endAt}. */
    public static final class Duel {
        private final UUID a;
        private final UUID b;
        private final DuelMode mode;
        private final long startAt;
        private final long endAt;

        Duel(UUID a, UUID b, DuelMode mode, long startAt, long endAt) {
            this.a = a;
            this.b = b;
            this.mode = mode;
            this.startAt = startAt;
            this.endAt = endAt;
        }

        /** Quem desafiou. */
        public UUID a() {
            return a;
        }

        /** Quem aceitou. */
        public UUID b() {
            return b;
        }

        public DuelMode mode() {
            return mode;
        }

        public long startAt() {
            return startAt;
        }

        public long endAt() {
            return endAt;
        }

        public boolean has(UUID p) {
            return a.equals(p) || b.equals(p);
        }

        public UUID opponent(UUID p) {
            return a.equals(p) ? b : b.equals(p) ? a : null;
        }

        /** A contagem acabou e os golpes já valem? */
        public boolean started(long now) {
            return now >= startAt;
        }

        public boolean timedOut(long now) {
            return now >= endAt;
        }
    }

    /** desafiado → (desafiante → desafio) */
    private final Map<UUID, Map<UUID, Challenge>> challenges = new HashMap<>();
    private final Map<UUID, Duel> active = new HashMap<>();
    private final LongSupplier clock;

    public DuelService(LongSupplier clock) {
        this.clock = clock;
    }

    public Ask challenge(UUID from, UUID to, DuelMode mode, long ttlMillis) {
        if (from.equals(to)) {
            return Ask.SELF;
        }
        if (active.containsKey(from)) {
            return Ask.BUSY;
        }
        if (active.containsKey(to)) {
            return Ask.TARGET_BUSY;
        }
        long now = clock.getAsLong();
        Map<UUID, Challenge> mine = challenges.computeIfAbsent(to, k -> new LinkedHashMap<>());
        Challenge old = mine.get(from);
        if (old != null && old.expires() > now && old.mode() == mode) {
            return Ask.ALREADY;
        }
        mine.remove(from); // um desafio novo (ou com outro modo) vai para o fim da fila
        mine.put(from, new Challenge(from, to, mode, now + ttlMillis));
        return Ask.SENT;
    }

    /**
     * O desafio que {@code to} tem de {@code from}, ainda válido. Com {@code from} null, o mais
     * recente. Não remove.
     */
    public Challenge pending(UUID to, UUID from) {
        Map<UUID, Challenge> mine = challenges.get(to);
        if (mine == null) {
            return null;
        }
        long now = clock.getAsLong();
        mine.values().removeIf(c -> c.expires() <= now);
        if (mine.isEmpty()) {
            challenges.remove(to);
            return null;
        }
        if (from != null) {
            return mine.get(from);
        }
        Challenge last = null;
        for (Challenge c : mine.values()) {
            last = c;
        }
        return last;
    }

    /** Recusa (ou retira) um desafio. @return o desafio que existia, ou null */
    public Challenge decline(UUID to, UUID from) {
        Challenge c = pending(to, from);
        if (c != null) {
            Map<UUID, Challenge> mine = challenges.get(to);
            mine.remove(c.from());
            if (mine.isEmpty()) {
                challenges.remove(to);
            }
        }
        return c;
    }

    /**
     * Aceita o desafio e começa a contagem. Os outros desafios dos dois caem.
     *
     * @return o duelo, ou null se um dos dois já está em outro
     */
    public Duel begin(Challenge c, long countdownMillis, long limitMillis) {
        if (active.containsKey(c.from()) || active.containsKey(c.to())) {
            return null;
        }
        long start = clock.getAsLong() + countdownMillis;
        Duel d = new Duel(c.from(), c.to(), c.mode(), start, start + limitMillis);
        active.put(c.from(), d);
        active.put(c.to(), d);
        forget(c.from());
        forget(c.to());
        return d;
    }

    /** Apaga os desafios feitos por e para o jogador (saiu do servidor, entrou num duelo). */
    public void forget(UUID p) {
        challenges.remove(p);
        challenges.values().forEach(m -> m.remove(p));
        challenges.values().removeIf(Map::isEmpty);
    }

    public Duel duelOf(UUID p) {
        return active.get(p);
    }

    /** Os dois estão no mesmo duelo (em contagem ou valendo)? */
    public boolean inDuel(UUID a, UUID b) {
        Duel d = active.get(a);
        return d != null && !a.equals(b) && d.has(b);
    }

    /** Encerra. @return false se já tinha acabado */
    public boolean end(Duel d) {
        boolean was = active.remove(d.a(), d) | active.remove(d.b(), d);
        return was;
    }

    public Set<Duel> duels() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(active.values()));
    }

    /**
     * O tempo acabou: vence quem tem a maior fração de vida.
     *
     * @return 1 se A vence, -1 se B vence, 0 se empatou
     */
    public static int byHealth(double healthA, double maxA, double healthB, double maxB) {
        double fa = maxA <= 0 ? 0 : healthA / maxA;
        double fb = maxB <= 0 ? 0 : healthB / maxB;
        if (Math.abs(fa - fb) < 0.01) {
            return 0;
        }
        return fa > fb ? 1 : -1;
    }
}
