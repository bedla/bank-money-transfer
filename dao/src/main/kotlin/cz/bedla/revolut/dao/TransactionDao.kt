package cz.bedla.revolut.dao

import cz.bedla.revolut.domain.Account
import cz.bedla.revolut.domain.Transaction
import java.math.BigDecimal

interface TransactionDao : Dao {
    fun create(item: Transaction): Transaction

    fun calculateBalance(account: Account): BigDecimal

    fun findAccountTransactions(account: Account): List<Transaction>
}
