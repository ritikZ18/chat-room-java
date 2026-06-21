package com.chatapp.service;

import com.chatapp.messaging.MessageBroker;
import com.chatapp.model.Message;
import com.chatapp.model.MessageType;
import com.chatapp.model.User;
import com.chatapp.model.UserSession;
import com.chatapp.presence.PresenceTracker;
import com.chatapp.profile.UserProfile;
import com.chatapp.profile.UserProfileStore;
import com.chatapp.room.RoomRegistry;
import com.chatapp.signaling.SignalingService;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ChatService {
    private static final int INBOX_CAPACITY = 256;

    private final RoomRegistry    registry;
    private final MessageBroker   broker;
    private final PresenceTracker presenceTracker;
    private final SignalingService signalingService;
    private final UserProfileStore profileStore;
    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    public ChatService(String profileStorePath) {
        this.registry         = new RoomRegistry();
        this.broker           = new MessageBroker(registry);
        this.presenceTracker  = new PresenceTracker(30_000, this::disconnect);
        this.signalingService = new SignalingService(
                uid -> Optional.ofNullable(sessions.get(uid))); // session resolver lambda
        this.profileStore     = new UserProfileStore(profileStorePath);
        this.presenceTracker.start();
    }

    // ── Connection ──────────────────────────────────────────────────────────

    public UserSession connect(User user) {
        return connect(user, null);
    }

    /**
     * Connect a user, optionally setting an avatar.
     * @param avatarBase64 full data-URI or null to skip / keep existing
     */
    public UserSession connect(User user, String avatarBase64) {
        UserSession session = new UserSession(user, INBOX_CAPACITY);
        sessions.put(user.userId(), session);
        presenceTracker.heartbeat(user.userId());

        // Create or update profile
        UserProfile profile = profileStore.getProfile(user.userId())
                .orElseGet(() -> UserProfile.builder()
                        .userId(user.userId())
                        .username(user.username())
                        .displayName(user.username())
                        .build());
        if (avatarBase64 != null && !avatarBase64.isBlank()) {
            profile.setAvatarBase64(avatarBase64);
        }
        profileStore.saveProfile(profile);

        System.out.printf("✓ %s connected (profile saved)%n", user.username());
        return session;
    }

    public void disconnect(String userId) {
        UserSession session = sessions.remove(userId);
        if (session == null) return;
        session.deactivate();
        presenceTracker.remove(userId);
        registry.listRoomIds().forEach(roomId ->
                registry.find(roomId).ifPresent(room -> {
                    room.unsubscribe(session);
                    if (room.isEmpty()) registry.remove(roomId);
                }));
        System.out.printf("✗ %s disconnected%n", session.getUser().username());
    }

    // ── Rooms ────────────────────────────────────────────────────────────────

    public void joinRoom(String userId, String roomId) {
        UserSession session = requireSession(userId);
        registry.getOrCreate(roomId).subscribe(session);
        broker.route(Message.builder()
                .roomId(roomId).senderId(userId)
                .type(MessageType.JOIN)
                .content(session.getUser().username() + " joined")
                .build());
        System.out.printf("→ %s joined [%s]%n", session.getUser().username(), roomId);
    }

    public void leaveRoom(String userId, String roomId) {
        UserSession session = requireSession(userId);
        registry.find(roomId).ifPresent(room -> {
            room.unsubscribe(session);
            broker.route(Message.builder()
                    .roomId(roomId).senderId(userId)
                    .type(MessageType.LEAVE)
                    .content(session.getUser().username() + " left")
                    .build());
            if (room.isEmpty()) registry.remove(roomId);
        });
    }

    public void sendMessage(String userId, String roomId, String content) {
        requireSession(userId);
        presenceTracker.heartbeat(userId);
        broker.route(Message.builder()
                .roomId(roomId).senderId(userId)
                .content(content).type(MessageType.TEXT)
                .build());
    }

    // ── WebRTC signaling ─────────────────────────────────────────────────────

    /** Step 1 — caller sends offer to callee with their SDP. */
    public boolean initiateCall(String callerUserId, String calleeUserId, String sdpOffer) {
        System.out.printf("📹 %s → %s: OFFER%n", callerUserId, calleeUserId);
        return signalingService.sendOffer(callerUserId, calleeUserId, sdpOffer);
    }

    /** Step 2 — callee responds with their SDP answer. */
    public boolean answerCall(String calleeUserId, String callerUserId, String sdpAnswer) {
        System.out.printf("📹 %s → %s: ANSWER%n", calleeUserId, callerUserId);
        return signalingService.sendAnswer(calleeUserId, callerUserId, sdpAnswer);
    }

    /** Step 3 — either side sends ICE candidates until connectivity established. */
    public boolean sendIceCandidate(String fromUserId, String toUserId, String candidateJson) {
        return signalingService.sendIceCandidate(fromUserId, toUserId, candidateJson);
    }

    /** Either side can hang up. */
    public boolean hangup(String fromUserId, String toUserId) {
        System.out.printf("📴 %s hung up on %s%n", fromUserId, toUserId);
        return signalingService.hangup(fromUserId, toUserId);
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    public Optional<UserProfile> getProfile(String userId) {
        return profileStore.getProfile(userId);
    }

    public boolean updateAvatar(String userId, String base64Image) {
        return profileStore.updateAvatar(userId, base64Image);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void heartbeat(String userId) {
        UserSession s = sessions.get(userId);
        if (s != null) { s.updateHeartbeat(); presenceTracker.heartbeat(userId); }
    }

    public void shutdown() {
        presenceTracker.stop();
        registry.listRoomIds().forEach(registry::remove);
    }

    private UserSession requireSession(String userId) {
        UserSession s = sessions.get(userId);
        if (s == null) throw new IllegalStateException("No session: " + userId);
        return s;
    }
}