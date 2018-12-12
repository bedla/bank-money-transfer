package cz.bedla.revolut.service.impl

import cz.bedla.revolut.service.AccountDao
import cz.bedla.revolut.service.TransactionDao
import cz.bedla.revolut.service.createDsl
import cz.bedla.revolut.domain.Account
import cz.bedla.revolut.domain.Transaction
import cz.bedla.revolut.jooq.Tables.TRANSACTION
import cz.bedla.revolut.jooq.tables.records.TransactionRecord
import org.jooq.impl.DSL.sum
import java.math.BigDecimal

class TransactionDaoIml(private val accountDao: AccountDao) : TransactionDao {
    override fun create(item: Transaction): Transaction {
        val dsl = createDsl()

        val transactionRecord = dsl.newRecord(TRANSACTION)
        transactionRecord.id = null
        transactionRecord.fromAccId = item.fromAccount.id
        transactionRecord.toAccId = item.toAccount.id
        transactionRecord.amount = item.amount
        transactionRecord.dateTransacted = item.dateTransacted

        transactionRecord.store()

        return item.copy(id = transactionRecord.id)
    }

    override fun calculateBalance(account: Account): BigDecimal {
        val dsl = createDsl()

        // TODO better single-run with JOOQ DSL
        val debit = dsl.select(sum(TRANSACTION.AMOUNT))
                .from(TRANSACTION)
                .where(TRANSACTION.FROM_ACC_ID.eq(account.id))
                .fetchOneInto(BigDecimal::class.java).orZero()
        val credit = dsl.select(sum(TRANSACTION.AMOUNT))
                .from(TRANSACTION)
                .where(TRANSACTION.TO_ACC_ID.eq(account.id))
                .fetchOneInto(BigDecimal::class.java).orZero()

        return credit - debit
    }

    override fun findAccountTransactions(account: Account): List<Transaction> {
        val dsl = createDsl()

        return dsl.selectFrom(TRANSACTION)
                .where(TRANSACTION.FROM_ACC_ID.eq(account.id))
                .or(TRANSACTION.TO_ACC_ID.eq(account.id))
                .orderBy(TRANSACTION.DATE_TRANSACTED)
                .fetch()
                .map { it.toTransaction(accountDao) }
    }

    // TODO solve N+1 problem
    private fun TransactionRecord.toTransaction(accountDao: AccountDao): Transaction {
        val id = getValue(TRANSACTION.ID)!!
        val fromAccountId: Int = getValue(TRANSACTION.FROM_ACC_ID)
        val toAccountId: Int = getValue(TRANSACTION.TO_ACC_ID)
        val fromAccount = accountDao.findAccount(fromAccountId)
                ?: throw IllegalStateException("Unable to find fromAccount.id=$fromAccountId for transaction.id=$id")
        val toAccount = accountDao.findAccount(toAccountId)
                ?: throw IllegalStateException("Unable to find toAccount.id=$toAccountId for transaction.id=$id")

        return Transaction(
                fromAccount,
                toAccount,
                getValue(TRANSACTION.AMOUNT),
                getValue(TRANSACTION.DATE_TRANSACTED),
                id)
    }

    private fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO
}
