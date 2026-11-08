package ru.tbank.scrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class MigrationTest {

    private static final PostgreSQLContainer CLEAN_POSTGRES = new PostgreSQLContainer("postgres:17");

    @BeforeAll
    static void start() {
        CLEAN_POSTGRES.start();
    }

    @AfterAll
    static void stop() {
        CLEAN_POSTGRES.stop();
    }

    @Test
    void migrationsShouldCreateAllTablesOnCleanDatabase() throws Exception {
        MigrationRunner.runMigrations(
            CLEAN_POSTGRES.getJdbcUrl(), CLEAN_POSTGRES.getUsername(), CLEAN_POSTGRES.getPassword());

        assertThat(query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"))
            .contains("chats", "links", "subscriptions", "tags", "subscription_tags", "subscription_filters",
                "outbox", "databasechangelog");
        assertThat(query("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'"))
            .contains("idx_links_last_checked_at", "idx_subscriptions_link_id", "idx_subscription_tags_tag_id",
                "idx_outbox_new");
        assertThat(query("SELECT id FROM databasechangelog")).hasSize(6);

        assertThatCode(() -> MigrationRunner.runMigrations(
            CLEAN_POSTGRES.getJdbcUrl(), CLEAN_POSTGRES.getUsername(), CLEAN_POSTGRES.getPassword()))
            .doesNotThrowAnyException();
        assertThat(query("SELECT id FROM databasechangelog")).hasSize(6);
    }

    private List<String> query(String sql) throws Exception {
        List<String> result = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(
                CLEAN_POSTGRES.getJdbcUrl(), CLEAN_POSTGRES.getUsername(), CLEAN_POSTGRES.getPassword());
             ResultSet rs = connection.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }
}
