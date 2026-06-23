package com.chatapp.repository;

import com.chatapp.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

    @Modifying @Transactional
    @Query("UPDATE UserEntity u SET u.status = :status, u.lastSeen = :ts WHERE u.userId = :uid")
    void updateStatus(@Param("uid")    String userId,
                      @Param("status") String status,
                      @Param("ts")     Instant lastSeen);
}