package com.aincrad.economy.house;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Uma casa à venda: região do core ({@code region}), preço, andar, dono (null = à venda),
 * convidados e ponto de chegada ({@code spawn} = "mundo,x,y,z,yaw,pitch").
 */
public final class House {

    private final String name;
    private final String region;
    private final long price;
    private final int floor;
    private UUID owner;
    private String ownerName;
    private final Set<UUID> members = new LinkedHashSet<>();
    private String spawn;

    public House(String name, String region, long price, int floor, UUID owner, String ownerName, String members, String spawn) {
        this.name = name;
        this.region = region;
        this.price = price;
        this.floor = floor;
        this.owner = owner;
        this.ownerName = ownerName;
        this.spawn = spawn;
        if (members != null) {
            for (String s : members.split(",")) {
                try {
                    if (!s.isBlank()) {
                        this.members.add(UUID.fromString(s.trim()));
                    }
                } catch (IllegalArgumentException e) {
                    // ignora lixo
                }
            }
        }
    }

    public String name() {
        return name;
    }

    public String region() {
        return region;
    }

    public long price() {
        return price;
    }

    public int floor() {
        return floor;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean forSale() {
        return owner == null;
    }

    public Set<UUID> members() {
        return members;
    }

    public String spawn() {
        return spawn;
    }

    public void spawn(String s) {
        this.spawn = s;
    }

    public void owner(UUID id, String name) {
        this.owner = id;
        this.ownerName = name;
        if (id == null) {
            members.clear();
        }
    }

    /** Dono ou convidado. */
    public boolean canUse(UUID id) {
        return id != null && (id.equals(owner) || members.contains(id));
    }

    public String membersText() {
        StringBuilder b = new StringBuilder();
        for (UUID u : members) {
            if (!b.isEmpty()) {
                b.append(',');
            }
            b.append(u);
        }
        return b.toString();
    }
}
