package com.aincrad.ui.prefs;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.config.UiSettings.DrawMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/** Preferências do jogador (modo do menu, HUD), guardadas no próprio jogador (PDC). */
public final class PlayerPrefs {

    private final AincradUIPlugin plugin;
    private final NamespacedKey modeKey;
    private final NamespacedKey hudKey;

    public PlayerPrefs(AincradUIPlugin plugin) {
        this.plugin = plugin;
        this.modeKey = new NamespacedKey(plugin, "modo_menu");
        this.hudKey = new NamespacedKey(plugin, "hud");
    }

    public DrawMode mode(Player p) {
        String raw = p.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
        return DrawMode.parse(raw, plugin.settings().defaultMode);
    }

    public void setMode(Player p, DrawMode mode) {
        p.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode.name().toLowerCase(Locale.ROOT));
    }

    public boolean hud(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Byte b = pdc.get(hudKey, PersistentDataType.BYTE);
        return b == null || b != 0;
    }

    public void setHud(Player p, boolean on) {
        p.getPersistentDataContainer().set(hudKey, PersistentDataType.BYTE, (byte) (on ? 1 : 0));
    }
}
