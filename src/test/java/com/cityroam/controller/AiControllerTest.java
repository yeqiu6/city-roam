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

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    private MockMvc mockMvc(AiGateway gateway, AiConversationService conversations) {
        AiPromptService prompts = mock(AiPromptService.class);
        org.mockito.Mockito.when(conversations.load(7L, "abc")).thenReturn(Arrays.asList());
        org.mockito.Mockito.when(prompts.chatMessages(org.mockito.ArgumentMatchers.anyList(), eq("\u4f60\u597d")))
                .thenReturn(Arrays.asList(new AiGateway.AiMessage("user", "\u4f60\u597d")));
        AiController controller = new AiController(gateway, conversations, prompts, mock(ShopContextService.class));
        return MockMvcBuilders.standaloneSetup(controller).build();
    }
}
