package cz.bedla.bank.service

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.PaymentOrder
import cz.bedla.bank.domain.PaymentOrderState

interface PaymentOrderDao : Dao {
    fun create(item: PaymentOrder): PaymentOrder

    fun findPaymentOrder(id: Int): PaymentOrder?

    fun findItemsWithState(state: PaymentOrderState): List<PaymentOrder>

    fun findItemsForAccount(account: Account): List<PaymentOrder>

    fun updateState(paymentOrder: PaymentOrder)

    fun delete(item: PaymentOrder)
}
