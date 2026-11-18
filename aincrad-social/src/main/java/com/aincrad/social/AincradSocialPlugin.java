package com.aincrad.social;

import com.aincrad.social.config.ConfigFiles;
import com.aincrad.social.config.Messages;
import com.aincrad.social.config.Settings;
import com.aincrad.social.data.SocialStore;
import com.aincrad.social.data.Storage;
import com.aincrad.social.data.YamlStorage;
import com.aincrad.social.api.AincradSocialAPI;
import com.aincrad.social.api.AincradSocialAPIImpl;
import com.aincrad.social.duel.DuelCommand;
import com.aincrad.social.duel.DuelManager;
import com.aincrad.social.party.PartyRewards;
import com.aincrad.social.guild.GuildCommands;
import com.aincrad.social.guild.GuildConfig;
import com.aincrad.social.guild.GuildListener;
import com.aincrad.social.guild.GuildManager;
import com.aincrad.social.guild.GuildService;
import com.aincrad.social.guild.GuildVault;
import com.aincrad.social.hook.Hooks;
import com.aincrad.social.menu.MenuHook;
import com.aincrad.social.service.FriendService;
import com.aincrad.social.service.MessageService;
import com.aincrad.social.service.PartyService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;


/**
 * AincradSocial: amigos, party, guilda e mensagens. Funciona por comandos e chat; com o AincradUI,
 * tudo aparece também no menu SAO (Party, Amigos, Caixa, Escrever) e os membros da party ganham
 * barras de vida embaixo da barra do jogador.
 */
