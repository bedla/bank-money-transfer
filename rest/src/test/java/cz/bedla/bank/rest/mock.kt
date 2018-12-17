package cz.bedla.bank.rest

import com.nhaarman.mockitokotlin2.KStubbing
import cz.bedla.bank.Database
import cz.bedla.bank.context.ApplicationContext
import cz.bedla.bank.service.*
import cz.bedla.bank.tx.Transactional
import org.mockito.Mockito.mock

internal class MockApplicationContext : ApplicationContext {
    private val paymentOrderService = mock(PaymentOrderService::class.java)

    private val paymentOrderDao = mock(PaymentOrderDao::class.java)

    private val accountDao = mock(AccountDao::class.java)

    private val transactionDao = mock(TransactionDao::class.java)

    private val transactionService = mock(TransactionService::class.java)

    private val accountService = mock(AccountService::class.java)

    private val transactional = mock(Transactional::class.java)

    private val database = mock(Database::class.java)

    private val coordinator = mock(Coordinator::class.java)

    private val transactor = mock(Transactor::class.java)

    private val bankInitializer = mock(BankInitializer::class.java)

    override fun paymentOrderServiceBean(): PaymentOrderService = paymentOrderService

    override fun paymentOrderDaoBean(): PaymentOrderDao = paymentOrderDao

    override fun accountDaoBean(): AccountDao = accountDao

    override fun transactionDaoBean(): TransactionDao = transactionDao

    override fun transactionServiceBean(): TransactionService = transactionService

    override fun accountServiceBean(): AccountService = accountService

    override fun transactionalBean(): Transactional = transactional

    override fun databaseBean(): Database = database

    override fun coordinatorBean(): Coordinator = coordinator

    override fun transactorBean(): Transactor = transactor

    override fun bankInitializerBean(): BankInitializer = bankInitializer

    override fun start() {
    }

    override fun stop() {
    }
}

internal fun <T> mock(mock: T, stubbing: KStubbing<T>.(T) -> Unit) {
    return KStubbing(mock).stubbing(mock)
}
