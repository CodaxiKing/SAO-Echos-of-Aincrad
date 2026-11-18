package com.aincrad.core;

import com.aincrad.core.bus.MessageBus;
import com.aincrad.core.command.CommandRegistry;
import com.aincrad.core.config.ConfigFile;
import com.aincrad.core.db.Database;
import com.aincrad.core.menu.MenuService;
import com.aincrad.core.message.Messages;
import com.aincrad.core.profile.PlayerProfile;
import com.aincrad.core.profile.ProfileService;
import com.aincrad.core.region.RegionManager;
import com.aincrad.core.util.Bedrock;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Ponto de entrada dos outros plugins Aincrad (todos declaram {@code depend: [AincradCore]}).
 *
 * <pre>
 * AincradCore core = AincradCore.get();
 * Messages msg = core.messages(this);
 * core.database().migrate(getName(), List.of("CREATE TABLE ..."));
 * core.commands().register(this, new MeuSubcomando());
 * core.profile(player).set(this, "andar", 3);
 * core.publish("floors:unlock", "3");
 * </pre>
 */
public final class AincradCore {

    private static volatile AincradCore instance;

    private final AincradCorePlugin plugin;
    private final Map<String, Messages> messages = new ConcurrentHashMap<>();
    private final com.aincrad.core.ui.UiService ui = new com.aincrad.core.ui.UiService();

    AincradCore(AincradCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** O core ligado. Lança erro se for chamado antes do core ligar (falta "depend" no plugin.yml). */
    public static AincradCore get() {
        AincradCore c = instance;
        if (c == null) {
            throw new IllegalStateException("AincradCore não está ligado: declare depend: [AincradCore] no plugin.yml");
        }
        return c;
    }

    static void set(AincradCore core) {
        instance = core;
    }

    public Plugin plugin() {
        return plugin;
    }

    /** Nome deste servidor na rede (config.yml → servidor). */
    public String server() {
        return plugin.bus().server();
    }

    // ------------------------------------------------------------------ config e mensagens

    /** messages.yml do plugin (o prefixo e os textos comuns vêm do core). Fica em cache. */
    public Messages messages(Plugin p) {
        return messages.computeIfAbsent(p.getName(), k -> new Messages(p, plugin.coreMessages()));
    }

    /** Recarrega o messages.yml de todos os plugins. */
    public void reloadMessages() {
        messages.values().forEach(Messages::reload);
    }

    /** Um YAML do plugin com padrões do .jar e chaves novas adicionadas sozinhas. */
    public ConfigFile config(Plugin p, String file) {
        return new ConfigFile(p, file);
    }

    // ------------------------------------------------------------------ serviços

    public Database database() {
        return plugin.database();
    }

    public CommandRegistry commands() {
        return plugin.commands();
    }

    public RegionManager regions() {
        return plugin.regions();
    }

    public MenuService menus() {
        return plugin.menus();
    }

    /** O que os plugins pedem à interface do jogador (o AincradUI, se instalado). */
    public com.aincrad.core.ui.UiService ui() {
        return ui;
    }

    public ProfileService profiles() {
        return plugin.profiles();
    }

    /** Perfil de um jogador online. */
    public PlayerProfile profile(Player p) {
        return plugin.profiles().get(p);
    }

    public MessageBus bus() {
        return plugin.bus();
    }

    /** Publica para a rede; neste servidor os handlers rodam já, na thread principal. */
    public void publish(String channel, String payload) {
        plugin.bus().publish(channel, payload, this::runSync);
    }

    /** Recebe avisos da rede (o handler roda na thread principal). */
    public void subscribe(String channel, Consumer<MessageBus.Message> handler) {
        plugin.bus().subscribe(channel, handler);
    }

    public boolean isBedrock(Player p) {
        return Bedrock.isBedrock(p);
    }

    /** Roda na thread principal (agora, se já estiver nela). */
    public void runSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    /** Registra a API de um plugin para os outros (ServicesManager do Bukkit). */
    public <T> void provide(Class<T> type, T impl, Plugin owner) {
        Bukkit.getServicesManager().register(type, impl, owner, ServicePriority.Normal);
    }

    /** API de outro plugin, ou null se ele não estiver ligado. */
    public <T> T service(Class<T> type) {
        RegisteredServiceProvider<T> r = Bukkit.getServicesManager().getRegistration(type);
        return r == null ? null : r.getProvider();
    }
}
