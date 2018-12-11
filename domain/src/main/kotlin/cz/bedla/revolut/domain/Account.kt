package cz.bedla.revolut.domain

import java.math.BigDecimal
import java.time.OffsetDateTime

data class Account(
        val type: AccountType,
        val name: String,
        val dateOpened: OffsetDateTime,
        val balance: BigDecimal,
        val id: Int = 0,
        val version: Int = 0)

enum class AccountType {
    PERSONAL, TOP_UP, WITHDRAWAL
}

