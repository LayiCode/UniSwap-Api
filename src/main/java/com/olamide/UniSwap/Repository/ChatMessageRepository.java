package com.olamide.UniSwap.Repository;

import com.olamide.UniSwap.Entity.ChatMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @EntityGraph(attributePaths = {"sender", "receiver"})
    @Query("select m from ChatMessage m " +
            "where (m.sender.id = :uid and m.receiver.id = :other) " +
            "   or (m.sender.id = :other and m.receiver.id = :uid) " +
            "order by m.createdAt asc")
    List<ChatMessage> findThread(@Param("uid") Long userId, @Param("other") Long otherId);

    @EntityGraph(attributePaths = {"sender", "receiver"})
    @Query("select m from ChatMessage m where m.sender.id = :uid or m.receiver.id = :uid order by m.createdAt desc")
    List<ChatMessage> findInvolving(@Param("uid") Long userId);

    @Modifying
    @Query("update ChatMessage m set m.read = true " +
            "where m.read = false and m.sender.id = :other and m.receiver.id = :uid")
    int markThreadRead(@Param("uid") Long userId, @Param("other") Long otherId);

    long countByReceiverIdAndReadFalse(Long receiverId);
}
