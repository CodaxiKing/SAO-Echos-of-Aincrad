package com.aincrad.social;

import com.aincrad.social.data.CoreNet;
import com.aincrad.social.data.CoreStorage;
import com.aincrad.social.data.Storage;
import com.aincrad.social.guild.Guild;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Liga o AincradSocial ao banco do AincradCore. Só é carregada quando o core está no servidor:
 * diz ao armazenamento o que fazer aqui quando outro servidor muda alguma coisa.
 */
final class CoreLink {

    private CoreLink() {
    }

    static Storage create(AincradSocialPlugin plugin) {
        return CoreNet.storage(plugin, new CoreStorage.Host() {
            @Override
            public void refreshNames(Collection<UUID> players) {
                for (UUID id : players) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        plugin.guildManager().refreshName(p);
                    }
                }
            }

            @Override
            public void guildRemoved(UUID guild) {
                plugin.guildVault().forceClose(guild);
            }

            @Override
            public void inboxGrew(UUID player, int unread) {
                Player p = Bukkit.getPlayer(player);
                if (p != null) {
                    plugin.messages().send(p, "mensagem.nao_lidas", Placeholder.unparsed("total", String.valueOf(unread)));
                }
            }

            @Override
            public void chat(Collection<UUID> guilds, Component line) {
                Set<Player> to = new LinkedHashSet<>();
                for (UUID id : guilds) {
                    Guild g = plugin.guilds().get(id);
                    if (g == null) {
                        continue;
                    }
                    for (UUID m : g.members().keySet()) {
                        Player p = Bukkit.getPlayer(m);
                        if (p != null) {
                            to.add(p);
                        }
                    }
                }
                to.forEach(p -> p.sendMessage(line));
            }
        });
    }
}
