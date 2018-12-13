package cz.bedla.bank.context.impl

import cz.bedla.bank.Database
import cz.bedla.bank.DatabaseImpl
import cz.bedla.bank.DbInitializer
import cz.bedla.bank.context.ApplicationContext
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.AccountService
import cz.bedla.bank.service.WaitingRoomDao
import cz.bedla.bank.service.WaitingRoomService
import cz.bedla.bank.service.impl.AccountDaoImpl
import cz.bedla.bank.service.impl.AccountServiceImpl
import cz.bedla.bank.service.impl.WaitingRoomDaoImpl
import cz.bedla.bank.service.impl.WaitingRoomServiceImpl
import cz.bedla.bank.tx.Transactional
import cz.bedla.bank.tx.TransactionalImpl
import org.slf4j.LoggerFactory
import java.io.File

class ApplicationContextImpl(private val databaseFile: File) : ApplicationContext {
    private val waitingRoomService = lazyBean {
        WaitingRoomServiceImpl(
            waitingRoomDaoBean(),
            accountServiceBean(),
            transactionalBean()
        )
    }

    private val waitingRoomDao = lazyBean {
        WaitingRoomDaoImpl(accountDaoBean())
    }

    private val accountDao = lazyBean {
        AccountDaoImpl()
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

    override fun waitingRoomServiceBean(): WaitingRoomService = waitingRoomService.value

    override fun waitingRoomDaoBean(): WaitingRoomDao = waitingRoomDao.value

    override fun accountDaoBean(): AccountDao = accountDao.value

    override fun accountServiceBean(): AccountService = accountService.value

    override fun transactionalBean(): Transactional = transactional.value

    override fun databaseBean(): Database = database.value

    override fun start() {
        databaseBean().start()
        val dbInitializer = DbInitializer("database.sql", databaseBean().dataSource)
        if (dbInitializer.checkDbInitialized()) {
            logger.info("DB already initialized")
        } else {
            logger.info("DB not initialized, running init script...")
            dbInitializer.run()
        }
    }

    override fun stop() {
        databaseBean().stop()
    }

    private inline fun <reified T> lazyBean(noinline initializer: () -> T): Lazy<T> =
        lazy(LazyThreadSafetyMode.NONE, initializer)

    companion object {
        private val logger = LoggerFactory.getLogger(ApplicationContextImpl::class.java)!!
    }
}
