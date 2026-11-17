package com.aincrad.ui.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandRedirectListenerTest {

    @Test
    void redirecionaSubcomandoUi() {
        assertEquals("aincradui teste menu", CommandRedirectListener.rewrite("aincrad ui teste menu"));
        assertEquals("aincradui reload", CommandRedirectListener.rewrite("AINCRAD UI reload"));
        assertEquals("aincradui", CommandRedirectListener.rewrite("aincrad ui"));
    }

    @Test
    void ignoraOutrosComandos() {
        assertNull(CommandRedirectListener.rewrite("aincrad andar liberar 2"));
        assertNull(CommandRedirectListener.rewrite("aincrad uix"));
        assertNull(CommandRedirectListener.rewrite("menu"));
    }
}
