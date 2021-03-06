package cz.bedla.bank;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.commons.lang3.Validate.validState;

public final class DatabaseImpl implements AutoCloseable, Database {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseImpl.class);

    private final File file;
    private final AtomicReference<HikariDataSource> reference = new AtomicReference<>();

    public DatabaseImpl(File file) {
        this.file = file;
    }

    @Override
    public DataSource getDataSource() {
        final HikariDataSource dataSource = reference.get();
        validState(dataSource != null, "Database not running");
        return dataSource;
    }

    @Override
    public void start() {
        if (reference.compareAndSet(null, createDataSource())) {
            LOG.info("Database started");
        } else {
            throw new IllegalStateException("Database already started");
        }
    }

    @Override
    public void stop() {
        close();
    }

    private HikariDataSource createDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:file:" + dbPath() + ";DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setAutoCommit(false);
        dataSource.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        return dataSource;
    }

    private String dbPath() {
        try {
            return file.getCanonicalPath().replace('\\', '/');
        } catch (IOException e) {
            return ExceptionUtils.rethrow(e);
        }
    }

    @Override
    public void close() {
        final HikariDataSource dataSource = reference.getAndSet(null);
        validState(dataSource != null, "Database already stopped");
        dataSource.close();
    }
}
