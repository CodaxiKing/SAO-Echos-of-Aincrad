package com.aincrad.social;

import com.aincrad.social.config.ConfigFiles;
import com.aincrad.social.config.Messages;
import com.aincrad.social.config.Settings;
import com.aincrad.social.data.YamlSocialStore;
import com.aincrad.social.menu.MenuHook;
import com.aincrad.social.service.FriendService;
import com.aincrad.social.service.MessageService;
import com.aincrad.social.service.PartyService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * AincradSocial: amigos, party e mensagens. Funciona por comandos e chat; com o AincradUI,
 * tudo aparece também no menu SAO (Party, Amigos, Caixa, Escrever) e os membros da party ganham
 * barras de vida embaixo da barra do jogador.
 */
public final class AincradSocialPlugin extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private YamlSocialStore store;
    private Names names;
    private FriendService friends;
    private PartyService parties;
    private MessageService inbox;
    private SocialActions actions;
    private ChatInput chatInput;
    private MenuHook menu = MenuHook.NONE;
    private boolean saveScheduled;

    @Override
    public void onEnable() {
        settings = new Settings(ConfigFiles.load(this, "config.yml"));
        messages = new Messages(ConfigFiles.load(this, "messages.yml"));
        store = new YamlSocialStore(new File(getDataFolder(), "dados.yml"), getLogger());
        store.load();
        names = new Names(store);
        friends = new FriendService(store, settings.maxFriends);
        parties = new PartyService(settings.partyMax, settings.inviteSeconds * 1000L, System::currentTimeMillis);
        inbox = new MessageService(store, settings.inboxMax, System::currentTimeMillis);
        actions = new SocialActions(this);
        chatInput = new ChatInput(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(chatInput, this);
        pm.registerEvents(new SocialListener(this), this);

        SocialCommands commands = new SocialCommands(this);
        for (String name : new String[]{"amigo", "party", "mensagem", "caixa"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }

        // O AincradUI liga antes (softdepend). A classe UiMenu só é carregada se ele existir.
        if (pm.isPluginEnabled("AincradUI")) {
            menu = com.aincrad.social.menu.UiMenu.connect(this);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            store.rememberName(p.getUniqueId(), p.getName());
        }
        getLogger().info("AincradSocial ligado" + (menu == MenuHook.NONE ? " (sem AincradUI: só comandos)." : " com o menu SAO."));
    }

    @Override
    public void onDisable() {
        menu.shutdown();
        if (store != null && store.dirty()) {
            store.save();
        }
    }

    /** Agenda a gravação para daqui a 5 s (várias mudanças viram uma gravação só, fora da thread principal). */
    public void saveSoon() {
        if (saveScheduled || !isEnabled()) {
            return;
        }
        saveScheduled = true;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            saveScheduled = false;
            if (!store.dirty()) {
                return;
            }
            String yaml = store.snapshot();
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> store.write(yaml));
        }, 100L);
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

    public YamlSocialStore store() {
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
}
