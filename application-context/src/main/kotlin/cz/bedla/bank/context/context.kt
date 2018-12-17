package cz.bedla.bank.context

import cz.bedla.bank.Database
import cz.bedla.bank.service.*
import cz.bedla.bank.tx.Transactional

interface ApplicationContext {
    fun paymentOrderServiceBean(): PaymentOrderService
    fun paymentOrderDaoBean(): PaymentOrderDao
    fun accountServiceBean(): AccountService
    fun accountDaoBean(): AccountDao
    fun transactionDaoBean(): TransactionDao
    fun transactionServiceBean(): TransactionService
    fun transactionalBean(): Transactional
    fun databaseBean(): Database
    fun coordinatorBean(): Coordinator
    fun transactorBean(): Transactor
    fun bankInitializerBean(): BankInitializer

    fun start()
    fun stop()
}
