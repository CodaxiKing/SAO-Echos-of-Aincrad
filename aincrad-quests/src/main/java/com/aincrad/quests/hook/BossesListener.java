package com.aincrad.quests.hook;

import com.aincrad.bosses.api.BossDefeatEvent;
import com.aincrad.quests.QuestService;
import com.aincrad.quests.model.QuestDef.Goal;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/** Chefe derrotado: conta para o objetivo "chefe" de cada participante. */
public final class BossesListener implements Listener {

    private final QuestService quests;

    public BossesListener(QuestService quests) {
        this.quests = quests;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDefeat(BossDefeatEvent event) {
        for (UUID id : event.getParticipants()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                // Cada participante conta por si: a party não soma duas vezes.
                quests.happenedSolo(p, Goal.CHEFE, 1, event.getBossId());
            }
        }
    }
}
