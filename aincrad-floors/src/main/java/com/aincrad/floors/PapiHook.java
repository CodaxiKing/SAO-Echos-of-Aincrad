package com.aincrad.floors;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Placeholders do PlaceholderAPI: %aincrad_frontline% (linha de frente), %aincrad_floor_atual%
 * (número do andar do jogador) e %aincrad_floor_nome%. Só é carregada com o PlaceholderAPI instalado.
 */
final class PapiHook extends PlaceholderExpansion {

    private final Plugin plugin;
    private final FloorService floors;

    PapiHook(Plugin plugin, FloorService floors) {
        this.plugin = plugin;
        this.floors = floors;
    }

    @Override
    public String getIdentifier() {
        return "aincrad";
    }

    @Override
    public String getAuthor() {
        return "CodaxiKing";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "frontline" -> String.valueOf(floors.frontline());
            case "floor_atual" -> player == null ? "" : String.valueOf(floors.currentFloor(player));
            case "floor_nome" -> player == null || floors.currentFloor(player) == 0 ? "" : floors.name(floors.currentFloor(player));
            default -> null;
        };
    }
}
