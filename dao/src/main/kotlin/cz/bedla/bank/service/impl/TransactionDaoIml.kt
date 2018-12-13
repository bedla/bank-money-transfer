package cz.bedla.bank.service.impl

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.Transaction
import cz.bedla.bank.jooq.Tables.TRANSACTION
import cz.bedla.bank.jooq.tables.records.TransactionRecord
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.TransactionDao
import cz.bedla.bank.service.WaitingRoomDao
import cz.bedla.bank.service.createDsl
import org.jooq.impl.DSL.sum
import java.math.BigDecimal

class TransactionDaoIml(
    private val accountDao: AccountDao,
    private val waitingRoomDao: WaitingRoomDao
) : TransactionDao {
    override fun create(item: Transaction): Transaction {
        val dsl = createDsl()

        val transactionRecord = dsl.newRecord(TRANSACTION)
        transactionRecord.wrId = item.waitingRoom.id
        transactionRecord.fromAccId = item.fromAccount.id
        transactionRecord.toAccId = item.toAccount.id
        transactionRecord.amount = item.amount
        transactionRecord.dateTransacted = item.dateTransacted

        transactionRecord.store()

        return item
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
            .map { it.toTransaction(accountDao, waitingRoomDao) }
    }

    // TODO solve N+1 problem
    private fun TransactionRecord.toTransaction(accountDao: AccountDao, waitingRoomDao: WaitingRoomDao): Transaction {
        val fromAccountId: Int = getValue(TRANSACTION.FROM_ACC_ID)
        val toAccountId: Int = getValue(TRANSACTION.TO_ACC_ID)
        val wrId: Int = getValue(TRANSACTION.WR_ID)

        val fromAccount = accountDao.findAccount(fromAccountId)
            ?: throw IllegalStateException("Unable to find fromAccount.id=$fromAccountId for transaction.wrId=$wrId")
        val toAccount = accountDao.findAccount(toAccountId)
            ?: throw IllegalStateException("Unable to find toAccount.id=$toAccountId for transaction.wrId=$wrId")
        val waitingRoom = waitingRoomDao.findWaitingRoom(wrId)
            ?: throw IllegalStateException("Unable to find waitingRoom.id=$wrId for transaction.wrId=$wrId")

        return Transaction(
            waitingRoom,
            fromAccount,
            toAccount,
            getValue(TRANSACTION.AMOUNT),
            getValue(TRANSACTION.DATE_TRANSACTED)
        )
    }

    private fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO
}
