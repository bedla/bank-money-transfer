package cz.bedla.bank.service.impl

import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.PaymentOrder
import cz.bedla.bank.domain.PaymentOrderState
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.PaymentOrderDao
import cz.bedla.bank.service.TransactionDao
import cz.bedla.bank.service.Transactor
import cz.bedla.bank.tx.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean

class TransactorImpl(
    private val transactionDao: TransactionDao,
    private val paymentOrderDao: PaymentOrderDao,
    private val accountDao: AccountDao,
    private val transactional: Transactional,
    private val beforeProcessBlock: () -> Unit = {}
) : Transactor {
    private val running = AtomicBoolean()

    override fun process(paymentOrder: PaymentOrder): Transactor.ResultState =
        when {
            !running.get() -> {
                logger.info("PaymentOrder.id=${paymentOrder.id} - transactor not running, skipping.")
                Transactor.ResultState.STOPPED
            }
            checkPaymentOrderReceived(paymentOrder) -> {
                logger.info("PaymentOrder.id=${paymentOrder.id} - already processed (heavy-load?), skipping.")
                Transactor.ResultState.INVALID_STATE
            }
            else -> trySendMoney(paymentOrder)
        }

    private fun checkPaymentOrderReceived(paymentOrder: PaymentOrder) = transactional.execute {
        (paymentOrderDao.findPaymentOrder(paymentOrder.id)
            ?: error("Unable to find paymentOrder.id=${paymentOrder.id}"))
            .state != PaymentOrderState.RECEIVED
    }

    private fun isPersonalAccountWithoutFunds(paymentOrder: PaymentOrder): Boolean {
        val fromAccount = paymentOrder.fromAccount
        return fromAccount.type == AccountType.PERSONAL && fromAccount.balance < paymentOrder.amount
    }

    private fun trySendMoney(paymentOrder: PaymentOrder) = transactional.execute {
        if (isPersonalAccountWithoutFunds(paymentOrder)) {
            logger.info("PaymentOrder.id=${paymentOrder.id} - from account.id=${paymentOrder.fromAccount.id} does not have enough funds.")
            paymentOrderDao.updateState(paymentOrder.copy(state = PaymentOrderState.NO_FUNDS))
            Transactor.ResultState.NO_FUNDS
        } else {
            beforeProcessBlock()

            val fromAccount = paymentOrder.fromAccount
            val toAccount = paymentOrder.toAccount
            logger.info("PaymentOrder.id=${paymentOrder.id} - sending money from account.id=${fromAccount.id} to account.id=${toAccount.id}")

            val transaction = transactionDao.create(
                paymentOrder.id,
                fromAccount.id,
                toAccount.id,
                paymentOrder.amount,
                OffsetDateTime.now()
            )
            logger.info("PaymentOrder.id=${paymentOrder.id} - created transaction.wrId=${transaction.paymentOrder.id}")

            accountDao.updateBalance(fromAccount.copy(balance = fromAccount.balance - paymentOrder.amount))
            accountDao.updateBalance(toAccount.copy(balance = toAccount.balance + paymentOrder.amount))
            logger.info("PaymentOrder.id=${paymentOrder.id} - balances updated with amount=${paymentOrder.amount}")

            logger.info("PaymentOrder.id=${paymentOrder.id} - state updated to ${PaymentOrderState.OK}, commit...")
            paymentOrderDao.updateState(paymentOrder.copy(state = PaymentOrderState.OK))

            Transactor.ResultState.MONEY_SENT
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
