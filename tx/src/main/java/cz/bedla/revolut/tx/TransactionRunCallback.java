package cz.bedla.revolut.tx;

@FunctionalInterface
public interface TransactionRunCallback {
    void doInTransaction();
}
