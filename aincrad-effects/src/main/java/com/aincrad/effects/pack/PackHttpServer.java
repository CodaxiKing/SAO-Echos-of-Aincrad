package com.aincrad.effects.pack;

import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Servidor HTTP mínimo que entrega o .zip do pack do servidor. */
public final class PackHttpServer {

    private final HttpServer server;
    private final ExecutorService executor;
    private final int port;

    public PackHttpServer(int port, File zip) throws IOException {
        this.port = port;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "AincradEffects-Pack-HTTP");
            t.setDaemon(true);
            return t;
        });
        server.createContext("/", exchange -> {
            try (exchange) {
                if (!zip.isFile()) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] data = Files.readAllBytes(zip.toPath());
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            }
        });
        server.setExecutor(executor);
    }

    public int port() {
        return port;
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }
}
