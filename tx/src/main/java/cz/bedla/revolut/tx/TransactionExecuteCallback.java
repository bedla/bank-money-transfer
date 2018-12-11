package cz.bedla.revolut.tx;

@FunctionalInterface
public interface TransactionExecuteCallback<T> {
    T doInTransaction();
}
