package com.aincrad.core.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * O que os plugins pedem à interface do jogador sem saber quem a desenha: uma entrada no menu,
 * um aviso na tela, as barras de recarga das skills, o fundo de um baú. O AincradUI se liga aqui
 * ({@link #attach}); sem ele, cada pedido tem uma saída simples (action bar, título comum) ou
 * não faz nada.
 *
 * <pre>
 * AincradCore.get().ui().menuEntry("skills", "under_icons/skills", nome, "perfil", p -&gt; abrir(p));
 * AincradCore.get().ui().toast(player, Component.text("Level 12"), Component.text("+2 pontos"));
 * </pre>
 */
public final class UiService {

    /** Uma entrada do menu do jogador. {@code group}: perfil, party, mensagens, mapa ou config. */
    public record Entry(String id, String icon, Component name, String group, Consumer<Player> action) {
    }

    /** Uma barrinha da HUD (a recarga de uma skill): rótulo curto e quanto falta, de 0 (pronta) a 1. */
    public record Bar(String label, double remaining) {
    }

    /** Quem desenha (o AincradUI). */
    public interface Backend {
        void menuEntry(Entry entry);

        /** @return false se o jogador não pode ver o aviso na tela (sem o resource pack) */
        boolean toast(Player player, Component title, Component detail);

        /** Janela de sim/não aberta sem o jogador pedir. @return false se não dá para abrir agora */
        boolean prompt(Player player, Component question, Consumer<Player> yes, Consumer<Player> no);

        /** Uma faixa no meio da tela. @return false se o jogador não a vê */
        boolean banner(Player player, String id);

        /** O título de um baú de {@code rows} linhas com o fundo da interface; o próprio título se não der. */
        Component chestTitle(Player player, Component title, int rows);
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private Backend backend;
    private Function<Player, List<Bar>> skillBars;
    private Function<Player, double[]> energy;

    /**
     * Põe (ou troca) uma entrada no menu do jogador. Pode ser chamado antes de o AincradUI ligar:
     * a entrada fica guardada e entra quando ele chegar.
     */
    public void menuEntry(String id, String icon, Component name, String group, Consumer<Player> action) {
        Entry e = new Entry(id, icon, name, group, action);
        entries.put(id, e);
        if (backend != null) {
            backend.menuEntry(e);
        }
    }

    /** Aviso curto na tela (item recebido, quest atualizada...). Sem a interface, vai no action bar. */
    public void toast(Player player, Component title, Component detail) {
        if (backend != null && backend.toast(player, title, detail)) {
            return;
        }
        player.sendActionBar(detail == null ? title : title.append(Component.text("  ")).append(detail));
    }

    /**
     * Abre uma janela de aceitar ou recusar na frente do jogador, sem ele ter pedido (um convite,
     * um desafio). Só abre com a interface instalada e o jogador livre.
     *
     * @return false se não abriu: quem chama mantém o aviso no chat
     */
    public boolean prompt(Player player, Component question, Consumer<Player> yes, Consumer<Player> no) {
        return backend != null && backend.prompt(player, question, yes, no);
    }

    /**
     * Uma faixa do anime no meio da tela, por um instante: warning, system, immortal,
     * congratulations ou duel. Sem a interface, não faz nada.
     *
     * @return false se o jogador não viu a faixa (quem chama pode avisar de outro jeito)
     */
    public boolean banner(Player player, String id) {
        return backend != null && backend.banner(player, id);
    }

    /** O título de um baú com o fundo da interface, para as telas de pôr e tirar item. */
    public Component chestTitle(Player player, Component title, int rows) {
        return backend == null ? title : backend.chestTitle(player, title, rows);
    }

    /** As recargas mostradas na HUD (até 4). A função devolve só as skills equipadas. */
    public void skillBars(Function<Player, List<Bar>> provider) {
        this.skillBars = provider;
    }

    public List<Bar> skillBars(Player p) {
        if (skillBars == null) {
            return List.of();
        }
        try {
            List<Bar> out = skillBars.apply(p);
            return out == null ? List.of() : out;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** A barra azul da HUD: {atual, máximo}. Sem provedor, a interface mostra a fome. */
    public void energy(Function<Player, double[]> provider) {
        this.energy = provider;
    }

    /** @return {atual, máximo}, ou null sem provedor */
    public double[] energy(Player p) {
        if (energy == null) {
            return null;
        }
        try {
            return energy.apply(p);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public boolean attached() {
        return backend != null;
    }

    /** O AincradUI ligou: recebe as entradas já pedidas. */
    public void attach(Backend b) {
        this.backend = b;
        for (Entry e : new ArrayList<>(entries.values())) {
            b.menuEntry(e);
        }
    }

    public void detach() {
        this.backend = null;
    }
}
