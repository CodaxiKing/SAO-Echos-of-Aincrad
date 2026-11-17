package com.aincrad.social;

import com.aincrad.social.data.SocialStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/** Nomes e cabeças de jogadores, online ou não. */
public final class Names {

    private final SocialStore store;

    public Names(SocialStore store) {
        this.store = store;
    }

    /** Jogador pelo nome: online, depois os nomes já vistos, depois o cache do servidor. */
    public UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        UUID known = store.byName(name);
        if (known != null) {
            return known;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null ? cached.getUniqueId() : null;
    }

    public String name(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            return p.getName();
        }
        String n = store.name(id);
        if (n != null) {
            return n;
        }
        String offline = Bukkit.getOfflinePlayer(id).getName();
        return offline != null ? offline : id.toString().substring(0, 8);
    }

    public static Player online(UUID id) {
        return Bukkit.getPlayer(id);
    }

    /** Cabeça com a skin do jogador (o cliente baixa a skin; o servidor não faz requisição). */
    public static ItemStack head(UUID id) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            head.setItemMeta(meta);
        }
        return head;
    }
}
