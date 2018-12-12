package cz.bedla.revolut.service.impl

import cz.bedla.revolut.Database
import cz.bedla.revolut.DbInitializer
import cz.bedla.revolut.service.AccountDao
import cz.bedla.revolut.domain.Account
import cz.bedla.revolut.domain.AccountType
import cz.bedla.revolut.domain.WaitingRoom
import cz.bedla.revolut.domain.WaitingRoomState
import cz.bedla.revolut.tx.TransactionalImpl
import org.assertj.core.api.Assertions.*
import org.jooq.exception.DataAccessException
import org.jooq.exception.DataChangedException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junitpioneer.jupiter.TempDirectory
import java.nio.file.Path
import java.time.OffsetDateTime

@ExtendWith(TempDirectory::class)
class WaitingRoomDaoImplTest {
    private lateinit var fixture: WaitingRoomDaoImpl
    private lateinit var accountDao: AccountDao

    private lateinit var database: Database

    @BeforeEach
    fun setUp(@TempDirectory.TempDir tempDir: Path) {
        database = Database(tempDir.toFile())
        database.start()
        DbInitializer("database.sql", database.dataSource).run()

        accountDao = AccountDaoImpl()
        fixture = WaitingRoomDaoImpl(accountDao)
    }

    @Test
    fun storeAndFind() {
        TransactionalImpl(database.dataSource).run {
            val waitingRoom = createWaitingRoom()
            assertThat(waitingRoom.id).isGreaterThan(0)
            assertThat(waitingRoom.version).isEqualTo(1)

            val found = fixture.findWaitingRoom(waitingRoom.id) ?: fail("not found")
            assertThat(found.id).isEqualTo(waitingRoom.id)
            assertThat(found.fromAccount.name).isEqualTo("bank top-up");
            assertThat(found.toAccount.name).isEqualTo("Mr. Foo");
            assertThat(found.amount).isEqualTo(100.toBigDecimal());
            assertThat(found.state).isEqualTo(WaitingRoomState.RECEIVED);
            assertThat(found.dateCreated).isAfter(OffsetDateTime.now().minusDays(1));
            assertThat(found.version).isEqualTo(1)
        }
    }

