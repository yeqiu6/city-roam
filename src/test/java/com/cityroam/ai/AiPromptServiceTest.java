package com.cityroam.ai;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiPromptServiceTest {

    private final AiPromptService service = new AiPromptService();

    @Test
    void chatMessagesIncludeGroundingInstructionAndTrustedContext() {
        List<AiGateway.AiMessage> messages = service.chatMessages(
                Arrays.asList("system:可信商铺上下文：名称：西湖咖啡", "user:附近有什么？", "assistant:可以去西湖咖啡"),
                "西湖有哪些咖啡店？");

        assertThat(messages.get(0).getContent()).contains("不得编造", "可信商铺上下文");
        assertThat(messages).extracting(AiGateway.AiMessage::getContent)
                .contains("可信商铺上下文：名称：西湖咖啡", "附近有什么？", "可以去西湖咖啡", "西湖有哪些咖啡店？");
    }

    @Test
    void reviewMessagesRequestOnlyAnEightyChineseCharacterReview() {
        List<AiGateway.AiMessage> messages = service.reviewMessages(
                "名称：西湖咖啡", "西湖咖啡", "环境很好，咖啡很香。", "温暖");

        assertThat(messages.get(0).getContent()).contains("仅生成约80字的中文点评");
        assertThat(messages.get(1).getContent())
                .contains("名称：西湖咖啡", "西湖咖啡", "环境很好，咖啡很香。", "温暖");
    }
}
