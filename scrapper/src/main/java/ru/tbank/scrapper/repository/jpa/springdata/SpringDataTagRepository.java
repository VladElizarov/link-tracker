package ru.tbank.scrapper.repository.jpa.springdata;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.tbank.scrapper.repository.jpa.entity.TagEntity;

public interface SpringDataTagRepository extends JpaRepository<TagEntity, Long> {

    List<TagEntity> findByChatIdOrderByNameAsc(Long chatId);

    Optional<TagEntity> findByChatIdAndName(Long chatId, String name);

    boolean existsByChatIdAndName(Long chatId, String name);
}
