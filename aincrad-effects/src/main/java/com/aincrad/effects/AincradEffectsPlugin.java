package com.aincrad.effects;

import com.aincrad.effects.api.AincradEffectsAPI;
import com.aincrad.effects.api.AincradEffectsAPIImpl;
import com.aincrad.effects.command.EffectCommand;
import com.aincrad.effects.config.ConfigFiles;
import com.aincrad.effects.config.Messages;
import com.aincrad.effects.config.Settings;
import com.aincrad.effects.engine.EffectEngine;
import com.aincrad.effects.engine.EffectLoader;
import com.aincrad.effects.engine.Limits;
import com.aincrad.effects.engine.TextRenderer;
import com.aincrad.effects.image.ImageRegistry;
import com.aincrad.effects.listener.SafetyListener;
import com.aincrad.effects.pack.PackManager;
import com.aincrad.effects.pack.PackStatus;
import com.aincrad.effects.trigger.TriggerManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * AincradEffects: motor de efeitos visuais e sonoros do servidor inteiro, com resource pack único.
 * Funciona sozinho; Floors, Bosses, UI e outros plugins usam a API {@link AincradEffectsAPI}.
 */
public final class AincradEffectsPlugin extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private ImageRegistry images;
    private Limits limits;
    private EffectEngine engine;
    private PackManager pack;
    private PackStatus packStatus;
    private TriggerManager triggers;

    @Override
    public void onEnable() {
        pack = new PackManager(this);
        packStatus = new PackStatus(this);
        triggers = new TriggerManager(this);
        limits = new Limits(600, 40);
        engine = new EffectEngine(this, limits);

        pack.copyDefaults(getFile());
        copyDefaultEffects();
        List<String> errors = loadAll();
        errors.forEach(e -> getLogger().warning(e));

        packStatus.onSettled(triggers::packSettled);
        var pm = getServer().getPluginManager();
        pm.registerEvents(packStatus, this);
        pm.registerEvents(triggers, this);
        pm.registerEvents(new SafetyListener(this), this);

        PluginCommand cmd = getCommand("efeito");
        if (cmd != null) {
            EffectCommand ec = new EffectCommand(this);
            cmd.setExecutor(ec);
            cmd.setTabCompleter(ec);
        }

        // Registrado já no onEnable: plugins que dependem do Effects (softdepend) o encontram no deles.
        getServer().getServicesManager().register(AincradEffectsAPI.class, new AincradEffectsAPIImpl(this), this, ServicePriority.Normal);

        // O pack é montado no primeiro tick, depois que os outros plugins registraram suas pastas.
        pack.scheduleBuild();
        getLogger().info("AincradEffects ligado: " + engine.ids().size() + " efeitos, " + images.presentCount()
                + " imagens, " + triggers.count() + " gatilhos.");
    }

    @Override
    public void onDisable() {
        if (engine != null) {
            engine.stopAll();
        }
        if (pack != null) {
            pack.stopHttp();
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    /** Lê config, mensagens, imagens, efeitos e gatilhos. @return erros encontrados */
    private List<String> loadAll() {
        List<String> errors = new ArrayList<>();
        settings = new Settings(ConfigFiles.load(this, "config.yml"));
        messages = new Messages(ConfigFiles.load(this, "messages.yml"));
        limits.configure(settings.maxParticlesPerPlayerTick, settings.maxEntitiesPerPlayer);
        images = ImageRegistry.load(ConfigFiles.load(this, "images.yml"), pack.texturesRoot(), getLogger());
        engine.setText(new TextRenderer(images, p -> packStatus.hasPack(p)));
        EffectLoader.Result result = EffectLoader.loadFolder(new File(getDataFolder(), "effects"), images);
        engine.setEffects(result.effects());
        errors.addAll(result.errors());
        errors.addAll(triggers.load(ConfigFiles.load(this, "triggers.yml")));
        return errors;
    }

    /** /efeito reload: relê tudo e reconstrói o pack na hora. */
    public List<String> reloadAll() {
        List<String> errors = loadAll();
        errors.forEach(e -> getLogger().warning(e));
        pack.build();
        return errors;
    }

    /** Copia os exemplos de effects/ do .jar que ainda não existem na pasta. */
    private void copyDefaultEffects() {
        File dir = new File(getDataFolder(), "effects");
        try (JarFile jar = new JarFile(getFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.isDirectory() || !e.getName().startsWith("effects/") || !e.getName().endsWith(".yml")) {
                    continue;
                }
                File out = new File(getDataFolder(), e.getName());
                if (out.exists()) {
                    continue;
                }
                Files.createDirectories(dir.toPath());
                try (InputStream in = jar.getInputStream(e)) {
                    Files.copy(in, out.toPath());
                }
            }
        } catch (IOException e) {
            getLogger().warning("Não foi possível copiar os efeitos de exemplo: " + e.getMessage());
        }
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public ImageRegistry images() {
        return images;
    }

    public Limits limits() {
        return limits;
    }

    public EffectEngine engine() {
        return engine;
    }

    public PackManager pack() {
        return pack;
    }

    public PackStatus packStatus() {
        return packStatus;
    }

    public TriggerManager triggers() {
        return triggers;
    }
}
