package com.cityroam.dto;

import lombok.Data;

@Data
public class AiReviewRequest {
    private String conversationId;
    private Long shopId;
    private String title;
    private String content;
    private String style;
}
