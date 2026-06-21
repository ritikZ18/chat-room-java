package com.chatapp.room;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ChatRoom {
    private final String roomId;
    private final CopyOnWriteArrayList<UserSession> subscribers = new CopyOnWriteArrayList<>();
    // Single thread = messages arrive at subscribers in exact publish order, zero locks
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor();

    public ChatRoom(String roomId) {
        this.roomId = roomId;
    }

    public void publish(Message msg) {
        dispatcher.submit(() -> fanOut(msg)); // calling thread returns immediately
    }

    private void fanOut(Message msg) {
        // CopyOnWriteArrayList gives a snapshot — safe even if someone joins mid-loop
        for (UserSession session : subscribers) {
            if (session.isActive()) {
                boolean delivered = session.enqueue(msg); // offer(), non-blocking
                if (!delivered) {
                    System.out.printf("  ⚠ inbox full for %s — message dropped%n", session);
                }
            }
        }
    }

    public boolean subscribe(UserSession session) {
        return subscribers.addIfAbsent(session); // idempotent
    }

    public boolean unsubscribe(UserSession session) {
        return subscribers.remove(session);
    }

    public boolean isEmpty()        { return subscribers.isEmpty(); }
    public int     subscriberCount(){ return subscribers.size(); }
    public String  getRoomId()      { return roomId; }

    public void shutdown() {
        dispatcher.shutdown();
        try { dispatcher.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
