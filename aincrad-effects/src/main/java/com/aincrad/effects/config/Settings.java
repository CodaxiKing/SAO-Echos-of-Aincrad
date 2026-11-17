package com.aincrad.effects.config;

import com.aincrad.effects.engine.Audience;
import org.bukkit.configuration.file.YamlConfiguration;

/** Valores de config.yml. Recriado a cada reload. */
public final class Settings {

    public final Audience defaultAudience;

    public final int packFormat;
    public final String packDescription;
    public final String packUrl;
    public final boolean packRequired;
    public final String packPrompt;
    public final boolean packAssumeLoaded;
    public final boolean packServerEnabled;
    public final int packServerPort;
    public final String packServerPublicAddress;

    public final int maxEntitiesPerPlayer;
    public final int maxParticlesPerPlayerTick;
    public final double viewDistance;
    public final int maxConcurrentPerPlayer;

    public Settings(YamlConfiguration c) {
        defaultAudience = Audience.parse(c.getString("publico_padrao"), Audience.player());

        packFormat = c.getInt("pack.formato", 75);
        packDescription = c.getString("pack.descricao", "Aincrad");
        packUrl = c.getString("pack.url", "").trim();
        packRequired = c.getBoolean("pack.obrigatorio", false);
        packPrompt = c.getString("pack.mensagem", "");
        packAssumeLoaded = c.getBoolean("pack.assumir_carregado", false);
        packServerEnabled = c.getBoolean("pack.servidor_embutido.ativo", true);
        packServerPort = c.getInt("pack.servidor_embutido.porta", 8163);
        packServerPublicAddress = c.getString("pack.servidor_embutido.endereco_publico", "").trim();

        maxEntitiesPerPlayer = Math.max(1, c.getInt("limites.entidades_por_jogador", 40));
        maxParticlesPerPlayerTick = Math.max(1, c.getInt("limites.particulas_por_jogador_por_tick", 600));
        viewDistance = Math.max(4, c.getDouble("limites.distancia_visao", 48));
        maxConcurrentPerPlayer = Math.max(1, c.getInt("limites.efeitos_simultaneos_por_jogador", 8));
    }
}
