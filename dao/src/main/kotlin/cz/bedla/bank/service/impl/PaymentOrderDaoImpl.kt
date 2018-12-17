package cz.bedla.bank.service.impl

import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.PaymentOrder
import cz.bedla.bank.domain.PaymentOrderState
import cz.bedla.bank.jooq.Tables.PAYMENT_ORDER
import cz.bedla.bank.jooq.tables.records.PaymentOrderRecord
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.PaymentOrderDao
import cz.bedla.bank.service.createDsl
import org.jooq.exception.DataChangedException

class PaymentOrderDaoImpl(private val accountDao: AccountDao) : PaymentOrderDao {
    override fun create(item: PaymentOrder): PaymentOrder {
        val dsl = createDsl()

        val paymentOrderRecord = dsl.newRecord(PAYMENT_ORDER)
        paymentOrderRecord.id = null
        paymentOrderRecord.fromAccId = item.fromAccount.id
        paymentOrderRecord.toAccId = item.toAccount.id
        paymentOrderRecord.amount = item.amount
        paymentOrderRecord.state = item.state.name
        paymentOrderRecord.dateCreated = item.dateCreated
        paymentOrderRecord.version = 0

        paymentOrderRecord.store()

        return item.copy(id = paymentOrderRecord.id, version = paymentOrderRecord.version)
    }

    override fun findPaymentOrder(id: Int): PaymentOrder? {
        val dsl = createDsl()
        val record = dsl.selectFrom(PAYMENT_ORDER).where(PAYMENT_ORDER.ID.eq(id)).fetchOne()
        return record?.toPaymentOrder(accountDao)
    }

    override fun delete(item: PaymentOrder) {
        val dsl = createDsl()
        val record = dsl.selectFrom(PAYMENT_ORDER).where(PAYMENT_ORDER.ID.eq(item.id)).fetchOne()
        if (record != null) {
            record.set(PAYMENT_ORDER.VERSION, item.version)
            record.delete()
        } else {
            throw DataChangedException("Record id=${item.id} not found")
        }
    }

    override fun findItemsWithState(state: PaymentOrderState): List<PaymentOrder> {
        val dsl = createDsl()
        return dsl.selectFrom(PAYMENT_ORDER)
            .where(PAYMENT_ORDER.STATE.eq(state.name))
            .orderBy(PAYMENT_ORDER.DATE_CREATED)
            .fetch()
            .map { it.toPaymentOrder(accountDao) }
    }

    override fun findItemsForAccount(account: Account): List<PaymentOrder> {
        val dsl = createDsl()
        return dsl.selectFrom(PAYMENT_ORDER)
            .where(PAYMENT_ORDER.FROM_ACC_ID.eq(account.id))
            .or(PAYMENT_ORDER.TO_ACC_ID.eq(account.id))
            .orderBy(PAYMENT_ORDER.DATE_CREATED)
            .fetch()
            .map { it.toPaymentOrder(accountDao) }
    }

    override fun updateState(paymentOrder: PaymentOrder) {
        val dsl = createDsl()
        val record = dsl.selectFrom(PAYMENT_ORDER).where(PAYMENT_ORDER.ID.eq(paymentOrder.id)).fetchOne()
        record.set(PAYMENT_ORDER.VERSION, paymentOrder.version)
        record.set(PAYMENT_ORDER.STATE, paymentOrder.state.name)
        record.store()
    }

    // TODO solve N+1 problem
    private fun PaymentOrderRecord.toPaymentOrder(accountDao: AccountDao): PaymentOrder {
        val id = getValue(PAYMENT_ORDER.ID)!!
        val fromAccountId: Int = getValue(PAYMENT_ORDER.FROM_ACC_ID)
        val toAccountId: Int = getValue(PAYMENT_ORDER.TO_ACC_ID)
        val fromAccount = accountDao.findAccount(fromAccountId)
            ?: throw IllegalStateException("Unable to find fromAccount.id=$fromAccountId for paymentOrder.id=$id")
        val toAccount = accountDao.findAccount(toAccountId)
            ?: throw IllegalStateException("Unable to find toAccount.id=$toAccountId for paymentOrder.id=$id")

        return PaymentOrder(
            fromAccount,
            toAccount,
            getValue(PAYMENT_ORDER.AMOUNT),
            PaymentOrderState.valueOf(getValue(PAYMENT_ORDER.STATE)),
            getValue(PAYMENT_ORDER.DATE_CREATED),
            getValue(PAYMENT_ORDER.ID),
            getValue(PAYMENT_ORDER.VERSION)
        )
    }
}