    @Test
    fun storeIncorrectRelation() {
        TransactionalImpl(database.dataSource).run {
            val invalidFrom = Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal())
            val invalidTo = Account(AccountType.PERSONAL, "Mr. Bar", OffsetDateTime.now(), 0.toBigDecimal())

            assertThatThrownBy {
                fixture.create(WaitingRoom(invalidFrom, invalidTo, 100.toBigDecimal(), WaitingRoomState.RECEIVED, OffsetDateTime.now()))
            }.isInstanceOf(DataAccessException::class.java)
                    .hasMessageContaining("Referential integrity constraint violation")
        }
    }

    @Test
    fun findItemsWithState() {
        TransactionalImpl(database.dataSource).run {
            val fromAccount = accountDao.createAccount(
                    Account(AccountType.TOP_UP, "bank top-up", OffsetDateTime.now(), 999999.toBigDecimal()))
            val toAccount = accountDao.createAccount(
                    Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal()))

            fixture.create(WaitingRoom(fromAccount, toAccount, 1.toBigDecimal(), WaitingRoomState.RECEIVED, OffsetDateTime.now().minusDays(2)))
            fixture.create(WaitingRoom(fromAccount, toAccount, 2.toBigDecimal(), WaitingRoomState.RECEIVED, OffsetDateTime.now().minusDays(1)))
            fixture.create(WaitingRoom(fromAccount, toAccount, 100.toBigDecimal(), WaitingRoomState.NO_FUNDS, OffsetDateTime.now()))

            val received = fixture.findItemsWithState(WaitingRoomState.RECEIVED)
            assertThat(received).hasSize(2)
            assertThat(received[0].amount).isEqualTo(1.toBigDecimal())
            assertThat(received[1].amount).isEqualTo(2.toBigDecimal())

            val noFunds = fixture.findItemsWithState(WaitingRoomState.NO_FUNDS)
            assertThat(noFunds).hasSize(1)
            assertThat(noFunds[0].amount).isEqualTo(100.toBigDecimal())

            val ok = fixture.findItemsWithState(WaitingRoomState.OK)
            assertThat(ok).isEmpty()
        }
    }

    @Test
    fun findItemsForAccount() {
        TransactionalImpl(database.dataSource).run {
            val account1 = accountDao.createAccount(
                    Account(AccountType.TOP_UP, "bank top-up", OffsetDateTime.now(), 999999.toBigDecimal()))
            val account2 = accountDao.createAccount(
                    Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal()))
            val account3 = accountDao.createAccount(
                    Account(AccountType.PERSONAL, "Mr. Bar", OffsetDateTime.now(), 0.toBigDecimal()))

            fixture.create(WaitingRoom(account1, account2, 1.toBigDecimal(), WaitingRoomState.RECEIVED, OffsetDateTime.now().minusDays(2)))
            fixture.create(WaitingRoom(account1, account3, 2.toBigDecimal(), WaitingRoomState.RECEIVED, OffsetDateTime.now().minusDays(1)))

            val list = fixture.findItemsForAccount(account1)
            assertThat(list).hasSize(2)
            assertThat(list[0].amount).isEqualTo(1.toBigDecimal())
            assertThat(list[1].amount).isEqualTo(2.toBigDecimal())
        }
    }

    @Test
    fun updateState() {
        TransactionalImpl(database.dataSource).run {
            val waitingRoom = createWaitingRoom()

            fixture.updateState(waitingRoom.copy(state = WaitingRoomState.NO_FUNDS))

            (fixture.findWaitingRoom(waitingRoom.id) ?: fail("not found")).also {
                assertThat(it.state).isEqualTo(WaitingRoomState.NO_FUNDS)
            }
        }
    }

    @Test
    fun updateStateInvalidOptimisticLock() {
        TransactionalImpl(database.dataSource).run {
            val waitingRoom = createWaitingRoom()

            assertThatThrownBy {
                fixture.updateState(waitingRoom.copy(state = WaitingRoomState.NO_FUNDS, version = 999999))
            }.isInstanceOf(DataChangedException::class.java)
                    .hasMessage("Database record has been changed or doesn't exist any longer")
        }
    }

    @Test
    fun delete() {
        TransactionalImpl(database.dataSource).run {
            val waitingRoom = createWaitingRoom()

            fixture.delete(waitingRoom)

            assertThat(fixture.findWaitingRoom(waitingRoom.id)).isNull()
            assertThat(accountDao.findAccounts()).hasSize(2)
        }
    }

    @Test
    fun deleteWithOptimisticLock() {
        TransactionalImpl(database.dataSource).run {
            val waitingRoom = createWaitingRoom()

            assertThatThrownBy {
                fixture.delete(waitingRoom.copy(version = 999))
            }.isInstanceOf(DataChangedException::class.java)
                    .hasMessage("Database record has been changed or doesn't exist any longer")
        }
    }

    @Test
    fun deleteNotFound() {
        TransactionalImpl(database.dataSource).run {
            val waitingRoom = createWaitingRoom()

            assertThatThrownBy {
                fixture.delete(waitingRoom.copy(id = 123))
            }.isInstanceOf(DataChangedException::class.java)
                    .hasMessage("Record id=123 not found")
        }
    }

    private fun createWaitingRoom(): WaitingRoom {
        val fromAccount = accountDao.createAccount(
                Account(AccountType.TOP_UP, "bank top-up", OffsetDateTime.now(), 999999.toBigDecimal()))
        val toAccount = accountDao.createAccount(
                Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal()))

        return fixture.create(WaitingRoom(fromAccount, toAccount, 100.toBigDecimal(), WaitingRoomState.RECEIVED, OffsetDateTime.now()))
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }
}


