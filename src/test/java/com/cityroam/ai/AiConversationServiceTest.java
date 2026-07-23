package com.cityroam.ai;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
}
