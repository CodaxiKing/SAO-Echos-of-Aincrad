package com.aincrad.social.guild;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Regras da guilda (cargos, entrada, saída, alianças, nível, tesouro), só sobre UUIDs: quem chama
 * cuida de Col, mensagens e efeitos. Requisitos de criação que dependem de outros plugins
 * (andar, quest, level, Col) são checados antes por quem chama.
 */
public final class GuildService {

    public enum CreateResult { CREATED, IN_GUILD, COOLDOWN, NAME_INVALID, TAG_INVALID, BLOCKED_WORD, NAME_TAKEN, TAG_TAKEN, EMBLEM_INVALID }

    public enum InviteResult { SENT, NO_GUILD, NO_PERMISSION, SELF, TARGET_IN_GUILD, FULL, TARGET_COOLDOWN, ALREADY_INVITED }

    public enum JoinResult { JOINED, APPLIED, NO_GUILD, NO_INVITE, EXPIRED, IN_GUILD, FULL, COOLDOWN, CLOSED, ALREADY_APPLIED }

    public enum ActResult { OK, NO_GUILD, NO_PERMISSION, NOT_MEMBER, HIGHER_RANK, SELF, LIMIT, SUB_TAKEN, LEADER_MUST_TRANSFER, LEVEL_TOO_LOW, INVALID, NOT_ENOUGH, TAKEN }

    public enum AllyResult { PROPOSED, ALLIED, BROKEN, NO_GUILD, NO_PERMISSION, LEVEL_TOO_LOW, SELF, ALREADY, LIMIT, NO_PROPOSAL }

    /** Resultado de sair. {@code disbanded}: o líder estava sozinho e a guilda acabou. */
    public record Leave(ActResult result, boolean disbanded, Guild guild) {
    }

    /** O que a manutenção periódica mudou, para avisar os jogadores. */
    public record Event(Kind kind, Guild guild, UUID who, long refund) {
        public enum Kind { RECRUIT_PROMOTED, LEADER_CHANGED, DISBANDED_SMALL }
    }

