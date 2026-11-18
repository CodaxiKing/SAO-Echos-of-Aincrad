package com.aincrad.economy;

import com.aincrad.economy.house.HouseRules;
import com.aincrad.economy.house.HouseRules.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A retomada por inatividade e a placa das casas, sem servidor. */
class HouseRulesTest {

    private static final long DAY = 86_400_000L;
    private static final long SEEN = 1_000 * DAY;

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                HouseRulesTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    @Test
    void oDonoQueSomePerdeACasa() {
        assertFalse(HouseRules.inactive(SEEN, SEEN + 29 * DAY, 30));
        assertTrue(HouseRules.inactive(SEEN, SEEN + 30 * DAY, 30));
        // 0 dias desliga a regra; dono nunca visto não conta.
        assertFalse(HouseRules.inactive(SEEN, SEEN + 9_999 * DAY, 0));
        assertFalse(HouseRules.inactive(0, SEEN + 9_999 * DAY, 30));
        assertEquals(30, HouseRules.daysLeft(SEEN, SEEN, 30));
        assertEquals(1, HouseRules.daysLeft(SEEN, SEEN + 29 * DAY + 1, 30));
        assertEquals(0, HouseRules.daysLeft(SEEN, SEEN + 40 * DAY, 30));
        // A regra vem desligada no arquivo, e a restauração ligada.
        YamlConfiguration cfg = resource("config.yml");
        assertEquals(0, cfg.getInt("casas.inatividade_dias", -1));
        assertTrue(cfg.getBoolean("casas.restaurar_ao_devolver"));
    }

    @Test
    void oImpostoEARetomada() {
        // 2% de 50.000 Col = 1.000 por período; casa sem preço ou fração 0 não paga.
        assertEquals(1_000, HouseRules.tax(50_000, 0.02));
        assertEquals(1, HouseRules.tax(10, 0.02));
        assertEquals(0, HouseRules.tax(0, 0.02));
        assertEquals(0, HouseRules.tax(50_000, 0));
        // Em dia até a data; vencido durante a tolerância; depois a casa é retomada.
        assertEquals(HouseRules.Tax.EM_DIA, HouseRules.taxState(SEEN, SEEN - 1, 7));
        assertEquals(HouseRules.Tax.VENCIDO, HouseRules.taxState(SEEN, SEEN, 7));
        assertEquals(HouseRules.Tax.VENCIDO, HouseRules.taxState(SEEN, SEEN + 7 * DAY - 1, 7));
        assertEquals(HouseRules.Tax.PERDIDO, HouseRules.taxState(SEEN, SEEN + 7 * DAY, 7));
        // Vence em 3 dias, períodos de 7, máximo de 8 à frente: cabem 8 pagamentos (até o dia 59).
        assertEquals(8, HouseRules.payable(SEEN + 3 * DAY, SEEN, 7, 8));
        // Já pago 8 períodos à frente: nada a adiantar.
        assertEquals(0, HouseRules.payable(SEEN + 56 * DAY, SEEN, 7, 8));
        // Vencido: dá para pagar o que venceu mesmo sem adiantamento permitido.
        assertEquals(1, HouseRules.payable(SEEN - DAY, SEEN, 7, 0));
        assertEquals(SEEN + 14 * DAY, HouseRules.paid(SEEN, 2, 7));
        assertEquals(3, HouseRules.daysUntil(SEEN + 2 * DAY + 1, SEEN));
        assertEquals(0, HouseRules.daysUntil(SEEN, SEEN));
        YamlConfiguration cfg = resource("config.yml");
        assertEquals(0.02, cfg.getDouble("casas.imposto.fracao"), 1e-9);
        assertEquals(30, cfg.getInt("casas.visita.segundos"));
    }

    @Test
    void aVisitaDoCorretor() {
        assertFalse(HouseRules.visitOver(SEEN, SEEN + 1, true));
        assertTrue(HouseRules.visitOver(SEEN + 1, SEEN + 1, true));
        // Saiu da casa: a visita acaba na hora.
        assertTrue(HouseRules.visitOver(SEEN, SEEN + 1, false));
        assertEquals(2, HouseRules.free(java.util.List.of(true, false, true)));
        YamlConfiguration msg = resource("messages.yml");
        for (String k : new String[] {"titulo", "andar", "venda", "leilao", "ocupada", "visitar", "fim"}) {
            assertNotNull(msg.getString("corretor." + k), k);
        }
    }

    @Test
    void oQueAPlacaMostra() {
        assertEquals(Sign.VENDA, HouseRules.sign(true, false));
        assertEquals(Sign.LEILAO, HouseRules.sign(true, true));
        // Casa de leilão com dono mostra o dono.
        assertEquals(Sign.DONO, HouseRules.sign(false, true));
        YamlConfiguration msg = resource("messages.yml");
        for (Sign s : Sign.values()) {
            assertNotNull(msg.getString("placa." + s.name().toLowerCase(Locale.ROOT)), s.name());
        }
    }
}
