package cz.bedla.bank.context.impl

import cz.bedla.bank.Database
import cz.bedla.bank.DatabaseImpl
import cz.bedla.bank.DbInitializer
import cz.bedla.bank.context.ApplicationContext
import cz.bedla.bank.service.*
import cz.bedla.bank.service.impl.*
import cz.bedla.bank.tx.Transactional
import cz.bedla.bank.tx.TransactionalImpl
import org.slf4j.LoggerFactory
import java.io.File

class ApplicationContextImpl(
    private val databaseFile: File,
    private val coordinatorInitDelaySeconds: Int = 5,
    private val coordinatorPeriodSeconds: Int = 5
) : ApplicationContext {
    private val paymentOrderService = lazyBean {
        PaymentOrderServiceImpl(
            paymentOrderDaoBean(),
            accountServiceBean(),
            transactionalBean()
        )
    }

    private val paymentOrderDao = lazyBean {
        PaymentOrderDaoImpl(accountDaoBean())
    }

    private val accountDao = lazyBean {
        AccountDaoImpl()
    }

    private val transactionDao = lazyBean {
        TransactionDaoIml(accountDaoBean(), paymentOrderDaoBean())
    }

    private val transactionService = lazyBean {
        TransactionServiceImpl(transactionDaoBean(), transactionalBean())
    }

    private val accountService = lazyBean {
        AccountServiceImpl(accountDaoBean(), transactionalBean())
    }

    private val transactional = lazyBean {
        TransactionalImpl(databaseBean().dataSource)
    }

    private val database = lazyBean {
        DatabaseImpl(databaseFile)
    }

    private val coordinator = lazyBean {
        CoordinatorImpl(
            Runtime.getRuntime().availableProcessors() * 2,
            coordinatorInitDelaySeconds,
            coordinatorPeriodSeconds,
            paymentOrderServiceBean(),
            transactorBean()
        )
    }

    private val transactor = lazyBean {
        TransactorImpl(
            transactionDaoBean(),
            paymentOrderDaoBean(),
            accountDaoBean(),
            transactionalBean()
        )
    }

    private val bankInitializer = lazyBean {
        BankInitializerImpl(accountServiceBean())
    }

    override fun paymentOrderServiceBean(): PaymentOrderService = paymentOrderService.value

    override fun paymentOrderDaoBean(): PaymentOrderDao = paymentOrderDao.value

    override fun accountDaoBean(): AccountDao = accountDao.value

    override fun transactionDaoBean(): TransactionDao = transactionDao.value

    override fun transactionServiceBean(): TransactionService = transactionService.value

    override fun accountServiceBean(): AccountService = accountService.value

    override fun transactionalBean(): Transactional = transactional.value

    override fun databaseBean(): Database = database.value

    override fun coordinatorBean(): Coordinator = coordinator.value

    override fun transactorBean(): Transactor = transactor.value

    override fun bankInitializerBean(): BankInitializer = bankInitializer.value

    override fun start() {
        logger.info("Application context is starting")
        databaseBean().start()
        val dbInitializer = DbInitializer("database.sql", databaseBean().dataSource)
        if (dbInitializer.checkDbInitialized()) {
            logger.info("DB already initialized")
        } else {
            logger.info("DB not initialized, running init script...")
            dbInitializer.run()
        }

        bankInitializerBean().init()

        transactorBean().start()
        coordinatorBean().start()
    }

    override fun stop() {
        logger.info("Application context is stopping")

        databaseBean().stop()

        transactorBean().stop()
        coordinatorBean().stop()
    }

    private inline fun <reified T> lazyBean(noinline initializer: () -> T): Lazy<T> =
        lazy(LazyThreadSafetyMode.NONE, initializer)

    companion object {
        private val logger = LoggerFactory.getLogger(ApplicationContextImpl::class.java)!!
    }
}
