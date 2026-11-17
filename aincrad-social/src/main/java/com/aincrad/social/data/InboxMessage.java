package com.aincrad.social.data;

import java.util.UUID;

/**
 * Uma mensagem na caixa de entrada.
 *
 * @param time milissegundos desde 1970 (System.currentTimeMillis)
 */
public record InboxMessage(UUID id, UUID from, String fromName, String text, long time, boolean read) {

    public InboxMessage asRead() {
        return read ? this : new InboxMessage(id, from, fromName, text, time, true);
    }
}
