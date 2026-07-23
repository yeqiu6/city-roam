package com.cityroam.ai;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.cityroam.config.AiProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class DashScopeAiGateway implements AiGateway {

    private static final String CONFIGURATION_ERROR = "AI\u670d\u52a1\u6682\u672a\u5f00\u542f";
    private static final String UPSTREAM_ERROR = "AI\u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5";

    private final AiProperties properties;
    private final Supplier<String> apiKeySupplier;

    public DashScopeAiGateway(AiProperties properties) {
        this(properties, () -> System.getenv("AI_API_KEY"));
    }

    DashScopeAiGateway(AiProperties properties, Supplier<String> apiKeySupplier) {
        this.properties = properties;
        this.apiKeySupplier = apiKeySupplier;
    }

    @Override
    public void stream(List<AiMessage> messages, TokenConsumer consumer) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException(CONFIGURATION_ERROR);
        }
        String apiKey = apiKeySupplier.get();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(CONFIGURATION_ERROR);
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint()).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(properties.getConnectTimeoutMillis());
            connection.setReadTimeout(properties.getReadTimeoutMillis());
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "text/event-stream");
            writeRequest(connection, messages);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Unexpected upstream response status");
            }
            readEvents(connection.getInputStream(), consumer);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(UPSTREAM_ERROR);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String endpoint() {
        String baseUrl = properties.getBaseUrl();
        return baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
    }

    private void writeRequest(HttpURLConnection connection, List<AiMessage> messages) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getModelName());
        body.put("stream", true);
        List<Map<String, String>> requestMessages = new ArrayList<>();
        for (AiMessage message : messages) {
            Map<String, String> requestMessage = new HashMap<>();
            requestMessage.put("role", message.getRole());
            requestMessage.put("content", message.getContent());
            requestMessages.add(requestMessage);
        }
        body.put("messages", requestMessages);
        byte[] bytes = JSONUtil.toJsonStr(body).getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
    }

    private void readEvents(InputStream input, TokenConsumer consumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            boolean complete = false;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    complete = true;
                    break;
                }
                String token = token(data);
                if (StringUtils.hasText(token)) {
                    consumer.accept(token);
                }
            }
            if (!complete) {
                throw new IOException("Upstream stream ended before completion");
            }
        }
    }

    private String token(String data) {
        JSONObject root = JSONUtil.parseObj(data);
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JSONObject choice = choices.getJSONObject(0);
        if (choice == null) {
            return null;
        }
        JSONObject delta = choice.getJSONObject("delta");
        return delta == null ? null : delta.getStr("content");
    }
}
