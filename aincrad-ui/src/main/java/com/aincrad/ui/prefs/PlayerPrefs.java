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
    private final NamespacedKey hudDxKey;
    /** Quanto o jogador pode puxar a HUD para o centro, em pixels de interface. */
    public static final int HUD_DX_MAX = 160;

    public PlayerPrefs(AincradUIPlugin plugin) {
        this.plugin = plugin;
        this.modeKey = new NamespacedKey(plugin, "modo_menu");
        this.hudKey = new NamespacedKey(plugin, "hud");
        this.hudDxKey = new NamespacedKey(plugin, "hud_dx");
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

    /**
     * Ajuste da posição da HUD em barras: quantos pixels para dentro (positivo) ou para fora
     * (negativo) das bordas da tela. Cada jogador acerta o seu, porque a posição certa depende da
     * resolução e da escala de interface dele.
     */
    public int hudDx(Player p) {
        Integer v = p.getPersistentDataContainer().get(hudDxKey, PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(-HUD_DX_MAX, Math.min(HUD_DX_MAX, v));
    }

    public void setHudDx(Player p, int dx) {
        p.getPersistentDataContainer().set(hudDxKey, PersistentDataType.INTEGER, Math.max(-HUD_DX_MAX, Math.min(HUD_DX_MAX, dx)));
    }
}
