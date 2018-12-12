package cz.bedla.bank.service.impl

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.WaitingRoom
import cz.bedla.bank.domain.WaitingRoomState
import cz.bedla.bank.service.*
import cz.bedla.bank.tx.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime

class WaitingRoomServiceImpl(
    private val waitingRoomDao: WaitingRoomDao,
    private val accountDao: AccountDao,
    private val transactional: Transactional
) : WaitingRoomService {
    override fun receivePaymentRequest(fromAccountId: Int, toAccountId: Int, amount: BigDecimal): WaitingRoom =
        transactional.execute {
            check(amount > 0.toBigDecimal()) { "Invalid amount value" }

            val fromAccount = accountDao.findAccount(fromAccountId) ?: throw AccountNotFound(fromAccountId)
            val toAccount = accountDao.findAccount(toAccountId) ?: throw AccountNotFound(toAccountId)

            if (fromAccount.type == AccountType.PERSONAL && toAccount.type == AccountType.PERSONAL) {
                waitingRoomDao.create(
                    WaitingRoom(
                        fromAccount,
                        toAccount,
                        amount,
                        WaitingRoomState.RECEIVED,
                        OffsetDateTime.now()
                    )
                )
            } else {
                throw InvalidPaymentRequest(fromAccountId, toAccountId)
            }
        }

    override fun topUpRequest(toAccountId: Int, amount: BigDecimal): WaitingRoom = transactional.execute {
        check(amount > 0.toBigDecimal()) { "Invalid amount value" }

        val account = accountDao.findAccount(toAccountId) ?: throw AccountNotFound(toAccountId)
        val topUpAccount = topUpAccount()

        account.topUpWithPersonalAccountOnly {
            waitingRoomDao.create(
                WaitingRoom(
                    topUpAccount,
                    it,
                    amount,
                    WaitingRoomState.RECEIVED,
                    OffsetDateTime.now()
                )
            )
        }
    }

    override fun withdrawalRequest(fromAccountId: Int, amount: BigDecimal): WaitingRoom = transactional.execute {
        check(amount > 0.toBigDecimal()) { "Invalid amount value" }

        val account = accountDao.findAccount(fromAccountId) ?: throw AccountNotFound(fromAccountId)
        val withdrawalAccount = withdrawalAccount()

        account.withdrawWithPersonalAccountOnly {
            waitingRoomDao.create(
                WaitingRoom(
                    it,
                    withdrawalAccount,
                    amount,
                    WaitingRoomState.RECEIVED,
                    OffsetDateTime.now()
                )
            )
        }
    }

    override fun listWaitingRoomRequestsForPersonalAccounts(accountId: Int): List<WaitingRoom> = transactional.execute {
        val account = accountDao.findAccount(accountId) ?: throw AccountNotFound(accountId)

        account.withPersonalAccountOnly({
            waitingRoomDao.findItemsForAccount(it)
        }, {
            throw InvalidAccountRequest(it.id, "list request")
        })
    }

    private inline fun <T> Account.withdrawWithPersonalAccountOnly(block: (Account) -> T): T =
        withPersonalAccountOnly(block) {
            throw InvalidWithdrawalRequest(it.id)
        }

    private inline fun <T> Account.topUpWithPersonalAccountOnly(block: (Account) -> T): T =
        withPersonalAccountOnly(block) {
            throw InvalidTopUpRequest(it.id)
        }

    private inline fun <T> Account.withPersonalAccountOnly(block: (Account) -> T, elseBlock: (Account) -> Nothing): T =
        if (type == AccountType.PERSONAL) {
            block(this)
        } else {
            elseBlock(this)
        }

    private fun topUpAccount(): Account {
        val list = accountDao.findAccountsOfType(AccountType.TOP_UP)
        check(list.isNotEmpty()) { "Unable to find any top-up account" }
        list.sortedBy { it.balance }
        return list.first()
    }

    private fun withdrawalAccount(): Account {
        val list = accountDao.findAccountsOfType(AccountType.WITHDRAWAL)
        check(list.isNotEmpty()) { "Unable to find any withdrawal account" }
        list.sortedByDescending { it.balance }
        return list.first()
    }
}