public final class AincradSocialPlugin extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private Storage storage;
    private SocialStore store;
    private Names names;
    private FriendService friends;
    private PartyService parties;
    private MessageService inbox;
    private SocialActions actions;
    private ChatInput chatInput;
    private MenuHook menu = MenuHook.NONE;
    private GuildConfig guildConfig;
    private GuildService guilds;
    private GuildManager guildManager;
    private GuildVault guildVault;
    private Hooks hooks;
    private DuelManager duels;
    private PartyRewards partyRewards;
    private com.aincrad.social.party.PartyNet partyNet;

    @Override
    public void onEnable() {
        settings = new Settings(ConfigFiles.load(this, "config.yml"));
        messages = new Messages(ConfigFiles.load(this, "messages.yml"));
        storage = chooseStorage();
        store = storage.store();
        names = new Names(store);
        friends = new FriendService(store, settings.maxFriends);
        parties = new PartyService(settings.partyMax, settings.inviteSeconds * 1000L, System::currentTimeMillis);
        inbox = new MessageService(store, settings.inboxMax, System::currentTimeMillis);
        actions = new SocialActions(this);
        chatInput = new ChatInput(this);

        guildConfig = new GuildConfig(ConfigFiles.load(this, "guilds.yml"));
        guilds = new GuildService(guildConfig.rules, System::currentTimeMillis);
        try {
            storage.load(guilds);
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Não foi possível ler os dados: o AincradSocial não vai ligar", e);
            storage = null;
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        guildManager = new GuildManager(this);
        guildVault = new GuildVault(this);
        hooks = new Hooks(getLogger());
        duels = new DuelManager(this);
        partyRewards = new PartyRewards(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(chatInput, this);
        pm.registerEvents(new SocialListener(this), this);
        pm.registerEvents(new GuildListener(this), this);
        pm.registerEvents(guildVault, this);
        pm.registerEvents(duels, this);
        pm.registerEvents(partyRewards, this);

        SocialCommands commands = new SocialCommands(this);
        for (String name : new String[]{"amigo", "party", "mensagem", "caixa"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }
        GuildCommands guildCommands = new GuildCommands(this);
        for (String name : new String[]{"guilda", "g", "ga"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(guildCommands);
                cmd.setTabCompleter(guildCommands);
            }
        }
        PluginCommand duelCmd = getCommand("duelo");
        if (duelCmd != null) {
            DuelCommand dc = new DuelCommand(this);
            duelCmd.setExecutor(dc);
            duelCmd.setTabCompleter(dc);
        }
        Bukkit.getScheduler().runTaskTimer(this, duels::tick, 10L, 10L);
        if (storage.network()) {
            partyNet = new com.aincrad.social.party.PartyNet(this);
            partyNet.start();
        }
        getServer().getServicesManager().register(AincradSocialAPI.class, new AincradSocialAPIImpl(this), this, ServicePriority.Normal);
        // Economia, andares, quests e reputação: procurados depois que todos os plugins ligaram.
        Bukkit.getScheduler().runTask(this, () -> {
            hooks.detect();
            if (partyRewards.hookXp()) {
                getLogger().info("Party: XP dos mobs do AincradRPG dividido entre os membros próximos.");
            }
            runMaintenance();
            Bukkit.getOnlinePlayers().forEach(guildManager::refreshName);
        });
        // Manutenção da guilda a cada 10 minutos (recrutas, líder inativo, guildas pequenas).
        Bukkit.getScheduler().runTaskTimer(this, this::runMaintenance, 12_000L, 12_000L);

        // O AincradUI liga antes (softdepend). A classe UiMenu só é carregada se ele existir.
        if (pm.isPluginEnabled("AincradUI")) {
            menu = com.aincrad.social.menu.UiMenu.connect(this);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            store.rememberName(p.getUniqueId(), p.getName());
        }
        getLogger().info("Dados: " + (storage.network() ? "banco do AincradCore (valem para a rede inteira)." : "arquivos nesta pasta (só este servidor)."));
        getLogger().info("AincradSocial ligado" + (menu == MenuHook.NONE ? " (sem AincradUI: só comandos)." : " com o menu SAO."));
    }

    @Override
    public void onDisable() {
        menu.shutdown();
        if (duels != null) {
            duels.shutdown();
        }
        if (guildVault != null && storage != null) {
            guildVault.saveAll();
        }
        if (storage != null) {
            storage.shutdown();
            if (storage.network()) {
                try {
                    com.aincrad.social.data.CoreNet.unsubscribe();
                } catch (LinkageError | RuntimeException ignored) {
                    // o core já desligou
                }
            }
        }
        if (guildManager != null) {
            guildManager.clearTeams();
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    /** Banco do AincradCore quando ele existe (a rede inteira vê os mesmos dados); senão, arquivos. */
    private Storage chooseStorage() {
        String mode = settings.storageMode;
        boolean core = getServer().getPluginManager().isPluginEnabled("AincradCore");
        if (mode.equals("arquivo") || (!core && !mode.equals("banco"))) {
            return new YamlStorage(this);
        }
        if (!core) {
            getLogger().warning("dados.onde é \"banco\", mas o AincradCore não está no servidor: usando arquivos.");
            return new YamlStorage(this);
        }
        try {
            return CoreLink.create(this);
        } catch (LinkageError | RuntimeException e) {
            getLogger().log(java.util.logging.Level.WARNING, "AincradCore incompatível: usando arquivos", e);
            return new YamlStorage(this);
        }
    }

    /** Manutenção das guildas: numa rede, só um servidor faz por vez; os outros só pagam reembolsos. */
    private void runMaintenance() {
        storage.exclusive("manutencao", mine -> {
            if (mine) {
                guildManager.maintenance();
            } else {
                guildManager.payRefunds();
            }
        });
    }

    /** Guildas mudaram: grava daqui a pouco, fora da thread principal. */
    public void saveGuildsSoon() {
        if (storage != null) {
            storage.saveGuildsSoon();
        }
    }

    /** Amigos, pedidos, caixa ou nomes mudaram: grava daqui a pouco, fora da thread principal. */
    public void saveSoon() {
        if (storage != null) {
            storage.saveSoon();
        }
    }

    public Storage storage() {
        return storage;
    }

    /** /caixa abre a caixa no menu, quando o AincradUI existe. */
    boolean openInboxMenu(Player p) {
        return menu.openInbox(p);
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public SocialStore store() {
        return store;
    }

    public Names names() {
        return names;
    }

    public FriendService friends() {
        return friends;
    }

    public PartyService parties() {
        return parties;
    }

    public MessageService inbox() {
        return inbox;
    }

    public SocialActions actions() {
        return actions;
    }

    public ChatInput chatInput() {
        return chatInput;
    }

    public MenuHook menu() {
        return menu;
    }

    public GuildConfig guildConfig() {
        return guildConfig;
    }

    public GuildService guilds() {
        return guilds;
    }

    public GuildManager guildManager() {
        return guildManager;
    }

    public GuildVault guildVault() {
        return guildVault;
    }

    /** A party na rede; null num servidor só (dados em arquivo). */
    public com.aincrad.social.party.PartyNet partyNet() {
        return partyNet;
    }

    public DuelManager duels() {
        return duels;
    }

    public Hooks hooks() {
        return hooks;
    }
}
