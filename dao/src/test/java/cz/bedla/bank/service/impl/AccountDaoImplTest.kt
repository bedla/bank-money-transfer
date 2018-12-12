package cz.bedla.bank.service.impl

import cz.bedla.bank.DatabaseImpl
import cz.bedla.bank.DbInitializer
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.tx.TransactionalImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junitpioneer.jupiter.TempDirectory
import java.nio.file.Path
import java.time.OffsetDateTime

@ExtendWith(TempDirectory::class)
class AccountDaoImplTest {
    private lateinit var fixture: AccountDaoImpl

    private lateinit var database: DatabaseImpl

    @BeforeEach
    fun setUp(@TempDirectory.TempDir tempDir: Path) {
        database = DatabaseImpl(tempDir.toFile())
        database.start()
        DbInitializer("database.sql", database.dataSource).run()

        fixture = AccountDaoImpl()
    }

    @Test
    fun storeAndFetch() {
        TransactionalImpl(database.dataSource).run {
            val account = fixture.createAccount(
                Account(AccountType.PERSONAL, "foo", OffsetDateTime.now(), 123.4.toBigDecimal())
            )
            assertThat(account.id).isGreaterThan(0)
            assertThat(account.version).isEqualTo(1)

            val found = fixture.findAccount(account.id) ?: fail("not found")
            assertThat(found.id).isEqualTo(account.id)
            assertThat(found.type).isEqualTo(AccountType.PERSONAL)
            assertThat(found.name).isEqualTo("foo")
            assertThat(found.balance).isEqualTo(123.4.toBigDecimal())
            assertThat(found.version).isEqualTo(1)
        }
    }

    @Test
    fun updateBalance() {
        TransactionalImpl(database.dataSource).run {
            val account = fixture.createAccount(
                Account(AccountType.PERSONAL, "lock", OffsetDateTime.now(), 123.4.toBigDecimal())
            )

            fixture.updateBalance(account.copy(balance = 999.toBigDecimal()))

            val updated = fixture.findAccount(account.id) ?: fail("account not found")
            assertThat(updated.version).isEqualTo(2)
            assertThat(updated.balance).isEqualTo(999.toBigDecimal())
        }
    }

    @Test
    fun findAll() {
        TransactionalImpl(database.dataSource).run {
            fixture.createAccount(
                Account(AccountType.PERSONAL, "Afoo", OffsetDateTime.now(), 123.toBigDecimal())
            )
            fixture.createAccount(
                Account(AccountType.TOP_UP, "Bbar", OffsetDateTime.now(), 456.toBigDecimal())
            )

            val list = fixture.findAccounts()
            assertThat(list).hasSize(2)
            assertThat(list[0].id).isNotEqualTo(0)
            assertThat(list[0].type).isEqualTo(AccountType.PERSONAL)
            assertThat(list[0].name).isEqualTo("Afoo")
            assertThat(list[0].balance).isEqualTo(123.toBigDecimal())
            assertThat(list[1].id).isNotEqualTo(0)
            assertThat(list[1].type).isEqualTo(AccountType.TOP_UP)
            assertThat(list[1].name).isEqualTo("Bbar")
            assertThat(list[1].balance).isEqualTo(456.toBigDecimal())
        }
    }

    @Test
    fun findByType() {
        TransactionalImpl(database.dataSource).run {
            fixture.createAccount(
                Account(AccountType.PERSONAL, "Afoo", OffsetDateTime.now(), 123.toBigDecimal())
            )
            fixture.createAccount(
                Account(AccountType.TOP_UP, "Bbar", OffsetDateTime.now(), 456.toBigDecimal())
            )
            fixture.createAccount(
                Account(AccountType.TOP_UP, "Cbar", OffsetDateTime.now(), 789.toBigDecimal())
            )

            val personal = fixture.findAccountsOfType(AccountType.PERSONAL)
            val topUp = fixture.findAccountsOfType(AccountType.TOP_UP)
            val withdrawal = fixture.findAccountsOfType(AccountType.WITHDRAWAL)
            assertThat(personal).hasSize(1)
            assertThat(topUp).hasSize(2)
            assertThat(topUp[0].name).isEqualTo("Bbar")
            assertThat(topUp[1].name).isEqualTo("Cbar")
            assertThat(withdrawal).isEmpty()
        }
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }
}

