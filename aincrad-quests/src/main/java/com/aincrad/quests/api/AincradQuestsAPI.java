package com.aincrad.quests.api;

import com.aincrad.quests.QuestService;
import com.aincrad.quests.model.QuestDef;
import com.aincrad.quests.model.QuestRules;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** API do AincradQuests. Métodos estáticos (dá para chamar por reflexão); também no ServicesManager. */
public final class AincradQuestsAPI {

    private static final AincradQuestsAPI INSTANCE = new AincradQuestsAPI();
    private static QuestService quests;

    private AincradQuestsAPI() {
    }

    public static void init(QuestService q) {
        quests = q;
    }

    public static AincradQuestsAPI instance() {
        return INSTANCE;
    }

    public static boolean isEnabled() {
        return quests != null;
    }

    /** O jogador já concluiu a quest alguma vez? */
    public static boolean isCompleted(Player p, String quest) {
        Map<String, QuestRules.Progress> m = quests == null ? null : quests.data(p);
        QuestRules.Progress pr = m == null || quest == null ? null : m.get(quest.toLowerCase(java.util.Locale.ROOT));
        return pr != null && pr.times() > 0;
    }

    /** Os ids das quests que o jogador está fazendo. */
    public static List<String> getActive(Player p) {
        List<String> out = new ArrayList<>();
        Map<String, QuestRules.Progress> m = quests == null ? null : quests.data(p);
        if (m != null) {
            m.forEach((k, v) -> {
                if (v.state() == QuestRules.State.ATIVA) {
                    out.add(k);
                }
            });
        }
        return out;
    }

    /** Dá a quest ao jogador, respeitando os requisitos. True se ele a recebeu. */
    public static boolean giveQuest(Player p, String quest) {
        QuestDef def = quests == null ? null : quests.quest(quest);
        return def != null && quests.accept(p, def);
    }
}
