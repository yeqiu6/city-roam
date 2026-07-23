package com.cityroam.controller;

import com.cityroam.ai.AiConversationService;
import com.cityroam.ai.AiGateway;
import com.cityroam.ai.AiPromptService;
import com.cityroam.ai.ShopContextService;
import com.cityroam.dto.AiChatRequest;
import com.cityroam.dto.AiReviewRequest;
import com.cityroam.dto.UserDTO;
import com.cityroam.utils.UserHolder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/ai")
public class AiController {

    private static final String INVALID_REQUEST = "\u8bf7\u6c42\u53c2\u6570\u4e0d\u5408\u6cd5";
    private static final String UNAVAILABLE = "AI\u670d\u52a1\u6682\u65f6\u4e0d\u53ef\u7528\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5";
    private static final String BUSY = "AI\u670d\u52a1\u7e41\u5fd9\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5";

    private final AiGateway gateway;
    private final AiConversationService conversationService;
    private final AiPromptService promptService;
    private final ShopContextService shopContextService;
    private final TaskExecutor aiSseTaskExecutor;

    public AiController(AiGateway gateway, AiConversationService conversationService,
                        AiPromptService promptService, ShopContextService shopContextService,
                        @Qualifier("aiSseTaskExecutor") TaskExecutor aiSseTaskExecutor) {
        this.gateway = gateway;
        this.conversationService = conversationService;
        this.promptService = promptService;
        this.shopContextService = shopContextService;
        this.aiSseTaskExecutor = aiSseTaskExecutor;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter chat(@RequestBody AiChatRequest request, HttpServletResponse response) {
        utf8(response);
        Long userId = currentUserId();
        SseEmitter emitter = new SseEmitter(70000L);
        execute(emitter, () -> streamChat(emitter, userId, request));
        return emitter;
    }

    @PostMapping(value = "/review/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter review(@RequestBody AiReviewRequest request, HttpServletResponse response) {
        utf8(response);
        Long userId = currentUserId();
        SseEmitter emitter = new SseEmitter(70000L);
        execute(emitter, () -> streamReview(emitter, userId, request));
        return emitter;
    }

    private void streamChat(SseEmitter emitter, Long userId, AiChatRequest request) {
        if (request == null || !validConversation(request.getConversationId())
                || !valid(request.getMessage(), 500)) {
            error(emitter, INVALID_REQUEST);
            return;
        }
        StringBuilder answer = new StringBuilder();
        try {
            Double longitude = request.getLongitude();
            Double latitude = request.getLatitude();
            if (!validCoordinate(longitude, latitude)) {
                longitude = null;
                latitude = null;
            }
            List<AiGateway.AiMessage> messages = promptService.chatMessages(
                    conversationService.load(userId, request.getConversationId()),
                    shopContextService.retrieve(request.getMessage(), longitude, latitude), request.getMessage());
            gateway.stream(messages, token -> {
                answer.append(token);
                send(emitter, "token", token);
            });
            conversationService.append(userId, request.getConversationId(), "user", request.getMessage());
            conversationService.append(userId, request.getConversationId(), "assistant", answer.toString());
            send(emitter, "done", "");
            emitter.complete();
        } catch (Exception e) {
            error(emitter, safeMessage(e));
        }
    }

    private void streamReview(SseEmitter emitter, Long userId, AiReviewRequest request) {
        if (request == null || !validConversation(request.getConversationId())
                || request.getShopId() == null || request.getShopId() <= 0
                || !valid(request.getTitle(), 80) || !valid(request.getContent(), 1000)
                || !valid(request.getStyle(), 20)) {
            error(emitter, INVALID_REQUEST);
            return;
        }
        try {
            String shopContext = shopContextService.retrieveById(request.getShopId());
            if (!StringUtils.hasText(shopContext)) {
                error(emitter, INVALID_REQUEST);
                return;
            }
            gateway.stream(promptService.reviewMessages(shopContext, request.getTitle(),
                    request.getContent(), request.getStyle()), token -> send(emitter, "token", token));
            send(emitter, "done", "");
            emitter.complete();
        } catch (Exception e) {
            error(emitter, safeMessage(e));
        }
    }

    private Long currentUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null || user.getId() <= 0) {
            throw new IllegalStateException("\u672a\u767b\u5f55");
        }
        return user.getId();
    }

    private void execute(SseEmitter emitter, Runnable task) {
        try {
            aiSseTaskExecutor.execute(task);
        } catch (TaskRejectedException e) {
            error(emitter, BUSY);
        }
    }

    private void utf8(HttpServletResponse response) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
        response.setHeader("X-Accel-Buffering", "no");
    }

    private boolean validConversation(String conversationId) {
        return valid(conversationId, 64);
    }

    private boolean validCoordinate(Double longitude, Double latitude) {
        return longitude != null && latitude != null
                && longitude >= -180D && longitude <= 180D
                && latitude >= -90D && latitude <= 90D;
    }

    private boolean valid(String value, int maxLength) {
        return StringUtils.hasText(value) && value.length() <= maxLength;
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            throw new IllegalStateException(UNAVAILABLE);
        }
    }

    private void error(SseEmitter emitter, String message) {
        try {
            send(emitter, "error", message);
        } finally {
            emitter.completeWithError(new IllegalStateException(message));
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return "AI\u670d\u52a1\u6682\u672a\u5f00\u542f".equals(message) ? message : UNAVAILABLE;
    }
}
