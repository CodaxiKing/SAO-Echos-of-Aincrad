package com.aincrad.ui.screen;

import com.aincrad.ui.config.UiSettings.DrawMode;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;

/** Menu aberto de um jogador: modo de desenho, pilha de telas e o renderizador. */
public final class MenuSession {

    private final Player player;
    private final DrawMode mode;
    private final Renderer renderer;
    private final Deque<Screen> stack = new ArrayDeque<>();
    private final long openedAt;
    private boolean closed;

    MenuSession(Player player, DrawMode mode, Renderer renderer, long openedAt) {
        this.player = player;
        this.mode = mode;
        this.renderer = renderer;
        this.openedAt = openedAt;
    }

    public Player player() {
        return player;
    }

    public DrawMode mode() {
        return mode;
    }

    public Renderer renderer() {
        return renderer;
    }

    public long openedAt() {
        return openedAt;
    }

    public Screen current() {
        return stack.peek();
    }

    /** Abre uma tela por cima da atual (agachar volta para a anterior). */
    public void push(Screen screen) {
        if (closed) {
            return;
        }
        stack.push(screen);
        renderer.show(screen);
    }

    /** Troca a tela atual sem empilhar. */
    public void replace(Screen screen) {
        if (closed) {
            return;
        }
        if (!stack.isEmpty()) {
            stack.pop();
        }
        push(screen);
    }

    /** @return false quando não há tela anterior (o menu deve fechar) */
    boolean pop() {
        if (stack.size() <= 1) {
            return false;
        }
        stack.pop();
        renderer.show(stack.peek());
        return true;
    }

    boolean closed() {
        return closed;
    }

    void markClosed() {
        closed = true;
        stack.clear();
    }
}
