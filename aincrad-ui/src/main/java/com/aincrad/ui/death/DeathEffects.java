package com.aincrad.ui.death;

import com.aincrad.ui.AincradUIPlugin;
import com.aincrad.ui.config.UiSettings;
import com.aincrad.ui.glyph.Glyph;
import com.aincrad.ui.sound.UiSound;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.time.Duration;
import java.util.Map;

/** Tela de morte: estilhaços azuis, imagem "You are dead" e som de morte. */
public final class DeathEffects implements Listener {

    private static final BlockData SHARD = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();
    private static final Particle.DustOptions GLOW = new Particle.DustOptions(Color.fromRGB(130, 210, 255), 1.3f);

    private final AincradUIPlugin plugin;

    public DeathEffects(AincradUIPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getPlayer();
        plugin.sessions().close(p, false);
        plugin.hud().hide(p);
        showDeath(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e instanceof Player || e instanceof ArmorStand || !plugin.settings().shardsMobs) {
            return;
        }
        double scale = Math.max(0.4, Math.min(2.5, e.getHeight()));
        shatter(e.getLocation(), scale, Map.of("entidade", e.getType().name().toLowerCase()));
    }

    /** Efeito completo da morte de um jogador (também usado por /aincrad ui teste morte). */
    public void showDeath(Player p) {
        UiSettings st = plugin.settings();
        if (st.shardsPlayers) {
            shatter(p.getLocation(), 1.0, Map.of("jogador", p.getName()));
        }
        Glyph img = plugin.glyphs().get("you_are_dead");
        Title.Times times = Title.Times.times(Duration.ofMillis(250),
                Duration.ofSeconds(st.deathTitleSeconds), Duration.ofMillis(750));
        if (plugin.packStatus().hasPack(p) && img != null && img.present()) {
            p.showTitle(Title.title(img.component(), Component.empty(), times));
        } else {
            p.showTitle(Title.title(plugin.messages().get("morte.titulo"), plugin.messages().get("morte.subtitulo"), times));
        }
        plugin.sounds().play(p, UiSound.DEATH);
    }

    /**
     * Estilhaços: usa o efeito do AincradEffects quando ele existe; senão, partículas próprias.
     */
    public void shatter(Location where, double scale, Map<String, String> vars) {
        World w = where.getWorld();
        if (w == null) {
            return;
        }
        if (plugin.effects().available()
                && plugin.effects().play(plugin.settings().shardsEffect, where, 32.0, vars)) {
            return;
        }
        Location c = where.clone().add(0, 0.9 * scale, 0);
        w.spawnParticle(Particle.BLOCK, c, (int) (70 * scale), 0.3 * scale, 0.55 * scale, 0.3 * scale, 0.15, SHARD);
        w.spawnParticle(Particle.DUST, c, (int) (45 * scale), 0.35 * scale, 0.6 * scale, 0.35 * scale, 0, GLOW);
        w.spawnParticle(Particle.END_ROD, c, (int) (12 * scale), 0.25 * scale, 0.4 * scale, 0.25 * scale, 0.04);
        w.playSound(Sound.sound(Key.key("minecraft:block.glass.break"), Sound.Source.PLAYER, 1f, 1.3f),
                c.getX(), c.getY(), c.getZ());
    }
}
