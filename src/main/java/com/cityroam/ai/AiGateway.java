package com.cityroam.ai;

import java.util.List;

public interface AiGateway {

    void stream(List<AiMessage> messages, TokenConsumer consumer);

    @FunctionalInterface
    interface TokenConsumer {
        void accept(String token);
    }

    class AiMessage {
        private final String role;
        private final String content;

        public AiMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}
