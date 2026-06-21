package com.chatapp.room;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.chatapp.model.Message;
import com.chatapp.model.UserSession;

public class ChatRoom {
    private final String roomId;
    private final CopyOnWriteArrayList<UserSession> subscribers = new CopyOnWriteArrayList<>();
    // Single thread = fanOut() calls are serialised → messages always arrive in publish order
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor();

    public ChatRoom(String roomId) { this.roomId = roomId; }

    /** Calling thread returns immediately — delivery is async. */
    public void publish(Message msg) {
        dispatcher.submit(() -> fanOut(msg));
    }

    private void fanOut(Message msg) {
        for (UserSession s : subscribers) {   // CopyOnWriteArrayList snapshot — no lock
            if (s.isActive()) {
                boolean ok = s.enqueue(msg);  // offer(), non-blocking
                if (!ok) System.out.printf("  ⚠ inbox full for %s — dropped%n", s);
            }
        }
    }

    public boolean subscribe(UserSession s)   { return subscribers.addIfAbsent(s); }
    public boolean unsubscribe(UserSession s) { return subscribers.remove(s); }
    public boolean isEmpty()                  { return subscribers.isEmpty(); }
    public int     subscriberCount()          { return subscribers.size(); }
    public String  getRoomId()                { return roomId; }

    public void shutdown() {
        dispatcher.shutdown();
        try { dispatcher.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}