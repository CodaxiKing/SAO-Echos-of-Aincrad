package com.aincrad.social.party;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** As contas da party: divisão de XP e de quem é o drop. Sem servidor, para testar. */
public final class PartyRules {

    /** De quem são os itens que um mob solta. */
    public enum DropRule {
        /** Como no Minecraft: quem pegar primeiro. */
        LIVRE,
        /** Um membro por vez, em rodízio. */
        POR_VEZ,
        /** Tudo para o líder (ele distribui). */
        LIDER;

        public static DropRule parse(String s) {
            if (s == null) {
                return LIVRE;
            }
            return switch (s.toLowerCase(Locale.ROOT).replace(' ', '_')) {
                case "por_vez", "vez", "rodizio", "rodízio" -> POR_VEZ;
                case "lider", "líder", "do_lider" -> LIDER;
                default -> LIVRE;
            };
        }
    }

    private PartyRules() {
    }

    /**
     * Quanto de XP cada membro próximo recebe. O total cresce um pouco a cada membro a mais
     * ({@code bonusPerMember}, ex.: 0.1 = +10%), para valer a pena andar em grupo.
     *
     * @param amount  XP que o mob daria a quem matou
     * @param members membros próximos, contando quem matou
     */
    public static long share(long amount, int members, double bonusPerMember) {
        if (amount <= 0) {
            return 0;
        }
        if (members <= 1) {
            return amount;
        }
        double total = amount * (1.0 + Math.max(0, bonusPerMember) * (members - 1));
        return Math.max(1, (long) Math.floor(total / members));
    }

    /**
     * Rodízio: o próximo membro, na ordem da party, que está por perto.
     *
     * @param order    membros na ordem de entrada
     * @param eligible quem está perto o bastante para receber
     * @param last     quem recebeu por último (ou null)
     * @return o próximo, ou null se ninguém pode receber
     */
    public static UUID nextTurn(List<UUID> order, Set<UUID> eligible, UUID last) {
        int n = order.size();
        int start = last == null ? -1 : order.indexOf(last);
        for (int i = 1; i <= n; i++) {
            UUID m = order.get(Math.floorMod(start + i, n));
            if (eligible.contains(m)) {
                return m;
            }
        }
        return null;
    }

    /** Dono do drop conforme a regra. null = deixa cair livre. */
    public static UUID dropOwner(DropRule rule, List<UUID> order, UUID leader, Set<UUID> eligible, UUID killer, UUID last) {
        return switch (rule) {
            case LIVRE -> null;
            case LIDER -> eligible.contains(leader) ? leader : killer;
            case POR_VEZ -> {
                UUID next = order.isEmpty() ? null : nextTurn(order, eligible, last);
                yield next == null ? killer : next;
            }
        };
    }
}
