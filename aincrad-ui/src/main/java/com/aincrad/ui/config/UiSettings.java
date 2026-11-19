package com.aincrad.ui.config;

import com.aincrad.ui.sound.UiSound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Valores de ui.yml já convertidos. Recriado a cada reload. */
public final class UiSettings {

    public enum DrawMode {
        CURSOR, BAU;

        public static DrawMode parse(String s, DrawMode fallback) {
            if (s == null) {
                return fallback;
            }
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "cursor" -> CURSOR;
                case "bau", "baú", "chest" -> BAU;
                default -> fallback;
            };
        }
    }

    public record SoundDef(String event, String file, String fallback, float volume, float pitch) {
    }

    public final DrawMode defaultMode;
    public final boolean gestureEnabled;
    public final boolean gestureEmptyHand;
    public final double menuDistance;
    public final double menuScale;
    public final double menuOffsetY;
    public final boolean closeOnDamage;
    public final boolean menuProfilePanel;

    public final boolean hudEnabled;
    public final int hudIntervalTicks;
    public final int hudSegments;
    public final int hudOffsetX;
    public final double hudYellow;
    public final double hudRed;
    public final int hudBlinkTicks;
    public final int hudAlertSeconds;
    public final boolean hudNumbers;
    public final String hudLevelText;
    /** Posições dentro da moldura da barra (pixels de fonte). */
    public final int hudNameX;
    public final int hudNameMaxPx;
    public final int hudFillX;
    public final int hudNumbersX;
    public final int hudPartyMax;
    public final int hudPartyIndent;

    public final boolean shardsPlayers;
    public final boolean shardsMobs;
    public final int deathTitleSeconds;
    public final String shardsEffect;

    public final boolean logoutDeathGame;

    /** HUD em barras (desenhada pelas telas planas) no lugar da moldura do anime. */
    public final boolean hudFlat;
    /** Alvo no alto da tela. */
    public final boolean targetEnabled;
    public final int targetRange;
    public final double targetBossHealth;
    public final int targetBossBars;
    public final boolean clockEnabled;
    public final boolean compassEnabled;
    public final boolean cooldownsEnabled;
    public final boolean toastsEnabled;
    public final boolean toastItems;
    public final int toastSeconds;
    /** Convites e desafios abrem a janela de aceitar/recusar. */
    public final boolean promptsEnabled;
    /** Faixas do anime no meio da tela (WARNING, Immortal Object, duelo). */
    public final boolean bannersEnabled;
    public final int bannerSeconds;
    public final int flatHudX;
    /** A HUD em barras usa as artes do pack (moldura do anime, ícones de skill). */
    public final boolean flatArt;
    public final boolean flatHotbar;
    /** O ícone do menu SAO nos espaços de fabricação do inventário (tecla E). */
    public final boolean inventoryButton;
    public final boolean flatHideVanilla;
    public final boolean flatEnabled;
    public final double flatSensitivity;

    public final int packFormat;
    public final String packDescription;
    public final boolean packAssumeLoaded;
    public final boolean packHideWhiteBar;
    public final String packUrl;
    public final boolean packRequired;
    public final boolean packServerEnabled;
    public final int packServerPort;
    public final String packServerPublicAddress;

    public final Map<UiSound, SoundDef> sounds = new EnumMap<>(UiSound.class);

    public UiSettings(YamlConfiguration c) {
        defaultMode = DrawMode.parse(c.getString("menu.modo_padrao"), DrawMode.CURSOR);
        gestureEnabled = c.getBoolean("menu.gesto.ativo", true);
        inventoryButton = c.getBoolean("menu.botao_no_inventario", true);
        gestureEmptyHand = c.getBoolean("menu.gesto.mao_vazia", true);
        menuDistance = clamp(c.getDouble("menu.distancia", 2.2), 1.2, 6.0);
        menuScale = clamp(c.getDouble("menu.escala", 1.0), 0.3, 3.0);
        menuOffsetY = c.getDouble("menu.ajuste_y", -0.1);
        closeOnDamage = c.getBoolean("menu.fechar_ao_levar_dano", true);
        menuProfilePanel = c.getBoolean("menu.painel_perfil", true);

        hudEnabled = c.getBoolean("hud.ativo", true);
        hudIntervalTicks = Math.max(1, c.getInt("hud.intervalo_ticks", 4));
        hudSegments = Math.max(4, Math.min(60, c.getInt("hud.segmentos", 20)));
        hudOffsetX = c.getInt("hud.deslocamento_x", 200);
        hudYellow = c.getDouble("hud.limite_amarelo", 0.5);
        hudRed = c.getDouble("hud.limite_vermelho", 0.25);
        hudBlinkTicks = Math.max(1, c.getInt("hud.piscar_ticks", 8));
        hudAlertSeconds = Math.max(1, c.getInt("hud.alerta_intervalo_segundos", 3));
        hudNumbers = c.getBoolean("hud.mostrar_numeros", true);
        hudLevelText = c.getString("hud.texto_level", "LV:");
        hudNameX = c.getInt("hud.moldura.nome_x", 9);
        hudNameMaxPx = c.getInt("hud.moldura.nome_largura", 44);
        hudFillX = c.getInt("hud.moldura.barra_x", 57);
        hudNumbersX = c.getInt("hud.moldura.numeros_x", 62);
        hudPartyMax = Math.max(0, c.getInt("hud.party.maximo", 7));
        hudPartyIndent = c.getInt("hud.party.recuo", 8);

        shardsPlayers = c.getBoolean("morte.estilhacos_jogador", true);
        shardsMobs = c.getBoolean("morte.estilhacos_mobs", true);
        deathTitleSeconds = Math.max(1, c.getInt("morte.titulo_segundos", 4));
        shardsEffect = c.getString("morte.efeito_estilhacos", "aincrad_estilhacos");

        logoutDeathGame = c.getBoolean("logout.modo_death_game", false);

        packFormat = c.getInt("pack.formato", 75);
        packDescription = c.getString("pack.descricao", "Aincrad UI");
        packAssumeLoaded = c.getBoolean("pack.assumir_carregado", false);
        hudFlat = "barras".equalsIgnoreCase(c.getString("hud.estilo", "barras"));
        flatHudX = c.getInt("hud.barras.deslocamento_x", 232);
        flatArt = c.getBoolean("hud.barras.artes", true);
        targetEnabled = c.getBoolean("hud.alvo.ativo", true);
        targetRange = Math.max(4, Math.min(48, c.getInt("hud.alvo.distancia", 20)));
        targetBossHealth = c.getDouble("hud.alvo.chefe_vida", 300);
        targetBossBars = Math.max(1, Math.min(8, c.getInt("hud.alvo.chefe_barras", 4)));
        clockEnabled = c.getBoolean("hud.relogio", true);
        compassEnabled = c.getBoolean("hud.bussola", true);
        cooldownsEnabled = c.getBoolean("hud.recargas", true);
        toastsEnabled = c.getBoolean("hud.avisos.ativo", true);
        toastItems = c.getBoolean("hud.avisos.itens", true);
        toastSeconds = Math.max(1, Math.min(15, c.getInt("hud.avisos.segundos", 4)));
        promptsEnabled = c.getBoolean("menu.janela_de_convite", true);
        bannersEnabled = c.getBoolean("hud.faixas.ativo", true);
        bannerSeconds = Math.max(1, Math.min(10, c.getInt("hud.faixas.segundos", 2)));
        flatHotbar = c.getBoolean("hud.barras.hotbar_redonda", true);
        flatHideVanilla = c.getBoolean("hud.barras.esconder_vanilla", true);
        flatEnabled = c.getBoolean("tela_plana.ativo", true);
        flatSensitivity = clamp(c.getDouble("tela_plana.sensibilidade", 6.0), 0.5, 40.0);
        packHideWhiteBar = c.getBoolean("pack.ocultar_barra_branca", true);
        packUrl = c.getString("pack.url", "").trim();
        packRequired = c.getBoolean("pack.obrigatorio", false);
        packServerEnabled = c.getBoolean("pack.servidor_embutido.ativo", false);
        packServerPort = c.getInt("pack.servidor_embutido.porta", 8164);
        packServerPublicAddress = c.getString("pack.servidor_embutido.endereco_publico", "").trim();

        ConfigurationSection s = c.getConfigurationSection("sons");
        for (UiSound sound : UiSound.values()) {
            ConfigurationSection e = s == null ? null : s.getConfigurationSection(sound.configKey());
            String event = e == null ? sound.defaultEvent() : e.getString("evento", sound.defaultEvent());
            String file = e == null ? sound.defaultEvent().replace('.', '/') : e.getString("arquivo", sound.defaultEvent().replace('.', '/'));
            String fallback = e == null ? sound.defaultFallback() : e.getString("reserva", sound.defaultFallback());
            float volume = e == null ? 1f : (float) e.getDouble("volume", 1.0);
            float pitch = e == null ? 1f : (float) e.getDouble("tom", 1.0);
            sounds.put(sound, new SoundDef(event, file, fallback, volume, pitch));
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
