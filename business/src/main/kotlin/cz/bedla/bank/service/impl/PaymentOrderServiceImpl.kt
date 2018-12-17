package cz.bedla.bank.service.impl

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.PaymentOrder
import cz.bedla.bank.domain.PaymentOrderState
import cz.bedla.bank.service.*
import cz.bedla.bank.tx.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime

class PaymentOrderServiceImpl(
    private val paymentOrderDao: PaymentOrderDao,
    private val accountService: AccountService,
    private val transactional: Transactional
) : PaymentOrderService {
    override fun receivePaymentRequest(fromAccountId: Int, toAccountId: Int, amount: BigDecimal): PaymentOrder =
        transactional.execute {
            check(amount > 0.toBigDecimal()) { "Invalid amount value" }

            val fromAccount = accountService.findAccount(fromAccountId)
            val toAccount = accountService.findAccount(toAccountId)

            if (fromAccount.type == AccountType.PERSONAL && toAccount.type == AccountType.PERSONAL) {
                paymentOrderDao.create(
                    PaymentOrder(
                        fromAccount,
                        toAccount,
                        amount,
                        PaymentOrderState.RECEIVED,
                        OffsetDateTime.now()
                    )
                )
            } else {
                throw InvalidPaymentRequest(fromAccountId, toAccountId)
            }
        }

    override fun topUpRequest(toAccountId: Int, amount: BigDecimal): PaymentOrder = transactional.execute {
        check(amount > 0.toBigDecimal()) { "Invalid amount value" }

        val account = accountService.findAccount(toAccountId)
        val topUpAccount = accountService.findTopUpAccount()

        account.topUpWithPersonalAccountOnly {
            paymentOrderDao.create(
                PaymentOrder(
                    topUpAccount,
                    it,
                    amount,
                    PaymentOrderState.RECEIVED,
                    OffsetDateTime.now()
                )
            )
        }
    }

    override fun withdrawalRequest(fromAccountId: Int, amount: BigDecimal): PaymentOrder = transactional.execute {
        check(amount > 0.toBigDecimal()) { "Invalid amount value" }

        val account = accountService.findAccount(fromAccountId)
        val withdrawalAccount = accountService.findWithdrawalAccount()

        account.withdrawWithPersonalAccountOnly {
            paymentOrderDao.create(
                PaymentOrder(
                    it,
                    withdrawalAccount,
                    amount,
                    PaymentOrderState.RECEIVED,
                    OffsetDateTime.now()
                )
            )
        }
    }

    override fun paymentOrderState(id: Int): PaymentOrderState = transactional.execute {
        (paymentOrderDao.findPaymentOrder(id) ?: throw PaymentOrderNotFound(id)).state
    }

    override fun listItemsForPersonalAccounts(accountId: Int): List<PaymentOrder> = transactional.execute {
        val account = accountService.findAccount(accountId)

        account.withPersonalAccountOnly({
            paymentOrderDao.findItemsForAccount(it)
        }, {
            throw InvalidAccountRequest(it.id, "list request")
        })
    }

    override fun listItemsToProcess(): List<PaymentOrder> = transactional.execute {
        paymentOrderDao.findItemsWithState(PaymentOrderState.RECEIVED)
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
