package com.cityroam.controller;

import com.cityroam.ai.AiConversationService;
import com.cityroam.ai.AiGateway;
import com.cityroam.ai.AiPromptService;
import com.cityroam.ai.ShopContextService;
import com.cityroam.dto.UserDTO;
import com.cityroam.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AiControllerTest {

    @AfterEach
    void clearUser() {
        UserHolder.removeUser();
    }

    @Test
    void chatStreamsTokensThenDoneAndPersistsAssistantReplyForCurrentUser() throws Exception {
        AiConversationService conversations = mock(AiConversationService.class);
        AiGateway gateway = (messages, consumer) -> {
            consumer.accept("\u57ce");
            consumer.accept("\u5e02");
        };
        MockMvc mvc = mockMvc(gateway, conversations);
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/ai/chat/stream")
                        .contentType("application/json")
                        .content("{\"conversationId\":\"abc\",\"message\":\"\\u4f60\\u597d\"}"))
                .andReturn();
        result.getAsyncResult();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:token", "data:\u57ce", "data:\u5e02", "event:done");
        verify(conversations).append(7L, "abc", "user", "\u4f60\u597d");
        verify(conversations).append(7L, "abc", "assistant", "\u57ce\u5e02");
    }

    @Test
    void chatEmitsOnlySafeErrorWhenGatewayIsDisabled() throws Exception {
        AiConversationService conversations = mock(AiConversationService.class);
        AiGateway gateway = (messages, consumer) -> {
            throw new IllegalStateException("AI\u670d\u52a1\u6682\u672a\u5f00\u542f");
        };
        MockMvc mvc = mockMvc(gateway, conversations);
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/ai/chat/stream")
                        .contentType("application/json")
                        .content("{\"conversationId\":\"abc\",\"message\":\"\\u4f60\\u597d\"}"))
                .andReturn();
        result.getAsyncResult();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:error", "data:AI\u670d\u52a1\u6682\u672a\u5f00\u542f");
        assertThat(body).doesNotContain("event:token", "event:done");
    }

    @Test
    void chatEmitsBusyErrorWithoutCallingGatewayWhenExecutorRejectsWork() throws Exception {
        AiConversationService conversations = mock(AiConversationService.class);
        AiGateway gateway = mock(AiGateway.class);
        MockMvc mvc = mockMvc(gateway, conversations, task -> {
            throw new TaskRejectedException("saturated");
        });
        saveUser();

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/ai/chat/stream")
                        .contentType("application/json")
                        .content("{\"conversationId\":\"abc\",\"message\":\"\\u4f60\\u597d\"}"))
                .andReturn();
        result.getAsyncResult();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:error", "data:AI\u670d\u52a1\u7e41\u5fd9\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5");
        assertThat(body).doesNotContain("event:token", "event:done");
        verifyNoInteractions(gateway);
    }

    @Test
    void reviewStreamsTokensThenDoneUsingTrustedShopContext() throws Exception {
        AiConversationService conversations = mock(AiConversationService.class);
        AiGateway gateway = (messages, consumer) -> consumer.accept("\u597d");
        AiPromptService prompts = mock(AiPromptService.class);
        ShopContextService shopContext = mock(ShopContextService.class);
        org.mockito.Mockito.when(shopContext.retrieve("1 title content")).thenReturn("context");
        org.mockito.Mockito.when(prompts.reviewMessages("context", "title", "content", "style"))
                .thenReturn(Arrays.asList(new AiGateway.AiMessage("user", "review")));
        MockMvc mvc = mockMvc(gateway, conversations, prompts, shopContext, asyncExecutor());
        saveUser();

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/ai/review/stream")
                        .contentType("application/json")
                        .content("{\"conversationId\":\"abc\",\"shopId\":1,\"title\":\"title\",\"content\":\"content\",\"style\":\"style\"}"))
                .andReturn();
        result.getAsyncResult();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:token", "data:\u597d", "event:done");
        verify(shopContext).retrieve("1 title content");
        verify(prompts).reviewMessages("context", "title", "content", "style");
    }

    @Test
    void reviewRejectsTitleLongerThanEightyCharactersWithoutCallingGateway() throws Exception {
        AiConversationService conversations = mock(AiConversationService.class);
        AiGateway gateway = mock(AiGateway.class);
        MockMvc mvc = mockMvc(gateway, conversations);
        saveUser();
        String title = repeat('a', 81);

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/ai/review/stream")
                        .contentType("application/json")
                        .content("{\"conversationId\":\"abc\",\"shopId\":1,\"title\":\"" + title
                                + "\",\"content\":\"content\",\"style\":\"style\"}"))
                .andReturn();
        result.getAsyncResult();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:error", "data:\u8bf7\u6c42\u53c2\u6570\u4e0d\u5408\u6cd5");
        assertThat(body).doesNotContain("event:token", "event:done");
        verifyNoInteractions(gateway);
    }

    private MockMvc mockMvc(AiGateway gateway, AiConversationService conversations) {
        return mockMvc(gateway, conversations, asyncExecutor());
    }

    private MockMvc mockMvc(AiGateway gateway, AiConversationService conversations, TaskExecutor executor) {
        AiPromptService prompts = mock(AiPromptService.class);
        org.mockito.Mockito.when(conversations.load(7L, "abc")).thenReturn(Arrays.asList());
        org.mockito.Mockito.when(prompts.chatMessages(org.mockito.ArgumentMatchers.anyList(), eq("\u4f60\u597d")))
                .thenReturn(Arrays.asList(new AiGateway.AiMessage("user", "\u4f60\u597d")));
        return mockMvc(gateway, conversations, prompts, mock(ShopContextService.class), executor);
    }

    private MockMvc mockMvc(AiGateway gateway, AiConversationService conversations, AiPromptService prompts,
                            ShopContextService shopContext, TaskExecutor executor) {
        AiController controller = new AiController(gateway, conversations, prompts, shopContext, executor);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private TaskExecutor asyncExecutor() {
        return task -> {
            Thread thread = new Thread(task, "test-ai-sse");
            thread.start();
        };
    }

    private void saveUser() {
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);
    }

    private String repeat(char character, int count) {
        StringBuilder value = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            value.append(character);
        }
        return value.toString();
    }
}
