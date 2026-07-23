package com.cityroam.ai;

import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class AiConversationService {

    private static final int HISTORY_LIMIT = 24;
    private static final Duration HISTORY_TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;

    public AiConversationService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public List<String> load(Long userId, String conversationId) {
        String json = stringRedisTemplate.opsForValue().get(key(userId, conversationId));
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(JSONUtil.toList(json, String.class));
    }

    public void append(Long userId, String conversationId, String role, String content) {
        List<String> history = new ArrayList<>(load(userId, conversationId));
        history.add(role + ":" + content);
        if (history.size() > HISTORY_LIMIT) {
            history = new ArrayList<>(history.subList(history.size() - HISTORY_LIMIT, history.size()));
        }
        stringRedisTemplate.opsForValue().set(key(userId, conversationId), JSONUtil.toJsonStr(history), HISTORY_TTL);
    }

    public String key(Long userId, String conversationId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (!StringUtils.hasText(conversationId) || conversationId.length() > 64) {
            throw new IllegalArgumentException("conversationId must be between 1 and 64 characters");
        }
        return String.format("ai:conversation:%d:%s", userId, conversationId);
    }
}
