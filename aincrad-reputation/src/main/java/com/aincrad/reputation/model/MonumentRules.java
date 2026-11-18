package com.aincrad.reputation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Regras do Monumento da Vida: a causa da morte, a ordem dos nomes, a busca e as páginas do mural. */
public final class MonumentRules {

    private MonumentRules() {
    }

    /** Do que o jogador morreu. */
    public enum Cause { MOB, CHEFE, JOGADOR, QUEDA, OUTRO }

    /**
     * Um nome do monumento.
     *
     * @param diedAt 0 = vivo; senão, quando o nome foi riscado (a primeira morte)
     * @param detail quem ou o quê (o mob, o chefe, o jogador), ou vazio
     * @param deaths quantas vezes já morreu
     */
    public record Entry(String uuid, String name, long joinedAt, long diedAt, Cause cause, String detail, int deaths) {

        public boolean struck() {
            return diedAt > 0;
        }
    }

    /**
     * A causa de uma morte.
     *
     * @param damage     nome da causa do Minecraft ({@code FALL}, {@code ENTITY_ATTACK}...), ou null
     * @param byPlayer   foi outro jogador
     * @param inBossFight o jogador estava numa luta de chefe
     * @param byEntity   o último dano veio de uma entidade
     */
    public static Cause cause(String damage, boolean byPlayer, boolean inBossFight, boolean byEntity) {
        if (byPlayer) {
            return Cause.JOGADOR;
        }
        if (inBossFight) {
            return Cause.CHEFE;
        }
        if (byEntity) {
            return Cause.MOB;
        }
        return "FALL".equals(damage) || "FLY_INTO_WALL".equals(damage) ? Cause.QUEDA : Cause.OUTRO;
    }

    /** Ordem do mural: por quando entrou, como os nomes foram gravados na pedra. */
    public static List<Entry> ordered(List<Entry> all) {
        List<Entry> out = new ArrayList<>(all);
        out.sort((a, b) -> a.joinedAt() != b.joinedAt() ? Long.compare(a.joinedAt(), b.joinedAt()) : a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /** Os nomes que contêm o texto buscado (sem diferenciar maiúsculas). Busca vazia = todos. */
    public static List<Entry> search(List<Entry> all, String query) {
        if (query == null || query.isBlank()) {
            return all;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (e.name().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    public static int pages(int total, int perPage) {
        return Math.max(1, (total + perPage - 1) / perPage);
    }

    /** Os nomes de uma página do mural (a página dá a volta). */
    public static List<Entry> page(List<Entry> all, int page, int perPage) {
        if (all.isEmpty()) {
            return List.of();
        }
        int p = Math.floorMod(page, pages(all.size(), perPage));
        return all.subList(p * perPage, Math.min(all.size(), (p + 1) * perPage));
    }

    public static int[] count(List<Entry> all) {
        int dead = 0;
        for (Entry e : all) {
            if (e.struck()) {
                dead++;
            }
        }
        return new int[] {all.size() - dead, dead};
    }
}
