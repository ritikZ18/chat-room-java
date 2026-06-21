package com.chatapp.model;

import java.time.Instant;
import java.util.UUID;

public final class Message {
    private final String      messageId;
    private final String      roomId;
    private final String      senderId;
    private final String      toUserId;   // non-null for direct/signaling messages
    private final String      content;
    private final MessageType type;
    private final Instant     timestamp;

    private Message(Builder b) {
        this.messageId = b.messageId;
        this.roomId    = b.roomId;
        this.senderId  = b.senderId;
        this.toUserId  = b.toUserId;
        this.content   = b.content;
        this.type      = b.type;
        this.timestamp = b.timestamp;
    }

    public String      getMessageId() { return messageId; }
    public String      getRoomId()    { return roomId; }
    public String      getSenderId()  { return senderId; }
    public String      getToUserId()  { return toUserId; }
    public String      getContent()   { return content; }
    public MessageType getType()      { return type; }
    public Instant     getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("[%s] %s → %s: %s", type, senderId,
            toUserId != null ? toUserId : roomId, content);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String      messageId = UUID.randomUUID().toString();
        private String      roomId    = "__direct__";
        private String      senderId;
        private String      toUserId;
        private String      content   = "";
        private MessageType type      = MessageType.TEXT;
        private Instant     timestamp = Instant.now();

        public Builder roomId(String v)    { this.roomId = v;    return this; }
        public Builder senderId(String v)  { this.senderId = v;  return this; }
        public Builder toUserId(String v)  { this.toUserId = v;  return this; }
        public Builder content(String v)   { this.content = v;   return this; }
        public Builder type(MessageType v) { this.type = v;      return this; }
        public Message build()             { return new Message(this); }
    }
}