package cz.bedla.bank.service

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.Transaction
import java.math.BigDecimal

interface TransactionService {
    fun calculateBalance(account: Account): BigDecimal

    fun findAccountTransactions(account: Account): List<Transaction>
}
