package com.chatapp.model;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class UserSession {
    private final User user;
    private final LinkedBlockingQueue<Message> inbox;
    private volatile long  lastHeartbeat;
    private final AtomicBoolean active = new AtomicBoolean(true);

    public UserSession(User user, int inboxCapacity) {
        this.user          = user;
        this.inbox         = new LinkedBlockingQueue<>(inboxCapacity);
        this.lastHeartbeat = System.currentTimeMillis();
    }

    /** Non-blocking. Returns false if inbox full — never stalls the room dispatcher. */
    public boolean enqueue(Message msg) { return inbox.offer(msg); }

    public Message poll(long timeout, TimeUnit unit) throws InterruptedException {
        return inbox.poll(timeout, unit);
    }

    public void    updateHeartbeat()   { this.lastHeartbeat = System.currentTimeMillis(); }
    public long    getLastHeartbeat()  { return lastHeartbeat; }
    public User    getUser()           { return user; }
    public boolean isActive()          { return active.get(); }
    public void    deactivate()        { active.set(false); }

    @Override
    public String toString() { return "Session[" + user.username() + "]"; }
}