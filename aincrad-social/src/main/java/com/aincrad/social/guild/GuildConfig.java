package com.aincrad.social.guild;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** guilds.yml: as regras do serviço e os valores que só a camada do servidor usa. */
public final class GuildConfig {

    private static final long DAY = 86_400_000L;

    public final GuildRules rules;
    public final int minFloor;
    public final String quest;
    public final int minLevel;
    public final long cost;
    public final int homeWarmupSeconds;
    public final boolean friendlyFire;
    public final String chatPrefix;
    public final long xpBoss;
    public final long xpLastAttack;
    public final long xpQuest;
    public final long xpMob;
    public final int xpMobDailyCap;

    public GuildConfig(YamlConfiguration c) {
        List<GuildRules.Level> levels = new ArrayList<>();
        for (Map<?, ?> m : c.getMapList("niveis")) {
            levels.add(new GuildRules.Level(num(m, "nivel", levels.size() + 1), num(m, "xp", 0),
                    num(m, "vagas", 10), Math.max(9, Math.min(54, num(m, "cofre", 9) / 9 * 9))));
        }
        if (levels.isEmpty()) {
            levels.addAll(GuildRules.defaults().levels());
        }
        rules = new GuildRules(
                c.getInt("nome.minimo", 3), c.getInt("nome.maximo", 24),
                c.getInt("tag.minimo", 2), c.getInt("tag.maximo", 4),
                c.getStringList("palavras_bloqueadas"),
                c.getStringList("emblemas").isEmpty() ? GuildRules.defaults().emblems() : c.getStringList("emblemas"),
                Math.max(1, c.getInt("criacao.membros_minimos", 3)),
                (long) (c.getDouble("criacao.prazo_dias", 7) * DAY),
                Math.max(0, Math.min(1, c.getDouble("criacao.reembolso", 0.5))),
                (long) (c.getDouble("recruta_dias", 3) * DAY),
                (long) (c.getDouble("lider_inativo_dias", 30) * DAY),
                (long) (c.getDouble("espera_nova_horas", 24) * 3_600_000L),
                c.getLong("convite_expira_segundos", 120) * 1000L,
                c.getInt("cofre.retiradas_membro_por_dia", 16),
                c.getInt("tesouro.taxa_maxima", 10),
                c.getInt("aliancas.maximo", 3), c.getInt("aliancas.nivel_minimo", 3),
                c.getInt("uniforme.nivel_minimo", 2), c.getInt("casa.nivel_minimo", 2),
                c.getInt("extrato_maximo", 200),
                levels);
        minFloor = c.getInt("criacao.andar_minimo", 3);
        quest = c.getString("criacao.quest", "");
        minLevel = c.getInt("criacao.level_minimo", 10);
        cost = Math.max(0, c.getLong("criacao.custo", 5000));
        homeWarmupSeconds = Math.max(0, c.getInt("casa.espera_segundos", 3));
        friendlyFire = c.getBoolean("fogo_amigo", false);
        chatPrefix = c.getString("chat_prefixo", "#");
        xpBoss = c.getLong("xp.chefe", 100);
        xpLastAttack = c.getLong("xp.last_attack", 50);
        xpQuest = c.getLong("xp.quest", 20);
        xpMob = c.getLong("xp.mob", 1);
        xpMobDailyCap = c.getInt("xp.mob_limite_dia", 200);
    }

    private static int num(Map<?, ?> m, String k, int def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.intValue() : def;
    }
}
