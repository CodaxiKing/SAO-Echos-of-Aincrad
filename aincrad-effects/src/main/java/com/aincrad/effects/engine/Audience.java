package com.aincrad.effects.engine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Quem vê um efeito: só o jogador, quem está num raio, o mundo ou o servidor todo. */
public record Audience(Kind kind, double radius) {

    public enum Kind { PLAYER, RADIUS, WORLD, SERVER }

    /** Raio usado quando "jogador" é pedido mas não há jogador (efeito tocado num local). */
    public static final double FALLBACK_RADIUS = 16;

    public static Audience player() {
        return new Audience(Kind.PLAYER, 0);
    }

    public static Audience radius(double r) {
        return new Audience(Kind.RADIUS, r);
    }

    /** jogador | raio:&lt;n&gt; | mundo | servidor. Valor inválido devolve {@code fallback}. */
    public static Audience parse(String raw, Audience fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("raio")) {
            String num = s.substring(4).replace(":", "").replace("=", "").trim();
            try {
                double r = Double.parseDouble(num);
                return r > 0 ? radius(r) : fallback;
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return switch (s) {
            case "jogador", "player", "alvo" -> player();
            case "mundo", "world" -> new Audience(Kind.WORLD, 0);
            case "servidor", "server", "todos" -> new Audience(Kind.SERVER, 0);
            default -> fallback;
        };
    }

    public List<Player> resolve(Location origin, Player target) {
        List<Player> out = new ArrayList<>();
        switch (kind) {
            case PLAYER -> {
                if (target != null) {
                    out.add(target);
                } else {
                    addNear(out, origin, FALLBACK_RADIUS);
                }
            }
            case RADIUS -> addNear(out, origin, radius);
            case WORLD -> {
                if (origin.getWorld() != null) {
                    out.addAll(origin.getWorld().getPlayers());
                }
            }
            case SERVER -> out.addAll(Bukkit.getOnlinePlayers());
        }
        return out;
    }

    private static void addNear(List<Player> out, Location origin, double r) {
        if (origin.getWorld() == null) {
            return;
        }
        double r2 = r * r;
        for (Player p : origin.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(origin) <= r2) {
                out.add(p);
            }
        }
    }

    @Override
    public String toString() {
        return switch (kind) {
            case PLAYER -> "jogador";
            case RADIUS -> "raio:" + (radius == Math.floor(radius) ? String.valueOf((long) radius) : String.valueOf(radius));
            case WORLD -> "mundo";
            case SERVER -> "servidor";
        };
    }
}
