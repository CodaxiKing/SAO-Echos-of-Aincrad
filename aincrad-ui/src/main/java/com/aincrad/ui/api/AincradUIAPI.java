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
     * <p>O menu continua aberto quando a ação roda: ela pode abrir uma lista
     * ({@link #openList}), que entra por cima (agachar volta), ou chamar {@link #closeMenu}.
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
     * Abre uma tela: menu, itens, perfil, opcoes, ajuda, detalhes (item na mão) ou icones.
     *
     * @return false se a tela não existir
     */
    boolean openScreen(Player player, String tela);

    /**
     * Janela de confirmação sim/não sobre window_blank. Abre por cima do que estiver aberto;
     * depois da resposta, volta para a tela anterior (ou fecha, se não havia menu aberto).
     */
    void openConfirm(Player player, Component pergunta, MenuAction sim, MenuAction nao);

    /** Abre uma lista por cima da tela atual (agachar volta). Abre o menu se estiver fechado. */
    void openList(Player player, UiList lista);

    /** Troca a tela atual pela lista (para atualizar uma lista depois de uma ação). */
    void replaceList(Player player, UiList lista);

    /**
     * Janela branca com título, texto e botões redondos (ex.: ler uma mensagem). Abre por cima
     * da tela atual (agachar volta).
     */
    void openWindow(Player player, Component titulo, java.util.List<Component> linhas, java.util.List<UiList.Button> botoes);

    /** Volta uma tela (fecha o menu se for a primeira). */
    void back(Player player);

    boolean isMenuOpen(Player player);

    void closeMenu(Player player);

    /** Quem aparece com barras menores embaixo da barra de vida (membros da party). */
    void setPartyHudProvider(PartyHudProvider provider);

    /**
     * Símbolo antes do nome do jogador na barra de vida e no painel de perfil (ex.: o emblema da
     * guilda). A função devolve null para "nenhum". Use texto curto (1 ou 2 caracteres).
     */
    void setHudBadgeProvider(java.util.function.Function<Player, Component> provider);

    /** Telas planas: desenhadas direto na tela, com cursor (como a loja). */
    FlatApi flat();

    /**
     * Liga ou desliga o jogo da morte: com ele ligado o logout some do menu. Vale até o servidor
     * reiniciar; quem liga (o dia do anúncio do AincradFloors) chama de novo ao ligar.
     * O {@code logout.modo_death_game} do ui.yml continua valendo por conta própria.
     */
    void setDeathGame(boolean ativo);

    boolean isDeathGame();
}
