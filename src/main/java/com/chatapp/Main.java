package com.chatapp;

import com.chatapp.model.Message;
import com.chatapp.model.MessageType;
import com.chatapp.model.User;
import com.chatapp.model.UserSession;
import com.chatapp.profile.UserProfile;
import com.chatapp.service.ChatService;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {
    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(Main.class, args);
        ChatService chat = new ChatService("data/profiles/users.json");

        // ── Tiny fake PNG as Base64 (1×1 red pixel) for demo purposes ──────
        // In real use: read an image file and encode it yourself:
        //   byte[] bytes = Files.readAllBytes(Path.of("avatar.png"));
        //   String b64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        String fakeAvatar = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(new byte[]{
                (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A}); // PNG header only, for demo

        // ── Connect users ────────────────────────────────────────────────────
        User alice = new User("u1", "Alice");
        User bob   = new User("u2", "Bob");
        User carol = new User("u3", "Carol");

        UserSession aliceSession = chat.connect(alice, fakeAvatar);
        UserSession bobSession   = chat.connect(bob,   fakeAvatar);
                                   chat.connect(carol, null);         // no avatar

        // ── Rooms ─────────────────────────────────────────────────────────────
        chat.joinRoom("u1", "general");
        chat.joinRoom("u2", "general");
        chat.joinRoom("u3", "general");
        chat.joinRoom("u1", "java-dev");
        chat.joinRoom("u2", "java-dev");

        chat.sendMessage("u1", "general",  "Hey everyone!");
        chat.sendMessage("u2", "general",  "Hi Alice!");
        chat.sendMessage("u1", "java-dev", "CopyOnWriteArrayList for subscribers 💡");
        chat.sendMessage("u2", "java-dev", "Yep — lock-free reads, perfect for fan-out");

        TimeUnit.MILLISECONDS.sleep(200); // let dispatchers drain

        // ── Drain Alice's inbox ───────────────────────────────────────────────
        System.out.println("\n=== Alice's inbox ===");
        Message m;
        while ((m = aliceSession.poll(100, TimeUnit.MILLISECONDS)) != null) {
            System.out.println("  " + m);
        }

        // ── WebRTC call: Alice → Bob ──────────────────────────────────────────
        System.out.println("\n=== WebRTC signaling ===");
        String fakeSdpOffer  = "v=0\r\no=alice 123 1 IN IP4 127.0.0.1\r\n...";
        String fakeSdpAnswer = "v=0\r\no=bob 456 1 IN IP4 127.0.0.1\r\n...";
        String fakeIce       = "{\"candidate\":\"candidate:1 1 UDP 123 192.168.1.1 5000\","
                             + "\"sdpMLineIndex\":0,\"sdpMid\":\"0\"}";

        chat.initiateCall("u1", "u2", fakeSdpOffer);   // Alice → Bob: OFFER
        chat.answerCall("u2", "u1", fakeSdpAnswer);    // Bob → Alice: ANSWER
        chat.sendIceCandidate("u1", "u2", fakeIce);    // Alice → Bob: ICE
        chat.sendIceCandidate("u2", "u1", fakeIce);    // Bob → Alice: ICE

        TimeUnit.MILLISECONDS.sleep(100);

        // Drain Bob's inbox to see signaling messages land
        System.out.println("\n=== Bob's inbox (includes WebRTC signals) ===");
        while ((m = bobSession.poll(100, TimeUnit.MILLISECONDS)) != null) {
            if (m.getType() == MessageType.WEBRTC_OFFER
             || m.getType() == MessageType.WEBRTC_ICE_CANDIDATE) {
                System.out.printf("  [%s] from=%s payload=%s...%n",
                        m.getType(), m.getSenderId(),
                        m.getContent().substring(0, Math.min(60, m.getContent().length())));
            } else {
                System.out.println("  " + m);
            }
        }

        // ── Profile ───────────────────────────────────────────────────────────
        System.out.println("\n=== Profiles ===");
        chat.getProfile("u1").ifPresent(p ->
                System.out.printf("  %s | displayName=%s | hasAvatar=%s%n",
                        p.getUsername(), p.getDisplayName(),
                        p.getAvatarBase64() != null && !p.getAvatarBase64().isBlank()));

        // Update Carol's avatar after the fact
        chat.updateAvatar("u3", fakeAvatar);
        chat.getProfile("u3").ifPresent(p ->
                System.out.printf("  %s avatar updated: %s%n",
                        p.getUsername(),
                        p.getAvatarBase64().substring(0, 30) + "..."));

        // ── Teardown ──────────────────────────────────────────────────────────
        chat.hangup("u1", "u2");
        chat.disconnect("u2");
        chat.shutdown();
        System.out.println("\nShut down cleanly.");
        System.out.println("Profile data persisted → profiles/users.json");
    }
}