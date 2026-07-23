# 城市漫游 AI 功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 在不升级 Java 8 和 Spring Boot 2.3 的前提下，为城市漫游增加通义千问驱动的店铺推荐客服和点评辅助写作。

**Architecture:** 后端用 JDK 8 的 HttpURLConnection 调用通义千问 OpenAI 兼容流式接口，避免使用需要 Java 17/Spring Boot 3 的最新 LangChain4j。店铺检索、会话记忆、提示词构造和上游流式网关分离；控制器只将 SSE token 转发给前端。前端通过原生 fetch() 消费 SSE，保留当前 Vue 2、Axios 和既有业务 API。

**Tech Stack:** Java 8、Spring Boot 2.3、Spring MVC、Jackson、StringRedisTemplate、MyBatis-Plus、Vue 2、通义千问 OpenAI 兼容 API。

## Global Constraints

- 保持 Java 8 和 Spring Boot 2.3；不添加 LangChain4j、WebFlux、JDK 17 或 Spring Boot 3 依赖。
- API Key 只可从 AI_API_KEY 环境变量读取，绝不能写入源码、YAML、测试、日志、前端或 Git。
- 不修改既有 HTTP API、数据库表、Redis 业务键、MyBatis 映射、店铺/笔记发布逻辑或路由。
- 新的 /ai/** 接口必须受现有登录拦截器保护；会话 Redis key 必须包含当前用户 ID。
- 上游模型仅获得受限的店铺文本上下文，不能获得数据库访问、工具调用或业务写权限。
- 所有自动化测试使用 fake 网关和 Mockito，不得调用真实 Qwen API 或依赖外部 Redis。
- 依照通义千问 OpenAI 兼容接口与流式响应格式实现：https://help.aliyun.com/en/model-studio/compatibility-of-openai-with-dashscope
- 当前官方 LangChain4j 需要 Java 17 和 Spring Boot 3，不适用于本项目：https://docs.langchain4j.dev/tutorials/spring-boot-integration/

---

## File structure

| 路径 | 责任 |
| --- | --- |
| src/main/java/com/cityroam/config/AiProperties.java | AI 非敏感配置绑定与启用状态。 |
| src/main/java/com/cityroam/ai/AiGateway.java | 可替换的流式模型网关接口。 |
| src/main/java/com/cityroam/ai/DashScopeAiGateway.java | 基于 HttpURLConnection 的 DashScope SSE 客户端。 |
| src/main/java/com/cityroam/ai/ShopContextService.java | 从店铺/分类数据产生最多 8 条可信上下文。 |
| src/main/java/com/cityroam/ai/AiConversationService.java | Redis 会话隔离、截断与 TTL。 |
| src/main/java/com/cityroam/ai/AiPromptService.java | 客服与点评助手的系统提示词和消息组装。 |
| src/main/java/com/cityroam/dto/AiChatRequest.java | AI 客服请求。 |
| src/main/java/com/cityroam/dto/AiReviewRequest.java | 点评助手请求。 |
| src/main/java/com/cityroam/controller/AiController.java | 登录用户的 SSE 接口。 |
| src/test/java/com/cityroam/ai/*.java | 无外部服务的 AI 单元测试。 |
| src/main/resources/application.yaml | 非敏感 AI 配置。 |
| nginx-1.18.0/html/cityroam/index.html | AI 向导入口、浮层和 fetch SSE 客户端。 |
| nginx-1.18.0/html/cityroam/blog-edit.html | 点评生成按钮、文风选择和 SSE 客户端。 |
| nginx-1.18.0/html/cityroam/css/index.css、blog-edit.css | AI UI 的局部样式。 |
| README.md | 环境变量与手工验收说明。 |

### Task 1: 构建可测试的 AI 核心层

**Files:**
- Create: src/main/java/com/cityroam/config/AiProperties.java
- Create: src/main/java/com/cityroam/ai/AiGateway.java
- Create: src/main/java/com/cityroam/ai/ShopContextService.java
- Create: src/main/java/com/cityroam/ai/AiConversationService.java
- Create: src/main/java/com/cityroam/ai/AiPromptService.java
- Create: src/test/java/com/cityroam/ai/ShopContextServiceTest.java
- Create: src/test/java/com/cityroam/ai/AiConversationServiceTest.java
- Create: src/test/java/com/cityroam/ai/AiPromptServiceTest.java
- Modify: src/main/java/com/cityroam/CityRoamApplication.java
- Modify: src/main/resources/application.yaml

**Interfaces:**
- Produces AiGateway.stream(List<AiMessage> messages, AiGateway.TokenConsumer consumer).
- Produces AiConversationService.load(Long userId, String conversationId), append(Long userId, String conversationId, String role, String content), and key(Long userId, String conversationId).
- Produces ShopContextService.retrieve(String query), which returns no more than 8 formatted shop entries.
- Produces AiPromptService.chatMessages(List<String> history, String question) and reviewMessages(String shopContext, String title, String content, String style).

- [ ] **Step 1: Write failing core tests**

Create focused JUnit tests with fake IShopService, fake IShopTypeService, and mocked StringRedisTemplate. The tests must assert:
1. retrieve("西湖 咖啡") returns at most 8 entries and includes only fields 名称、分类、地址、人均、评分.
2. AiConversationService.key(7L, "abc") equals ai:conversation:7:abc; append writes a JSON list and uses Duration.ofHours(24).
3. AiPromptService.chatMessages contains the instruction 不得编造 and supplied trusted context; reviewMessages requires only a review of about 80 Chinese characters.

~~~java
@Test
void shouldScopeConversationKeyToUser() {
    assertThat(service.key(7L, "abc")).isEqualTo("ai:conversation:7:abc");
}
~~~

- [ ] **Step 2: Run tests to verify they fail**

Run: mvn -Dtest=ShopContextServiceTest,AiConversationServiceTest,AiPromptServiceTest test

Expected: FAIL because the three core classes do not exist.

- [ ] **Step 3: Implement minimal Java 8-compatible core classes**

Use @ConfigurationProperties(prefix = "ai") on AiProperties, enable it with @EnableConfigurationProperties(AiProperties.class) on CityRoamApplication, and add exactly this non-sensitive configuration:

~~~yaml
ai:
  enabled: false
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model-name: qwen-plus
  connect-timeout-millis: 5000
  read-timeout-millis: 60000
~~~

Use the exact Redis key template ai:conversation:%d:%s. Reject blank or over-64-character conversation IDs, retain only 24 messages (12 turns), and store JSON using existing Hutool JSON utilities. ShopContextService must score matches in shop name, area and type name, then use score/sold as deterministic fallbacks; format the five allowed fields only.

- [ ] **Step 4: Run focused core tests**

Run: mvn -Dtest=ShopContextServiceTest,AiConversationServiceTest,AiPromptServiceTest test

Expected: PASS with no network access.

- [ ] **Step 5: Commit the core layer**

~~~powershell
git add src/main/java/com/cityroam/config/AiProperties.java src/main/java/com/cityroam/ai src/main/java/com/cityroam/CityRoamApplication.java src/main/resources/application.yaml src/test/java/com/cityroam/ai
git commit -m "feat: add AI context and conversation core"
~~~

### Task 2: 实现受控的 DashScope 流式 API

**Files:**
- Create: src/main/java/com/cityroam/ai/DashScopeAiGateway.java
- Create: src/main/java/com/cityroam/dto/AiChatRequest.java
- Create: src/main/java/com/cityroam/dto/AiReviewRequest.java
- Create: src/main/java/com/cityroam/controller/AiController.java
- Create: src/test/java/com/cityroam/ai/DashScopeAiGatewayTest.java
- Create: src/test/java/com/cityroam/controller/AiControllerTest.java

**Interfaces:**
- Consumes the Task 1 services and UserHolder.getUser().getId().
- Exposes POST /ai/chat/stream and POST /ai/review/stream, both producing text/event-stream.
- Request bodies are { "conversationId": "...", "message": "..." } and { "conversationId": "...", "shopId": 1, "title": "...", "content": "...", "style": "真诚自然" }.
- SSE events are token, done and error.

- [ ] **Step 1: Write failing controller and gateway tests**

Use a fake AiGateway that emits "城" and "市", then completes. Assert AiController.chat emits two token events, one done event, and persists the completed assistant message under the current user ID. Assert with ai.enabled=false it emits only error with AI 服务暂未开启. For the HTTP gateway, use a local JDK HttpServer returning:

~~~text
data: {"choices":[{"delta":{"content":"城"}}]}

data: {"choices":[{"delta":{"content":"市"}}]}

data: [DONE]
~~~

Assert the gateway forwards 城市, sets Authorization: Bearer <key>, and never logs the key.

- [ ] **Step 2: Run tests to verify they fail**

Run: mvn -Dtest=DashScopeAiGatewayTest,AiControllerTest test

Expected: FAIL because the gateway, DTOs and controller do not exist.

- [ ] **Step 3: Implement the streaming gateway and controller**

DashScopeAiGateway must return a local configuration error without opening a connection when ai.enabled is false or AI_API_KEY is blank; POST JSON to {base-url}/chat/completions with model, stream:true and role/content messages; parse only data: SSE lines, stop at [DONE], and forward nonblank choices[0].delta.content; apply configured timeouts and convert upstream failures to the user-safe message AI 服务暂时不可用，请稍后再试.

AiController must construct new SseEmitter(70000L), derive the user ID only from UserHolder, validate request lengths (chat message ≤ 500, review title ≤ 80, content ≤ 1000, style ≤ 20), and call emitter.completeWithError only after sending the safe error event. It must never accept a user ID from the client.

- [ ] **Step 4: Run focused tests**

Run: mvn -Dtest=DashScopeAiGatewayTest,AiControllerTest test

Expected: PASS without API credentials, public Internet, Redis, MySQL or the configured external Redis host.

- [ ] **Step 5: Commit the API slice**

~~~powershell
git add src/main/java/com/cityroam/ai/DashScopeAiGateway.java src/main/java/com/cityroam/dto/AiChatRequest.java src/main/java/com/cityroam/dto/AiReviewRequest.java src/main/java/com/cityroam/controller/AiController.java src/test/java/com/cityroam/ai/DashScopeAiGatewayTest.java src/test/java/com/cityroam/controller/AiControllerTest.java
git commit -m "feat: add streaming AI assistant APIs"
~~~

### Task 3: 接入两个移动端 AI 入口并完成交付说明

**Files:**
- Modify: nginx-1.18.0/html/cityroam/index.html
- Modify: nginx-1.18.0/html/cityroam/blog-edit.html
- Modify: nginx-1.18.0/html/cityroam/css/index.css
- Modify: nginx-1.18.0/html/cityroam/css/blog-edit.css
- Modify: README.md
- Test: src/test/java/com/cityroam/controller/AiControllerTest.java

**Interfaces:**
- Consumes Task 2 SSE events from /ai/chat/stream and /ai/review/stream.
- Produces an AI guide overlay on the home page and review insertion into the existing params.content Vue field.
- Preserves all existing axios calls, location.href routes and blog submission logic.

- [ ] **Step 1: Write failing UI acceptance checks**

Add assertions to AiControllerTest that the response media type is text/event-stream. Run:

~~~powershell
rg -n 'ai/chat/stream|fetch\(' nginx-1.18.0/html/cityroam/index.html
rg -n 'ai/review/stream|params\.content' nginx-1.18.0/html/cityroam/blog-edit.html
~~~

Expected before frontend changes: both commands fail to find the AI endpoint text.

- [ ] **Step 2: Implement the home AI guide**

In index.html, add Vue state aiOpen, aiQuestion, aiMessages, aiLoading and a client-generated UUID-like conversation ID stored in sessionStorage under city-roam-ai-conversation. Add a floating AI 向导 button and overlay. Send a JSON POST using fetch with Content-Type: application/json and the existing authorization header from sessionStorage; split SSE blocks on blank lines and append token data to the active assistant message. Show safe error event text in the conversation.

- [ ] **Step 3: Implement AI review insertion**

In blog-edit.html, add a 真诚自然 / 活泼分享 / 简洁推荐 style selector and an AI 帮我写点评 button. Disable it until params.shopId is selected. Stream /ai/review/stream with the selected shop ID, current title/content and style; on done, assign the streamed text to params.content. Do not call /blog from the generation method.

- [ ] **Step 4: Add local styles and operator instructions**

Add only component-scoped selectors for the guide overlay, chat bubbles, composer, generator row and disabled button. In README, document:

~~~powershell
$env:AI_API_KEY = '<your-dashscope-api-key>'
mvn spring-boot:run
~~~

State that the key must not be placed in any file and that ai.enabled: true is required only for a real manual call.

- [ ] **Step 5: Run frontend and build verification**

Run:

~~~powershell
rg -n 'axios\.(get|post|put|delete)|location\.href' nginx-1.18.0/html/cityroam/index.html nginx-1.18.0/html/cityroam/blog-edit.html
mvn -Dtest=ShopContextServiceTest,AiConversationServiceTest,AiPromptServiceTest,DashScopeAiGatewayTest,AiControllerTest test
mvn -DskipTests package
~~~

Expected: existing navigation/Axios sites remain present; focused AI tests and package pass. Run mvn test separately only when the configured Redis host is reachable.

- [ ] **Step 6: Commit UI and documentation**

~~~powershell
git add nginx-1.18.0/html/cityroam/index.html nginx-1.18.0/html/cityroam/blog-edit.html nginx-1.18.0/html/cityroam/css/index.css nginx-1.18.0/html/cityroam/css/blog-edit.css README.md
git commit -m "feat: add city roam AI guide and review helper"
~~~

## Plan self-review

- Spec coverage: Task 1 provides configuration, retrieval, memory and prompts; Task 2 provides safe upstream streaming and protected interfaces; Task 3 provides both user interfaces and operating instructions.
- Placeholder scan: no unfinished markers or unspecified commands remain.
- Interface consistency: both controllers and browser clients use the same DTO fields and SSE event names; Redis user isolation uses the same ai:conversation:{userId}:{conversationId} key in all tasks.
