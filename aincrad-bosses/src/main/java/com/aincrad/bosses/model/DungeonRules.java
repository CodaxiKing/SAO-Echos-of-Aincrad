package com.aincrad.bosses.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Regras das dungeons escondidas (dungeons.yml): leitura do arquivo e as contas da armadilha. */
public final class DungeonRules {

    private DungeonRules() {
    }

    /**
     * Uma dungeon escondida.
     *
     * @param region      a região do core (a sala inteira)
     * @param chest       "mundo,x,y,z" do baú-armadilha (null = ainda não definido)
     * @param locked      só entra quem usou uma chave (ou está na party de quem usou)
     * @param antiCrystal nenhum cristal funciona com a armadilha disparada
     * @param waves       quantas levas de mobs o alarme chama
     * @param perWave     mobs por leva
     * @param waveMs      tempo entre uma leva e outra
     * @param limitMs     o alarme para sozinho depois disto, vençam ou não
     * @param cooldownMs  tempo até a armadilha armar de novo
     * @param accessMs    quanto vale uma chave usada
     */
    public record Def(String id, String name, String region, String chest, boolean locked, boolean antiCrystal, List<String> mobs,
                      int level, int waves, int perWave, long waveMs, long limitMs, long cooldownMs, long accessMs, List<String> loot) {
    }

    /** Como a armadilha acabou. */
    public enum End { VENCERAM, TEMPO, TODOS_MORRERAM }

    /** Lê a seção {@code masmorras}. As com erro ficam de fora e entram em {@code errors}. */
    public static Map<String, Def> parse(ConfigurationSection root, List<String> errors) {
        Map<String, Def> out = new LinkedHashMap<>();
        if (root == null) {
            return out;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            String region = s == null ? null : s.getString("regiao");
            if (region == null || region.isBlank()) {
                errors.add(key + ": 'regiao' faltando");
                continue;
            }
            List<String> mobs = new ArrayList<>();
            for (String m : s.getStringList("mobs")) {
                if (!m.isBlank()) {
                    mobs.add(m.trim().toUpperCase(Locale.ROOT));
                }
            }
            if (mobs.isEmpty()) {
                mobs.add("ZOMBIE");
            }
            String chest = s.getString("bau");
            out.put(key.toLowerCase(Locale.ROOT), new Def(key.toLowerCase(Locale.ROOT), s.getString("nome", key), region.toLowerCase(Locale.ROOT),
                    chest == null || chest.isBlank() ? null : chest, s.getBoolean("trancada", false), s.getBoolean("anti_cristal", true),
                    List.copyOf(mobs), clamp(s.getInt("level", 1), 1, 1000), clamp(s.getInt("levas", 3), 1, 20),
                    clamp(s.getInt("mobs_por_leva", 5), 1, 40), clamp(s.getInt("intervalo_segundos", 15), 1, 600) * 1000L,
                    clamp(s.getInt("tempo_limite_segundos", 180), 10, 3600) * 1000L,
                    clamp(s.getInt("recarga_minutos", 60), 0, 100_000) * 60_000L,
                    clamp(s.getInt("chave_minutos", 30), 1, 100_000) * 60_000L, List.copyOf(s.getStringList("recompensa"))));
        }
        return out;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** A armadilha pode disparar? (não está ativa e a recarga passou) */
    public static boolean armed(boolean active, long lastAt, long now, long cooldownMs) {
        return !active && now - lastAt >= cooldownMs;
    }

    /** Quantas levas já deviam ter saído, contando a primeira (que sai na hora). */
    public static int wavesDue(long startedAt, long now, long waveMs, int waves) {
        return (int) Math.min(waves, (now - startedAt) / Math.max(1, waveMs) + 1);
    }

    /**
     * A armadilha acabou? Null enquanto dura.
     *
     * @param spawned levas que já saíram
     * @param alive   mobs do alarme ainda vivos
     * @param inside  jogadores vivos na sala
     */
    public static End ended(int spawned, int waves, int alive, int inside, long startedAt, long now, long limitMs) {
        if (inside <= 0) {
            return End.TODOS_MORRERAM;
        }
        if (spawned >= waves && alive <= 0) {
            return End.VENCERAM;
        }
        return now - startedAt >= limitMs ? End.TEMPO : null;
    }

    /** O jogador pode entrar? Sala aberta, ou acesso de chave ainda valendo. */
    public static boolean mayEnter(boolean locked, long accessUntil, long now) {
        return !locked || accessUntil > now;
    }
}
