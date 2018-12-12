package cz.bedla.revolut.context.impl

import cz.bedla.revolut.Database
import cz.bedla.revolut.DatabaseImpl
import cz.bedla.revolut.DbInitializer
import cz.bedla.revolut.context.ApplicationContext
import cz.bedla.revolut.service.AccountDao
import cz.bedla.revolut.service.WaitingRoomDao
import cz.bedla.revolut.service.WaitingRoomService
import cz.bedla.revolut.service.impl.AccountDaoImpl
import cz.bedla.revolut.service.impl.WaitingRoomDaoImpl
import cz.bedla.revolut.service.impl.WaitingRoomServiceImpl
import cz.bedla.revolut.tx.Transactional
import cz.bedla.revolut.tx.TransactionalImpl
import org.slf4j.LoggerFactory
import java.io.File

class ApplicationContextImpl(private val databaseFile: File) : ApplicationContext {
    private val waitingRoomService = lazyBean {
        WaitingRoomServiceImpl(
            waitingRoomDaoBean(),
            accountDaoBean(),
            transactionalBean()
        )
    }

    private val waitingRoomDao = lazyBean {
        WaitingRoomDaoImpl(accountDaoBean())
    }

    private val accountDao = lazyBean {
        AccountDaoImpl()
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
