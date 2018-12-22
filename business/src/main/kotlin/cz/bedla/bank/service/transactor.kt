package cz.bedla.bank.service

import cz.bedla.bank.domain.PaymentOrder

interface Transactor {
    fun process(paymentOrder: PaymentOrder): ResultState
    fun start()
    fun stop()

    enum class ResultState {
        STOPPED, INVALID_STATE, MONEY_SENT, NO_FUNDS
    }
}
