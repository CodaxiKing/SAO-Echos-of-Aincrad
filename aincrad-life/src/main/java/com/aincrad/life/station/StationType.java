package com.aincrad.life.station;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Um tipo de bancada de stations.yml. Os pontos de ancoragem são posições em blocos a partir do
 * centro da base do modelo, com a frente da bancada no +Z; o plugin gira conforme a bancada foi posta.
 *
 * @param model      id do modelo no resource pack (ex.: "aincrad:station/forge_anvil")
 * @param block      bloco provisório (quem está sem o pack e no Bedrock vê este bloco)
 * @param clickWidth largura e {@code clickHeight} altura da área de clique
 * @param hologram   onde fica o holograma de progresso
 * @param slots      onde ficam os encaixes de material
 * @param marks      onde aparecem as marcas de batida (esquerda, centro, direita...)
 */
public record StationType(String id, String name, String profession, String model, double scale, String block,
                          double clickWidth, double clickHeight, double[] hologram, List<double[]> slots, List<double[]> marks) {

    public static StationType parse(String id, ConfigurationSection s, List<String> errors) {
        ConfigurationSection a = s.getConfigurationSection("ancoras");
        double[] holo = point(a == null ? null : a.getList("holograma"), new double[]{0, 1.9, 0});
        List<double[]> slots = points(a == null ? null : a.getList("encaixes"));
        List<double[]> marks = points(a == null ? null : a.getList("batidas"));
        if (marks.isEmpty()) {
            marks = List.of(new double[]{-0.3, 1.15, 0}, new double[]{0, 1.15, 0}, new double[]{0.3, 1.15, 0});
        }
        List<Double> click = s.getDoubleList("clique");
        String model = s.getString("modelo", "");
        if (!model.contains(":")) {
            errors.add("estação " + id + ": \"modelo\" precisa ser como \"aincrad:station/nome\".");
        }
        return new StationType(id.toLowerCase(Locale.ROOT), s.getString("nome", id), s.getString("profissao", ""), model,
                Math.max(0.05, s.getDouble("escala", 1.0)), s.getString("bloco_provisorio", "CRAFTING_TABLE").toUpperCase(Locale.ROOT),
                click.isEmpty() ? 1.05 : Math.max(0.2, click.get(0)), click.size() < 2 ? 1.05 : Math.max(0.2, click.get(1)),
                holo, slots, marks);
    }

    private static List<double[]> points(List<?> raw) {
        List<double[]> out = new ArrayList<>();
        if (raw != null) {
            for (Object o : raw) {
                if (o instanceof List<?> l) {
                    out.add(point(l, new double[]{0, 1.2, 0}));
                }
            }
        }
        return List.copyOf(out);
    }

    private static double[] point(List<?> raw, double[] def) {
        if (raw == null || raw.size() != 3) {
            return def;
        }
        double[] p = new double[3];
        for (int i = 0; i < 3; i++) {
            if (!(raw.get(i) instanceof Number n)) {
                return def;
            }
            p[i] = n.doubleValue();
        }
        return p;
    }

    /**
     * Gira um ponto de ancoragem em volta do eixo vertical. {@code yaw} é o do Minecraft (0 = sul,
     * +Z; 90 = oeste, −X). Devolve o deslocamento em blocos a partir do centro da base.
     */
    public static double[] rotate(double[] p, float yaw) {
        double rad = Math.toRadians(yaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new double[]{p[0] * cos - p[2] * sin, p[1], p[0] * sin + p[2] * cos};
    }

    /** Arredonda o olhar do jogador para uma das quatro direções (a bancada fica de frente para ele). */
    public static float snapYaw(float playerYaw) {
        float facing = playerYaw + 180f;
        return ((Math.round(facing / 90f) * 90) % 360 + 360) % 360;
    }
}
