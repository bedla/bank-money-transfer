package cz.bedla.revolut.dao.impl

import cz.bedla.revolut.Database
import cz.bedla.revolut.DbInitializer
import cz.bedla.revolut.dao.AccountDao
import cz.bedla.revolut.domain.Account
import cz.bedla.revolut.domain.AccountType
import cz.bedla.revolut.domain.Transaction
import cz.bedla.revolut.tx.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
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

    private lateinit var database: Database

    @BeforeEach
    fun setUp(@TempDirectory.TempDir tempDir: Path) {
        database = Database(tempDir.toFile())
        database.start()
        DbInitializer("database.sql", database.dataSource).run()

        accountDao = AccountDaoImpl()
        fixture = TransactionDaoIml(accountDao)
    }

    @Test
    fun store() {
        Transactional(database.dataSource).run {
            val fromAccount = accountDao.createAccount(
                    Account(AccountType.TOP_UP, "bank top-up", OffsetDateTime.now(), 999999.toBigDecimal()))
            val toAccount = accountDao.createAccount(
                    Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal()))

            val transaction = fixture.create(Transaction(fromAccount, toAccount, 100.toBigDecimal(), OffsetDateTime.now()))
            assertThat(transaction.id).isGreaterThan(0)

            val list = fixture.findAccountTransactions(fromAccount) ?: fail("not found")
            assertThat(list).hasSize(1)
            assertThat(list[0].id).isEqualTo(transaction.id)
            assertThat(list[0].fromAccount.name).isEqualTo("bank top-up");
            assertThat(list[0].toAccount.name).isEqualTo("Mr. Foo");
            assertThat(list[0].amount).isEqualTo(100.toBigDecimal());
            assertThat(list[0].dateTransacted).isAfter(OffsetDateTime.now().minusDays(1));
        }
    }

    @Test
    fun calculateBalance() {
        Transactional(database.dataSource).run {
            val mainAccount = accountDao.createAccount(
                    Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal()))

            val account1 = accountDao.createAccount(
                    Account(AccountType.TOP_UP, "Bank top-up", OffsetDateTime.now(), 1000.toBigDecimal()))
            val account2 = accountDao.createAccount(
                    Account(AccountType.WITHDRAWAL, "Bank withdrawal", OffsetDateTime.now(), 1000.toBigDecimal()))

            assertThat(fixture.calculateBalance(mainAccount)).isEqualTo(0.toBigDecimal())

            fixture.create(Transaction(account1, mainAccount, 100.toBigDecimal(), OffsetDateTime.now()))
            fixture.create(Transaction(account1, mainAccount, 200.toBigDecimal(), OffsetDateTime.now()))
            fixture.create(Transaction(mainAccount, account2, 50.toBigDecimal(), OffsetDateTime.now()))

            assertThat(fixture.calculateBalance(mainAccount)).isEqualTo(250.toBigDecimal())
        }
    }

    @Test
    fun findAccountTransactions() {
        Transactional(database.dataSource).run {
            val mainAccount = accountDao.createAccount(
                    Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal()))
            val anotherAccount = accountDao.createAccount(
                    Account(AccountType.PERSONAL, "Mr. Another", OffsetDateTime.now(), 0.toBigDecimal()))

            val account1 = accountDao.createAccount(
                    Account(AccountType.TOP_UP, "Bank top-up", OffsetDateTime.now(), 1000.toBigDecimal()))
            val account2 = accountDao.createAccount(
                    Account(AccountType.WITHDRAWAL, "Bank withdrawal", OffsetDateTime.now(), 1000.toBigDecimal()))

            assertThat(fixture.calculateBalance(mainAccount)).isEqualTo(0.toBigDecimal())

            fixture.create(Transaction(account1, mainAccount, 100.toBigDecimal(), OffsetDateTime.now().minusDays(3)))
            fixture.create(Transaction(account1, mainAccount, 200.toBigDecimal(), OffsetDateTime.now().minusDays(2)))
            fixture.create(Transaction(mainAccount, account2, 300.toBigDecimal(), OffsetDateTime.now().minusDays(1)))
            fixture.create(Transaction(account1, anotherAccount, 123.toBigDecimal(), OffsetDateTime.now()))

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
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }
}
