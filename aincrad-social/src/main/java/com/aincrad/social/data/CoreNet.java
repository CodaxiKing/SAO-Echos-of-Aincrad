package com.aincrad.social.data;

import com.aincrad.core.AincradCore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * A ligação do {@link CoreStorage} com o AincradCore de verdade: MessageBus e agendador do
 * servidor. Só é carregada quando o AincradCore está ligado.
 */
public final class CoreNet implements CoreStorage.Net {

    private final Plugin plugin;
    private final AincradCore core = AincradCore.get();

    public CoreNet(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Monta o armazenamento no banco do core. */
    public static CoreStorage storage(Plugin plugin, CoreStorage.Host host) {
        return new CoreStorage(AincradCore.get().database(), new CoreNet(plugin), host, plugin.getLogger(), plugin.getDataFolder());
    }

    /** Tira as assinaturas do MessageBus (plugin desligando). */
    public static void unsubscribe() {
        AincradCore.get().bus().unsubscribeAll("social:");
    }

    @Override
    public String server() {
        return core.server();
    }

    @Override
    public void publish(String channel, String payload) {
        core.publish(channel, payload);
    }

    @Override
    public void subscribe(String channel, Consumer<String> handler) {
        // O core entrega também o que este servidor publicou; aqui só interessa o dos outros.
        core.subscribe(channel, m -> {
            if (!core.server().equals(m.origin())) {
                handler.accept(m.payload());
            }
        });
    }

    @Override
    public void main(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    @Override
    public void later(long ticks, Runnable r) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
        }
    }

    @Override
    public void every(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskTimer(plugin, r, ticks, ticks);
    }
}
