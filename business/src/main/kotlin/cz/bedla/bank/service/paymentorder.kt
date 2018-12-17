package cz.bedla.bank.service

import cz.bedla.bank.domain.PaymentOrder
import cz.bedla.bank.domain.PaymentOrderState
import java.math.BigDecimal


interface PaymentOrderService {
    fun receivePaymentRequest(fromAccountId: Int, toAccountId: Int, amount: BigDecimal): PaymentOrder

    fun topUpRequest(toAccountId: Int, amount: BigDecimal): PaymentOrder

    fun withdrawalRequest(fromAccountId: Int, amount: BigDecimal): PaymentOrder

    fun paymentOrderState(id: Int): PaymentOrderState

    fun listItemsForPersonalAccounts(accountId: Int): List<PaymentOrder>

    fun listItemsToProcess(): List<PaymentOrder>
}

class PaymentOrderNotFound(paymentOrderId: Int) : RuntimeException("Unable to find paymentOrder.id=$paymentOrderId")

class InvalidPaymentRequest(fromAccountId: Int, toAccountId: Int) :
    RuntimeException("Invalid payment request from account.id=$fromAccountId to account.id=$toAccountId")

class InvalidTopUpRequest(toAccountId: Int) :
    InvalidAccountRequest(toAccountId, "top-up request")

class InvalidWithdrawalRequest(fromAccountId: Int) :
    InvalidAccountRequest(fromAccountId, "withdrawal request")

open class InvalidAccountRequest(accountId: Int, action: String) :
    RuntimeException("Invalid $action for account.id=$accountId")
