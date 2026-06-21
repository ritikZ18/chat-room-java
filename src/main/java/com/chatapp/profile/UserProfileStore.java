package com.chatapp.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Persists user profiles to a JSON file on disk.
 * File format: { "userId": { ...UserProfile fields... }, ... }
 *
 * Avatar is stored as a full data-URI Base64 string inside the JSON.
 * Large avatars will inflate file size — consider a 64 KB cap per avatar.
 *
 * Thread-safe via ReadWriteLock: many concurrent readers, one writer at a time.
 */
public class UserProfileStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final File                    storeFile;
    private final ReadWriteLock           lock  = new ReentrantReadWriteLock();
    private       Map<String, UserProfile> cache = new HashMap<>();

    public UserProfileStore(String filePath) {
        this.storeFile = new File(filePath);
        init();
    }

    private void init() {
        if (storeFile.getParentFile() != null) storeFile.getParentFile().mkdirs();
        if (storeFile.exists()) {
            try {
                cache = MAPPER.readValue(storeFile,
                        new TypeReference<Map<String, UserProfile>>() {});
                System.out.printf("✓ loaded %d profile(s) from %s%n",
                        cache.size(), storeFile.getPath());
            } catch (IOException e) {
                System.err.println("⚠ Could not read profiles, starting fresh: " + e.getMessage());
            }
        } else {
            persist(); // create empty file
        }
    }

    /** Saves or overwrites the profile for profile.getUserId(). */
    public void saveProfile(UserProfile profile) {
        lock.writeLock().lock();
        try   { cache.put(profile.getUserId(), profile); persist(); }
        finally { lock.writeLock().unlock(); }
    }

    public Optional<UserProfile> getProfile(String userId) {
        lock.readLock().lock();
        try   { return Optional.ofNullable(cache.get(userId)); }
        finally { lock.readLock().unlock(); }
    }

    /**
     * Replaces the avatar for an existing profile.
     * @param base64Image full data-URI: "data:image/png;base64,iVBORw0..."
     */
    public boolean updateAvatar(String userId, String base64Image) {
        lock.writeLock().lock();
        try {
            UserProfile profile = cache.get(userId);
            if (profile == null) return false;
            profile.setAvatarBase64(base64Image);
            persist();
            return true;
        } finally { lock.writeLock().unlock(); }
    }

    public boolean deleteProfile(String userId) {
        lock.writeLock().lock();
        try   { boolean removed = cache.remove(userId) != null; if (removed) persist(); return removed; }
        finally { lock.writeLock().unlock(); }
    }

    public int size() {
        lock.readLock().lock();
        try { return cache.size(); } finally { lock.readLock().unlock(); }
    }

    private void persist() {
        try { MAPPER.writerWithDefaultPrettyPrinter().writeValue(storeFile, cache); }
        catch (IOException e) { System.err.println("⚠ persist failed: " + e.getMessage()); }
    }
}