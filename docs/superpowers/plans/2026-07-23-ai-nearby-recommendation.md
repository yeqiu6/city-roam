# AI Nearby Recommendation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ground Chinese AI guidance questions in matching City Roam shops and prioritize nearby matches when the browser supplies a permitted location.

**Architecture:** `ShopContextService` expands Chinese category and activity intent terms using existing `ShopType` names, then ranks trusted shops. `AiChatRequest` carries optional request-only coordinates from the AI modal; the controller forwards them to the retrieval service. The frontend requests browser geolocation only for the submitted question and degrades to normal ranking if it is unavailable.

**Tech Stack:** Spring Boot 2.3 / Java 8, MyBatis-Plus entities, JUnit 5 + Mockito, vanilla browser JavaScript.

## Global Constraints

- Do not add dependencies or persist coordinates in Redis, MySQL, conversation history, logs, or frontend storage.
- Coordinates are optional and invalid values must use non-location ranking, not reject a valid chat message.
- The AI context remains restricted to trusted shop fields; optional distance is derived from trusted coordinates.
- Do not add API keys to source, configuration, tests, or commits.

---

### Task 1: Add Chinese intent retrieval and distance ranking

**Files:**
- Modify: `src/main/java/com/cityroam/ai/ShopContextService.java`
- Modify: `src/test/java/com/cityroam/ai/ShopContextServiceTest.java`

**Interfaces:**
- Produces: `String retrieve(String query, Double longitude, Double latitude)` while retaining `retrieve(String query)` as a no-location delegator.
- Consumes: `Shop.x` as longitude and `Shop.y` as latitude.

- [ ] **Step 1: Write failing retrieval tests**

```java
assertThat(service.retrieve("附近有什么适合朋友聚餐的美食店？"))
        .contains("名称：好友餐厅")
        .doesNotContain("名称：唱歌馆");
assertThat(service.retrieve("附近美食", 120.0D, 30.0D))
        .contains("距离：0.0km");
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ShopContextServiceTest test`

Expected: the Chinese question returns `NO_MATCH_CONTEXT` before keyword expansion.

- [ ] **Step 3: Implement the minimum retrieval extension**

```java
public String retrieve(String query) {
    return retrieve(query, null, null);
}

public String retrieve(String query, Double longitude, Double latitude) {
    // Expand known type names and explicit activity aliases, rank matching shops,
    // and sort nearby queries by Haversine distance when both coordinates are valid.
}
```

Use existing type names as extractable category tokens. Add only the five aliases specified in the design. Add `距离：%.1fkm` only when a distance was calculated.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=ShopContextServiceTest test`

Expected: all `ShopContextServiceTest` tests pass.

- [ ] **Step 5: Commit the completed slice**

```bash
git add src/main/java/com/cityroam/ai/ShopContextService.java src/test/java/com/cityroam/ai/ShopContextServiceTest.java
git commit -m "feat: rank AI shop context for Chinese nearby queries"
```

### Task 2: Carry optional location through the chat API

**Files:**
- Modify: `src/main/java/com/cityroam/dto/AiChatRequest.java`
- Modify: `src/main/java/com/cityroam/controller/AiController.java`
- Modify: `src/test/java/com/cityroam/controller/AiControllerTest.java`

**Interfaces:**
- Consumes: `AiChatRequest.longitude` and `AiChatRequest.latitude` as nullable `Double` values.
- Produces: controller calls `shopContextService.retrieve(message, longitude, latitude)`.

- [ ] **Step 1: Write a failing controller test**

```java
verify(shopContextService).retrieve("附近美食", 120.0D, 30.0D);
```

Use a valid authenticated test request and assert that missing coordinates still invoke `retrieve(message, null, null)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=AiControllerTest test`

Expected: the old controller only calls `retrieve(message)`.

- [ ] **Step 3: Implement optional coordinate forwarding**

```java
private boolean validCoordinate(Double longitude, Double latitude) {
    return longitude != null && latitude != null
            && longitude >= -180D && longitude <= 180D
            && latitude >= -90D && latitude <= 90D;
}
```

Set invalid pairs to `null, null` before calling the overload. Do not store coordinates in `AiConversationService`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=AiControllerTest test`

Expected: all controller SSE tests pass.

- [ ] **Step 5: Commit the completed slice**

```bash
git add src/main/java/com/cityroam/dto/AiChatRequest.java src/main/java/com/cityroam/controller/AiController.java src/test/java/com/cityroam/controller/AiControllerTest.java
git commit -m "feat: accept optional AI chat location"
```

### Task 3: Request browser location for AI guidance

**Files:**
- Modify: `nginx-1.18.0/html/cityroam/index.html`

**Interfaces:**
- Produces: JSON chat payload with optional `longitude` and `latitude` fields.
- Consumes: standard `navigator.geolocation.getCurrentPosition` browser API.

- [ ] **Step 1: Add a request-local location helper**

```javascript
async getAiLocation() {
  if (!navigator.geolocation) return {};
  return new Promise(resolve => navigator.geolocation.getCurrentPosition(
    position => resolve({longitude: position.coords.longitude, latitude: position.coords.latitude}),
    () => resolve({}),
    {enableHighAccuracy: false, timeout: 3000, maximumAge: 60000}
  ));
}
```

- [ ] **Step 2: Include helper output only in the active fetch payload**

```javascript
const location = await this.getAiLocation();
body: JSON.stringify({conversationId: this.aiConversationId, message: question, ...location})
```

Do not place coordinates in component state, `sessionStorage`, or a URL.

- [ ] **Step 3: Verify browser behavior manually**

Run the frontend, grant location permission, ask `附近有什么适合朋友聚餐的美食店？`, and confirm the reply includes matched shop names. Deny the permission and confirm a category question still gets matched shops.

- [ ] **Step 4: Commit the completed slice**

```bash
git add nginx-1.18.0/html/cityroam/index.html
git commit -m "feat: send optional browser location to AI guide"
```

### Task 4: Complete regression verification

**Files:**
- No production changes expected.

- [ ] **Step 1: Run all tests**

Run: `mvn test`

Expected: all tests pass with no skipped tests.

- [ ] **Step 2: Build the application**

Run: `mvn -DskipTests package`

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Inspect the final diff and secret exposure**

Run: `git diff HEAD~3..HEAD --check` and `git diff HEAD~3..HEAD | rg -i 'sk-[a-z0-9]|api[_-]?key\\s*[:=].*[a-z0-9]'`

Expected: no whitespace errors and no secret values.

