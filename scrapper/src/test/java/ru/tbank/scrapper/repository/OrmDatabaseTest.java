package ru.tbank.scrapper.repository;

import java.util.List;
import org.springframework.test.context.TestPropertySource;
import ru.tbank.scrapper.repository.jpa.JpaChatRepository;
import ru.tbank.scrapper.repository.jpa.JpaLinkRepository;
import ru.tbank.scrapper.repository.jpa.JpaTagRepository;

@TestPropertySource(properties = {"app.database.access-type=ORM", "app.scheduler.batch-size=50"})
class OrmDatabaseTest extends AbstractDatabaseTest {

    @Override
    protected List<Class<?>> expectedImplementations() {
        return List.of(JpaChatRepository.class, JpaLinkRepository.class, JpaTagRepository.class);
    }
}
