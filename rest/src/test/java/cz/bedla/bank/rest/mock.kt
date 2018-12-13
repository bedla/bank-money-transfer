package cz.bedla.bank.rest

import com.nhaarman.mockitokotlin2.KStubbing
import cz.bedla.bank.Database
import cz.bedla.bank.context.ApplicationContext
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.AccountService
import cz.bedla.bank.service.WaitingRoomDao
import cz.bedla.bank.service.WaitingRoomService
import cz.bedla.bank.tx.Transactional
import org.mockito.Mockito

internal class MockApplicationContext : ApplicationContext {
    private val waitingRoomService = Mockito.mock(WaitingRoomService::class.java)

    private val waitingRoomDao = Mockito.mock(WaitingRoomDao::class.java)

    private val accountDao = Mockito.mock(AccountDao::class.java)

    private val accountService = Mockito.mock(AccountService::class.java)

    private val transactional = Mockito.mock(Transactional::class.java)

    private val database = Mockito.mock(Database::class.java)

    override fun waitingRoomServiceBean(): WaitingRoomService = waitingRoomService

    override fun waitingRoomDaoBean(): WaitingRoomDao = waitingRoomDao

    override fun accountDaoBean(): AccountDao = accountDao

    override fun accountServiceBean(): AccountService = accountService

    override fun transactionalBean(): Transactional = transactional

    override fun databaseBean(): Database = database

    override fun start() {
    }

    override fun stop() {
    }
}

internal fun <T> mock(mock: T, stubbing: KStubbing<T>.(T) -> Unit) {
    return KStubbing(mock).stubbing(mock)
}
