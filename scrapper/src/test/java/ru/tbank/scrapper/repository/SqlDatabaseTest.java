package ru.tbank.scrapper.repository;

import java.util.List;
import org.springframework.test.context.TestPropertySource;
import ru.tbank.scrapper.repository.jdbc.JdbcChatRepository;
import ru.tbank.scrapper.repository.jdbc.JdbcLinkRepository;
import ru.tbank.scrapper.repository.jdbc.JdbcTagRepository;

@TestPropertySource(properties = {"app.database.access-type=SQL", "app.scheduler.batch-size=50"})
class SqlDatabaseTest extends AbstractDatabaseTest {

    @Override
    protected List<Class<?>> expectedImplementations() {
        return List.of(JdbcChatRepository.class, JdbcLinkRepository.class, JdbcTagRepository.class);
    }
}
