package com.aincrad.ui.api;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * API pública do AincradUI, registrada no ServicesManager do Bukkit.
 *
 * <pre>
 * AincradUIAPI ui = Bukkit.getServicesManager().load(AincradUIAPI.class);
 * ui.registerMenuEntry("party", "under_icons/friends", player -&gt; abrirParty(player));
 * </pre>
 */
public interface AincradUIAPI {

    /**
     * Liga uma entrada do menu. Se o id já existe em menu.yml (ex.: party, guilda, quests,
     * skills, mapa), ela deixa de aparecer com cadeado e passa a executar {@code acao}.
     * Se não existe, é criada no grupo "config".
     *
     * @param id    id da entrada (chave em menu.yml → entradas)
     * @param icone id de glifo de glyphs.yml (ex.: under_icons/guild) ou null para manter o da config
     * @param acao  o que fazer quando o jogador clicar
     */
    void registerMenuEntry(String id, String icone, MenuAction acao);

    /** Igual ao outro, escolhendo nome e grupo para entradas novas. */
    void registerMenuEntry(String id, String icone, Component nome, String grupo, MenuAction acao);

    /** Volta a entrada para o estado da config (cadeado, se não tiver ação embutida). */
    void unregisterMenuEntry(String id);

    /**
     * Abre uma tela: menu, perfil, opcoes, ajuda, detalhes (item na mão) ou icones.
     *
     * @return false se a tela não existir
     */
    boolean openScreen(Player player, String tela);

    /** Janela de confirmação sim/não sobre window_blank. */
    void openConfirm(Player player, Component pergunta, MenuAction sim, MenuAction nao);

    boolean isMenuOpen(Player player);

    void closeMenu(Player player);
}
