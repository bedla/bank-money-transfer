package cz.bedla.revolut.tx;

import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.apache.commons.lang3.Validate.notNull;
import static org.apache.commons.lang3.Validate.validState;

/**
 * Inspired by Spring TransactionTemplate
 */
public final class Transactional {
    private static final ThreadLocal<Connection> connectionThreadLocal = new ThreadLocal<>();

    private final DataSource dataSource;

    public Transactional(DataSource dataSource) {
        this.dataSource = notNull(dataSource, "dataSource cannot be null");
    }

    public void run(TransactionRunCallback action) {
        execute(() -> {
            action.doInTransaction();
            return null;
        });
    }

    public <T> T execute(TransactionExecuteCallback<T> action) {
        validState(currentConnection() == null, "Unable to nest transactions");

        try (final Connection connection = obtainConnection()) {
            try {
                connectionThreadLocal.set(connection);
                disableAutoCommit();

                T result;
                try {
                    result = action.doInTransaction();
                } catch (RuntimeException | Error e) {
                    rollbackOnException(e);
                    throw e;
                } catch (Throwable e) {
                    rollbackOnException(e);
                    return ExceptionUtils.rethrow(e);
                }

                doCommit();
                return result;
            } finally {
                connectionThreadLocal.remove();
            }
        } catch (SQLException e) {
            return ExceptionUtils.rethrow(e);
        }
    }

    private Connection obtainConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            return ExceptionUtils.rethrow(e);
        }
    }

    private void disableAutoCommit() {
        try {
            currentConnection().setAutoCommit(false);
        } catch (SQLException e) {
            ExceptionUtils.rethrow(e);
        }
    }

    private void doCommit() {
        try {
            currentConnection().commit();
        } catch (SQLException e) {
            ExceptionUtils.rethrow(e);
        }
    }

    private void rollbackOnException(Throwable ex) {
        try {
            currentConnection().rollback();
        } catch (Exception e) {
            e.addSuppressed(ex);
            ExceptionUtils.rethrow(e);
        }
    }

    public static Connection currentConnection() {
        return connectionThreadLocal.get();
    }
}
