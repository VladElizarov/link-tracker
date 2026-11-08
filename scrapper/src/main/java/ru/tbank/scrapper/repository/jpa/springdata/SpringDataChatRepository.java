package ru.tbank.scrapper.repository.jpa.springdata;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.tbank.scrapper.repository.jpa.entity.ChatEntity;

public interface SpringDataChatRepository extends JpaRepository<ChatEntity, Long> {
}
