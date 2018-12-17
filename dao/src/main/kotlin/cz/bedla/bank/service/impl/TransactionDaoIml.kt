package cz.bedla.bank.service.impl

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.Transaction
import cz.bedla.bank.jooq.Tables.TRANSACTION
import cz.bedla.bank.jooq.tables.records.TransactionRecord
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.TransactionDao
import cz.bedla.bank.service.PaymentOrderDao
import cz.bedla.bank.service.createDsl
import org.jooq.impl.DSL.sum
import java.math.BigDecimal
import java.time.OffsetDateTime

class TransactionDaoIml(
    private val accountDao: AccountDao,
    private val paymentOrderDao: PaymentOrderDao
) : TransactionDao {
    override fun create(
        paymentOrderId: Int,
        fromAccountId: Int,
        toAccountId: Int,
        amount: BigDecimal,
        dateTransacted: OffsetDateTime
    ): Transaction {
        val dsl = createDsl()

        val transactionRecord = dsl.newRecord(TRANSACTION)
        transactionRecord.poId = paymentOrderId
        transactionRecord.fromAccId = fromAccountId
        transactionRecord.toAccId = toAccountId
        transactionRecord.amount = amount
        transactionRecord.dateTransacted = dateTransacted

        transactionRecord.store()

        return transactionRecord.toTransaction(accountDao, paymentOrderDao)
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
            .map { it.toTransaction(accountDao, paymentOrderDao) }
    }

    // TODO solve N+1 problem
    private fun TransactionRecord.toTransaction(accountDao: AccountDao, paymentOrderDao: PaymentOrderDao): Transaction {
        val fromAccountId: Int = getValue(TRANSACTION.FROM_ACC_ID)
        val toAccountId: Int = getValue(TRANSACTION.TO_ACC_ID)
        val wrId: Int = getValue(TRANSACTION.PO_ID)

        val fromAccount = accountDao.findAccount(fromAccountId)
            ?: throw IllegalStateException("Unable to find fromAccount.id=$fromAccountId for transaction.wrId=$wrId")
        val toAccount = accountDao.findAccount(toAccountId)
            ?: throw IllegalStateException("Unable to find toAccount.id=$toAccountId for transaction.wrId=$wrId")
        val paymentOrder = paymentOrderDao.findPaymentOrder(wrId)
            ?: throw IllegalStateException("Unable to find paymentOrder.id=$wrId for transaction.wrId=$wrId")

        return Transaction(
            paymentOrder,
            fromAccount,
            toAccount,
            getValue(TRANSACTION.AMOUNT),
            getValue(TRANSACTION.DATE_TRANSACTED)
        )
    }

    private fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO
}
