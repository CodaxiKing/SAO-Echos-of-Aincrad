package com.aincrad.ui.screen;

/** Desenha telas para um jogador: {@link CursorRenderer} ou {@link ChestRenderer}. */
public interface Renderer {

    void show(Screen screen);

    /** Chamado a cada tick enquanto o menu está aberto. */
    void tick();

    /** Elemento sob o cursor (modo cursor) ou null. */
    ScreenElement hovered();

    /** Elemento no slot clicado (modo baú) ou null. */
    ScreenElement atSlot(int slot);

    void destroy();
}
