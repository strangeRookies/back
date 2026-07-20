package com.strange.safety.vlm.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.vlm.service.VlmIndexPayloadParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiVlmWorkerClientRateLimitTest {

    @Test
    void preservesUpstream429AsStatusAwareFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/vlm/jobs", exchange -> {
            byte[] response = "{\"message\":\"Gemini quota exhausted\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            AiVlmWorkerClient client = new AiVlmWorkerClient(mapper, new VlmIndexPayloadParser(mapper));
            ReflectionTestUtils.setField(client, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(client, "timeoutSeconds", 5L);

            AiVlmWorkerClient.AiVlmWorkerException failure = assertThrows(
                    AiVlmWorkerClient.AiVlmWorkerException.class,
                    () -> client.processClipJob(1L, "incident", "camera", "https://clip",
                            "COLLAPSE", "CRITICAL", "2026-07-17T12:00:00Z", 0.0, null)
            );

            assertEquals(429, failure.getHttpStatus());
            assertTrue(failure.isRateLimited());
            assertTrue(failure.isTransient());
            assertTrue(failure.getMessage().startsWith("ai_worker_transient_http_429"));
        } finally {
            server.stop(0);
        }
    }
}
