package ru.tbank.scrapper.repository.jpa.springdata;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.tbank.scrapper.repository.jpa.entity.LinkEntity;

public interface SpringDataLinkRepository extends JpaRepository<LinkEntity, Long> {

    Optional<LinkEntity> findByUrl(String url);

    @Query("""
        SELECT l FROM LinkEntity l
        WHERE (l.lastCheckedAt IS NULL OR l.lastCheckedAt < :checkedBefore)
          AND l.id > :afterId
          AND EXISTS (SELECT s.id FROM SubscriptionEntity s WHERE s.link = l)
        ORDER BY l.id
        """)
    List<LinkEntity> findLinksToCheck(
        @Param("checkedBefore") Instant checkedBefore,
        @Param("afterId") long afterId,
        Pageable pageable
    );
}