    private static final Pattern NAME = Pattern.compile("[\\p{L}0-9 ]+");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9]+");
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final long DAY = 86_400_000L;

    private final Map<UUID, Guild> guilds = new LinkedHashMap<>();
    private final Map<UUID, UUID> byMember = new HashMap<>();
    private final Map<UUID, Long> leftAt = new HashMap<>();
    /** Reembolsos de guildas desfeitas cujo líder estava offline (pagos quando ele entrar). */
    private final Map<UUID, Long> pendingRefunds = new HashMap<>();
    /** jogador → {dia, retiradas no dia} (memória: zera ao reiniciar). */
    private final Map<UUID, long[]> withdrawals = new HashMap<>();
    private final LongSupplier clock;
    private GuildRules rules;

    public GuildService(GuildRules rules, LongSupplier clock) {
        this.rules = rules;
        this.clock = clock;
    }

    public void setRules(GuildRules rules) {
        this.rules = rules;
    }

    public GuildRules rules() {
        return rules;
    }

    private long now() {
        return clock.getAsLong();
    }

    // ------------------------------------------------------------------ consulta

    public Guild guildOf(UUID p) {
        UUID g = byMember.get(p);
        return g == null ? null : guilds.get(g);
    }

    public Guild get(UUID id) {
        return guilds.get(id);
    }

    public Collection<Guild> all() {
        return List.copyOf(guilds.values());
    }

    public Guild byName(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (Guild g : guilds.values()) {
            if (g.name.toLowerCase(Locale.ROOT).equals(n) || g.tag.toLowerCase(Locale.ROOT).equals(n)) {
                return g;
            }
        }
        return null;
    }

    public boolean has(UUID p, GuildPerm perm) {
        Guild g = guildOf(p);
        if (g == null) {
            return false;
        }
        GuildRank r = g.rankOf(p);
        GuildRank min = g.minRank(perm);
        return r == min || r.above(min);
    }

    public boolean sameGuild(UUID a, UUID b) {
        UUID ga = byMember.get(a);
        return ga != null && ga.equals(byMember.get(b));
    }

    /** Membros de guildas aliadas (não da mesma guilda). */
    public boolean areAllies(UUID a, UUID b) {
        Guild ga = guildOf(a);
        Guild gb = guildOf(b);
        return ga != null && gb != null && ga != gb && ga.allies.contains(gb.id);
    }

    public int slots(Guild g) {
        return rules.level(g.level).slots();
    }

    public int vaultSize(Guild g) {
        return rules.level(g.level).vault();
    }

    public long cooldownLeft(UUID p) {
        Long left = leftAt.get(p);
        return left == null ? 0 : Math.max(0, left + rules.rejoinCooldownMs() - now());
    }

    // ------------------------------------------------------------------ criação

    /** Nome válido? (letras, números e espaços, tamanho e palavras bloqueadas) */
    public CreateResult checkName(String name) {
        String n = name.trim();
        if (n.length() < rules.nameMin() || n.length() > rules.nameMax() || !NAME.matcher(n).matches()) {
            return CreateResult.NAME_INVALID;
        }
        return blocked(n) ? CreateResult.BLOCKED_WORD : CreateResult.CREATED;
    }

    public CreateResult checkTag(String tag) {
        String t = tag.trim();
        if (t.length() < rules.tagMin() || t.length() > rules.tagMax() || !TAG.matcher(t).matches()) {
            return CreateResult.TAG_INVALID;
        }
        return blocked(t) ? CreateResult.BLOCKED_WORD : CreateResult.CREATED;
    }

    private boolean blocked(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        return rules.blockedWords().stream().anyMatch(w -> !w.isBlank() && l.contains(w.toLowerCase(Locale.ROOT)));
    }

    private boolean nameTaken(String name, Guild except) {
        return guilds.values().stream().anyMatch(g -> g != except && g.name.equalsIgnoreCase(name.trim()));
    }

    private boolean tagTaken(String tag, Guild except) {
        return guilds.values().stream().anyMatch(g -> g != except && g.tag.equalsIgnoreCase(tag.trim()));
    }

    /** Checa tudo sem criar (para validar antes de cobrar o Col). */
    public CreateResult canCreate(UUID founder, String name, String tag, String emblem) {
        if (byMember.containsKey(founder)) {
            return CreateResult.IN_GUILD;
        }
        if (cooldownLeft(founder) > 0) {
            return CreateResult.COOLDOWN;
        }
        CreateResult n = checkName(name);
        if (n != CreateResult.CREATED) {
            return n;
        }
        CreateResult t = checkTag(tag);
        if (t != CreateResult.CREATED) {
            return t;
        }
        if (emblem != null && !rules.emblems().contains(emblem)) {
            return CreateResult.EMBLEM_INVALID;
        }
        if (nameTaken(name, null)) {
            return CreateResult.NAME_TAKEN;
        }
        if (tagTaken(tag, null)) {
            return CreateResult.TAG_TAKEN;
        }
        return CreateResult.CREATED;
    }

    public CreateResult create(UUID founder, String name, String tag, String emblem, long cost) {
        CreateResult r = canCreate(founder, name, tag, emblem);
        if (r != CreateResult.CREATED) {
            return r;
        }
        Guild g = new Guild(UUID.randomUUID(), name.trim(), tag.trim().toUpperCase(Locale.ROOT),
                emblem != null ? emblem : rules.emblems().isEmpty() ? "⚔" : rules.emblems().get(0), now());
        g.creationCost = cost;
        guilds.put(g.id, g);
        addMember(g, founder, GuildRank.LIDER);
        checkEstablished(g);
        return CreateResult.CREATED;
    }

    private void addMember(Guild g, UUID p, GuildRank rank) {
        g.members.put(p, new Guild.Member(rank, now()));
        byMember.put(p, g.id);
        for (Guild other : guilds.values()) {
            other.invites.remove(p);
            other.applications.remove(p);
        }
        checkEstablished(g);
    }

    private void checkEstablished(Guild g) {
        if (g.members.size() >= rules.minMembers()) {
            g.established = true;
        }
    }

    private void removeMember(Guild g, UUID p) {
        g.members.remove(p);
        byMember.remove(p);
        leftAt.put(p, now());
    }

    // ------------------------------------------------------------------ entrada

    public InviteResult invite(UUID actor, UUID target) {
        Guild g = guildOf(actor);
        if (g == null) {
            return InviteResult.NO_GUILD;
        }
        if (!has(actor, GuildPerm.CONVIDAR)) {
            return InviteResult.NO_PERMISSION;
        }
        if (actor.equals(target)) {
            return InviteResult.SELF;
        }
        if (byMember.containsKey(target)) {
            return InviteResult.TARGET_IN_GUILD;
        }
        if (g.size() >= slots(g)) {
            return InviteResult.FULL;
        }
        if (cooldownLeft(target) > 0) {
            return InviteResult.TARGET_COOLDOWN;
        }
        Long exp = g.invites.get(target);
        if (exp != null && exp > now()) {
            return InviteResult.ALREADY_INVITED;
        }
        g.invites.put(target, now() + rules.inviteTtlMs());
        return InviteResult.SENT;
    }

    /** Guildas que convidaram o jogador (convites válidos). */
    public List<Guild> invitesFor(UUID p) {
        List<Guild> out = new ArrayList<>();
        for (Guild g : guilds.values()) {
            Long exp = g.invites.get(p);
            if (exp != null && exp > now()) {
                out.add(g);
            }
        }
        return out;
    }

    private JoinResult canJoin(Guild g, UUID p) {
        if (byMember.containsKey(p)) {
            return JoinResult.IN_GUILD;
        }
        if (g.size() >= slots(g)) {
            return JoinResult.FULL;
        }
        if (cooldownLeft(p) > 0) {
            return JoinResult.COOLDOWN;
        }
        return JoinResult.JOINED;
    }

    public JoinResult acceptInvite(UUID p, UUID guildId) {
        Guild g = guilds.get(guildId);
        if (g == null) {
            return JoinResult.NO_GUILD;
        }
        Long exp = g.invites.get(p);
        if (exp == null) {
            return JoinResult.NO_INVITE;
        }
        if (exp <= now()) {
            g.invites.remove(p);
            return JoinResult.EXPIRED;
        }
        JoinResult r = canJoin(g, p);
        if (r == JoinResult.JOINED) {
            addMember(g, p, GuildRank.RECRUTA);
        }
        return r;
    }

    public boolean declineInvite(UUID p, UUID guildId) {
        Guild g = guilds.get(guildId);
        return g != null && g.invites.remove(p) != null;
    }

    /** Candidatura: entra direto se a guilda é aberta; fica pendente se é por candidatura. */
    public JoinResult apply(UUID p, UUID guildId) {
        Guild g = guilds.get(guildId);
        if (g == null) {
            return JoinResult.NO_GUILD;
        }
        JoinResult r = canJoin(g, p);
        if (r != JoinResult.JOINED) {
            return r;
        }
        switch (g.recruit) {
            case CONVITE -> {
                return JoinResult.CLOSED;
            }
            case ABERTA -> {
                addMember(g, p, GuildRank.RECRUTA);
                return JoinResult.JOINED;
            }
            default -> {
                if (g.applications.containsKey(p)) {
                    return JoinResult.ALREADY_APPLIED;
                }
                g.applications.put(p, now());
                return JoinResult.APPLIED;
            }
        }
    }

    public ActResult answerApplication(UUID actor, UUID applicant, boolean accept) {
        Guild g = guildOf(actor);
        if (g == null) {
            return ActResult.NO_GUILD;
        }
        if (!has(actor, GuildPerm.CONVIDAR)) {
            return ActResult.NO_PERMISSION;
        }
        if (!g.applications.containsKey(applicant)) {
            return ActResult.INVALID;
        }
        g.applications.remove(applicant);
        if (!accept) {
            return ActResult.OK;
        }
        if (byMember.containsKey(applicant)) {
            return ActResult.TAKEN;
        }
        if (g.size() >= slots(g)) {
            return ActResult.LIMIT;
        }
        addMember(g, applicant, GuildRank.RECRUTA);
        return ActResult.OK;
    }

    // ------------------------------------------------------------------ saída e cargos

    public Leave leave(UUID p) {
        Guild g = guildOf(p);
        if (g == null) {
            return new Leave(ActResult.NO_GUILD, false, null);
        }
        if (g.rankOf(p) == GuildRank.LIDER) {
            if (g.size() > 1) {
                return new Leave(ActResult.LEADER_MUST_TRANSFER, false, g);
            }
            removeGuild(g);
            return new Leave(ActResult.OK, true, g);
        }
        removeMember(g, p);
        return new Leave(ActResult.OK, false, g);
    }

    public ActResult kick(UUID actor, UUID target) {
        Guild g = guildOf(actor);
        if (g == null) {
            return ActResult.NO_GUILD;
        }
        if (!has(actor, GuildPerm.EXPULSAR)) {
            return ActResult.NO_PERMISSION;
        }
        if (actor.equals(target)) {
            return ActResult.SELF;
        }
        if (!g.has(target)) {
            return ActResult.NOT_MEMBER;
        }
        if (!g.rankOf(actor).above(g.rankOf(target))) {
            return ActResult.HIGHER_RANK;
        }
        removeMember(g, target);
        return ActResult.OK;
    }

    public ActResult promote(UUID actor, UUID target) {
        Guild g = guildOf(actor);
        ActResult pre = rankChangeCheck(g, actor, target);
        if (pre != ActResult.OK) {
            return pre;
        }
        GuildRank next = g.rankOf(target).up();
        if (next == null || next == GuildRank.LIDER) {
            return ActResult.LIMIT; // liderança só por transferência
        }
        if (!g.rankOf(actor).above(next)) {
            return ActResult.HIGHER_RANK;
        }
        if (next == GuildRank.SUBLIDER && !g.withRank(GuildRank.SUBLIDER).isEmpty()) {
            return ActResult.SUB_TAKEN;
        }
        g.members.get(target).rank = next;
        return ActResult.OK;
    }

    public ActResult demote(UUID actor, UUID target) {
        Guild g = guildOf(actor);
        ActResult pre = rankChangeCheck(g, actor, target);
        if (pre != ActResult.OK) {
            return pre;
        }
        GuildRank next = g.rankOf(target).down();
        if (next == null) {
            return ActResult.LIMIT;
        }
        g.members.get(target).rank = next;
        return ActResult.OK;
    }

    private ActResult rankChangeCheck(Guild g, UUID actor, UUID target) {
        if (g == null) {
            return ActResult.NO_GUILD;
        }
        if (!has(actor, GuildPerm.PROMOVER)) {
            return ActResult.NO_PERMISSION;
        }
        if (actor.equals(target)) {
            return ActResult.SELF;
        }
        if (!g.has(target)) {
            return ActResult.NOT_MEMBER;
        }
        if (!g.rankOf(actor).above(g.rankOf(target))) {
            return ActResult.HIGHER_RANK;
        }
        return ActResult.OK;
    }

    /** O líder passa a liderança; ele vira sub-líder (o sub-líder antigo vira oficial). */
    public ActResult transfer(UUID leader, UUID target) {
        Guild g = guildOf(leader);
        if (g == null) {
            return ActResult.NO_GUILD;
        }
        if (!has(leader, GuildPerm.LIDERANCA)) {
            return ActResult.NO_PERMISSION;
        }
        if (leader.equals(target)) {
            return ActResult.SELF;
        }
        if (!g.has(target)) {
            return ActResult.NOT_MEMBER;
        }
        setLeader(g, target, GuildRank.SUBLIDER);
        return ActResult.OK;
    }

    private void setLeader(Guild g, UUID newLeader, GuildRank oldLeaderBecomes) {
        UUID old = g.leader();
        for (UUID sub : g.withRank(GuildRank.SUBLIDER)) {
            if (!sub.equals(newLeader)) {
                g.members.get(sub).rank = GuildRank.OFICIAL;
            }
        }
        g.members.get(newLeader).rank = GuildRank.LIDER;
        if (old != null && !old.equals(newLeader)) {
            g.members.get(old).rank = oldLeaderBecomes;
        }
    }

    /** O líder desfaz a guilda. @return a guilda desfeita (null se não pôde) */
    public Guild disband(UUID leader) {
        Guild g = guildOf(leader);
        if (g == null || !has(leader, GuildPerm.LIDERANCA)) {
            return null;
        }
        removeGuild(g);
        return g;
    }

    private void removeGuild(Guild g) {
        guilds.remove(g.id);
        for (UUID m : g.members.keySet()) {
            byMember.remove(m);
        }
        for (Guild other : guilds.values()) {
            other.allies.remove(g.id);
            other.allyProposals.remove(g.id);
        }
    }

    // ------------------------------------------------------------------ admin

    /** Admin: renomeia sem checar cargo. */
    public ActResult adminRename(Guild g, String name) {
        if (checkName(name) != CreateResult.CREATED) {
            return ActResult.INVALID;
        }
        if (nameTaken(name, g)) {
            return ActResult.TAKEN;
        }
        g.name = name.trim();
        return ActResult.OK;
    }

    /** Admin: desfaz sem checar cargo. */
    public void adminDisband(Guild g) {
        removeGuild(g);
    }

    // ------------------------------------------------------------------ edição (só o líder)

    private ActResult editCheck(Guild g, UUID actor) {
        if (g == null) {
            return ActResult.NO_GUILD;
        }
        return has(actor, GuildPerm.EDITAR) ? ActResult.OK : ActResult.NO_PERMISSION;
    }

    public ActResult setRecruit(UUID actor, Guild.Recruit mode) {
        Guild g = guildOf(actor);
        ActResult r = editCheck(g, actor);
        if (r == ActResult.OK) {
            g.recruit = mode;
        }
        return r;
    }

    public ActResult setTax(UUID actor, int percent) {
        Guild g = guildOf(actor);
        ActResult r = editCheck(g, actor);
        if (r != ActResult.OK) {
            return r;
        }
        if (percent < 0 || percent > rules.maxTaxPercent()) {
            return ActResult.INVALID;
        }
        g.taxPercent = percent;
        return ActResult.OK;
    }

    public ActResult setColors(UUID actor, String c1, String c2) {
        Guild g = guildOf(actor);
        ActResult r = editCheck(g, actor);
        if (r != ActResult.OK) {
            return r;
        }
        if (!HEX.matcher(c1).matches() || !HEX.matcher(c2).matches()) {
            return ActResult.INVALID;
        }
        g.color1 = c1.toUpperCase(Locale.ROOT);
        g.color2 = c2.toUpperCase(Locale.ROOT);
        return ActResult.OK;
    }

    public ActResult setEmblem(UUID actor, String emblem) {
        Guild g = guildOf(actor);
        ActResult r = editCheck(g, actor);
        if (r != ActResult.OK) {
            return r;
        }
        if (!rules.emblems().contains(emblem)) {
            return ActResult.INVALID;
        }
        g.emblem = emblem;
        return ActResult.OK;
    }

    public ActResult setTag(UUID actor, String tag) {
        Guild g = guildOf(actor);
        ActResult r = editCheck(g, actor);
        if (r != ActResult.OK) {
            return r;
        }
        if (checkTag(tag) != CreateResult.CREATED) {
            return ActResult.INVALID;
        }
        if (tagTaken(tag, g)) {
            return ActResult.TAKEN;
        }
        g.tag = tag.trim().toUpperCase(Locale.ROOT);
        return ActResult.OK;
    }

    public ActResult setPermission(UUID actor, GuildPerm perm, GuildRank min) {
        Guild g = guildOf(actor);
        ActResult r = editCheck(g, actor);
        if (r != ActResult.OK) {
            return r;
        }
        if (perm.fixed()) {
            return ActResult.INVALID;
        }
        g.perms.put(perm, min);
        return ActResult.OK;
    }

    /** Casa da guilda (local serializado por quem chama). Precisa do nível de casa. */
    public ActResult setHome(UUID actor, String location) {
        Guild g = guildOf(actor);
        ActResult r = editCheck(g, actor);
        if (r != ActResult.OK) {
            return r;
        }
        if (g.level < rules.homeMinLevel()) {
            return ActResult.LEVEL_TOO_LOW;
        }
        g.home = location;
        return ActResult.OK;
    }

    // ------------------------------------------------------------------ nível e linha de frente

    /** Soma XP e devolve quantos níveis a guilda subiu. */
    public int addXp(Guild g, long amount) {
        if (amount <= 0) {
            return 0;
        }
        int before = g.level;
        g.xp += amount;
        g.level = Math.min(rules.maxLevel(), Math.max(g.level, rules.levelFor(g.xp)));
        return g.level - before;
    }

    public void recordBossKill(Guild g, int floor, boolean lastAttack) {
        g.bossKills++;
        if (lastAttack) {
            g.lastAttacks++;
        }
        g.highestFloor = Math.max(g.highestFloor, floor);
    }

    /** Ranking da linha de frente: chefes, depois Last Attacks, andar mais alto e nível. */
    public List<Guild> ranking() {
        List<Guild> list = new ArrayList<>(guilds.values());
        list.sort(Comparator.comparingInt(Guild::bossKills).reversed()
                .thenComparing(Comparator.comparingInt(Guild::lastAttacks).reversed())
                .thenComparing(Comparator.comparingInt(Guild::highestFloor).reversed())
                .thenComparing(Comparator.comparingInt(Guild::level).reversed())
                .thenComparing(Guild::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    // ------------------------------------------------------------------ tesouro e cofre

    private void log(Guild g, UUID who, String whoName, String type, long amount, String detail) {
        g.ledger.add(new Guild.Ledger(now(), who, whoName, type, amount, detail));
        while (g.ledger.size() > rules.ledgerMax()) {
            g.ledger.remove(0);
        }
    }

    public ActResult deposit(UUID p, String name, long amount) {
        Guild g = guildOf(p);
        if (g == null) {
            return ActResult.NO_GUILD;
        }
        if (!has(p, GuildPerm.DEPOSITAR)) {
            return ActResult.NO_PERMISSION;
        }
        if (amount <= 0) {
            return ActResult.INVALID;
        }
        g.treasury += amount;
        log(g, p, name, "deposito", amount, "");
        return ActResult.OK;
    }

    public ActResult withdraw(UUID p, String name, long amount) {
        Guild g = guildOf(p);
        if (g == null) {
            return ActResult.NO_GUILD;
        }
        if (!has(p, GuildPerm.TESOURO)) {
            return ActResult.NO_PERMISSION;
        }
        if (amount <= 0) {
            return ActResult.INVALID;
        }
        if (amount > g.treasury) {
            return ActResult.NOT_ENOUGH;
        }
        g.treasury -= amount;
        log(g, p, name, "saque", amount, "");
        return ActResult.OK;
    }

    /**
     * Taxa automática sobre o Col que um membro ganhou caçando (chamado pelo plugin de economia).
     * @return quanto foi para o tesouro (o resto fica com o jogador)
     */
    public long tax(UUID p, String name, long income) {
        Guild g = guildOf(p);
        if (g == null || g.taxPercent <= 0 || income <= 0) {
            return 0;
        }
        long t = income * g.taxPercent / 100;
        if (t > 0) {
            g.treasury += t;
            log(g, p, name, "taxa", t, "");
        }
        return t;
    }

    /** Pode tirar mais um item do cofre hoje? (membros têm limite; oficiais para cima, não) */
    public boolean canWithdrawItem(UUID p) {
        Guild g = guildOf(p);
        if (g == null || !has(p, GuildPerm.RETIRAR_COFRE)) {
            return false;
        }
        if (g.rankOf(p).above(GuildRank.MEMBRO)) {
            return true;
        }
        long[] w = withdrawals.get(p);
        long day = now() / DAY;
        return w == null || w[0] != day || w[1] < rules.memberWithdrawalsPerDay();
    }

    public void recordVault(UUID p, String name, boolean withdrawal, String item, int amount) {
        Guild g = guildOf(p);
        if (g == null) {
            return;
        }
        if (withdrawal) {
            long day = now() / DAY;
            long[] w = withdrawals.computeIfAbsent(p, k -> new long[]{day, 0});
            if (w[0] != day) {
                w[0] = day;
                w[1] = 0;
            }
            w[1]++;
        }
        log(g, p, name, withdrawal ? "cofre_saida" : "cofre_entrada", amount, item);
    }

    public void setVaultData(Guild g, String data) {
        g.vaultData = data == null ? "" : data;
    }

    // ------------------------------------------------------------------ alianças

    public AllyResult proposeAlliance(UUID actor, UUID otherId) {
        Guild g = guildOf(actor);
        Guild o = guilds.get(otherId);
        if (g == null || o == null) {
            return AllyResult.NO_GUILD;
        }
        if (!has(actor, GuildPerm.ALIANCAS)) {
            return AllyResult.NO_PERMISSION;
        }
        if (g == o) {
            return AllyResult.SELF;
        }
        if (g.level < rules.alliesMinLevel()) {
            return AllyResult.LEVEL_TOO_LOW;
        }
        if (g.allies.contains(o.id)) {
            return AllyResult.ALREADY;
        }
        if (g.allies.size() >= rules.maxAllies() || o.allies.size() >= rules.maxAllies()) {
            return AllyResult.LIMIT;
        }
        if (g.allyProposals.contains(o.id)) {
            ally(g, o);
            return AllyResult.ALLIED;
        }
        o.allyProposals.add(g.id);
        return AllyResult.PROPOSED;
    }

    public AllyResult acceptAlliance(UUID actor, UUID otherId) {
        Guild g = guildOf(actor);
        Guild o = guilds.get(otherId);
        if (g == null || o == null) {
            return AllyResult.NO_GUILD;
        }
        if (!has(actor, GuildPerm.ALIANCAS)) {
            return AllyResult.NO_PERMISSION;
        }
        if (!g.allyProposals.contains(o.id)) {
            return AllyResult.NO_PROPOSAL;
        }
        if (g.allies.size() >= rules.maxAllies() || o.allies.size() >= rules.maxAllies()) {
            return AllyResult.LIMIT;
        }
        ally(g, o);
        return AllyResult.ALLIED;
    }

    private void ally(Guild a, Guild b) {
        a.allyProposals.remove(b.id);
        b.allyProposals.remove(a.id);
        a.allies.add(b.id);
        b.allies.add(a.id);
    }

    public AllyResult breakAlliance(UUID actor, UUID otherId) {
        Guild g = guildOf(actor);
        Guild o = guilds.get(otherId);
        if (g == null || o == null) {
            return AllyResult.NO_GUILD;
        }
        if (!has(actor, GuildPerm.ALIANCAS)) {
            return AllyResult.NO_PERMISSION;
        }
        g.allyProposals.remove(o.id);
        if (!g.allies.remove(o.id)) {
            return AllyResult.NO_PROPOSAL;
        }
        o.allies.remove(g.id);
        return AllyResult.BROKEN;
    }

    // ------------------------------------------------------------------ manutenção

    public void markSeen(UUID p) {
        Guild g = guildOf(p);
        if (g != null) {
            g.members.get(p).lastSeen = now();
        }
    }

    /**
     * Rotina periódica: recrutas viram membros depois do prazo, líder inativo passa a liderança,
     * guilda que não chegou ao mínimo de membros no prazo é desfeita (com reembolso parcial).
     */
    public List<Event> maintenance() {
        List<Event> events = new ArrayList<>();
        long now = now();
        for (Guild g : new ArrayList<>(guilds.values())) {
            g.invites.values().removeIf(exp -> exp <= now);
            for (UUID r : g.withRank(GuildRank.RECRUTA)) {
                if (g.members.get(r).joined + rules.recruitPromoteMs() <= now) {
                    g.members.get(r).rank = GuildRank.MEMBRO;
                    events.add(new Event(Event.Kind.RECRUIT_PROMOTED, g, r, 0));
                }
            }
            UUID leader = g.leader();
            if (leader != null && g.size() > 1 && g.members.get(leader).lastSeen + rules.leaderInactiveMs() <= now) {
                UUID next = successor(g);
                if (next != null) {
                    setLeader(g, next, GuildRank.OFICIAL);
                    events.add(new Event(Event.Kind.LEADER_CHANGED, g, next, 0));
                }
            }
            if (!g.established && g.created + rules.minMembersDeadlineMs() <= now && g.size() < rules.minMembers()) {
                long refund = (long) Math.floor(g.creationCost * rules.refundFraction());
                removeGuild(g);
                events.add(new Event(Event.Kind.DISBANDED_SMALL, g, leader, refund));
            }
        }
        return events;
    }

    /** Sub-líder; senão o oficial mais antigo; senão o membro mais antigo. */
    private UUID successor(Guild g) {
        for (GuildRank r : List.of(GuildRank.SUBLIDER, GuildRank.OFICIAL, GuildRank.MEMBRO, GuildRank.RECRUTA)) {
            List<UUID> list = g.withRank(r);
            if (!list.isEmpty()) {
                return list.get(0);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ carregamento

    /** Usado pelo armazenamento ao ler do arquivo. */
    void restore(Guild g) {
        guilds.put(g.id, g);
        for (UUID m : g.members.keySet()) {
            byMember.put(m, g.id);
        }
    }

    /**
     * Põe a versão de uma guilda que veio do banco no lugar da que está na memória. Os convites
     * e os itens do cofre, que não vão no documento da guilda, continuam os de antes.
     */
    void replace(Guild g) {
        Guild old = guilds.get(g.id);
        if (old != null) {
            for (UUID m : old.members.keySet()) {
                byMember.remove(m, old.id);
            }
            g.invites.putAll(old.invites);
            if (g.vaultData.isEmpty()) {
                g.vaultData = old.vaultData;
            }
        }
        restore(g);
    }

    /** Tira uma guilda que acabou em outro servidor (as outras guildas chegam já sem ela como aliada). */
    void drop(UUID id) {
        Guild g = guilds.remove(id);
        if (g != null) {
            for (UUID m : g.members.keySet()) {
                byMember.remove(m, id);
            }
        }
    }

    public void addPendingRefund(UUID p, long amount) {
        if (amount > 0) {
            pendingRefunds.merge(p, amount, Long::sum);
        }
    }

    /** Tira e devolve o reembolso pendente do jogador (0 se não houver). */
    public long takePendingRefund(UUID p) {
        Long v = pendingRefunds.remove(p);
        return v == null ? 0 : v;
    }

    Map<UUID, Long> pendingRefunds() {
        return pendingRefunds;
    }

    void restoreLeft(UUID p, long when) {
        leftAt.put(p, when);
    }

    Map<UUID, Long> leftAt() {
        return leftAt;
    }

    void clear() {
        pendingRefunds.clear();
        guilds.clear();
        byMember.clear();
        leftAt.clear();
    }
}
