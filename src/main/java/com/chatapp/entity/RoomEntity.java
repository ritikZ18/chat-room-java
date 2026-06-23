package com.chatapp.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rooms")
public class RoomEntity {

    @Id @Column(name = "room_id", length = 128) private String roomId;
    @Column(nullable = false, length = 128)     private String name;
    @Column(length = 16)                        private String type = "text";
    @Column(name = "created_at")                private Instant createdAt;

    public RoomEntity() {}
    public RoomEntity(String roomId, String name, String type) {
        this.roomId = roomId; this.name = name;
        this.type = type;     this.createdAt = Instant.now();
    }

    public String  getRoomId()          { return roomId; }
    public void    setRoomId(String v)  { this.roomId = v; }
    public String  getName()            { return name; }
    public void    setName(String v)    { this.name = v; }
    public String  getType()            { return type; }
    public void    setType(String v)    { this.type = v; }
    public Instant getCreatedAt()       { return createdAt; }
    public void    setCreatedAt(Instant v){ this.createdAt = v; }
}