package com.aincrad.effects.engine;

/** Erro de configuração de um efeito; a mensagem diz o arquivo, o efeito e o passo. */
public final class EffectParseException extends RuntimeException {

    public EffectParseException(String message) {
        super(message);
    }
}
