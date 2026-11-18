package com.aincrad.social.guild;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Guildas em texto: todas juntas em guildas.yml (um servidor só) ou uma por vez, como documento
 * do banco do AincradCore (ver {@code CoreStorage}).
 */
public final class GuildStore {

    private final File file;
    private final Logger log;

    public GuildStore(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void load(GuildService service) {
        service.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection gs = c.getConfigurationSection("guildas");
        if (gs != null) {
            for (String key : gs.getKeys(false)) {
                ConfigurationSection s = gs.getConfigurationSection(key);
                UUID id = uuid(key);
                if (s == null || id == null) {
                    continue;
                }
                try {
                    service.restore(read(id, s));
                } catch (RuntimeException e) {
                    log.warning("Guilda " + key + " ignorada (dados inválidos): " + e.getMessage());
                }
            }
        }
        ConfigurationSection refunds = c.getConfigurationSection("reembolsos");
        if (refunds != null) {
            for (String k : refunds.getKeys(false)) {
                UUID p = uuid(k);
                if (p != null) {
                    service.addPendingRefund(p, refunds.getLong(k));
                }
            }
        }
        ConfigurationSection left = c.getConfigurationSection("saidas");
        if (left != null) {
            for (String k : left.getKeys(false)) {
                UUID p = uuid(k);
                if (p != null) {
                    service.restoreLeft(p, left.getLong(k));
                }
            }
        }
    }

    private static Guild read(UUID id, ConfigurationSection s) {
        Guild g = new Guild(id, s.getString("nome"), s.getString("tag"), s.getString("emblema", "⚔"), s.getLong("criada"));
        g.color1 = s.getString("cor1", g.color1);
        g.color2 = s.getString("cor2", g.color2);
        g.level = Math.max(1, s.getInt("nivel", 1));
        g.xp = s.getLong("xp");
        g.treasury = s.getLong("tesouro");
        g.taxPercent = s.getInt("taxa");
        g.recruit = Guild.Recruit.valueOf(s.getString("recrutamento", "CONVITE"));
        g.creationCost = s.getLong("custo");
        g.established = s.getBoolean("estabelecida");
        g.home = s.getString("casa");
        g.bossKills = s.getInt("chefes");
        g.lastAttacks = s.getInt("last_attacks");
        g.highestFloor = s.getInt("andar");
        g.vaultData = s.getString("cofre", "");
        ConfigurationSection ms = s.getConfigurationSection("membros");
        if (ms != null) {
            for (String k : ms.getKeys(false)) {
                UUID p = uuid(k);
                if (p == null) {
                    continue;
                }
                Guild.Member m = new Guild.Member(GuildRank.parse(ms.getString(k + ".cargo"), GuildRank.RECRUTA), ms.getLong(k + ".entrou"));
                m.lastSeen = ms.getLong(k + ".visto", m.joined);
                g.members.put(p, m);
            }
        }
        ConfigurationSection ps = s.getConfigurationSection("permissoes");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                try {
                    GuildPerm perm = GuildPerm.valueOf(k.toUpperCase());
                    if (!perm.fixed()) {
                        g.perms.put(perm, GuildRank.parse(ps.getString(k), perm.defaultMin()));
                    }
                } catch (IllegalArgumentException ignored) {
                    // permissão que não existe mais
                }
            }
        }
        s.getStringList("aliados").stream().map(GuildStore::uuid).filter(x -> x != null).forEach(g.allies::add);
        s.getStringList("propostas").stream().map(GuildStore::uuid).filter(x -> x != null).forEach(g.allyProposals::add);
        ConfigurationSection as = s.getConfigurationSection("candidaturas");
        if (as != null) {
            for (String k : as.getKeys(false)) {
                UUID p = uuid(k);
                if (p != null) {
                    g.applications.put(p, as.getLong(k));
                }
            }
        }
        ConfigurationSection is = s.getConfigurationSection("convites");
        if (is != null) {
            for (String k : is.getKeys(false)) {
                UUID p = uuid(k);
                if (p != null) {
                    g.invites.put(p, is.getLong(k));
                }
            }
        }
        for (Map<?, ?> m : s.getMapList("extrato")) {
            Object t = m.get("quando");
            Object amt = m.get("valor");
            g.ledger.add(new Guild.Ledger(t instanceof Number n ? n.longValue() : 0, uuid(String.valueOf(m.get("quem"))),
                    String.valueOf(m.get("nome")), String.valueOf(m.get("tipo")),
                    amt instanceof Number n ? n.longValue() : 0, String.valueOf(m.get("detalhe"))));
        }
        if (g.members.size() == 0) {
            throw new IllegalStateException("sem membros");
        }
        return g;
    }

    // ------------------------------------------------------------------ uma guilda por vez (banco)

    /** Uma guilda como texto. {@code withVault} false deixa os itens do cofre de fora. */
    public static String encode(Guild g, boolean withVault) {
        YamlConfiguration c = new YamlConfiguration();
        write(c, "", g, withVault);
        return c.saveToString();
    }

    /** O contrário de {@link #encode}. Lança RuntimeException se o texto não é uma guilda válida. */
    public static Guild decode(UUID id, String body) {
        YamlConfiguration c = new YamlConfiguration();
        try {
            c.loadFromString(body);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return read(id, c);
    }

    /** Quem tem convite desta guilda ainda sem resposta. */
    public static java.util.Set<UUID> invited(Guild g) {
        return new java.util.LinkedHashSet<>(g.invites.keySet());
    }

    /** Soma (ou tira) Col do tesouro, sem deixar negativo. */
    public static void addTreasury(Guild g, long delta) {
        g.treasury = Math.max(0, g.treasury + delta);
    }

    /** Põe (ou troca) a guilda no serviço. */
    public static void replace(GuildService service, Guild g) {
        service.replace(g);
    }

    public static void drop(GuildService service, UUID id) {
        service.drop(id);
    }

    /** Por jogador: {reembolso pendente, quando saiu da última guilda}. Só quem tem um dos dois. */
    public static Map<UUID, long[]> playerStates(GuildService service) {
        Map<UUID, long[]> out = new LinkedHashMap<>();
        service.pendingRefunds().forEach((p, v) -> out.computeIfAbsent(p, k -> new long[2])[0] = v);
        long cutoff = System.currentTimeMillis() - service.rules().rejoinCooldownMs();
        service.leftAt().forEach((p, t) -> {
            if (t > cutoff) {
                out.computeIfAbsent(p, k -> new long[2])[1] = t;
            }
        });
        return out;
    }

    /** Troca o estado de um jogador pelo que veio do banco (0 apaga). */
    public static void setPlayerState(GuildService service, UUID p, long refund, long leftAt) {
        if (refund > 0) {
            service.pendingRefunds().put(p, refund);
        } else {
            service.pendingRefunds().remove(p);
        }
        if (leftAt > 0) {
            service.leftAt().put(p, leftAt);
        } else {
            service.leftAt().remove(p);
        }
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Monta o YAML (na thread principal) para gravar em outra thread. */
    public String snapshot(GuildService service) {
        YamlConfiguration c = new YamlConfiguration();
        for (Guild g : service.all()) {
            write(c, "guildas." + g.id + ".", g, true);
        }
        service.pendingRefunds().forEach((p, v) -> c.set("reembolsos." + p, v));
        long cutoff = System.currentTimeMillis() - service.rules().rejoinCooldownMs();
        service.leftAt().forEach((p, t) -> {
            if (t > cutoff) {
                c.set("saidas." + p, t);
            }
        });
        return c.saveToString();
    }

    private static void write(YamlConfiguration c, String b, Guild g, boolean withVault) {
        c.set(b + "nome", g.name);
        c.set(b + "tag", g.tag);
        c.set(b + "emblema", g.emblem);
        c.set(b + "cor1", g.color1);
        c.set(b + "cor2", g.color2);
        c.set(b + "criada", g.created);
        c.set(b + "nivel", g.level);
        c.set(b + "xp", g.xp);
        c.set(b + "tesouro", g.treasury);
        c.set(b + "taxa", g.taxPercent);
        c.set(b + "recrutamento", g.recruit.name());
        c.set(b + "custo", g.creationCost);
        c.set(b + "estabelecida", g.established);
        c.set(b + "casa", g.home);
        c.set(b + "chefes", g.bossKills);
        c.set(b + "last_attacks", g.lastAttacks);
        c.set(b + "andar", g.highestFloor);
        if (withVault) {
            c.set(b + "cofre", g.vaultData);
        }
        g.members.forEach((p, m) -> {
            c.set(b + "membros." + p + ".cargo", m.rank.name());
            c.set(b + "membros." + p + ".entrou", m.joined);
            c.set(b + "membros." + p + ".visto", m.lastSeen);
        });
        g.perms.forEach((perm, r) -> c.set(b + "permissoes." + perm.key(), r.name()));
        c.set(b + "aliados", g.allies.stream().map(UUID::toString).toList());
        c.set(b + "propostas", g.allyProposals.stream().map(UUID::toString).toList());
        g.applications.forEach((p, t) -> c.set(b + "candidaturas." + p, t));
        g.invites.forEach((p, t) -> c.set(b + "convites." + p, t));
        List<Map<String, Object>> ledger = new ArrayList<>();
        for (Guild.Ledger l : g.ledger) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("quando", l.time());
            m.put("quem", String.valueOf(l.who()));
            m.put("nome", l.whoName());
            m.put("tipo", l.type());
            m.put("valor", l.amount());
            m.put("detalhe", l.detail());
            ledger.add(m);
        }
        c.set(b + "extrato", ledger);
    }

    public void write(String yaml) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), yaml);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.severe("Falha ao gravar " + file.getName() + ": " + e.getMessage());
        }
    }
}
