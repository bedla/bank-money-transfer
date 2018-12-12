package cz.bedla.revolut.tx;

import java.sql.Connection;

public final class ConnectionHolder {
    private static final ThreadLocal<Connection> connectionThreadLocal = new ThreadLocal<>();

    public static Connection getConnection() {
        return connectionThreadLocal.get();
    }

    public static void setConnection(Connection connection) {
        connectionThreadLocal.set(connection);
    }

    public static void remove() {
        connectionThreadLocal.remove();
    }
}
