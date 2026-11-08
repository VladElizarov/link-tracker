package ru.tbank.scrapper.controller;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"app.database.access-type=ORM", "app.scheduler.batch-size=50"})
class OrmScrapperApiTest extends AbstractScrapperApiTest {
}
