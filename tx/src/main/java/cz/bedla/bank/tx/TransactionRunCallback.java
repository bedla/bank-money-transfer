package cz.bedla.bank.tx;

@FunctionalInterface
public interface TransactionRunCallback {
    void doInTransaction();
}
