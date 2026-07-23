package com.cityroam.dto;

import lombok.Data;

@Data
public class AiChatRequest {
    private String conversationId;
    private String message;
}
