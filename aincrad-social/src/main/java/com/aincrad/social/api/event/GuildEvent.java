package com.aincrad.social.api.event;

import org.bukkit.event.Event;

import java.util.UUID;

/** Base dos eventos de guilda: id e nome da guilda. */
public abstract class GuildEvent extends Event {

    private final UUID guildId;
    private final String guildName;

    protected GuildEvent(UUID guildId, String guildName) {
        this.guildId = guildId;
        this.guildName = guildName;
    }

    public UUID guildId() {
        return guildId;
    }

    public String guildName() {
        return guildName;
    }
}
