package cz.bedla.bank.domain

import java.math.BigDecimal
import java.time.OffsetDateTime

data class WaitingRoom(
        val fromAccount: Account,
        val toAccount: Account,
        val amount: BigDecimal,
        val state: WaitingRoomState,
        val dateCreated: OffsetDateTime,
        val id: Int = 0,
        val version: Int = 0
)

enum class WaitingRoomState {
    RECEIVED, OK, NO_FUNDS
}
