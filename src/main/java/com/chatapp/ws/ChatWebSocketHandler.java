package com.chatapp.ws;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;   // ← this was missing
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.chatapp.entity.MessageEntity;
import com.chatapp.entity.RoomEntity;
import com.chatapp.entity.UserEntity;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.RoomRepository;
import com.chatapp.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final List<String> DEFAULT_ROOMS =
        List.of("general", "java-dev", "webrtc-help", "random");
    private static final int  HISTORY_LIMIT   = 100;
    private static final int  MAX_CONTENT_LEN = 4_000;
    private static final long MAX_AVATAR_B64  = 350_000L;

    private final ConcurrentHashMap<String, WebSocketSession>             sessions    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>                       sessionUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>                       usernames   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>                       avatarCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<String>>  roomMembers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<String>> voiceRooms = new ConcurrentHashMap<>();

    @Autowired private UserRepository    userRepo;
    @Autowired private MessageRepository msgRepo;
    @Autowired private RoomRepository    roomRepo;
    @Autowired private ObjectMapper      mapper;

    // ── Connect ───────────────────────────────────────────────────
   // 2. Remove the saveAndBroadcast JOIN call — frontend handles it via USER_JOINED
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, String> q = parseQuery(session);
        String userId   = q.getOrDefault("userId",   UUID.randomUUID().toString());
        String username = q.getOrDefault("username", "user_" + userId.substring(0, 6));

        sessions.put(userId, session);
        sessionUser.put(session.getId(), userId);
        usernames.put(userId, username);

        UserEntity u  = userRepo.findById(userId).orElseGet(UserEntity::new);
        boolean isNew = u.getCreatedAt() == null;
        u.setUserId(userId); u.setUsername(username);
        u.setStatus("online"); u.setLastSeen(Instant.now());
        if (isNew) u.setCreatedAt(Instant.now());
        userRepo.save(u);

        loadAvatar(userId);

        DEFAULT_ROOMS.forEach(r -> {
            roomMembers.computeIfAbsent(r, k -> new CopyOnWriteArraySet<>()).add(userId);
            ensureRoom(r, "text");
        });

        // send list then announce — no JOIN message saved to DB
        send(session, usersList());
        broadcast(userId, map("type","USER_JOINED","userId",userId,
                            "username",username,"status","online"));

        log.info("CONNECT {} ({}) | online={}", username, userId, sessions.size());
    }

    private void onJoinVoice(WebSocketSession session, String userId, JsonNode n) {
    String  channelId = n.path("channelId").asText(""); if (channelId.isBlank()) return;
    boolean watchOnly = n.path("watchOnly").asBoolean(false);
    voiceRooms.computeIfAbsent(channelId, k -> new CopyOnWriteArraySet<>()).add(userId);

    Set<String> members = voiceRooms.get(channelId);

    // Tell the joiner who is already present
    List<Map<String, Object>> present = members.stream()
        .filter(uid -> !uid.equals(userId)).map(uid -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId",   uid);
            m.put("username", usernames.getOrDefault(uid, ""));
            m.put("avatar",   avatarCache.get(uid));
            return m;
        }).collect(Collectors.toList());
    send(session, map("type","VOICE_MEMBERS","channelId",channelId,
                      "members",present,"watchOnly",watchOnly));

    // Tell existing members so THEY initiate WebRTC toward the new joiner
    if (!watchOnly) {
        members.stream().filter(uid -> !uid.equals(userId)).forEach(uid -> {
            WebSocketSession s = sessions.get(uid);
            if (s != null && s.isOpen())
                send(s, map("type","VOICE_USER_JOINED","channelId",channelId,
                    "userId",userId,"username",usernames.getOrDefault(userId,""),
                    "avatar",avatarCache.get(userId),"shouldCall",true));
        });
    }
}

