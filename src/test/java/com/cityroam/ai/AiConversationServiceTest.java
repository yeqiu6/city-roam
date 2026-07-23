package com.cityroam.ai;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConversationServiceTest {

    @Test
    void shouldScopeConversationKeyToUser() {
        AiConversationService service = new AiConversationService(mock(StringRedisTemplate.class));

        assertThat(service.key(7L, "abc")).isEqualTo("ai:conversation:7:abc");
    }

    @Test
    void shouldRejectMissingOrNonPositiveUserIds() {
        AiConversationService service = new AiConversationService(mock(StringRedisTemplate.class));

        assertThatThrownBy(() -> service.key(null, "abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.key(0L, "abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.key(-1L, "abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appendStoresJsonHistoryForTwentyFourHours() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        AiConversationService service = new AiConversationService(redisTemplate);

        service.append(7L, "abc", "user", "你好");

        org.mockito.ArgumentCaptor<String> json = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("ai:conversation:7:abc"), json.capture(), eq(Duration.ofHours(24)));
        List<String> history = JSONUtil.toList(json.getValue(), String.class);
        assertThat(history).containsExactly("user:你好");
    }

    @Test
    void appendRetainsOnlyTheNewestTwentyFourMessages() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        AtomicReference<String> persistedHistory = new AtomicReference<>();
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("ai:conversation:7:abc")).thenAnswer(invocation -> persistedHistory.get());
        doAnswer(invocation -> {
            persistedHistory.set(invocation.getArgument(1));
            return null;
        }).when(values).set(eq("ai:conversation:7:abc"), anyString(), eq(Duration.ofHours(24)));
        AiConversationService service = new AiConversationService(redisTemplate);

        for (int index = 0; index < 25; index++) {
            service.append(7L, "abc", "user", "message-" + index);
        }

        List<String> history = JSONUtil.toList(persistedHistory.get(), String.class);
        assertThat(history).hasSize(24)
                .startsWith("user:message-1")
                .endsWith("user:message-24");
    }
}
