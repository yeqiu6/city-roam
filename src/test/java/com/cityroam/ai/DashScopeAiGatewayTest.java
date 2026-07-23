package com.cityroam.ai;

import com.cityroam.config.AiProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashScopeAiGatewayTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void forwardsSseTokensAndUsesBearerAuthorization() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"\\u57ce\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"\\u5e02\"}}]}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        List<String> tokens = new ArrayList<>();

        gateway(true, "test-key").stream(Arrays.asList(new AiGateway.AiMessage("user", "hello")), tokens::add);

        assertThat(tokens).containsExactly("城", "市");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
    }

    @Test
    void rejectsDisabledConfigurationWithoutOpeningConnection() {
        AtomicInteger keyLookupCount = new AtomicInteger();
        DashScopeAiGateway gateway = new DashScopeAiGateway(properties(false), () -> {
            keyLookupCount.incrementAndGet();
            return "test-key";
        });

        assertThatThrownBy(() -> gateway.stream(Arrays.asList(new AiGateway.AiMessage("user", "hello")), token -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI服务暂未开启");
        assertThat(keyLookupCount).hasValue(0);
    }

    @Test
    void rejectsBlankApiKeyWithoutOpeningConnection() {
        DashScopeAiGateway gateway = new DashScopeAiGateway(properties(true), () -> " ");

        assertThatThrownBy(() -> gateway.stream(Arrays.asList(new AiGateway.AiMessage("user", "hello")), token -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI服务暂未开启");
    }

    private DashScopeAiGateway gateway(boolean enabled, String apiKey) {
        return new DashScopeAiGateway(properties(enabled), () -> apiKey);
    }

    private AiProperties properties(boolean enabled) {
        AiProperties properties = new AiProperties();
        properties.setEnabled(enabled);
        properties.setBaseUrl(server == null ? "http://localhost:1" : "http://localhost:" + server.getAddress().getPort());
        properties.setModelName("test-model");
        properties.setConnectTimeoutMillis(1000);
        properties.setReadTimeoutMillis(1000);
        return properties;
    }
}
