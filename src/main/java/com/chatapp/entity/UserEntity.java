package com.chatapp.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users",
       indexes = @Index(name = "idx_users_status", columnList = "status"))
public class UserEntity {

    @Id @Column(name = "user_id", length = 36)
    private String userId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "avatar_base64", columnDefinition = "TEXT")
    private String avatarBase64;

    @Column(length = 16)
    private String status = "offline";

    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "last_seen")  private Instant lastSeen;

    public UserEntity() {}
    public UserEntity(String userId, String username) {
        this.userId = userId; this.username = username;
        this.createdAt = Instant.now(); this.lastSeen = Instant.now();
    }

    public String  getUserId()            { return userId; }
    public void    setUserId(String v)    { this.userId = v; }
    public String  getUsername()          { return username; }
    public void    setUsername(String v)  { this.username = v; }
    public String  getDisplayName()       { return displayName; }
    public void    setDisplayName(String v){ this.displayName = v; }
    public String  getBio()               { return bio; }
    public void    setBio(String v)       { this.bio = v; }
    public String  getAvatarBase64()      { return avatarBase64; }
    public void    setAvatarBase64(String v){ this.avatarBase64 = v; }
    public String  getStatus()            { return status; }
    public void    setStatus(String v)    { this.status = v; }
    public Instant getCreatedAt()         { return createdAt; }
    public void    setCreatedAt(Instant v){ this.createdAt = v; }
    public Instant getLastSeen()          { return lastSeen; }
    public void    setLastSeen(Instant v) { this.lastSeen = v; }
}