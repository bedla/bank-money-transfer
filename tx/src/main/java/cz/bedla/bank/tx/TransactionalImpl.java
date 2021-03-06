package cz.bedla.bank.tx;

import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.apache.commons.lang3.Validate.notNull;

/**
 * Inspired by Spring TransactionTemplate
 */
public final class TransactionalImpl implements Transactional {
    private final DataSource dataSource;

    public TransactionalImpl(DataSource dataSource) {
        this.dataSource = notNull(dataSource, "dataSource cannot be null");
    }

    @Override
    public void run(TransactionRunCallback action) {
        execute(() -> {
            action.doInTransaction();
            return null;
        });
    }

    @Override
    public <T> T execute(TransactionExecuteCallback<T> action) {
        if (ConnectionHolder.getConnection() == null) {
            return doInNewTransaction(action);
        } else {
            return action.doInTransaction();
        }
    }

    private <T> T doInNewTransaction(TransactionExecuteCallback<T> action) {
        try (final Connection connection = obtainConnection()) {
            try {
                ConnectionHolder.setConnection(connection);

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
                ConnectionHolder.remove();
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

    private void doCommit() {
        try {
            Transactional.currentConnection().commit();
        } catch (SQLException e) {
            ExceptionUtils.rethrow(e);
        }
    }

    private void rollbackOnException(Throwable ex) {
        try {
            Transactional.currentConnection().rollback();
        } catch (Exception e) {
            e.addSuppressed(ex);
            ExceptionUtils.rethrow(e);
        }
    }
}
