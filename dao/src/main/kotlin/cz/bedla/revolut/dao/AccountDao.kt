package cz.bedla.revolut.dao

import cz.bedla.revolut.domain.Account

interface AccountDao : Dao {
    fun createAccount(account: Account): Account
    fun updateBalance(account: Account)
    fun findAccount(id: Int): Account?
    fun findAccounts(): List<Account>
}
