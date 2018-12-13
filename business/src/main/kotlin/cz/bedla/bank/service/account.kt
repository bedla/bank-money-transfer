package cz.bedla.bank.service

import cz.bedla.bank.domain.Account
import java.math.BigDecimal

interface AccountService {
    fun createPersonalAccount(name: String): Account
    fun createTopUpAccount(name: String, amount: BigDecimal): Account
    fun createWithdrawalAccount(name: String, amount: BigDecimal): Account
    fun findAccount(id: Int): Account
    fun findTopUpAccount(): Account
    fun findWithdrawalAccount(): Account
}

class AccountNotFound(accountId: Int) : RuntimeException("Unable to find account.id=$accountId")
