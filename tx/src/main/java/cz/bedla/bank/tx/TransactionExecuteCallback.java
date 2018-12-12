package cz.bedla.bank.tx;

@FunctionalInterface
public interface TransactionExecuteCallback<T> {
    T doInTransaction();
}
