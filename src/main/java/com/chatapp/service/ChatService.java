package com.chatapp.service;

import java.util.concurrent.ConcurrentHashMap;

import com.chatapp.messaging.MessageBroker;
import com.chatapp.model.Message;
import com.chatapp.model.MessageType;
import com.chatapp.model.User;
import com.chatapp.model.UserSession;
import com.chatapp.presence.PresenceTracker;
import com.chatapp.room.ChatRoom;
import com.chatapp.room.RoomRegistry;

public class ChatService {
    private final RoomRegistry     registry;
    private final MessageBroker    broker;
    private final PresenceTracker  presenceTracker;
    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    private static final int INBOX_CAPACITY = 256;

    public ChatService() {
        this.registry        = new RoomRegistry();
        this.broker          = new MessageBroker(registry);
        this.presenceTracker = new PresenceTracker(30_000, this::disconnect);
        this.presenceTracker.start();
    }

    public UserSession connect(User user) {
        UserSession session = new UserSession(user, INBOX_CAPACITY);
        sessions.put(user.userId(), session);
        presenceTracker.heartbeat(user.userId());
        System.out.printf("✓ %s connected%n", user.username());
        return session;
    }

    public void disconnect(String userId) {
        UserSession session = sessions.remove(userId);
        if (session == null) return;
        session.deactivate();
        presenceTracker.remove(userId);

        // unsubscribe from every room, clean up empty ones
        registry.listRoomIds().forEach(roomId ->
            registry.find(roomId).ifPresent(room -> {
                room.unsubscribe(session);
                if (room.isEmpty()) registry.remove(roomId);
            })
        );
        System.out.printf("✗ %s disconnected%n", session.getUser().username());
    }

    public void joinRoom(String userId, String roomId) {
        UserSession session = getSession(userId);
        ChatRoom    room    = registry.getOrCreate(roomId);
        if (room.subscribe(session)) {
            room.publish(Message.builder()
                .roomId(roomId).senderId(userId).type(MessageType.JOIN)
                .content(session.getUser().username() + " joined")
                .build());
            System.out.printf("→ %s joined [%s]%n", session.getUser().username(), roomId);
        }
    }

    public void leaveRoom(String userId, String roomId) {
        UserSession session = getSession(userId);
        registry.find(roomId).ifPresent(room -> {
            room.unsubscribe(session);
            room.publish(Message.builder()
                .roomId(roomId).senderId(userId).type(MessageType.LEAVE)
                .content(session.getUser().username() + " left")
                .build());
            if (room.isEmpty()) registry.remove(roomId);
            System.out.printf("← %s left [%s]%n", session.getUser().username(), roomId);
        });
    }

    public void sendMessage(String userId, String roomId, String content) {
        getSession(userId); // validates session exists
        presenceTracker.heartbeat(userId); // refresh TTL on activity
        broker.route(Message.builder()
            .roomId(roomId).senderId(userId).content(content).type(MessageType.TEXT)
            .build());
    }

    public void heartbeat(String userId) {
        UserSession s = sessions.get(userId);
        if (s != null) { s.updateHeartbeat(); presenceTracker.heartbeat(userId); }
    }

    public void shutdown() {
        presenceTracker.stop();
        registry.listRoomIds().forEach(registry::remove);
    }

    private UserSession getSession(String userId) {
        UserSession s = sessions.get(userId);
        if (s == null) throw new IllegalStateException("No session for userId: " + userId);
        return s;
    }

    public PresenceTracker getPresenceTracker() {
        return presenceTracker;
    }
}