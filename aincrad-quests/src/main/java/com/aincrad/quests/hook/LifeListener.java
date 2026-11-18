package com.aincrad.quests.hook;

import com.aincrad.life.api.ProfessionPracticeEvent;
import com.aincrad.quests.QuestService;
import com.aincrad.quests.model.QuestDef.Goal;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Pescou ou cozinhou no AincradLife: conta para os objetivos "pescar" e "cozinhar". */
public final class LifeListener implements Listener {

    private final QuestService quests;

    public LifeListener(QuestService quests) {
        this.quests = quests;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPractice(ProfessionPracticeEvent event) {
        switch (event.getProfession()) {
            case "pesca" -> quests.happened(event.getPlayer(), Goal.PESCAR, 1);
            case "culinaria" -> quests.happened(event.getPlayer(), Goal.COZINHAR, 1);
            default -> {
            }
        }
    }
}
