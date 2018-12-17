package cz.bedla.bank.service

import cz.bedla.bank.domain.PaymentOrder

interface Transactor {
    fun process(paymentOrder: PaymentOrder)
    fun start()
    fun stop()
}
