package cz.bedla.revolut;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

public final class DbInitializer {
    private final String sqlScript;
    private final DataSource dataSource;

    public DbInitializer(String sqlScript, DataSource dataSource) {
        this.sqlScript = sqlScript;
        this.dataSource = dataSource;
    }

    public void run() {
        try (Connection connection = dataSource.getConnection()) {
            runWithConnection(connection);
        } catch (SQLException e) {
            ExceptionUtils.rethrow(e);
        }
    }

    private void runWithConnection(Connection connection) throws SQLException {
        final List<String> sqls = sqlStatements();
        try (final Statement statement = connection.createStatement()) {
            for (String sql : sqls) {
                statement.execute(sql);
            }
        }
    }

    private List<String> sqlStatements() {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(sqlScript)) {
            final String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            return splitStatements(content);
        } catch (IOException e) {
            return ExceptionUtils.rethrow(e);
        }
    }

    private List<String> splitStatements(String content) {
        return Arrays.asList(StringUtils.split(content, ";"));
    }
}
