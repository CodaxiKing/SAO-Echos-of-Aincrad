package com.aincrad.ui.sound;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.config.UiSettings.SoundDef;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;

/** Toca os sons da interface: o do pack para quem tem o pack, a reserva vanilla para quem não tem. */
public final class SoundPlayer {

    private final AincradUIPlugin plugin;

    public SoundPlayer(AincradUIPlugin plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, UiSound sound) {
        SoundDef def = plugin.settings().sounds.get(sound);
        if (def == null) {
            return;
        }
        String key = plugin.packStatus().hasPack(player) ? "aincrad:" + def.event() : def.fallback();
        Key parsed;
        try {
            parsed = Key.key(key);
        } catch (Exception e) {
            return;
        }
        player.playSound(Sound.sound(parsed, Sound.Source.MASTER, def.volume(), def.pitch()), Sound.Emitter.self());
    }
}
