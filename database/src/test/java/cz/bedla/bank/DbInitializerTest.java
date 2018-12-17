package cz.bedla.bank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TempDirectory.class)
class DbInitializerTest {
    private DbInitializer fixture;
    private Database database;

    @BeforeEach
    void setUp(@TempDirectory.TempDir Path tempDir) {
        database = new DatabaseImpl(tempDir.toFile());
        database.start();

        fixture = new DbInitializer("database.sql", database.getDataSource());
    }

    @Test
    void checkInitialized() {
        assertThat(fixture.checkDbInitialized()).isFalse();
        fixture.run();
        assertThat(fixture.checkDbInitialized()).isTrue();
    }

    @AfterEach
    void tearDown() {
        database.stop();
    }
}
