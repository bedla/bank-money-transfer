package cz.bedla.bank.service.impl

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.AccountNotFound
import cz.bedla.bank.service.AccountService
import cz.bedla.bank.tx.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime

class AccountServiceImpl(
    private val accountDao: AccountDao,
    private val transactional: Transactional
) : AccountService {
    override fun createPersonalAccount(name: String): Account = transactional.execute {
        require(name.isNotBlank()) { "Account name cannot be empty" }
        accountDao.createAccount(Account(AccountType.PERSONAL, name, OffsetDateTime.now(), 0.toBigDecimal()))
    }

    override fun createTopUpAccount(name: String, amount: BigDecimal): Account = transactional.execute {
        require(name.isNotBlank()) { "Account name cannot be empty" }
        accountDao.createAccount(Account(AccountType.TOP_UP, name, OffsetDateTime.now(), amount))
    }

    override fun createWithdrawalAccount(name: String, amount: BigDecimal): Account = transactional.execute {
        require(name.isNotBlank()) { "Account name cannot be empty" }
        accountDao.createAccount(Account(AccountType.WITHDRAWAL, name, OffsetDateTime.now(), amount))
    }

    override fun findAccount(id: Int): Account = accountDao.findAccount(id) ?: throw AccountNotFound(id)

    override fun findTopUpAccount(): Account {
        val list = accountDao.findAccountsOfType(AccountType.TOP_UP)
        check(list.isNotEmpty()) { "Unable to find any top-up account" }
        return list.sortedBy { it.balance }.first()
    }

    override fun findWithdrawalAccount(): Account {
        val list = accountDao.findAccountsOfType(AccountType.WITHDRAWAL)
        check(list.isNotEmpty()) { "Unable to find any withdrawal account" }
        return list.sortedByDescending { it.balance }.first()
    }
}
