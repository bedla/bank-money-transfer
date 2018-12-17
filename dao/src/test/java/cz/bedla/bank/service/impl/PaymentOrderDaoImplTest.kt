package cz.bedla.bank.service.impl

import cz.bedla.bank.DatabaseImpl
import cz.bedla.bank.DbInitializer
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.PaymentOrder
import cz.bedla.bank.domain.PaymentOrderState
import cz.bedla.bank.tx.TransactionalImpl
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
class PaymentOrderDaoImplTest {
    private lateinit var fixture: PaymentOrderDaoImpl
    private lateinit var accountDao: AccountDao

    private lateinit var database: DatabaseImpl

    @BeforeEach
    fun setUp(@TempDirectory.TempDir tempDir: Path) {
        database = DatabaseImpl(tempDir.toFile())
        database.start()
        DbInitializer("database.sql", database.dataSource).run()

        accountDao = AccountDaoImpl()
        fixture = PaymentOrderDaoImpl(accountDao)
    }

    @Test
    fun storeAndFind() {
        TransactionalImpl(database.dataSource).run {
            val paymentOrder = createPaymentOrder()
            assertThat(paymentOrder.id).isGreaterThan(0)
            assertThat(paymentOrder.version).isEqualTo(1)

            val found = fixture.findPaymentOrder(paymentOrder.id) ?: fail("not found")
            assertThat(found.id).isEqualTo(paymentOrder.id)
            assertThat(found.fromAccount.name).isEqualTo("bank top-up");
            assertThat(found.toAccount.name).isEqualTo("Mr. Foo");
            assertThat(found.amount).isEqualTo(100.toBigDecimal());
            assertThat(found.state).isEqualTo(PaymentOrderState.RECEIVED);
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
                fixture.create(PaymentOrder(invalidFrom, invalidTo, 100.toBigDecimal(), PaymentOrderState.RECEIVED, OffsetDateTime.now()))
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

            fixture.create(PaymentOrder(fromAccount, toAccount, 1.toBigDecimal(), PaymentOrderState.RECEIVED, OffsetDateTime.now().minusDays(2)))
            fixture.create(PaymentOrder(fromAccount, toAccount, 2.toBigDecimal(), PaymentOrderState.RECEIVED, OffsetDateTime.now().minusDays(1)))
            fixture.create(PaymentOrder(fromAccount, toAccount, 100.toBigDecimal(), PaymentOrderState.NO_FUNDS, OffsetDateTime.now()))

            val received = fixture.findItemsWithState(PaymentOrderState.RECEIVED)
            assertThat(received).hasSize(2)
            assertThat(received[0].amount).isEqualTo(1.toBigDecimal())
            assertThat(received[1].amount).isEqualTo(2.toBigDecimal())

            val noFunds = fixture.findItemsWithState(PaymentOrderState.NO_FUNDS)
            assertThat(noFunds).hasSize(1)
            assertThat(noFunds[0].amount).isEqualTo(100.toBigDecimal())

            val ok = fixture.findItemsWithState(PaymentOrderState.OK)
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

            fixture.create(PaymentOrder(account1, account2, 1.toBigDecimal(), PaymentOrderState.RECEIVED, OffsetDateTime.now().minusDays(2)))
            fixture.create(PaymentOrder(account1, account3, 2.toBigDecimal(), PaymentOrderState.RECEIVED, OffsetDateTime.now().minusDays(1)))

            val list = fixture.findItemsForAccount(account1)
            assertThat(list).hasSize(2)
            assertThat(list[0].amount).isEqualTo(1.toBigDecimal())
            assertThat(list[1].amount).isEqualTo(2.toBigDecimal())
        }
    }

    @Test
    fun updateState() {
        TransactionalImpl(database.dataSource).run {
            val paymentOrder = createPaymentOrder()

            fixture.updateState(paymentOrder.copy(state = PaymentOrderState.NO_FUNDS))

            (fixture.findPaymentOrder(paymentOrder.id) ?: fail("not found")).also {
                assertThat(it.state).isEqualTo(PaymentOrderState.NO_FUNDS)
            }
        }
    }

    @Test
    fun updateStateInvalidOptimisticLock() {
        TransactionalImpl(database.dataSource).run {
            val paymentOrder = createPaymentOrder()

            assertThatThrownBy {
                fixture.updateState(paymentOrder.copy(state = PaymentOrderState.NO_FUNDS, version = 999999))
            }.isInstanceOf(DataChangedException::class.java)
                    .hasMessage("Database record has been changed or doesn't exist any longer")
        }
    }

    @Test
    fun delete() {
        TransactionalImpl(database.dataSource).run {
            val paymentOrder = createPaymentOrder()

            fixture.delete(paymentOrder)

            assertThat(fixture.findPaymentOrder(paymentOrder.id)).isNull()
            assertThat(accountDao.findAccounts()).hasSize(2)
        }
    }

    @Test
    fun deleteWithOptimisticLock() {
        TransactionalImpl(database.dataSource).run {
            val paymentOrder = createPaymentOrder()

            assertThatThrownBy {
                fixture.delete(paymentOrder.copy(version = 999))
            }.isInstanceOf(DataChangedException::class.java)
                    .hasMessage("Database record has been changed or doesn't exist any longer")
        }
    }

    @Test
    fun deleteNotFound() {
        TransactionalImpl(database.dataSource).run {
            val paymentOrder = createPaymentOrder()

            assertThatThrownBy {
                fixture.delete(paymentOrder.copy(id = 123))
            }.isInstanceOf(DataChangedException::class.java)
                    .hasMessage("Record id=123 not found")
        }
    }

    private fun createPaymentOrder(): PaymentOrder {
        val fromAccount = accountDao.createAccount(
                Account(AccountType.TOP_UP, "bank top-up", OffsetDateTime.now(), 999999.toBigDecimal()))
        val toAccount = accountDao.createAccount(
                Account(AccountType.PERSONAL, "Mr. Foo", OffsetDateTime.now(), 0.toBigDecimal()))

        return fixture.create(PaymentOrder(fromAccount, toAccount, 100.toBigDecimal(), PaymentOrderState.RECEIVED, OffsetDateTime.now()))
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }
}


