package ru.tbank.scrapper.repository.jpa.springdata;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.tbank.scrapper.repository.jpa.entity.SubscriptionEntity;

public interface SpringDataSubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {

    List<SubscriptionEntity> findByChat_IdOrderByIdAsc(Long chatId);

    Optional<SubscriptionEntity> findByChat_IdAndLink_Url(Long chatId, String url);

    boolean existsByChat_IdAndLink_Url(Long chatId, String url);

    @Query("SELECT s.chat.id FROM SubscriptionEntity s WHERE s.link.id = :linkId ORDER BY s.chat.id")
    List<Long> findChatIdsByLinkId(@Param("linkId") Long linkId);
}
