package com.aincrad.ui;

import com.aincrad.ui.api.AincradUIAPI;
import com.aincrad.ui.api.AincradUIAPIImpl;
import com.aincrad.ui.command.UiCommands;
import com.aincrad.ui.config.ConfigFiles;
import com.aincrad.ui.config.Messages;
import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.death.DeathEffects;
import com.aincrad.ui.glyph.GlyphRegistry;
import com.aincrad.ui.hud.HealthHud;
import com.aincrad.ui.listener.CommandRedirectListener;
import com.aincrad.ui.listener.MenuListener;
import com.aincrad.ui.menu.MenuRegistry;
import com.aincrad.ui.pack.EffectsBridge;
import com.aincrad.ui.pack.PackService;
import com.aincrad.ui.pack.PackStatusTracker;
import com.aincrad.ui.prefs.PlayerPrefs;
import com.aincrad.ui.screen.Screens;
import com.aincrad.ui.screen.SessionManager;
import com.aincrad.ui.sound.SoundPlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** AincradUI: interface estilo SAO (menu de ícones, HUD de vida, vida crítica, tela de morte). */
public final class AincradUIPlugin extends JavaPlugin {

    private UiSettings settings;
    private Messages messages;
    private GlyphRegistry glyphs;
    private final MenuRegistry menu = new MenuRegistry();
    private EffectsBridge effects;
    private PackService pack;
    private PackStatusTracker packStatus;
    private SoundPlayer sounds;
    private PlayerPrefs prefs;
    private SessionManager sessions;
    private Screens screens;
    private HealthHud hud;
    private DeathEffects deathEffects;

    @Override
    public void onEnable() {
        pack = new PackService(this);
        packStatus = new PackStatusTracker(this);
        sounds = new SoundPlayer(this);
        prefs = new PlayerPrefs(this);
        sessions = new SessionManager(this);
        screens = new Screens(this);
        hud = new HealthHud(this);
        deathEffects = new DeathEffects(this);

        loadAll();

        var pm = getServer().getPluginManager();
        pm.registerEvents(packStatus, this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new CommandRedirectListener(), this);
        pm.registerEvents(hud, this);
        pm.registerEvents(deathEffects, this);

        UiCommands commands = new UiCommands(this);
        for (String name : new String[]{"menu", "creditos", "aincradui"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }

        getServer().getServicesManager().register(AincradUIAPI.class, new AincradUIAPIImpl(this), this, ServicePriority.Normal);

        sessions.start();
        hud.start();
        getLogger().info("AincradUI ligado: " + glyphs.presentCount() + " glifos, pack " + pack.mode()
                + (effects.available() ? " (AincradEffects encontrado)" : " (sem AincradEffects)"));
    }

    @Override
    public void onDisable() {
        if (sessions != null) {
            sessions.stop();
        }
        if (hud != null) {
            hud.stop();
        }
        if (pack != null) {
            pack.stopHttp();
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    /** Lê as configs, prepara o pack e gera fontes/modelos. Ordem importa: o pack antes dos glifos. */
    private void loadAll() {
        settings = new UiSettings(ConfigFiles.load(this, "ui.yml"));
        messages = new Messages(ConfigFiles.load(this, "messages.yml"));
        effects = EffectsBridge.detect(getLogger());
        pack.prepare(getFile());
        glyphs = GlyphRegistry.load(ConfigFiles.load(this, "glyphs.yml"), pack.texturesRoot(), getLogger());
        menu.load(ConfigFiles.load(this, "menu.yml"));
        pack.build(glyphs, settings, effects);
    }

    /** /aincrad ui reload: fecha menus abertos, relê tudo e reinicia a HUD. */
    public void reloadAll() {
        sessions.closeAll();
        hud.stop();
        loadAll();
        hud.start();
    }

    public UiSettings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public GlyphRegistry glyphs() {
        return glyphs;
    }

    public MenuRegistry menu() {
        return menu;
    }

    public EffectsBridge effects() {
        return effects;
    }

    public PackService pack() {
        return pack;
    }

    public PackStatusTracker packStatus() {
        return packStatus;
    }

    public SoundPlayer sounds() {
        return sounds;
    }

    public PlayerPrefs prefs() {
        return prefs;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public Screens screens() {
        return screens;
    }

    public HealthHud hud() {
        return hud;
    }

    public DeathEffects deathEffects() {
        return deathEffects;
    }
}
