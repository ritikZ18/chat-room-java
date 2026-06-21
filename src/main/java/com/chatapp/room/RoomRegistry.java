package com.chatapp.room;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RoomRegistry {
    private final ConcurrentHashMap<String, ChatRoom> rooms = new ConcurrentHashMap<>();

    // computeIfAbsent is atomic — no duplicate ChatRoom even under concurrent bursts
    public ChatRoom getOrCreate(String roomId) {
        return rooms.computeIfAbsent(roomId, ChatRoom::new);
    }

    public Optional<ChatRoom> find(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    public boolean remove(String roomId) {
        ChatRoom room = rooms.remove(roomId);
        if (room != null) { room.shutdown(); return true; }
        return false;
    }

    public List<String> listRoomIds() {
        return new ArrayList<>(rooms.keySet()); // snapshot — safe to iterate while map mutates
    }
}
