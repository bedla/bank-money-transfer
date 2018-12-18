package cz.bedla.bank.service.impl

import cz.bedla.bank.DatabaseImpl
import cz.bedla.bank.DbInitializer
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.PaymentOrder
import cz.bedla.bank.domain.PaymentOrderState
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.PaymentOrderDao
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
    private lateinit var paymentOrderDao: PaymentOrderDao

    private lateinit var database: DatabaseImpl
    private lateinit var transactional: TransactionalImpl

    @BeforeEach
    fun setUp(@TempDirectory.TempDir tempDir: Path) {
        database = DatabaseImpl(tempDir.toFile())
        database.start()
        DbInitializer("database.sql", database.dataSource).run()
        transactional = TransactionalImpl(database.dataSource)

        accountDao = AccountDaoImpl()
        paymentOrderDao = PaymentOrderDaoImpl(accountDao)
        fixture = TransactionDaoIml(accountDao, paymentOrderDao)
    }

    @Test
    fun store() = transactional.run {
        val fromAccount = accountDao.create(
            Account(AccountType.TOP_UP, "bank top-up", OffsetDateTime.now(), 999999.toBigDecimal())
        )
        val toAccount = accountDao.create(
            Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal())
        )

        val paymentOrder = paymentOrderDao.create(
            PaymentOrder(fromAccount, toAccount, 5.toBigDecimal(), PaymentOrderState.RECEIVED, OffsetDateTime.now())
        )

        fixture.create(paymentOrder.id, fromAccount.id, toAccount.id, 100.toBigDecimal(), OffsetDateTime.now())

        val list = fixture.findAccountTransactions(fromAccount)
        assertThat(list).hasSize(1)
        assertThat(list[0].paymentOrder.id).isEqualTo(paymentOrder.id)
        assertThat(list[0].fromAccount.name).isEqualTo("bank top-up");
        assertThat(list[0].toAccount.name).isEqualTo("Mr. Foo");
        assertThat(list[0].amount).isEqualTo(100.toBigDecimal());
        assertThat(list[0].dateTransacted).isAfter(OffsetDateTime.now().minusDays(1));
    }

    @Test
    fun calculateBalance() = TransactionalImpl(database.dataSource).run {
        val mainAccount = accountDao.create(
            Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal())
        )

        val account1 = accountDao.create(
            Account(AccountType.TOP_UP, "Bank top-up", OffsetDateTime.now(), 1000.toBigDecimal())
        )
        val account2 = accountDao.create(
            Account(AccountType.WITHDRAWAL, "Bank withdrawal", OffsetDateTime.now(), 1000.toBigDecimal())
        )

        val paymentOrder1 = createFakePaymentOrder(account1)
        val paymentOrder2 = createFakePaymentOrder(account1)
        val paymentOrder3 = createFakePaymentOrder(account1)

        assertThat(fixture.calculateBalance(mainAccount)).isEqualTo(0.toBigDecimal())

        fixture.create(paymentOrder1.id, account1.id, mainAccount.id, 100.toBigDecimal(), OffsetDateTime.now())
        fixture.create(paymentOrder2.id, account1.id, mainAccount.id, 200.toBigDecimal(), OffsetDateTime.now())
        fixture.create(paymentOrder3.id, mainAccount.id, account2.id, 50.toBigDecimal(), OffsetDateTime.now())

        assertThat(fixture.calculateBalance(mainAccount)).isEqualTo(250.toBigDecimal())
    }

    @Test
    fun findAccountTransactions() = TransactionalImpl(database.dataSource).run {
        val mainAccount = accountDao.create(
            Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal())
        )
        val anotherAccount = accountDao.create(
            Account(AccountType.PERSONAL, "Mr. Another", OffsetDateTime.now(), 0.toBigDecimal())
        )

        val account1 = accountDao.create(
            Account(AccountType.TOP_UP, "Bank top-up", OffsetDateTime.now(), 1000.toBigDecimal())
        )
        val account2 = accountDao.create(
            Account(AccountType.WITHDRAWAL, "Bank withdrawal", OffsetDateTime.now(), 1000.toBigDecimal())
        )

        val paymentOrder1 = createFakePaymentOrder(account1)
        val paymentOrder2 = createFakePaymentOrder(account1)
        val paymentOrder3 = createFakePaymentOrder(account1)
        val paymentOrder4 = createFakePaymentOrder(account1)

        assertThat(fixture.calculateBalance(mainAccount)).isEqualTo(0.toBigDecimal())

        fixture.create(
            paymentOrder1.id, account1.id, mainAccount.id, 100.toBigDecimal(), OffsetDateTime.now().minusDays(3)
        )
        fixture.create(
            paymentOrder2.id, account1.id, mainAccount.id, 200.toBigDecimal(), OffsetDateTime.now().minusDays(2)
        )
        fixture.create(
            paymentOrder3.id, mainAccount.id, account2.id, 300.toBigDecimal(), OffsetDateTime.now().minusDays(1)
        )
        fixture.create(
            paymentOrder4.id, account1.id, anotherAccount.id, 123.toBigDecimal(), OffsetDateTime.now()
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
    fun duplicatePaymentOrder() {
        TransactionalImpl(database.dataSource).run {
            val account1 = accountDao.create(
                Account(AccountType.TOP_UP, "Bank top-up", OffsetDateTime.now(), 1000.toBigDecimal())
            )
            val account2 = accountDao.create(
                Account(AccountType.WITHDRAWAL, "Bank withdrawal", OffsetDateTime.now(), 1000.toBigDecimal())
            )

            val paymentOrder = createFakePaymentOrder(account1)

            fixture.create(paymentOrder.id, account1.id, account2.id, 100.toBigDecimal(), OffsetDateTime.now())

            assertThatThrownBy {
                fixture.create(paymentOrder.id, account1.id, account2.id, 200.toBigDecimal(), OffsetDateTime.now())
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("Unique index or primary key violation")
        }
    }

    private fun createFakePaymentOrder(account: Account): PaymentOrder = paymentOrderDao.create(
        PaymentOrder(account, account, 0.toBigDecimal(), PaymentOrderState.RECEIVED, OffsetDateTime.now())
    )

    @AfterEach
    fun tearDown() {
        database.close()
    }
}
