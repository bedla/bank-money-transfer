package cz.bedla.revolut.rest

import cz.bedla.revolut.Database
import cz.bedla.revolut.context.ApplicationContext
import cz.bedla.revolut.service.AccountDao
import cz.bedla.revolut.service.WaitingRoomDao
import cz.bedla.revolut.service.WaitingRoomService
import cz.bedla.revolut.tx.Transactional
import org.mockito.Mockito

internal class MockApplicationContext : ApplicationContext {
    private val waitingRoomService = Mockito.mock(WaitingRoomService::class.java)

    private val waitingRoomDao = Mockito.mock(WaitingRoomDao::class.java)

    private val accountDao = Mockito.mock(AccountDao::class.java)

    private val transactional = Mockito.mock(Transactional::class.java)

    private val database = Mockito.mock(Database::class.java)

    override fun waitingRoomServiceBean(): WaitingRoomService = waitingRoomService

    override fun waitingRoomDaoBean(): WaitingRoomDao = waitingRoomDao

    override fun accountDaoBean(): AccountDao = accountDao

    override fun transactionalBean(): Transactional = transactional

    override fun databaseBean(): Database = database

    override fun start() {
    }

    override fun stop() {
    }
}
