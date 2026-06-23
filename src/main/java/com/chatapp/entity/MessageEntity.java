package com.chatapp.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_msg_room_sent", columnList = "room_id, sent_at"),
    @Index(name = "idx_msg_sender",    columnList = "sender_id")
})
public class MessageEntity {

    @Id @Column(name = "message_id", length = 36)
    private String messageId;

    @Column(name = "room_id", nullable = false, length = 128)
    private String roomId;

    @Column(name = "sender_id",   length = 36)  private String senderId;
    @Column(name = "sender_name", length = 128) private String senderName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 32) private String type = "TEXT";
    @Column(name = "sent_at", nullable = false) private Instant sentAt;

    public MessageEntity() {}
    public MessageEntity(String messageId, String roomId, String senderId,
                         String senderName, String content, String type) {
        this.messageId = messageId; this.roomId    = roomId;
        this.senderId  = senderId;  this.senderName = senderName;
        this.content   = content;   this.type      = type;
        this.sentAt    = Instant.now();
    }

    public String  getMessageId()         { return messageId; }
    public void    setMessageId(String v) { this.messageId = v; }
    public String  getRoomId()            { return roomId; }
    public void    setRoomId(String v)    { this.roomId = v; }
    public String  getSenderId()          { return senderId; }
    public void    setSenderId(String v)  { this.senderId = v; }
    public String  getSenderName()        { return senderName; }
    public void    setSenderName(String v){ this.senderName = v; }
    public String  getContent()           { return content; }
    public void    setContent(String v)   { this.content = v; }
    public String  getType()              { return type; }
    public void    setType(String v)      { this.type = v; }
    public Instant getSentAt()            { return sentAt; }
    public void    setSentAt(Instant v)   { this.sentAt = v; }
}