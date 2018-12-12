package cz.bedla.bank.service

import cz.bedla.bank.domain.WaitingRoom
import java.math.BigDecimal


interface WaitingRoomService {
    fun receivePaymentRequest(fromAccountId: Int, toAccountId: Int, amount: BigDecimal): WaitingRoom

    fun topUpRequest(toAccountId: Int, amount: BigDecimal): WaitingRoom

    fun withdrawalRequest(fromAccountId: Int, amount: BigDecimal): WaitingRoom

    fun listWaitingRoomRequestsForPersonalAccounts(accountId: Int): List<WaitingRoom>
}

class AccountNotFound(accountId: Int) : RuntimeException("Unable to find account.id=$accountId")

class InvalidPaymentRequest(fromAccountId: Int, toAccountId: Int) :
        RuntimeException("Invalid payment request from account.id=$fromAccountId to account.id=$toAccountId")

class InvalidTopUpRequest(toAccountId: Int) :
        InvalidAccountRequest(toAccountId, "top-up request")

class InvalidWithdrawalRequest(fromAccountId: Int) :
        InvalidAccountRequest(fromAccountId, "withdrawal request")

open class InvalidAccountRequest(accountId: Int, action: String) :
        RuntimeException("Invalid $action for account.id=$accountId")
