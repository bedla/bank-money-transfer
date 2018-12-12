package cz.bedla.revolut;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TempDirectory.class)
class DatabaseTest {
    private DatabaseImpl fixture;

    @BeforeEach
    void setUp(@TempDirectory.TempDir Path tempDir) {
        fixture = new DatabaseImpl(tempDir.toFile());
    }

    @Test
    void startAndInitDatabase() {
        fixture.start();

        final DbInitializer initializer = new DbInitializer("database.sql", fixture.getDataSource());
        initializer.run();

        testConnection(fixture.getDataSource());
    }

    private void testConnection(DataSource dataSource) {
        try (Connection connection = fixture.getDataSource().getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("SELECT COUNT(*) FROM waiting_room")) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getInt(1)).isEqualTo(0);
                }
            }
        } catch (SQLException e) {
            ExceptionUtils.rethrow(e);
        }
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }
}
