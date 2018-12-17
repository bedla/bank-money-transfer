package cz.bedla.bank.service.impl

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.Transaction
import cz.bedla.bank.service.TransactionDao
import cz.bedla.bank.service.TransactionService
import cz.bedla.bank.tx.Transactional
import java.math.BigDecimal

class TransactionServiceImpl(
    private val transactionDao: TransactionDao,
    private val transactional: Transactional
) : TransactionService {
    override fun calculateBalance(account: Account): BigDecimal = transactional.execute {
        transactionDao.calculateBalance(account)
    }

    override fun findAccountTransactions(account: Account): List<Transaction> = transactional.execute {
        transactionDao.findAccountTransactions(account)
    }
}
