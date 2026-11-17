package com.aincrad.social.menu;

import org.bukkit.entity.Player;

/**
 * Ligação com o menu SAO. Sem o AincradUI fica {@link #NONE}: tudo continua funcionando pelos
 * comandos e pelo chat.
 */
public interface MenuHook {

    MenuHook NONE = new MenuHook() {
        @Override
        public void close(Player p) {
        }

        @Override
        public void shutdown() {
        }
    };

    /** Fecha o menu do jogador, se estiver aberto (antes de pedir texto no chat). */
    void close(Player p);

    /** Abre a caixa de entrada no menu. @return false sem menu */
    default boolean openInbox(Player p) {
        return false;
    }

    void shutdown();
}
