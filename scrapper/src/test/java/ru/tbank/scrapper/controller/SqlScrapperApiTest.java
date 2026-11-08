package ru.tbank.scrapper.controller;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"app.database.access-type=SQL", "app.scheduler.batch-size=50"})
class SqlScrapperApiTest extends AbstractScrapperApiTest {
}
