package com.aincrad.ui;

import com.aincrad.ui.api.AincradUIAPI;
import com.aincrad.ui.api.AincradUIAPIImpl;
import com.aincrad.ui.command.UiCommands;
import com.aincrad.ui.config.ConfigFiles;
import com.aincrad.ui.config.Messages;
import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.death.DeathEffects;
import com.aincrad.ui.glyph.GlyphRegistry;
import com.aincrad.ui.flat.FlatManager;
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
    private final MenuRegistry menu = new MenuRegistry(this::deathGame);
    private volatile boolean deathGame;
    private EffectsBridge effects;
    private PackService pack;
    private PackStatusTracker packStatus;
    private SoundPlayer sounds;
    private PlayerPrefs prefs;
    private SessionManager sessions;
    private Screens screens;
    private HealthHud hud;
    private com.aincrad.ui.hud.TargetTracker targets;
    private com.aincrad.ui.hud.ToastService toasts;
    private com.aincrad.ui.hud.BannerService banners;
    private FlatManager flat;
    private DeathEffects deathEffects;
    private com.aincrad.ui.listener.InventoryButton inventoryButton;

    @Override
    public void onEnable() {
        pack = new PackService(this);
        packStatus = new PackStatusTracker(this);
        sounds = new SoundPlayer(this);
        prefs = new PlayerPrefs(this);
        sessions = new SessionManager(this);
        screens = new Screens(this);
        hud = new HealthHud(this);
        targets = new com.aincrad.ui.hud.TargetTracker();
        toasts = new com.aincrad.ui.hud.ToastService(this);
        banners = new com.aincrad.ui.hud.BannerService(this);
        flat = new FlatManager(this);
        deathEffects = new DeathEffects(this);

        loadAll();

        var pm = getServer().getPluginManager();
        pm.registerEvents(packStatus, this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new CommandRedirectListener(), this);
        pm.registerEvents(hud, this);
        pm.registerEvents(targets, this);
        pm.registerEvents(toasts, this);
        pm.registerEvents(banners, this);
        pm.registerEvents(flat, this);
        pm.registerEvents(deathEffects, this);

        UiCommands commands = new UiCommands(this);
        for (String name : new String[]{"menu", "creditos", "aincradui"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
                cmd.setTabCompleter(commands);
            }
        }

        inventoryButton = new com.aincrad.ui.listener.InventoryButton(this);
        pm.registerEvents(inventoryButton, this);
        getServer().getScheduler().runTaskTimer(this, inventoryButton::tick, 40L, 40L);

        PluginCommand hudCmd = getCommand("hud");
        if (hudCmd != null) {
            com.aincrad.ui.command.HudCommand hc = new com.aincrad.ui.command.HudCommand(this);
            hudCmd.setExecutor(hc);
            hudCmd.setTabCompleter(hc);
        }

        AincradUIAPIImpl uiApi = new AincradUIAPIImpl(this);
        getServer().getServicesManager().register(AincradUIAPI.class, uiApi, this, ServicePriority.Normal);
        // Com o AincradCore, os menus dele marcados como flutuantes abrem no estilo SAO.
        if (getServer().getPluginManager().isPluginEnabled("AincradCore")) {
            try {
                com.aincrad.ui.core.CoreMenuBridge.register(this, uiApi);
                coreMenus = true;
            } catch (LinkageError | RuntimeException e) {
                getLogger().warning("AincradCore incompatível: os menus dele continuam em baú (" + e + ").");
            }
        }

        sessions.start();
        flat.start();
        hud.start();
        // No primeiro tick todos os plugins já ligaram: os avisos passam a ouvir os eventos deles.
        getServer().getScheduler().runTask(this, () -> {
            targets.resetHooks();
            hud.resetHooks();
            toasts.hookPlugins();
        });
        getLogger().info("AincradUI ligado: " + glyphs.presentCount() + " glifos, pack " + pack.mode()
                + (effects.available() ? " (AincradEffects encontrado)" : " (sem AincradEffects)"));
    }

    @Override
    public void onDisable() {
        if (inventoryButton != null) {
            inventoryButton.shutdown();
        }
        if (coreMenus) {
            try {
                com.aincrad.ui.core.CoreMenuBridge.unregister();
            } catch (LinkageError | RuntimeException ignored) {
                // o core já desligou
            }
            coreMenus = false;
        }
        if (sessions != null) {
            sessions.stop();
        }
        if (hud != null) {
            hud.stop();
        }
        if (flat != null) {
            flat.stop();
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
        flat.atlas().texturesRoot(pack.texturesRoot());
        flat.prepareBuiltins();
        pack.build(glyphs, settings, effects, flat.atlas());
    }

    /** Refaz só o pack (peças novas das telas planas). */
    public void rebuildPack() {
        pack.build(glyphs, settings, effects, flat.atlas());
    }

    public FlatManager flat() {
        return flat;
    }

    /** /aincrad ui reload: fecha menus abertos, relê tudo e reinicia a HUD. */
    public void reloadAll() {
        sessions.closeAll();
        flat.closeAll();
        hud.stop();
        loadAll();
        hud.start();
    }

    public UiSettings settings() {
        return settings;
    }

    /** O jogo da morte está valendo: pelo ui.yml ou porque outro plugin ligou pela API. */
    public boolean deathGame() {
        return deathGame || (settings != null && settings.logoutDeathGame);
    }

    public void deathGame(boolean on) {
        deathGame = on;
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

    private boolean coreMenus;

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

    public com.aincrad.ui.hud.TargetTracker targets() {
        return targets;
    }

    public com.aincrad.ui.hud.BannerService banners() {
        return banners;
    }

    public com.aincrad.ui.hud.ToastService toasts() {
        return toasts;
    }

    public HealthHud hud() {
        return hud;
    }

    public DeathEffects deathEffects() {
        return deathEffects;
    }
}
