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
    private val accountService: AccountService,
    private val transactional: Transactional
) : WaitingRoomService {
    override fun receivePaymentRequest(fromAccountId: Int, toAccountId: Int, amount: BigDecimal): WaitingRoom =
        transactional.execute {
            check(amount > 0.toBigDecimal()) { "Invalid amount value" }

            val fromAccount = accountService.findAccount(fromAccountId)
            val toAccount = accountService.findAccount(toAccountId)

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

        val account = accountService.findAccount(toAccountId)
        val topUpAccount = accountService.findTopUpAccount()

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

        val account = accountService.findAccount(fromAccountId)
        val withdrawalAccount = accountService.findWithdrawalAccount()

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

    override fun waitingRoomState(id: Int): WaitingRoomState = transactional.execute {
        (waitingRoomDao.findWaitingRoom(id) ?: throw WaitingRoomNotFound(id)).state
    }

    override fun listWaitingRoomRequestsForPersonalAccounts(accountId: Int): List<WaitingRoom> = transactional.execute {
        val account = accountService.findAccount(accountId)

        account.withPersonalAccountOnly({
            waitingRoomDao.findItemsForAccount(it)
        }, {
            throw InvalidAccountRequest(it.id, "list request")
        })
    }

    override fun listWaitingRoomsToProcess(): List<WaitingRoom> = transactional.execute {
        waitingRoomDao.findItemsWithState(WaitingRoomState.RECEIVED)
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
}
