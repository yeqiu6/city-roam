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
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

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
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        result.getAsyncResult();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:token", "data:\u57ce", "data:\u5e02", "event:done");
        verify(conversations).append(7L, "abc", "user", "\u4f60\u597d");
        verify(conversations).append(7L, "abc", "assistant", "\u57ce\u5e02");
    }

    @Test
    void chatAddsFreshTrustedShopContextAsASystemMessage() throws Exception {
        AiConversationService conversations = mock(AiConversationService.class);
        ShopContextService shopContext = mock(ShopContextService.class);
        AiPromptService prompts = new AiPromptService();
        AtomicReference<java.util.List<AiGateway.AiMessage>> captured = new AtomicReference<>();
        AiGateway gateway = (messages, consumer) -> {
            captured.set(messages);
            consumer.accept("好");
        };
        org.mockito.Mockito.when(conversations.load(7L, "abc")).thenReturn(Arrays.asList("user:旧问题"));
        org.mockito.Mockito.when(shopContext.retrieve("西湖咖啡在哪里")).thenReturn(
                "名称：西湖咖啡\n分类：咖啡\n地址：西湖路\n人均：50\n评分：4.5");
        MockMvc mvc = mockMvc(gateway, conversations, prompts, shopContext, asyncExecutor());
        saveUser();

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/ai/chat/stream")
                        .contentType("application/json")
                        .content("{\"conversationId\":\"abc\",\"message\":\"西湖咖啡在哪里\"}"))
                .andReturn();
        result.getAsyncResult();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().get(1).getRole()).isEqualTo("system");
        assertThat(captured.get().get(1).getContent())
                .contains("名称：西湖咖啡", "分类：咖啡", "地址：西湖路", "人均：50", "评分：4.5");
        assertThat(captured.get().get(captured.get().size() - 1).getContent()).isEqualTo("西湖咖啡在哪里");
        verify(shopContext).retrieve("西湖咖啡在哪里");
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
        org.mockito.Mockito.when(shopContext.retrieveById(1L)).thenReturn("context");
        org.mockito.Mockito.when(prompts.reviewMessages("context", "title", "content", "style"))
                .thenReturn(Arrays.asList(new AiGateway.AiMessage("user", "review")));
        MockMvc mvc = mockMvc(gateway, conversations, prompts, shopContext, asyncExecutor());
        saveUser();

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/ai/review/stream")
                        .contentType("application/json")
                        .content("{\"conversationId\":\"abc\",\"shopId\":1,\"title\":\"title\",\"content\":\"content\",\"style\":\"style\"}"))
                .andReturn();
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        result.getAsyncResult();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:token", "data:\u597d", "event:done");
        verify(shopContext).retrieveById(1L);
        verify(prompts).reviewMessages("context", "title", "content", "style");
    }

    @Test
    void reviewRejectsUnknownShopWithoutCallingGateway() throws Exception {
        AiConversationService conversations = mock(AiConversationService.class);
        AiGateway gateway = mock(AiGateway.class);
        ShopContextService shopContext = mock(ShopContextService.class);
        org.mockito.Mockito.when(shopContext.retrieveById(99L)).thenReturn(null);
        MockMvc mvc = mockMvc(gateway, conversations, mock(AiPromptService.class), shopContext, asyncExecutor());
        saveUser();

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/ai/review/stream")
                        .contentType("application/json")
                        .content("{\"conversationId\":\"abc\",\"shopId\":99,\"title\":\"title\",\"content\":\"content\",\"style\":\"style\"}"))
                .andReturn();
        result.getAsyncResult();

        assertThat(result.getResponse().getContentAsString()).contains("event:error", "data:请求参数不合法");
        verify(shopContext).retrieveById(99L);
        verifyNoInteractions(gateway);
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
        org.mockito.Mockito.when(prompts.chatMessages(org.mockito.ArgumentMatchers.anyList(),
                        org.mockito.ArgumentMatchers.anyString(), eq("\u4f60\u597d")))
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
