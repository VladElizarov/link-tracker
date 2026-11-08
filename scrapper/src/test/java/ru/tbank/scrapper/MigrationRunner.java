package ru.tbank.scrapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;

public final class MigrationRunner {

    private MigrationRunner() {
    }

    public static void runMigrations(String jdbcUrl, String username, String password) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase("master.xml", new DirectoryResourceAccessor(findMigrationsDir()), database);
            liquibase.update(new Contexts(), new LabelExpression());
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось применить миграции", e);
        }
    }

    private static Path findMigrationsDir() {
        Path fromModule = Path.of("..", "migrations");
        if (Files.isDirectory(fromModule)) {
            return fromModule.toAbsolutePath().normalize();
        }
        return Path.of("migrations").toAbsolutePath().normalize();
    }
}
