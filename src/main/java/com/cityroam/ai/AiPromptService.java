package com.cityroam.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiPromptService {

    private static final String CHAT_INSTRUCTION = "你是城市漫游助手。回答只能依据可信商铺上下文和对话历史，不得编造商铺、地址、评分或价格。";
    private static final String REVIEW_INSTRUCTION = "请根据可信商铺上下文与用户内容，仅生成约80字的中文点评。仅输出点评正文，不要包含标题、解释或其他内容。";

    public List<AiGateway.AiMessage> chatMessages(List<String> history, String question) {
        List<AiGateway.AiMessage> messages = new ArrayList<>();
        messages.add(new AiGateway.AiMessage("system", CHAT_INSTRUCTION));
        if (history != null) {
            for (String item : history) {
                messages.add(historyMessage(item));
            }
        }
        messages.add(new AiGateway.AiMessage("user", question));
        return messages;
    }

    public List<AiGateway.AiMessage> reviewMessages(String shopContext, String title, String content, String style) {
        List<AiGateway.AiMessage> messages = new ArrayList<>();
        messages.add(new AiGateway.AiMessage("system", REVIEW_INSTRUCTION));
        messages.add(new AiGateway.AiMessage("user", "可信商铺上下文：" + shopContext
                + "\n标题：" + title
                + "\n内容：" + content
                + "\n风格：" + style));
        return messages;
    }

    private AiGateway.AiMessage historyMessage(String item) {
        int separator = item == null ? -1 : item.indexOf(':');
        if (separator <= 0) {
            return new AiGateway.AiMessage("user", item);
        }
        String role = item.substring(0, separator);
        if (!"assistant".equals(role) && !"system".equals(role) && !"user".equals(role)) {
            role = "user";
        }
        return new AiGateway.AiMessage(role, item.substring(separator + 1));
    }
}
