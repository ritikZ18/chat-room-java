package com.chatapp.repository;

import com.chatapp.entity.MessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, String> {

    @Query("SELECT m FROM MessageEntity m WHERE m.roomId = :roomId ORDER BY m.sentAt ASC")
    List<MessageEntity> findHistoryByRoom(@Param("roomId") String roomId, Pageable pageable);
}