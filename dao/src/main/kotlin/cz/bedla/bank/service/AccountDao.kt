package cz.bedla.bank.service

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType

interface AccountDao : Dao {
    fun createAccount(account: Account): Account
    fun updateBalance(account: Account)
    fun findAccount(id: Int): Account?
    fun findAccounts(): List<Account>
    fun findAccountsOfType(type: AccountType): List<Account>
}
