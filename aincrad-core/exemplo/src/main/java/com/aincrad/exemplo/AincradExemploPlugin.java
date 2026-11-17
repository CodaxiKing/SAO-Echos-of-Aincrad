package com.aincrad.exemplo;

import com.aincrad.core.AincradCore;
import com.aincrad.core.AincradReloadEvent;
import com.aincrad.core.command.SubCommand;
import com.aincrad.core.menu.Menu;
import com.aincrad.core.message.Messages;
import com.aincrad.core.profile.PlayerProfile;
import com.aincrad.core.region.RegionEnterEvent;
import com.aincrad.core.region.RegionLeaveEvent;
import com.aincrad.core.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin de exemplo do AincradCore (critério de pronto da etapa 0):
 * {@code /aincrad exemplo} abre um menu; o botão soma 1 num contador guardado no perfil do jogador;
 * entrar ou sair de uma região mostra uma mensagem; {@code /aincrad reload} recarrega as mensagens.
 * Serve de modelo para os outros plugins.
 */
public final class AincradExemploPlugin extends JavaPlugin implements Listener {

    private AincradCore core;
    private Messages msg;

    @Override
    public void onEnable() {
        core = AincradCore.get();
        msg = core.messages(this);
        getServer().getPluginManager().registerEvents(this, this);
        core.commands().register(this, new SubCommand() {
            @Override
            public String name() {
                return "exemplo";
            }

            @Override
            public String permission() {
                return null;
            }

            @Override
            public String usage() {
                return "exemplo";
            }

            @Override
            public Component description() {
                return Component.text("Menu de exemplo do AincradCore");
            }

            @Override
            public void execute(CommandSender sender, String[] args) {
                if (sender instanceof Player p) {
                    open(p);
                } else {
                    msg.send(sender, "geral.so_jogador");
                }
            }
        });
    }

    private void open(Player p) {
        PlayerProfile profile = core.profile(p);
        int clicks = profile.getInt(this, "cliques", 0);
        Menu menu = Menu.of(msg.get("menu.titulo")).rows(3)
                .item(Menu.Button.of(ItemBuilder.of(Material.EMERALD).build(),
                        msg.get("menu.contar"), pl -> {
                            PlayerProfile pr = core.profile(pl);
                            int n = pr.getInt(this, "cliques", 0) + 1;
                            pr.set(this, "cliques", n);
                            msg.send(pl, "contou", Messages.vars("n", n));
                            open(pl);
                        }).lore(msg.list("menu.contar_lore", Messages.vars("n", clicks))))
                .item(Menu.Button.of(ItemBuilder.of(Material.BARRIER).build(), msg.get("menu.zerar"), pl -> {
                    core.profile(pl).remove(this, "cliques");
                    msg.send(pl, "zerou");
                    open(pl);
                }))
                .button(Menu.Button.of(ItemBuilder.of(Material.OAK_DOOR).build(), msg.get("menu.fechar"), pl -> { }).closing());
        core.menus().open(p, menu);
    }

    @EventHandler
    public void onEnter(RegionEnterEvent event) {
        msg.actionBar(event.getPlayer(), "regiao_entrou", Messages.vars("nome", event.region().name()));
    }

    @EventHandler
    public void onLeave(RegionLeaveEvent event) {
        msg.actionBar(event.getPlayer(), "regiao_saiu", Messages.vars("nome", event.region().name()));
    }

    @EventHandler
    public void onReload(AincradReloadEvent event) {
        msg.reload();
        event.reloaded(getName());
    }
}
