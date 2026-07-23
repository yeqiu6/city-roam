# AI Nearby Recommendation Design

## Goal

Make AI guidance retrieve City Roam shops for natural Chinese questions such as "附近有什么适合朋友聚餐的美食店？", and use optional browser location to prioritize nearby results.

## Scope

- Extract known shop-category names from Chinese questions without relying on whitespace.
- Map a small set of explicit activity intents to existing categories: dining or gathering to 美食, singing to KTV, drinks to 酒吧, party to 轰趴馆, and relaxation to 按摩·足疗.
- Send browser longitude and latitude only with the active chat request, after the user grants browser geolocation permission.
- If the question says "附近" and valid coordinates are available, rank matching shops by distance first. Otherwise preserve relevance, score, and sales ranking.
- Keep all location data request-scoped: do not persist it in Redis or the database.

## API Contract

`POST /ai/chat/stream` accepts two optional JSON fields in addition to the existing fields:

```json
{
  "conversationId": "uuid",
  "message": "附近有什么适合朋友聚餐的美食店？",
  "longitude": 120.14983,
  "latitude": 30.31211
}
```

Coordinates must be valid longitude/latitude values. Invalid or absent coordinates cause normal non-location ranking; they do not reject an otherwise valid chat message.

## Retrieval and Ranking

The backend loads the existing trusted shop and category data. It assigns relevance for a matching category, shop name, area, or explicit intent. Only positive-relevance shops enter the AI context. For nearby requests, shops with coordinates sort by shortest Haversine distance; score and sales break ties. The formatted context may include a rounded distance, while retaining the existing trusted name, category, address, average price, and score fields.

## Frontend Behavior

When a user submits a guidance question, the client tries `navigator.geolocation.getCurrentPosition` with a short timeout. Permission denial, timeout, or unavailable browser APIs do not block the request: it immediately sends the normal chat payload. Coordinates are kept only in the local request object.

## Verification

- Unit tests prove a Chinese category question selects matching shops.
- Unit tests prove a gathering intent selects food shops.
- Unit tests prove a nearby query sorts a closer matching shop first and exposes distance in its trusted context.
- Controller tests prove optional coordinates reach the retrieval service without making coordinates mandatory.
- Full Maven tests and a manual browser request verify the complete path.
