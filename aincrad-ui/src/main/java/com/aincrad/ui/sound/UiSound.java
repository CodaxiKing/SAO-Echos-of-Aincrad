package com.aincrad.ui.sound;

/** Sons da interface. O evento fica no namespace aincrad (ex.: aincrad:ui.open). */
public enum UiSound {
    OPEN("abrir", "ui.open", "minecraft:block.note_block.chime"),
    MOVE("mover", "ui.move", "minecraft:block.note_block.hat"),
    CONFIRM("confirmar", "ui.confirm", "minecraft:entity.experience_orb.pickup"),
    BACK("voltar", "ui.back", "minecraft:block.note_block.bass"),
    CLOSE("fechar", "ui.close", "minecraft:block.note_block.chime"),
    CRITICAL("critico", "ui.critical", "minecraft:block.note_block.bit"),
    DEATH("morte", "ui.death", "minecraft:block.glass.break");

    private final String configKey;
    private final String defaultEvent;
    private final String defaultFallback;

    UiSound(String configKey, String defaultEvent, String defaultFallback) {
        this.configKey = configKey;
        this.defaultEvent = defaultEvent;
        this.defaultFallback = defaultFallback;
    }

    public String configKey() {
        return configKey;
    }

    public String defaultEvent() {
        return defaultEvent;
    }

    public String defaultFallback() {
        return defaultFallback;
    }
}
