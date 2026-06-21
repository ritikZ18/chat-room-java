package com.chatapp.messaging;

import com.chatapp.model.Message;
import com.chatapp.room.RoomRegistry;

public class MessageBroker {
    private final RoomRegistry registry;

    public MessageBroker(RoomRegistry registry) { this.registry = registry; }

    public void route(Message msg) {
        registry.find(msg.getRoomId()).ifPresentOrElse(
            room -> room.publish(msg),
            () -> System.out.printf("  ⚠ room '%s' not found%n", msg.getRoomId())
        );
    }
}