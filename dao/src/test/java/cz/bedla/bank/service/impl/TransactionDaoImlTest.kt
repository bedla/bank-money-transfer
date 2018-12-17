package cz.bedla.bank.service.impl

import cz.bedla.bank.DatabaseImpl
import cz.bedla.bank.DbInitializer
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.WaitingRoom
import cz.bedla.bank.domain.WaitingRoomState
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.WaitingRoomDao
import cz.bedla.bank.tx.TransactionalImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.exception.DataAccessException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junitpioneer.jupiter.TempDirectory
import java.nio.file.Path
import java.time.OffsetDateTime

@ExtendWith(TempDirectory::class)
class TransactionDaoImlTest {
    private lateinit var fixture: TransactionDaoIml
    private lateinit var accountDao: AccountDao
    private lateinit var waitingRoomDao: WaitingRoomDao

    private lateinit var database: DatabaseImpl
    private lateinit var transactional: TransactionalImpl

    @BeforeEach
    fun setUp(@TempDirectory.TempDir tempDir: Path) {
        database = DatabaseImpl(tempDir.toFile())
        database.start()
        DbInitializer("database.sql", database.dataSource).run()
        transactional = TransactionalImpl(database.dataSource)

        accountDao = AccountDaoImpl()
        waitingRoomDao = WaitingRoomDaoImpl(accountDao)
        fixture = TransactionDaoIml(accountDao, waitingRoomDao)
    }

    @Test
    fun store() = transactional.run {
        val fromAccount = accountDao.createAccount(
            Account(AccountType.TOP_UP, "bank top-up", OffsetDateTime.now(), 999999.toBigDecimal())
        )
        val toAccount = accountDao.createAccount(
            Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal())
        )

        val waitingRoom = waitingRoomDao.create(
            WaitingRoom(fromAccount, toAccount, 5.toBigDecimal(), WaitingRoomState.RECEIVED, OffsetDateTime.now())
        )

        fixture.create(waitingRoom.id, fromAccount.id, toAccount.id, 100.toBigDecimal(), OffsetDateTime.now())

        val list = fixture.findAccountTransactions(fromAccount)
        assertThat(list).hasSize(1)
        assertThat(list[0].waitingRoom.id).isEqualTo(waitingRoom.id)
        assertThat(list[0].fromAccount.name).isEqualTo("bank top-up");
        assertThat(list[0].toAccount.name).isEqualTo("Mr. Foo");
        assertThat(list[0].amount).isEqualTo(100.toBigDecimal());
        assertThat(list[0].dateTransacted).isAfter(OffsetDateTime.now().minusDays(1));
    }

    @Test
    fun calculateBalance() = TransactionalImpl(database.dataSource).run {
        val mainAccount = accountDao.createAccount(
            Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal())
        )

        val account1 = accountDao.createAccount(
            Account(AccountType.TOP_UP, "Bank top-up", OffsetDateTime.now(), 1000.toBigDecimal())
        )
        val account2 = accountDao.createAccount(
            Account(AccountType.WITHDRAWAL, "Bank withdrawal", OffsetDateTime.now(), 1000.toBigDecimal())
        )

        val waitingRoom1 = createFakeWaitingRoom(account1)
        val waitingRoom2 = createFakeWaitingRoom(account1)
        val waitingRoom3 = createFakeWaitingRoom(account1)

        assertThat(fixture.calculateBalance(mainAccount)).isEqualTo(0.toBigDecimal())

        fixture.create(waitingRoom1.id, account1.id, mainAccount.id, 100.toBigDecimal(), OffsetDateTime.now())
        fixture.create(waitingRoom2.id, account1.id, mainAccount.id, 200.toBigDecimal(), OffsetDateTime.now())
        fixture.create(waitingRoom3.id, mainAccount.id, account2.id, 50.toBigDecimal(), OffsetDateTime.now())

        assertThat(fixture.calculateBalance(mainAccount)).isEqualTo(250.toBigDecimal())
    }

    @Test
    fun findAccountTransactions() = TransactionalImpl(database.dataSource).run {
        val mainAccount = accountDao.createAccount(
            Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal())
        )
        val anotherAccount = accountDao.createAccount(
            Account(AccountType.PERSONAL, "Mr. Another", OffsetDateTime.now(), 0.toBigDecimal())
        )

        val account1 = accountDao.createAccount(
            Account(AccountType.TOP_UP, "Bank top-up", OffsetDateTime.now(), 1000.toBigDecimal())
        )
        val account2 = accountDao.createAccount(
            Account(AccountType.WITHDRAWAL, "Bank withdrawal", OffsetDateTime.now(), 1000.toBigDecimal())
        )

        val waitingRoom1 = createFakeWaitingRoom(account1)
        val waitingRoom2 = createFakeWaitingRoom(account1)
        val waitingRoom3 = createFakeWaitingRoom(account1)
        val waitingRoom4 = createFakeWaitingRoom(account1)

        assertThat(fixture.calculateBalance(mainAccount)).isEqualTo(0.toBigDecimal())

        fixture.create(
            waitingRoom1.id, account1.id, mainAccount.id, 100.toBigDecimal(), OffsetDateTime.now().minusDays(3)
        )
        fixture.create(
            waitingRoom2.id, account1.id, mainAccount.id, 200.toBigDecimal(), OffsetDateTime.now().minusDays(2)
        )
        fixture.create(
            waitingRoom3.id, mainAccount.id, account2.id, 300.toBigDecimal(), OffsetDateTime.now().minusDays(1)
        )
        fixture.create(
            waitingRoom4.id, account1.id, anotherAccount.id, 123.toBigDecimal(), OffsetDateTime.now()
        )

        val list = fixture.findAccountTransactions(mainAccount)
        assertThat(list).hasSize(3)
        assertThat(list[0].fromAccount).isEqualTo(account1)
        assertThat(list[0].toAccount).isEqualTo(mainAccount)
        assertThat(list[0].amount).isEqualTo(100.toBigDecimal())
        assertThat(list[1].fromAccount).isEqualTo(account1)
        assertThat(list[1].toAccount).isEqualTo(mainAccount)
        assertThat(list[1].amount).isEqualTo(200.toBigDecimal())
        assertThat(list[2].fromAccount).isEqualTo(mainAccount)
        assertThat(list[2].toAccount).isEqualTo(account2)
        assertThat(list[2].amount).isEqualTo(300.toBigDecimal())
    }

    @Test
    fun duplicateWaitingRoom() {
        TransactionalImpl(database.dataSource).run {
            val account1 = accountDao.createAccount(
                Account(AccountType.TOP_UP, "Bank top-up", OffsetDateTime.now(), 1000.toBigDecimal())
            )
            val account2 = accountDao.createAccount(
                Account(AccountType.WITHDRAWAL, "Bank withdrawal", OffsetDateTime.now(), 1000.toBigDecimal())
            )

            val waitingRoom = createFakeWaitingRoom(account1)

            fixture.create(waitingRoom.id, account1.id, account2.id, 100.toBigDecimal(), OffsetDateTime.now())

            assertThatThrownBy {
                fixture.create(waitingRoom.id, account1.id, account2.id, 200.toBigDecimal(), OffsetDateTime.now())
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("Unique index or primary key violation")
        }
    }

    private fun createFakeWaitingRoom(account: Account): WaitingRoom = waitingRoomDao.create(
        WaitingRoom(account, account, 0.toBigDecimal(), WaitingRoomState.RECEIVED, OffsetDateTime.now())
    )

    @AfterEach
    fun tearDown() {
        database.close()
    }
}
