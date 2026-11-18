package com.aincrad.economy;

import com.aincrad.core.AincradCore;
import com.aincrad.core.db.Database;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Custódia de Col: o valor sai do saldo de quem paga e fica guardado até ser liberado para
 * alguém ou devolvido ao dono. Enquanto está em custódia não pode ser gasto, porque já não está
 * no saldo. Usada pelas encomendas do AincradLife, para ninguém sair no prejuízo.
 *
 * <p>Cada movimento é uma atualização atômica no banco ("tira X se ainda tem X"), então dois
 * servidores da rede nunca liberam o mesmo Col duas vezes. Os resultados chegam na thread principal.
 */
public final class HoldService {

    public static final List<String> MIGRATIONS = List.of(
            "CREATE TABLE IF NOT EXISTS eco_holds (id VARCHAR(36) NOT NULL PRIMARY KEY, owner VARCHAR(36) NOT NULL, "
                    + "amount BIGINT NOT NULL, original BIGINT NOT NULL, reason VARCHAR(64) NOT NULL, created BIGINT NOT NULL)");

    private final EconomyService eco;
    private final Database db;
    private final Logger log;

    public HoldService(EconomyService eco, Database db, Logger log) {
        this.eco = eco;
        this.db = db;
        this.log = log;
    }

    private static String cut(String reason) {
        String r = reason == null ? "custodia" : reason;
        return r.length() > 64 ? r.substring(0, 64) : r;
    }

    private <T> CompletableFuture<T> onMain(CompletableFuture<T> work, T onError) {
        CompletableFuture<T> out = new CompletableFuture<>();
        work.whenComplete((value, err) -> AincradCore.get().runSync(() -> {
            if (err != null) {
                log.warning("Custódia: erro no banco: " + err.getMessage());
                out.complete(onError);
            } else {
                out.complete(value);
            }
        }));
        return out;
    }

    /**
     * Põe Col do jogador em custódia. Ele precisa estar online neste servidor e ter o saldo.
     *
     * @return o id da custódia, ou null se não tinha saldo (ou alguém cancelou a transação)
     */
    public String hold(UUID owner, long amount, String reason) {
        if (amount <= 0 || !eco.withdraw(owner, amount, cut(reason))) {
            return null;
        }
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO eco_holds (id, owner, amount, original, reason, created) VALUES (?, ?, ?, ?, ?, ?)",
                id, owner.toString(), amount, amount, cut(reason), System.currentTimeMillis()).whenComplete((n, err) -> {
            if (err != null) {
                // O registro não foi gravado: o Col volta para o dono.
                log.warning("Custódia: não foi possível gravar " + amount + " Col de " + owner + ": " + err.getMessage());
                AincradCore.get().runSync(() -> eco.accounts().apply(owner, amount, cut(reason) + ":estorno"));
            }
        });
        return id;
    }

    /** Quanto ainda está guardado nesta custódia (0 se não existe ou já foi toda usada). */
    public CompletableFuture<Long> held(String holdId) {
        return onMain(db.query("SELECT amount FROM eco_holds WHERE id = ?", rs -> rs.getLong(1), holdId)
                .thenApply(l -> l.isEmpty() ? 0L : l.get(0)), 0L);
    }

    /**
     * Libera parte (ou tudo) da custódia para alguém, online ou não, em qualquer servidor.
     *
     * @return true se liberou; false se a custódia não tem esse valor
     */
    public CompletableFuture<Boolean> release(String holdId, UUID to, long amount, String reason) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(amount == 0);
        }
        CompletableFuture<Boolean> taken = db.update("UPDATE eco_holds SET amount = amount - ? WHERE id = ? AND amount >= ?",
                amount, holdId, amount).thenApply(n -> n > 0);
        return onMain(taken, false).thenApply(ok -> {
            if (ok && !eco.deposit(to, amount, cut(reason))) {
                // Alguém cancelou o depósito: o valor volta para a custódia.
                db.update("UPDATE eco_holds SET amount = amount + ? WHERE id = ?", amount, holdId);
                return false;
            }
            return ok;
        });
    }

    /**
     * Devolve ao dono tudo o que ainda está na custódia.
     *
     * @return o valor devolvido (0 se não havia nada)
     */
    public CompletableFuture<Long> refund(String holdId, String reason) {
        CompletableFuture<Object[]> taken = db.async(c -> {
            List<Object[]> rows = Database.query(c, "SELECT owner, amount FROM eco_holds WHERE id = ?",
                    rs -> new Object[]{rs.getString(1), rs.getLong(2)}, holdId);
            if (rows.isEmpty() || (Long) rows.get(0)[1] <= 0) {
                return null;
            }
            // Só zera se o valor ainda é o que foi lido: o valor devolvido é exato.
            int n = Database.update(c, "UPDATE eco_holds SET amount = 0 WHERE id = ? AND amount = ?", holdId, rows.get(0)[1]);
            return n > 0 ? rows.get(0) : null;
        });
        return onMain(taken, null).thenApply(row -> {
            if (row == null) {
                return 0L;
            }
            long amount = (Long) row[1];
            eco.accounts().apply(UUID.fromString((String) row[0]), amount, cut(reason));
            return amount;
        });
    }

    /**
     * Tira de circulação parte da custódia (a taxa de uma entrega de um contrato que continua).
     *
     * @return true se retirou; false se a custódia não tem esse valor
     */
    public CompletableFuture<Boolean> burn(String holdId, long amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(amount == 0);
        }
        return onMain(db.update("UPDATE eco_holds SET amount = amount - ? WHERE id = ? AND amount >= ?", amount, holdId, amount)
                .thenApply(n -> n > 0), false);
    }

    /**
     * Tira de circulação o que resta na custódia (a taxa de um contrato).
     *
     * @return o valor retirado
     */
    public CompletableFuture<Long> burn(String holdId) {
        CompletableFuture<Long> taken = db.async(c -> {
            List<Long> rows = Database.query(c, "SELECT amount FROM eco_holds WHERE id = ?", rs -> rs.getLong(1), holdId);
            if (rows.isEmpty() || rows.get(0) <= 0) {
                return 0L;
            }
            return Database.update(c, "UPDATE eco_holds SET amount = 0 WHERE id = ? AND amount = ?", holdId, rows.get(0)) > 0 ? rows.get(0) : 0L;
        });
        return onMain(taken, 0L);
    }
}
