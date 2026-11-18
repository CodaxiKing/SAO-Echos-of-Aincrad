package com.aincrad.social.data;

import com.aincrad.social.guild.GuildService;
import com.aincrad.social.guild.GuildStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collection;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Dados em arquivo: dados.yml (amigos, pedidos, caixa, nomes) e guildas.yml. Para um servidor
 * só, sem o AincradCore. Gravado 5 s depois de cada mudança, fora da thread principal.
 */
public final class YamlStorage implements Storage {

    private final Plugin plugin;
    private final YamlSocialStore store;
    private final GuildStore guildStore;
    private GuildService guilds;
    private boolean saveScheduled;
    private boolean guildSaveScheduled;

    public YamlStorage(Plugin plugin) {
        this.plugin = plugin;
        this.store = new YamlSocialStore(new File(plugin.getDataFolder(), "dados.yml"), plugin.getLogger());
        this.guildStore = new GuildStore(new File(plugin.getDataFolder(), "guildas.yml"), plugin.getLogger());
    }

    @Override
    public SocialStore store() {
        return store;
    }

    @Override
    public void load(GuildService guilds) {
        this.guilds = guilds;
        store.load();
        guildStore.load(guilds);
    }

    @Override
    public void saveSoon() {
        if (saveScheduled || !plugin.isEnabled()) {
            return;
        }
        saveScheduled = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            saveScheduled = false;
            if (!store.dirty()) {
                return;
            }
            String yaml = store.snapshot();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> store.write(yaml));
        }, 100L);
    }

    @Override
    public void saveGuildsSoon() {
        if (guildSaveScheduled || !plugin.isEnabled() || guilds == null) {
            return;
        }
        guildSaveScheduled = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            guildSaveScheduled = false;
            String yaml = guildStore.snapshot(guilds);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> guildStore.write(yaml));
        }, 100L);
    }

    @Override
    public void shutdown() {
        if (store.dirty()) {
            store.save();
        }
        if (guilds != null) {
            guildStore.write(guildStore.snapshot(guilds));
        }
    }

    @Override
    public boolean network() {
        return false;
    }

    // Um servidor só: o cofre é sempre dele, e os itens vão dentro de guildas.yml.

    @Override
    public void lockVault(UUID guild, BiConsumer<Boolean, String> ready) {
        ready.accept(true, null);
    }

    @Override
    public void saveVault(UUID guild, String data) {
        saveGuildsSoon();
    }

    @Override
    public void unlockVault(UUID guild, String data) {
        saveGuildsSoon();
    }

    @Override
    public void exclusive(String name, Consumer<Boolean> run) {
        run.accept(true);
    }

    @Override
    public void publish(String channel, String payload) {
        // sem rede
    }

    @Override
    public void subscribe(String channel, Consumer<String> handler) {
        // sem rede
    }

    @Override
    public void publishChat(Collection<UUID> guilds, Component line) {
        // sem rede
    }
}
