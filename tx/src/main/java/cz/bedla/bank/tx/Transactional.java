package cz.bedla.bank.tx;

import java.sql.Connection;

import static org.apache.commons.lang3.Validate.validState;

public interface Transactional {
    void run(TransactionRunCallback action);

    <T> T execute(TransactionExecuteCallback<T> action);

    static Connection currentConnection() {
        final Connection connection = ConnectionHolder.getConnection();
        validState(connection != null, "No transaction/connection bound to current thread");
        return connection;
    }

}
