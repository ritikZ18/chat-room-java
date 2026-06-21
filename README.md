# Chat System

Multi-room Java chat with WebRTC signaling, B64 avatar profiles, and a Discord-style SPA frontend.

## Features
- Concurrent multi-room messaging (CopyOnWriteArrayList + SingleThreadExecutor)
- WebRTC video call signaling (offer/answer/ICE relay)
- Base64 avatar persistence in JSON
- Presence tracking with TTL eviction (ScheduledExecutorService)
- Discord-style SPA — login, rooms, members list, call overlay
- Full refresh-state recovery via localStorage

## Setup

```bash
mvn compile
mvn test
mvn exec:java -Dexec.mainClass="com.chatapp.Main"
```

Open `http://localhost:8080` once you wire a WebSocket server (Jetty/Spring Boot).
Or open `index.html` directly — it falls back to demo mode automatically.

## Profile & avatar storage

`data/profiles/users.json` is auto-created on first run:

```json
{
  "u1-uuid": {
    "userId": "u1-uuid",
    "username": "alice",
    "displayName": "Alice",
    "bio": "",
    "avatarBase64": "data:image/png;base64,iVBORw0KGgo...",
    "createdAt": "2025-01-01T00:00:00Z"
  }
}
```

Avatar is stored as a full data-URI. Keep avatars under 256 KB.
This file lives in `data/` (not `src/`, not `target/`) so it survives `mvn clean`.

## WebSocket protocol
Client → Server                       Server → Client

─────────────────────────────────     ───────────────────────────────────

JOIN_ROOM    { roomId }               MESSAGE      { messageId, roomId,

SEND_MESSAGE { roomId, content }                   senderId, senderName,

WEBRTC_OFFER { toUserId, sdp }                     content, type, ts }

WEBRTC_ANSWER{ toUserId, sdp }        USERS_LIST   { users: [...] }

WEBRTC_ICE   { toUserId, candidate }  USER_JOINED  { userId, username }

WEBRTC_HANGUP{ toUserId }             USER_LEFT    { userId }

UPDATE_AVATAR{ avatarB64 }            WEBRTC_OFFER { fromUserId, sdp }

HEARTBEAT    {}                       WEBRTC_HANGUP{ fromUserId }

## Frontend state (localStorage keys)

| Key | Contents |
|-----|----------|
| `ca:user` | `{ userId, username, avatar (B64), joinedAt }` |
| `ca:room` | last active room ID |
| `ca:channels` | `{ text: [], voice: [] }` |
| `ca:drafts` | `{ roomId: draftText }` |
| `ca:unread` | `{ roomId: count }` |
| `ca:m:{roomId}` | last 100 messages, cached per room |

All keys are restored on page refresh — room, drafts, and message history survive.