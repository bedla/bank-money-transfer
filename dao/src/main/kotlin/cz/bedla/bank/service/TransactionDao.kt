package cz.bedla.bank.service

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.Transaction
import java.math.BigDecimal
import java.time.OffsetDateTime

interface TransactionDao : Dao {
    fun create(
        paymentOrderId: Int,
        fromAccountId: Int,
        toAccountId: Int,
        amount: BigDecimal,
        dateTransacted: OffsetDateTime
    ): Transaction

    fun calculateBalance(account: Account): BigDecimal

    fun findAccountTransactions(account: Account): List<Transaction>
}
