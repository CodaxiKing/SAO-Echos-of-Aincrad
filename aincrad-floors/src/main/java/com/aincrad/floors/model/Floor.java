package com.aincrad.floors.model;

/**
 * Um andar de floors.yml.
 *
 * @param number número (1, 2, 3...)
 * @param name   nome mostrado ("Andar 1 — Cidade do Início")
 * @param server servidor da rede onde o andar fica ("" = qualquer servidor que tenha o mundo/região)
 * @param world  mundo do andar (tipo "mundo") ou null
 * @param region região do AincradCore que delimita o andar (tipo "regiao") ou null
 * @param spawn  ponto de chegada: "mundo,x,y,z,yaw,pitch" (pode ser null até o admin definir)
 * @param city   região do core que é a cidade (zona segura), ou null
 */
public record Floor(int number, String name, String server, String world, String region, String spawn, String city) {

    public boolean byRegion() {
        return region != null;
    }

    /** O andar fica neste servidor? */
    public boolean isOn(String thisServer) {
        return server == null || server.isEmpty() || server.equalsIgnoreCase(thisServer);
    }

    public Floor withSpawn(String newSpawn) {
        return new Floor(number, name, server, world, region, newSpawn, city);
    }
}
