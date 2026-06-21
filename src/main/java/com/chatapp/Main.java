package com.chatapp;

import java.util.concurrent.TimeUnit;

import com.chatapp.model.Message;
import com.chatapp.model.User;
import com.chatapp.model.UserSession;
import com.chatapp.service.ChatService;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        ChatService chat = new ChatService();

        // --- connect users ---
        User alice = new User("u1", "Alice");
        User bob   = new User("u2", "Bob");
        User carol = new User("u3", "Carol");

        UserSession aliceSession = chat.connect(alice);
        UserSession bobSession   = chat.connect(bob);
                                   chat.connect(carol);

        // --- join rooms ---
        chat.joinRoom("u1", "general");
        chat.joinRoom("u2", "general");
        chat.joinRoom("u3", "general");
        chat.joinRoom("u1", "java-dev");
        chat.joinRoom("u2", "java-dev");

        // --- send messages ---
        chat.sendMessage("u1", "general",  "Hey everyone!");
        chat.sendMessage("u2", "general",  "Hi Alice!");
        chat.sendMessage("u3", "general",  "Hello!");
        chat.sendMessage("u1", "java-dev", "Anyone know CopyOnWriteArrayList well?");
        chat.sendMessage("u2", "java-dev", "Yeah — reads are lock-free, writes copy the array");

        // let the single-thread executor finish fan-out
        TimeUnit.MILLISECONDS.sleep(300);

        // --- drain Alice's inbox ---
        System.out.println("\n=== Alice's inbox ===");
        Message msg;
        while ((msg = aliceSession.poll(100, TimeUnit.MILLISECONDS)) != null) {
            System.out.println("  " + msg);
        }

        System.out.println("\n=== Bob's inbox ===");
        while ((msg = bobSession.poll(100, TimeUnit.MILLISECONDS)) != null) {
            System.out.println("  " + msg);
        }

        // --- lifecycle events ---
        chat.leaveRoom("u3", "general");
        chat.disconnect("u2");

        chat.shutdown();
        System.out.println("\nShut down cleanly.");
    }
}