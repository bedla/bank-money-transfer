package cz.bedla.bank.domain

import java.math.BigDecimal
import java.time.OffsetDateTime

data class PaymentOrder(
    val fromAccount: Account,
    val toAccount: Account,
    val amount: BigDecimal,
    val state: PaymentOrderState,
    val dateCreated: OffsetDateTime,
    val id: Int = 0,
    val version: Int = 0
)

enum class PaymentOrderState {
    RECEIVED, OK, NO_FUNDS
}
