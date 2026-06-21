package com.chatapp.profile;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Rich user profile, persisted to profiles/users.json as Base64-encoded avatar.
 * Kept separate from the lightweight User record used in sessions.
 */
public class UserProfile {
    @JsonProperty("userId")       private String userId;
    @JsonProperty("username")     private String username;
    @JsonProperty("displayName")  private String displayName;
    @JsonProperty("bio")          private String bio;
    /**
     * Full data-URI string, e.g. "data:image/png;base64,iVBORw0KGgo..."
     * Store and transmit exactly as-is; the browser/client renders it directly.
     */
    @JsonProperty("avatarBase64") private String avatarBase64;
    @JsonProperty("createdAt")    private String createdAt;   // ISO-8601

    public UserProfile() {}

    private UserProfile(Builder b) {
        this.userId       = b.userId;
        this.username     = b.username;
        this.displayName  = b.displayName;
        this.bio          = b.bio;
        this.avatarBase64 = b.avatarBase64;
        this.createdAt    = b.createdAt;
    }

    public String getUserId()      { return userId; }
    public String getUsername()    { return username; }
    public String getDisplayName() { return displayName; }
    public String getBio()         { return bio; }
    public String getAvatarBase64(){ return avatarBase64; }
    public String getCreatedAt()   { return createdAt; }

    public void setAvatarBase64(String avatarBase64) { this.avatarBase64 = avatarBase64; }
    public void setBio(String bio)                   { this.bio = bio; }
    public void setDisplayName(String displayName)   { this.displayName = displayName; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String userId;
        private String username;
        private String displayName  = "";
        private String bio          = "";
        private String avatarBase64 = "";
        private String createdAt    = Instant.now().toString();

        public Builder userId(String v)       { this.userId = v;       return this; }
        public Builder username(String v)     { this.username = v;     return this; }
        public Builder displayName(String v)  { this.displayName = v;  return this; }
        public Builder bio(String v)          { this.bio = v;          return this; }
        public Builder avatarBase64(String v) { this.avatarBase64 = v; return this; }
        public UserProfile build()            { return new UserProfile(this); }
    }
}