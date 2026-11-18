package com.aincrad.reputation.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** As conquistas de achievements.yml e a regra de qual delas um acontecimento libera. Só regras, sem servidor. */
public final class Achievements {

    /** O que dispara uma conquista. */
    public enum Type {
        /** O primeiro jogador do servidor a entrar num andar. */
        PRIMEIRO_NO_ANDAR,
        /** Qualquer jogador que entra num andar pela primeira vez. */
        ANDAR_VISITADO,
        /** Participou da primeira vitória sobre o chefe de um andar. */
        CHEFE_PRIMEIRA_VITORIA,
        /** Participou de uma vitória sobre um chefe (primeira ou não). */
        CHEFE_DERROTADO,
        /** Deu o golpe final num chefe. */
        LAST_ATTACK,
        /** Pescou um peixe lendário (o Senhor do Lago foi derrotado). */
        PEIXE_LENDARIO,
        /** Venceu o torneio semanal de pesca. */
        TORNEIO_DE_PESCA,
        /** Domou um familiar. */
        FAMILIAR_DOMADO,
        /** Chegou ao rank Completo (1000) numa profissão. */
        PROFISSAO_COMPLETA,
        /** Casou. */
        CASAMENTO;

        static Type parse(String s) {
            try {
                return s == null ? null : valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * Uma conquista.
     *
     * @param floor      só vale neste andar (0 = qualquer um)
     * @param profession só vale nesta profissão (null = qualquer uma)
     * @param perFloor   uma conquista separada para cada andar (ex.: "Primeiro no Andar 5")
     * @param title      o título que ela libera (null = nenhum); {@code <andar>} vira o número do andar
     * @param announce   anuncia para o servidor quando alguém consegue
     */
    public record Def(String id, String name, String description, Type type, int floor, String profession, boolean perFloor,
                      String title, boolean announce) {

        /** O acontecimento libera esta conquista? */
        public boolean matches(Type happened, int atFloor, String atProfession) {
            if (type != happened) {
                return false;
            }
            if (floor > 0 && floor != atFloor) {
                return false;
            }
            return profession == null || profession.equalsIgnoreCase(atProfession);
        }

        /** A chave gravada para o jogador: a da conquista ou, se ela é por andar, uma para cada andar. */
        public String key(int atFloor) {
            return perFloor && atFloor > 0 ? id + "_" + atFloor : id;
        }

        /** O texto com o andar no lugar de {@code <andar>}. */
        public static String fill(String text, int atFloor) {
            return text == null ? null : text.replace("<andar>", String.valueOf(atFloor));
        }
    }

    private final Map<String, Def> defs = new LinkedHashMap<>();

    public static Achievements parse(ConfigurationSection root, List<String> errors) {
        Achievements out = new Achievements();
        ConfigurationSection s = root == null ? null : root.getConfigurationSection("conquistas");
        if (s == null) {
            return out;
        }
        for (String raw : s.getKeys(false)) {
            ConfigurationSection e = s.getConfigurationSection(raw);
            if (e == null) {
                continue;
            }
            String id = raw.toLowerCase(Locale.ROOT);
            Type type = Type.parse(e.getString("tipo"));
            if (type == null) {
                errors.add("achievements.yml: " + id + ": \"tipo\" desconhecido (" + String.join(", ", types()) + ").");
                continue;
            }
            String title = e.getString("titulo");
            if (title != null && title.length() > 48) {
                errors.add("achievements.yml: " + id + ": o título passa de 48 letras.");
                continue;
            }
            String prof = e.getString("profissao");
            out.defs.put(id, new Def(id, e.getString("nome", id), e.getString("descricao", ""), type, Math.max(0, e.getInt("andar", 0)),
                    prof == null ? null : prof.toLowerCase(Locale.ROOT), e.getBoolean("por_andar", false), title, e.getBoolean("anunciar", false)));
        }
        return out;
    }

    private static List<String> types() {
        List<String> out = new ArrayList<>();
        for (Type t : Type.values()) {
            out.add(t.name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    public Map<String, Def> all() {
        return defs;
    }

    public Def get(String id) {
        return id == null ? null : defs.get(id.toLowerCase(Locale.ROOT));
    }

    /** As conquistas que um acontecimento libera. */
    public List<Def> matching(Type happened, int floor, String profession) {
        List<Def> out = new ArrayList<>();
        for (Def d : defs.values()) {
            if (d.matches(happened, floor, profession)) {
                out.add(d);
            }
        }
        return out;
    }
}