private void onLeaveVoice(String userId, JsonNode n) {
    String channelId = n.path("channelId").asText("");
    voiceRooms.forEach((ch, members) -> {
        if ((!channelId.isBlank() && !ch.equals(channelId))) return;
        if (members.remove(userId)) {
            members.forEach(uid -> {
                WebSocketSession s = sessions.get(uid);
                if (s != null) send(s, map("type","VOICE_USER_LEFT","channelId",ch,"userId",userId));
            });
        }
    });
}

    // ── Message ───────────────────────────────────────────────────
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage raw) {
        String userId = sessionUser.get(session.getId()); if (userId == null) return;
        try {
            JsonNode n = mapper.readTree(raw.getPayload());
            switch (n.path("type").asText("")) {
                case "JOIN_ROOM"            -> onJoinRoom(session, userId, n);
                case "SEND_MESSAGE"         -> onSendMessage(userId, n);
                case "WEBRTC_OFFER"         -> relay(userId, n, "WEBRTC_OFFER");
                case "WEBRTC_ANSWER"        -> relay(userId, n, "WEBRTC_ANSWER");
                case "WEBRTC_ICE_CANDIDATE" -> relay(userId, n, "WEBRTC_ICE_CANDIDATE");
                case "WEBRTC_HANGUP"        -> relay(userId, n, "WEBRTC_HANGUP");
                case "UPDATE_AVATAR"        -> onAvatar(userId, n);
                case "TYPING"               -> onTyping(userId, n);
                case "HEARTBEAT"            -> touch(userId);
                case "WHO_IS_ONLINE" -> send(session, usersList());
                case "JOIN_VOICE"    -> onJoinVoice(session, userId, n);
                case "LEAVE_VOICE"   -> onLeaveVoice(userId, n);
            }
        } catch (Exception e) { log.warn("handleTextMessage {}: {}", userId, e.getMessage()); }
    }

    // ── Disconnect ────────────────────────────────────────────────
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = sessionUser.remove(session.getId()); if (userId == null) return;
        sessions.remove(userId);
        String username = usernames.remove(userId);
        avatarCache.remove(userId);
        roomMembers.values().forEach(m -> m.remove(userId));
        userRepo.updateStatus(userId, "offline", Instant.now());
        broadcast(null, map("type","USER_LEFT","userId",userId,
                            "username", username != null ? username : ""));
        log.info("DISCONNECT {} ({}) | online={}", username, userId, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession s, Throwable ex) {
        log.warn("Transport error {}: {}", s.getId(), ex.getMessage());
    }

    // ── Handlers ──────────────────────────────────────────────────
    private void onJoinRoom(WebSocketSession session, String userId, JsonNode n) {
        String roomId = n.path("roomId").asText("general");
        roomMembers.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(userId);
        ensureRoom(roomId, roomId.startsWith("dm-") ? "dm" : "text");
        List<MessageEntity> history = msgRepo.findHistoryByRoom(roomId, PageRequest.of(0, HISTORY_LIMIT));
        List<Map<String, Object>> msgs = history.stream()
            .map(m -> wsMap(m, avatarCache.get(m.getSenderId()))).collect(Collectors.toList());
        send(session, map("type","HISTORY","roomId",roomId,"messages",msgs));
    }

    private void onSendMessage(String userId, JsonNode n) {
        String roomId   = n.path("roomId").asText("general");
        String content  = n.path("content").asText("").strip();
        String clientId = n.path("messageId").asText("").strip();
        String msgId    = clientId.isBlank() ? uuid() : clientId; // honour client id
        if (content.isBlank() || content.length() > MAX_CONTENT_LEN) return;
        saveAndBroadcast(new MessageEntity(msgId, roomId, userId,
            usernames.getOrDefault(userId, "Unknown"), content, "TEXT"), avatarCache.get(userId));
    }

    private void relay(String fromId, JsonNode n, String sigType) {
        WebSocketSession target = sessions.get(n.path("toUserId").asText("")); if (target == null) return;
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", sigType); p.put("fromUserId", fromId);
        p.put("fromUsername", usernames.getOrDefault(fromId,""));
        if (n.has("sdp"))       p.put("sdp",       n.get("sdp").asText());
        if (n.has("candidate")) p.put("candidate", n.get("candidate").asText());
        send(target, p);
    }

    private void onAvatar(String userId, JsonNode n) {
        String b64 = n.path("avatarB64").asText("");
        if (b64.length() > MAX_AVATAR_B64) return;
        avatarCache.put(userId, b64);
        userRepo.findById(userId).ifPresent(u -> { u.setAvatarBase64(b64); userRepo.save(u); });
        broadcast(null, map("type","USER_UPDATED","userId",userId,"avatar",b64));
    }

    private void onTyping(String userId, JsonNode n) {
        String roomId = n.path("roomId").asText("general");
        String json   = toJson(map("type","TYPING","userId",userId,
            "username",usernames.getOrDefault(userId,""),"roomId",roomId));
        roomMembers.getOrDefault(roomId, new CopyOnWriteArraySet<>()).stream()
            .filter(uid -> !uid.equals(userId)).map(sessions::get)
            .filter(s -> s != null && s.isOpen()).forEach(s -> sendRaw(s, json));
    }
    

    private void touch(String userId) { userRepo.updateStatus(userId, "online", Instant.now()); }

    // ── Persistence + broadcast ───────────────────────────────────
    // 1. Don't persist JOIN/LEAVE — they're ephemeral events, not history
    private void saveAndBroadcast(MessageEntity msg, String avatar) {
        if ("TEXT".equals(msg.getType())) {   // ← only TEXT goes to DB
            msgRepo.save(msg);
        }
        String json = toJson(wsMap(msg, avatar));
        Set<String> members = roomMembers.get(msg.getRoomId());
        if (members != null && !members.isEmpty())
            members.stream().map(sessions::get).filter(s -> s != null && s.isOpen())
                .forEach(s -> sendRaw(s, json));
        else broadcast(null, wsMap(msg, avatar));
    }

    private void broadcast(String excludeId, Map<String, Object> payload) {
        String json = toJson(payload);
        sessions.entrySet().stream().filter(e -> !e.getKey().equals(excludeId))
            .map(Map.Entry::getValue).filter(s -> s != null && s.isOpen())
            .forEach(s -> sendRaw(s, json));
    }

    @Scheduled(fixedDelay = 30_000)
    public void sweepStale() {
        sessions.entrySet().removeIf(e -> {
            if (!e.getValue().isOpen()) {
                String uid = e.getKey(), name = usernames.remove(uid);
                avatarCache.remove(uid); roomMembers.values().forEach(m -> m.remove(uid));
                userRepo.updateStatus(uid, "offline", Instant.now());
                broadcast(null, map("type","USER_LEFT","userId",uid,"username",name!=null?name:""));
                return true;
            }
            return false;
        });
    }

    // ── Helpers ───────────────────────────────────────────────────
    private Map<String, Object> usersList() {
        List<Map<String, Object>> users = sessions.keySet().stream().map(uid -> {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("userId", uid); u.put("username", usernames.getOrDefault(uid,""));
            u.put("avatar", avatarCache.get(uid)); u.put("status","online");
            return u;
        }).collect(Collectors.toList());
        return map("type","USERS_LIST","users",users);
    }

    private Map<String, Object> wsMap(MessageEntity m, String avatar) {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("type","MESSAGE");         w.put("messageId", m.getMessageId());
        w.put("roomId", m.getRoomId()); w.put("senderId",  m.getSenderId());
        w.put("senderName", m.getSenderName()); w.put("senderAvatar", avatar);
        w.put("content", m.getContent()); w.put("msgType", m.getType());
        w.put("ts", m.getSentAt().toEpochMilli());
        return w;
    }

    private void ensureRoom(String roomId, String type) {
        if (!roomRepo.existsById(roomId)) roomRepo.save(new RoomEntity(roomId, roomId, type));
    }

    private void loadAvatar(String userId) {
        userRepo.findById(userId).ifPresent(u -> {
            String av = u.getAvatarBase64();
            if (av != null && !av.isBlank()) avatarCache.put(userId, av);
        });
    }

    private void send(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) return;
        try { synchronized (session) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        }} catch (Exception e) { log.warn("send failed: {}", e.getMessage()); }
    }

    private void sendRaw(WebSocketSession s, String json) {
        if (s == null || !s.isOpen()) return;
        try { synchronized (s) { s.sendMessage(new TextMessage(json)); }
        } catch (Exception e) { log.warn("sendRaw failed: {}", e.getMessage()); }
    }

    private String toJson(Object o) {
        try { return mapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    private Map<String, String> parseQuery(WebSocketSession session) {
        String q = session.getUri() != null ? session.getUri().getQuery() : null;
        if (q == null || q.isBlank()) return Map.of();
        Map<String, String> m = new HashMap<>();
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) try {
                m.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            } catch (Exception e) { m.put(kv[0], kv[1]); }
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private <K,V> Map<K,V> map(Object... kv) {
        Map<K,V> m = new LinkedHashMap<>();
        for (int i = 0; i+1 < kv.length; i += 2) m.put((K)kv[i], (V)kv[i+1]);
        return m;
    }

    private String uuid() { return UUID.randomUUID().toString(); }
}