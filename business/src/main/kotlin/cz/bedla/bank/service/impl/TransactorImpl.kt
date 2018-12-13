package cz.bedla.bank.service.impl

import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.Transaction
import cz.bedla.bank.domain.WaitingRoom
import cz.bedla.bank.domain.WaitingRoomState
import cz.bedla.bank.service.*
import cz.bedla.bank.tx.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean

class TransactorImpl(
    private val transactionDao: TransactionDao,
    private val waitingRoomDao: WaitingRoomDao,
    private val accountDao: AccountDao,
    private val waitingRoomService: WaitingRoomService,
    private val transactional: Transactional
) : Transactor {
    private val running = AtomicBoolean()

    override fun process(waitingRoom: WaitingRoom) {
        if (!running.get()) {
            logger.info("WaitingRoom.id=${waitingRoom.id} - transactor not running, skipping.")
            return
        }

        if (waitingRoomService.waitingRoomState(waitingRoom.id) != WaitingRoomState.RECEIVED) {
            logger.info("WaitingRoom.id=${waitingRoom.id} - already processed (heavy-load?), skipping.")
            return
        }

        trySendMoney(waitingRoom)
    }

    private fun isPersonalAccountWithoutFunds(waitingRoom: WaitingRoom): Boolean {
        val fromAccount = waitingRoom.fromAccount
        return fromAccount.type == AccountType.PERSONAL && fromAccount.balance < waitingRoom.amount
    }

    private fun trySendMoney(waitingRoom: WaitingRoom) = transactional.run {
        if (isPersonalAccountWithoutFunds(waitingRoom)) {
            logger.info("WaitingRoom.id=${waitingRoom.id} - from account.id=${waitingRoom.fromAccount.id} does not have enough funds.")
            waitingRoomDao.updateState(waitingRoom.copy(state = WaitingRoomState.NO_FUNDS))
        } else {
            val fromAccount = waitingRoom.fromAccount
            val toAccount = waitingRoom.toAccount
            logger.info("WaitingRoom.id=${waitingRoom.id} - sending money from account.id=${fromAccount.id} to account.id=${toAccount.id}")

            val transaction = transactionDao.create(
                Transaction(waitingRoom, fromAccount, toAccount, waitingRoom.amount, OffsetDateTime.now())
            )
            logger.info("WaitingRoom.id=${waitingRoom.id} - created transaction.wrId=${transaction.waitingRoom.id}")

            accountDao.updateBalance(fromAccount.copy(balance = fromAccount.balance - waitingRoom.amount))
            accountDao.updateBalance(toAccount.copy(balance = toAccount.balance + waitingRoom.amount))
            logger.info("WaitingRoom.id=${waitingRoom.id} - balances updated with amount=${waitingRoom.amount}")

            logger.info("WaitingRoom.id=${waitingRoom.id} - state updated to ${WaitingRoomState.OK}, commit...")
            waitingRoomDao.updateState(waitingRoom.copy(state = WaitingRoomState.OK))
        }
    }

    override fun start() {
        running.set(true)
    }

    override fun stop() {
        running.set(false)
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(TransactorImpl::class.java)
    }
}
