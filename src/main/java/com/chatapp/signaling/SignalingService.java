package com.chatapp.signaling;

import com.chatapp.model.Message;
import com.chatapp.model.MessageType;
import com.chatapp.model.UserSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.function.Function;

/**
 * Handles WebRTC signaling relay.
 *
 * This class implements the server-side signaling logic only — it routes
 * SDP offers/answers and ICE candidates between peers. The actual video
 * streams are peer-to-peer (WebRTC) and never touch this server.
 *
 * In a real deployment you would wire inbound signals from a WebSocket
 * handler into these methods. The routing logic here stays the same.
 */
public class SignalingService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Injected lookup — avoids tight coupling to ChatService's session map
    private final Function<String, Optional<UserSession>> sessionResolver;

    public SignalingService(Function<String, Optional<UserSession>> sessionResolver) {
        this.sessionResolver = sessionResolver;
    }

    /** Caller initiates — sends SDP offer to callee. */
    public boolean sendOffer(String fromUserId, String toUserId, String sdpOffer) {
        return signal(fromUserId, toUserId, SignalingType.OFFER,
                      MessageType.WEBRTC_OFFER, sdpOffer);
    }

    /** Callee accepts — sends SDP answer back to caller. */
    public boolean sendAnswer(String fromUserId, String toUserId, String sdpAnswer) {
        return signal(fromUserId, toUserId, SignalingType.ANSWER,
                      MessageType.WEBRTC_ANSWER, sdpAnswer);
    }

    /** Either side sends an ICE candidate. candidateJson format: {"candidate":"...","sdpMLineIndex":0} */
    public boolean sendIceCandidate(String fromUserId, String toUserId, String candidateJson) {
        return signal(fromUserId, toUserId, SignalingType.ICE_CANDIDATE,
                      MessageType.WEBRTC_ICE_CANDIDATE, candidateJson);
    }

    /** Hang up — ends the peer connection on the remote side. */
    public boolean hangup(String fromUserId, String toUserId) {
        return signal(fromUserId, toUserId, SignalingType.HANGUP,
                      MessageType.WEBRTC_HANGUP, "");
    }

    private boolean signal(String from, String to, SignalingType sigType,
                           MessageType msgType, String payload) {
        String content = serialise(new SignalingPayload(sigType.name(), from, to, payload));
        Message msg = Message.builder()
                .senderId(from).toUserId(to)
                .type(msgType).content(content)
                .build();

        return sessionResolver.apply(to)
                .filter(UserSession::isActive)
                .map(s -> s.enqueue(msg))
                .orElseGet(() -> {
                    System.out.printf("  ⚠ signal target '%s' not found or offline%n", to);
                    return false;
                });
    }

    private String serialise(SignalingPayload p) {
        try { return MAPPER.writeValueAsString(p); }
        catch (JsonProcessingException e) { return "{}"; }
    }
}