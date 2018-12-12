package cz.bedla.revolut.service

import cz.bedla.revolut.domain.Account
import cz.bedla.revolut.domain.AccountType

interface AccountDao : Dao {
    fun createAccount(account: Account): Account
    fun updateBalance(account: Account)
    fun findAccount(id: Int): Account?
    fun findAccounts(): List<Account>
    fun findAccountsOfType(type: AccountType): List<Account>
}
