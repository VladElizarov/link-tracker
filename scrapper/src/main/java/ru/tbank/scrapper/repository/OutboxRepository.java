package ru.tbank.scrapper.repository;

import java.util.List;
import ru.tbank.common.dto.LinkUpdate;
import ru.tbank.scrapper.model.OutboxMessage;

public interface OutboxRepository {

    void save(LinkUpdate update);

    List<OutboxMessage> findNew(int limit);

    void markSent(long id);
}
