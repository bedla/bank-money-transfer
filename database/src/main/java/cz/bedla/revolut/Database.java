package cz.bedla.revolut;

import javax.sql.DataSource;

public interface Database {
    DataSource getDataSource();

    void start();

    void stop();
}
