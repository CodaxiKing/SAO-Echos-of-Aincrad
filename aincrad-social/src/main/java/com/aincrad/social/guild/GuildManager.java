package com.aincrad.social.guild;

import com.aincrad.social.AincradSocialPlugin;
import com.aincrad.social.Names;
import com.aincrad.social.api.event.GuildAllianceEvent;
import com.aincrad.social.api.event.GuildCreateEvent;
import com.aincrad.social.api.event.GuildJoinEvent;
import com.aincrad.social.api.event.GuildLeaveEvent;
import com.aincrad.social.api.event.GuildLevelUpEvent;
import com.aincrad.social.api.event.GuildRankChangeEvent;
import com.aincrad.social.config.Messages;
import com.aincrad.social.hook.Hooks;
import com.aincrad.social.service.MessageService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tudo que um jogador faz na guilda, com as mensagens, sons, Col e eventos. Usado pelos comandos
 * e pelo menu SAO, para os dois fazerem exatamente a mesma coisa.
 */
public final class GuildManager {

    private static final Sound POSITIVE = Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.MASTER, 0.7f, 1.2f);
    private static final Sound NOTIFY = Sound.sound(Key.key("minecraft:block.note_block.chime"), Sound.Source.MASTER, 0.8f, 1.4f);
    private static final Sound LEVEL_UP = Sound.sound(Key.key("minecraft:ui.toast.challenge_complete"), Sound.Source.MASTER, 0.8f, 1f);
    private static final TextColor ALLY = TextColor.color(0x55AAFF);
    private static final TextColor SAME = TextColor.color(0x55FF7F);
    private static final TextColor RED = TextColor.color(0xFF4040);
    private static final String TEAM_PREFIX = "aincg_";

    private final AincradSocialPlugin plugin;
    private final Map<UUID, BukkitTask> homeWarmups = new HashMap<>();
    private final Map<UUID, long[]> mobXp = new HashMap<>();
    private final NamespacedKey uniformKey;
    private final NamespacedKey uniformArmorKey;

    public GuildManager(AincradSocialPlugin plugin) {
        this.plugin = plugin;
        this.uniformKey = new NamespacedKey(plugin, "uniforme_guilda");
        this.uniformArmorKey = new NamespacedKey(plugin, "uniforme_cosmetico");
    }

    private GuildService svc() {
        return plugin.guilds();
    }

    private GuildConfig cfg() {
        return plugin.guildConfig();
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Hooks hooks() {
        return plugin.hooks();
    }

    private String name(UUID id) {
        return plugin.names().name(id);
    }

    private static TagResolver who(String n) {
        return Placeholder.unparsed("jogador", n);
    }

    private static TagResolver guild(Guild g) {
        return TagResolver.resolver(Placeholder.unparsed("guilda", g.name()), Placeholder.unparsed("tag", g.tag()));
    }

    private void fire(Event e) {
        Bukkit.getPluginManager().callEvent(e);
    }

    private void changed() {
        plugin.saveGuildsSoon();
    }

    /** Mensagem para todos os membros online (menos {@code except}). */
    public void tellGuild(Guild g, UUID except, String key, TagResolver... tags) {
        for (UUID m : g.members().keySet()) {
            Player o = Names.online(m);
            if (o != null && !m.equals(except)) {
                msg().send(o, key, tags);
            }
        }
    }

    private Component buttons(String acceptCmd, String declineCmd) {
        return Component.space()
                .append(msg().get("geral.aceitar").clickEvent(ClickEvent.runCommand(acceptCmd))
                        .hoverEvent(HoverEvent.showText(msg().get("geral.dica_aceitar"))))
                .append(Component.space())
                .append(msg().get("geral.recusar").clickEvent(ClickEvent.runCommand(declineCmd))
                        .hoverEvent(HoverEvent.showText(msg().get("geral.dica_recusar"))));
    }

    private void act(Player p, GuildService.ActResult r) {
        msg().send(p, "guilda.erro." + r.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ identidade

    /** Guilda vermelha: o líder tem cursor vermelho no AincradReputation. */
    public boolean isRed(Guild g) {
        Player leader = Names.online(g.leader());
        return leader != null && hooks().redCursor(leader);
    }

    /** Tag como o {@code viewer} a vê: verde (mesma guilda), azul (aliada), vermelha, ou a cor da guilda. */
    public Component tagFor(Guild g, Player viewer) {
        TextColor color;
        if (isRed(g)) {
            color = RED;
        } else if (viewer != null && g.has(viewer.getUniqueId())) {
            color = SAME;
        } else if (viewer != null && svc().guildOf(viewer.getUniqueId()) != null
                && svc().guildOf(viewer.getUniqueId()).allies().contains(g.id())) {
            color = ALLY;
        } else {
            color = TextColor.fromHexString(g.color1());
            if (color == null || color.equals(NamedTextColor.WHITE)) {
                color = NamedTextColor.GOLD;
            }
        }
        return Component.text("[" + g.tag() + "] ", color);
    }

    /** Emblema + tag, usado na HUD e no perfil do AincradUI. */
    public Component badge(Player p) {
        Guild g = svc().guildOf(p.getUniqueId());
        if (g == null) {
            return null;
        }
        TextColor c = isRed(g) ? RED : TextColor.fromHexString(g.color2());
        return Component.text(g.emblem() + " ", c == null ? NamedTextColor.GOLD : c);
    }

    /** Tag na lista de jogadores e acima da cabeça (time do placar principal). */
    public void refreshName(Player p) {
        Guild g = svc().guildOf(p.getUniqueId());
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team old = sb.getEntryTeam(p.getName());
        if (old != null && old.getName().startsWith(TEAM_PREFIX)) {
            old.removeEntry(p.getName());
            if (old.getEntries().isEmpty()) {
                old.unregister();
            }
        }
        if (g == null) {
            p.playerListName(null);
            return;
        }
        Component tag = tagFor(g, null);
        p.playerListName(tag.append(Component.text(p.getName())));
        if (old != null && !old.getName().startsWith(TEAM_PREFIX)) {
            return; // outro plugin controla o time desse jogador: não mexe
        }
        String teamName = TEAM_PREFIX + g.id().toString().substring(0, 8);
        Team t = sb.getTeam(teamName);
        if (t == null) {
            t = sb.registerNewTeam(teamName);
        }
        t.prefix(tag);
        t.addEntry(p.getName());
    }

    public void refreshGuildNames(Guild g) {
        for (UUID m : g.members().keySet()) {
            Player o = Names.online(m);
            if (o != null) {
                refreshName(o);
            }
        }
    }

    /** Ao desligar: tira os times criados pela guilda. */
    public void clearTeams() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : sb.getTeams()) {
            if (t.getName().startsWith(TEAM_PREFIX)) {
                t.unregister();
            }
        }
    }

    // ------------------------------------------------------------------ criar e entrar

    /** Requisitos do plano: andar, quest, level e Col. Devolve a chave da mensagem de erro ou null. */
    public String missingRequirement(Player p) {
        if (!hooks().floorUnlocked(cfg().minFloor)) {
            return "guilda.req.andar";
        }
        if (!hooks().questDone(p, cfg().quest)) {
            return "guilda.req.quest";
        }
        if (hooks().level(p) < cfg().minLevel) {
            return "guilda.req.level";
        }
        if (hooks().hasEconomy() && hooks().balance(p) < cfg().cost) {
            return "guilda.req.col";
        }
        return null;
    }

    public TagResolver requirementTags() {
        return TagResolver.resolver(
                Placeholder.unparsed("andar", String.valueOf(cfg().minFloor)),
                Placeholder.unparsed("level", String.valueOf(cfg().minLevel)),
                Placeholder.unparsed("col", String.valueOf(cfg().cost)));
    }

    public boolean create(Player p, String name, String tag) {
        String missing = missingRequirement(p);
        if (missing != null) {
            msg().send(p, missing, requirementTags());
            return false;
        }
        GuildService.CreateResult check = svc().canCreate(p.getUniqueId(), name, tag, null);
        if (check != GuildService.CreateResult.CREATED) {
            msg().send(p, "guilda.criar." + check.name().toLowerCase(Locale.ROOT), cooldownTag(p));
            return false;
        }
        long cost = hooks().hasEconomy() ? cfg().cost : 0;
        if (!hooks().withdraw(p, cost, "Criação de guilda")) {
            msg().send(p, "guilda.req.col", requirementTags());
            return false;
        }
        svc().create(p.getUniqueId(), name, tag, null, cost);
        Guild g = svc().guildOf(p.getUniqueId());
        msg().send(p, "guilda.criada", guild(g), Placeholder.unparsed("membros", String.valueOf(svc().rules().minMembers())),
                Placeholder.unparsed("dias", String.valueOf(svc().rules().minMembersDeadlineMs() / 86_400_000L)));
        p.playSound(LEVEL_UP, Sound.Emitter.self());
        refreshName(p);
        fire(new GuildCreateEvent(g.id(), g.name(), p.getUniqueId()));
        changed();
        return true;
    }

    private TagResolver cooldownTag(Player p) {
        long h = (svc().cooldownLeft(p.getUniqueId()) + 3_599_999L) / 3_600_000L;
        return Placeholder.unparsed("horas", String.valueOf(h));
    }

    public boolean invite(Player p, UUID target) {
        Guild g = svc().guildOf(p.getUniqueId());
        GuildService.InviteResult r = svc().invite(p.getUniqueId(), target);
        String tn = name(target);
        if (r != GuildService.InviteResult.SENT) {
            msg().send(p, "guilda.convite." + r.name().toLowerCase(Locale.ROOT), who(tn));
            return false;
        }
        Player t = Names.online(target);
        if (t == null) {
            svc().declineInvite(target, g.id());
            msg().send(p, "geral.jogador_nao_encontrado", who(tn));
            return false;
        }
        msg().send(p, "guilda.convite.sent", who(tn));
        t.sendMessage(msg().get("prefixo").append(msg().get("guilda.convite_recebido", guild(g), who(p.getName())))
                .append(buttons("/guilda aceitar " + g.tag(), "/guilda recusar " + g.tag())));
        t.playSound(NOTIFY, Sound.Emitter.self());
        changed();
        return true;
    }

    private void joined(Player p, Guild g) {
        msg().send(p, "guilda.voce_entrou", guild(g));
        p.playSound(POSITIVE, Sound.Emitter.self());
        tellGuild(g, p.getUniqueId(), "guilda.entrou", who(p.getName()));
        refreshName(p);
        fire(new GuildJoinEvent(g.id(), g.name(), p.getUniqueId()));
        changed();
    }

    public boolean acceptInvite(Player p, Guild g) {
        GuildService.JoinResult r = svc().acceptInvite(p.getUniqueId(), g.id());
        if (r == GuildService.JoinResult.JOINED) {
            joined(p, g);
            return true;
        }
        msg().send(p, "guilda.entrar." + r.name().toLowerCase(Locale.ROOT), guild(g), cooldownTag(p));
        return false;
    }

    public boolean declineInvite(Player p, Guild g) {
        boolean ok = svc().declineInvite(p.getUniqueId(), g.id());
        msg().send(p, ok ? "guilda.convite_recusado" : "guilda.entrar.no_invite", guild(g));
        return ok;
    }

    public boolean apply(Player p, Guild g) {
        GuildService.JoinResult r = svc().apply(p.getUniqueId(), g.id());
        switch (r) {
            case JOINED -> joined(p, g);
            case APPLIED -> {
                msg().send(p, "guilda.candidatura_enviada", guild(g));
                for (UUID m : g.members().keySet()) {
                    Player o = Names.online(m);
                    if (o != null && svc().has(m, GuildPerm.CONVIDAR)) {
                        o.sendMessage(msg().get("prefixo").append(msg().get("guilda.candidatura_recebida", who(p.getName())))
                                .append(buttons("/guilda candidaturas aceitar " + p.getName(), "/guilda candidaturas recusar " + p.getName())));
                        o.playSound(NOTIFY, Sound.Emitter.self());
                    }
                }
                changed();
            }
            default -> msg().send(p, "guilda.entrar." + r.name().toLowerCase(Locale.ROOT), guild(g), cooldownTag(p));
        }
        return r == GuildService.JoinResult.JOINED || r == GuildService.JoinResult.APPLIED;
    }

    public boolean answerApplication(Player p, UUID applicant, boolean accept) {
        Guild g = svc().guildOf(p.getUniqueId());
        GuildService.ActResult r = svc().answerApplication(p.getUniqueId(), applicant, accept);
        if (r != GuildService.ActResult.OK) {
            act(p, r);
            return false;
        }
        String an = name(applicant);
        Player a = Names.online(applicant);
        if (accept) {
            tellGuild(g, null, "guilda.entrou", who(an));
            if (a != null) {
                msg().send(a, "guilda.voce_entrou", guild(g));
                refreshName(a);
            }
            fire(new GuildJoinEvent(g.id(), g.name(), applicant));
        } else {
            msg().send(p, "guilda.candidatura_recusada", who(an));
            if (a != null) {
                msg().send(a, "guilda.sua_candidatura_recusada", guild(g));
            }
        }
        changed();
        return true;
    }

    // ------------------------------------------------------------------ sair e cargos

    public boolean leave(Player p) {
        GuildService.Leave l = svc().leave(p.getUniqueId());
        if (l.result() != GuildService.ActResult.OK) {
            act(p, l.result());
            return false;
        }
        Guild g = l.guild();
        if (l.disbanded()) {
            disbanded(g, p);
        } else {
            msg().send(p, "guilda.voce_saiu", guild(g));
            tellGuild(g, p.getUniqueId(), "guilda.saiu", who(p.getName()));
            fire(new GuildLeaveEvent(g.id(), g.name(), p.getUniqueId(), "saiu"));
        }
        refreshName(p);
        changed();
        return true;
    }

    public boolean kick(Player p, UUID target) {
        Guild g = svc().guildOf(p.getUniqueId());
        GuildService.ActResult r = svc().kick(p.getUniqueId(), target);
        if (r != GuildService.ActResult.OK) {
            act(p, r);
            return false;
        }
        String tn = name(target);
        tellGuild(g, null, "guilda.expulso", who(tn));
        Player t = Names.online(target);
        if (t != null) {
            msg().send(t, "guilda.voce_expulso", guild(g));
            refreshName(t);
        }
        fire(new GuildLeaveEvent(g.id(), g.name(), target, "expulso"));
        changed();
        return true;
    }

    public boolean promote(Player p, UUID target, boolean up) {
        Guild g = svc().guildOf(p.getUniqueId());
        GuildRank before = g == null ? null : g.rankOf(target);
        GuildService.ActResult r = up ? svc().promote(p.getUniqueId(), target) : svc().demote(p.getUniqueId(), target);
        if (r != GuildService.ActResult.OK) {
            act(p, r);
            return false;
        }
        GuildRank now = g.rankOf(target);
        tellGuild(g, null, "guilda.novo_cargo", who(name(target)), Placeholder.component("cargo", rankName(now)));
        fire(new GuildRankChangeEvent(g.id(), g.name(), target, before.name(), now.name()));
        changed();
        return true;
    }

    public boolean transfer(Player p, UUID target) {
        Guild g = svc().guildOf(p.getUniqueId());
        GuildService.ActResult r = svc().transfer(p.getUniqueId(), target);
        if (r != GuildService.ActResult.OK) {
            act(p, r);
            return false;
        }
        tellGuild(g, null, "guilda.novo_lider", who(name(target)));
        fire(new GuildRankChangeEvent(g.id(), g.name(), target, "?", GuildRank.LIDER.name()));
        changed();
        return true;
    }

    public boolean disband(Player p) {
        Guild g = svc().disband(p.getUniqueId());
        if (g == null) {
            act(p, svc().guildOf(p.getUniqueId()) == null ? GuildService.ActResult.NO_GUILD : GuildService.ActResult.NO_PERMISSION);
            return false;
        }
        disbanded(g, p);
        changed();
        return true;
    }

    /** Fim da guilda: tesouro e itens do cofre vão para o líder; todos são avisados. */
    private void disbanded(Guild g, Player leader) {
        for (UUID m : g.members().keySet()) {
            Player o = Names.online(m);
            if (o != null) {
                msg().send(o, "guilda.desfeita", guild(g));
                refreshName(o);
            }
            fire(new GuildLeaveEvent(g.id(), g.name(), m, "desfeita"));
        }
        if (g.treasury() > 0) {
            if (!hooks().deposit(leader, g.treasury(), "Tesouro da guilda desfeita")) {
                svc().addPendingRefund(leader.getUniqueId(), g.treasury());
            }
        }
        ItemStack[] items = plugin.guildVault().drain(g);
        int count = 0;
        for (ItemStack it : items) {
            if (it != null && !it.getType().isAir()) {
                count++;
                leader.getInventory().addItem(it).values()
                        .forEach(rest -> leader.getWorld().dropItemNaturally(leader.getLocation(), rest));
            }
        }
        if (count > 0) {
            msg().send(leader, "guilda.cofre_devolvido", Placeholder.unparsed("itens", String.valueOf(count)));
        }
    }

    public static Component rankName(Messages msg, GuildRank r) {
        return msg.get("guilda.cargo." + r.key());
    }

    public Component rankName(GuildRank r) {
        return rankName(msg(), r);
    }

    // ------------------------------------------------------------------ tesouro

    public boolean deposit(Player p, long amount) {
        if (!hooks().hasEconomy()) {
            msg().send(p, "guilda.sem_economia");
            return false;
        }
        if (!svc().has(p.getUniqueId(), GuildPerm.DEPOSITAR)) {
            act(p, svc().guildOf(p.getUniqueId()) == null ? GuildService.ActResult.NO_GUILD : GuildService.ActResult.NO_PERMISSION);
            return false;
        }
        if (amount <= 0 || !hooks().withdraw(p, amount, "Depósito no tesouro da guilda")) {
            msg().send(p, "guilda.erro.not_enough");
            return false;
        }
        svc().deposit(p.getUniqueId(), p.getName(), amount);
        msg().send(p, "guilda.depositado", Placeholder.unparsed("valor", String.valueOf(amount)));
        changed();
        return true;
    }

    public boolean withdraw(Player p, long amount) {
        if (!hooks().hasEconomy()) {
            msg().send(p, "guilda.sem_economia");
            return false;
        }
        GuildService.ActResult r = svc().withdraw(p.getUniqueId(), p.getName(), amount);
        if (r != GuildService.ActResult.OK) {
            act(p, r);
            return false;
        }
        if (!hooks().deposit(p, amount, "Saque do tesouro da guilda")) {
            svc().deposit(p.getUniqueId(), p.getName(), amount); // devolve ao tesouro
            msg().send(p, "guilda.sem_economia");
            return false;
        }
        msg().send(p, "guilda.sacado", Placeholder.unparsed("valor", String.valueOf(amount)));
        changed();
        return true;
    }

    // ------------------------------------------------------------------ edição

    public boolean edit(Player p, GuildService.ActResult r, String okKey, TagResolver... tags) {
        if (r != GuildService.ActResult.OK) {
            act(p, r);
            return false;
        }
        Guild g = svc().guildOf(p.getUniqueId());
        tellGuild(g, null, okKey, tags);
        refreshGuildNames(g);
        changed();
        return true;
    }

    public boolean setHome(Player p) {
        Location l = p.getLocation();
        String s = l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ() + "," + l.getYaw() + "," + l.getPitch();
        return edit(p, svc().setHome(p.getUniqueId(), s), "guilda.casa_definida");
    }

    static Location parseHome(String s) {
        if (s == null) {
            return null;
        }
        String[] a = s.split(",");
        if (a.length != 6) {
            return null;
        }
        World w = Bukkit.getWorld(a[0]);
        if (w == null) {
            return null;
        }
        try {
            return new Location(w, Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]),
                    Float.parseFloat(a[4]), Float.parseFloat(a[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Vai para a casa da guilda depois de alguns segundos parado. */
    public void home(Player p) {
        Guild g = svc().guildOf(p.getUniqueId());
        if (g == null) {
            msg().send(p, "guilda.sem_guilda");
            return;
        }
        if (!svc().has(p.getUniqueId(), GuildPerm.CASA)) {
            msg().send(p, "guilda.sem_permissao");
            return;
        }
        Location target = parseHome(g.home());
        if (target == null) {
            msg().send(p, "guilda.sem_casa");
            return;
        }
        plugin.menu().close(p);
        cancelHome(p.getUniqueId());
        Location start = p.getLocation();
        int seconds = cfg().homeWarmupSeconds;
        msg().send(p, "guilda.casa_espera", Placeholder.unparsed("segundos", String.valueOf(seconds)));
        homeWarmups.put(p.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, () -> {
            homeWarmups.remove(p.getUniqueId());
            if (!p.isOnline()) {
                return;
            }
            if (!p.getWorld().equals(start.getWorld()) || p.getLocation().distanceSquared(start) > 0.25) {
                msg().send(p, "guilda.casa_cancelada");
                return;
            }
            p.teleportAsync(target);
            msg().send(p, "guilda.casa_chegou", guild(g));
        }, seconds * 20L));
    }

    /** Levou dano ou saiu: cancela a ida para casa. */
    public void cancelHome(UUID p) {
        BukkitTask t = homeWarmups.remove(p);
        if (t != null) {
            t.cancel();
            Player pl = Names.online(p);
            if (pl != null) {
                msg().send(pl, "guilda.casa_cancelada");
            }
        }
    }

    /** Peças de couro com as cores da guilda; cosméticas (sem armadura) e marcadas. */
    public void uniform(Player p) {
        Guild g = svc().guildOf(p.getUniqueId());
        if (g == null) {
            msg().send(p, "guilda.sem_guilda");
            return;
        }
        if (g.level() < svc().rules().uniformMinLevel()) {
            act(p, GuildService.ActResult.LEVEL_TOO_LOW);
            return;
        }
        Color c1 = parseColor(g.color1());
        Color c2 = parseColor(g.color2());
        Material[] pieces = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
        for (int i = 0; i < pieces.length; i++) {
            ItemStack it = new ItemStack(pieces[i]);
            LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
            meta.setColor(i == 0 || i == 3 ? c2 : c1);
            meta.displayName(msg().get("guilda.uniforme_nome", guild(g)));
            meta.lore(List.of(msg().get("guilda.uniforme_lore", guild(g))));
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(uniformArmorKey, 0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE);
            meta.getPersistentDataContainer().set(uniformKey, PersistentDataType.STRING, g.id().toString());
            it.setItemMeta(meta);
            p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        }
        msg().send(p, "guilda.uniforme_entregue");
    }

    /** Peça de uniforme? (para outros plugins não deixarem vender) */
    public boolean isUniform(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(uniformKey, PersistentDataType.STRING);
    }

    private static Color parseColor(String hex) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.substring(1), 16));
        } catch (RuntimeException e) {
            return Color.WHITE;
        }
    }

    // ------------------------------------------------------------------ alianças

    public boolean ally(Player p, Guild other, boolean accept) {
        Guild g = svc().guildOf(p.getUniqueId());
        GuildService.AllyResult r = accept ? svc().acceptAlliance(p.getUniqueId(), other.id())
                : svc().proposeAlliance(p.getUniqueId(), other.id());
        switch (r) {
            case PROPOSED -> {
                msg().send(p, "guilda.alianca_proposta", guild(other));
                for (UUID m : other.members().keySet()) {
                    Player o = Names.online(m);
                    if (o != null && svc().has(m, GuildPerm.ALIANCAS)) {
                        o.sendMessage(msg().get("prefixo").append(msg().get("guilda.alianca_recebida", guild(g)))
                                .append(buttons("/guilda alianca aceitar " + g.tag(), "/guilda alianca desfazer " + g.tag())));
                    }
                }
            }
            case ALLIED -> {
                tellGuild(g, null, "guilda.alianca_formada", guild(other));
                tellGuild(other, null, "guilda.alianca_formada", guild(g));
                refreshGuildNames(g);
                refreshGuildNames(other);
                fire(new GuildAllianceEvent(g.id(), g.name(), other.id(), true));
            }
            default -> msg().send(p, "guilda.alianca." + r.name().toLowerCase(Locale.ROOT),
                    Placeholder.unparsed("nivel", String.valueOf(svc().rules().alliesMinLevel())),
                    Placeholder.unparsed("max", String.valueOf(svc().rules().maxAllies())));
        }
        changed();
        return r == GuildService.AllyResult.PROPOSED || r == GuildService.AllyResult.ALLIED;
    }

    public boolean breakAlliance(Player p, Guild other) {
        Guild g = svc().guildOf(p.getUniqueId());
        GuildService.AllyResult r = svc().breakAlliance(p.getUniqueId(), other.id());
        if (r != GuildService.AllyResult.BROKEN) {
            msg().send(p, "guilda.alianca." + r.name().toLowerCase(Locale.ROOT),
                    Placeholder.unparsed("nivel", String.valueOf(svc().rules().alliesMinLevel())),
                    Placeholder.unparsed("max", String.valueOf(svc().rules().maxAllies())));
            return false;
        }
        tellGuild(g, null, "guilda.alianca_desfeita", guild(other));
        tellGuild(other, null, "guilda.alianca_desfeita", guild(g));
        fire(new GuildAllianceEvent(g.id(), g.name(), other.id(), false));
        changed();
        return true;
    }

    // ------------------------------------------------------------------ chat

    public void chat(Player p, String text) {
        Guild g = svc().guildOf(p.getUniqueId());
        if (g == null) {
            msg().send(p, "guilda.sem_guilda");
            return;
        }
        Component line = msg().get("guilda.chat", guild(g), who(p.getName()),
                Placeholder.component("cargo", rankName(g.rankOf(p.getUniqueId()))),
                Placeholder.unparsed("mensagem", MessageService.clean(text)));
        for (UUID m : g.members().keySet()) {
            Player o = Names.online(m);
            if (o != null) {
                o.sendMessage(line);
            }
        }
        plugin.getLogger().info("[Guilda " + g.tag() + "] " + p.getName() + ": " + text);
    }

    /** Canal comum das guildas aliadas. */
    public void allyChat(Player p, String text) {
        Guild g = svc().guildOf(p.getUniqueId());
        if (g == null) {
            msg().send(p, "guilda.sem_guilda");
            return;
        }
        Component line = msg().get("guilda.chat_alianca", guild(g), who(p.getName()),
                Placeholder.unparsed("mensagem", MessageService.clean(text)));
        java.util.Set<UUID> guilds = new java.util.LinkedHashSet<>(g.allies());
        guilds.add(g.id());
        for (UUID gid : guilds) {
            Guild x = svc().get(gid);
            if (x == null) {
                continue;
            }
            for (UUID m : x.members().keySet()) {
                Player o = Names.online(m);
                if (o != null) {
                    o.sendMessage(line);
                }
            }
        }
    }

    // ------------------------------------------------------------------ XP e manutenção

    /** Soma XP e avisa a guilda se subiu de nível. */
    public void addXp(Guild g, long amount) {
        int gained = svc().addXp(g, amount);
        if (gained > 0) {
            for (UUID m : g.members().keySet()) {
                Player o = Names.online(m);
                if (o != null) {
                    msg().send(o, "guilda.subiu_nivel", guild(g), Placeholder.unparsed("nivel", String.valueOf(g.level())),
                            Placeholder.unparsed("vagas", String.valueOf(svc().slots(g))));
                    o.playSound(LEVEL_UP, Sound.Emitter.self());
                }
            }
            fire(new GuildLevelUpEvent(g.id(), g.name(), g.level()));
        }
        changed();
    }

    /** XP por mob hostil, com limite diário por membro. */
    public void mobKilled(Player p) {
        Guild g = svc().guildOf(p.getUniqueId());
        if (g == null || cfg().xpMob <= 0) {
            return;
        }
        long day = System.currentTimeMillis() / 86_400_000L;
        long[] c = mobXp.computeIfAbsent(p.getUniqueId(), k -> new long[]{day, 0});
        if (c[0] != day) {
            c[0] = day;
            c[1] = 0;
        }
        if (c[1] >= cfg().xpMobDailyCap) {
            return;
        }
        c[1]++;
        addXp(g, cfg().xpMob);
    }

    /** Recrutas promovidos, líder inativo, guildas pequenas desfeitas; reembolsos pendentes. */
    public void maintenance() {
        for (GuildService.Event e : svc().maintenance()) {
            Guild g = e.guild();
            switch (e.kind()) {
                case RECRUIT_PROMOTED -> {
                    Player o = Names.online(e.who());
                    if (o != null) {
                        msg().send(o, "guilda.recruta_promovido", guild(g));
                    }
                }
                case LEADER_CHANGED -> tellGuild(g, null, "guilda.novo_lider_inativo", who(name(e.who())));
                case DISBANDED_SMALL -> {
                    for (UUID m : g.members().keySet()) {
                        Player o = Names.online(m);
                        if (o != null) {
                            msg().send(o, "guilda.desfeita_pequena", guild(g),
                                    Placeholder.unparsed("membros", String.valueOf(svc().rules().minMembers())));
                            refreshName(o);
                        }
                    }
                    if (e.who() != null) {
                        svc().addPendingRefund(e.who(), e.refund() + g.treasury());
                    }
                }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            payRefund(p);
        }
        changed();
    }

    public void payRefund(Player p) {
        if (!hooks().hasEconomy()) {
            return;
        }
        long v = svc().takePendingRefund(p.getUniqueId());
        if (v > 0) {
            if (hooks().deposit(p, v, "Reembolso de guilda")) {
                msg().send(p, "guilda.reembolso", Placeholder.unparsed("valor", String.valueOf(v)));
            } else {
                svc().addPendingRefund(p.getUniqueId(), v);
            }
        }
    }
}
