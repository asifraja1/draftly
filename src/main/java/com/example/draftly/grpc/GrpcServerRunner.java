package com.example.draftly.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Starts a standalone gRPC server (separate from Spring's HTTP port) on
 * grpc.server.port (default 50051), exposing EmailFetcherService to the
 * Python agent. Runs in its own daemon thread.
 */
@Component
@RequiredArgsConstructor
public class GrpcServerRunner {

    @Value("${grpc.server.port:50051}")
    private int port;

    private final EmailFetcherServiceImpl emailFetcherService;

    private Server server;

    @PostConstruct
    public void start() throws Exception {
        server = ServerBuilder.forPort(port)
                .addService(emailFetcherService)
                .build()
                .start();
        System.out.println("[gRPC] Server started on port " + port);

        Thread awaitThread = new Thread(() -> {
            try {
                server.awaitTermination();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "grpc-server");
        awaitThread.setDaemon(true);
        awaitThread.start();
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            System.out.println("[gRPC] Shutting down server on port " + port);
            server.shutdown();
        }
    }
}
