package cz.bedla.bank.domain

import java.math.BigDecimal
import java.time.OffsetDateTime

data class Transaction(
    val waitingRoom: WaitingRoom,
    val fromAccount: Account,
    val toAccount: Account,
    val amount: BigDecimal,
    val dateTransacted: OffsetDateTime
)
