package cz.bedla.bank.service.impl

import cz.bedla.bank.Database
import cz.bedla.bank.DatabaseImpl
import cz.bedla.bank.DbInitializer
import cz.bedla.bank.domain.*
import cz.bedla.bank.service.AccountDao
import cz.bedla.bank.service.PaymentOrderDao
import cz.bedla.bank.service.TransactionDao
import cz.bedla.bank.service.Transactor
import cz.bedla.bank.tx.Transactional
import cz.bedla.bank.tx.TransactionalImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.exception.DataChangedException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junitpioneer.jupiter.TempDirectory
import java.nio.file.Path
import java.time.OffsetDateTime

@ExtendWith(TempDirectory::class)
class TransactorImplTest {
    private lateinit var fixture: TransactorImpl

    private lateinit var database: Database
    private lateinit var accountDao: AccountDao
    private lateinit var paymentOrderDao: PaymentOrderDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var transactional: Transactional

    @BeforeEach
    fun setUp(@TempDirectory.TempDir tempDir: Path) {
        database = DatabaseImpl(tempDir.toFile())
        database.start()
        DbInitializer("database.sql", database.dataSource).run()

        transactional = TransactionalImpl(database.dataSource)
        accountDao = AccountDaoImpl()
        paymentOrderDao = PaymentOrderDaoImpl(accountDao)
        transactionDao = TransactionDaoIml(accountDao, paymentOrderDao)
        fixture = TransactorImpl(transactionDao, paymentOrderDao, accountDao, transactional)
        fixture.start()
    }

    @Test
    fun doNotTransferWhenDbStateChanged() {
        val account1 = createTopUpAccount()
        val account2 = createPersonalAccount("Mr. Bar", 1000)

        val paymentOrder: PaymentOrder = transactional.execute {
            paymentOrderDao.create(
                paymentOrder(
                    fromAccount = account1,
                    toAccount = account2,
                    state = PaymentOrderState.RECEIVED,
                    amount = 999
                )
            )
        }

        transactional.run { accountDao.updateBalance(account1.copy(balance = 123.toBigDecimal())) }

        assertThatThrownBy { fixture.process(paymentOrder) }
            .isInstanceOf(DataChangedException::class.java)
            .hasMessage("Database record has been changed or doesn't exist any longer")

        assertNoPaymentOccurred(account1, account2, paymentOrder, PaymentOrderState.RECEIVED, 123)
    }

    @Test
    fun transferFromTopUpAccountWithoutFunds() {
        val account1 = createTopUpAccount()
        val account2 = createPersonalAccount("Mr. Bar", 1000)

        val paymentOrder: PaymentOrder = transactional.execute {
            paymentOrderDao.create(
                paymentOrder(
                    fromAccount = account1,
                    toAccount = account2,
                    state = PaymentOrderState.RECEIVED,
                    amount = 999
                )
            )
        }
        val result = fixture.process(paymentOrder)
        assertThat(result).isEqualTo(Transactor.ResultState.MONEY_SENT)

        val transaction = findTransaction(account1, paymentOrder.id)
        assertThat(transaction.paymentOrder.state).isEqualTo(PaymentOrderState.OK)
        assertThat(transaction.fromAccount.name).isEqualTo("Top-up")
        assertThat(transaction.fromAccount.balance).isEqualTo((-999).toBigDecimal())
        assertThat(transaction.toAccount.balance).isEqualTo(1999.toBigDecimal())
        assertThat(transaction.toAccount.name).isEqualTo("Mr. Bar")
        assertThat(transaction.amount).isEqualTo(999.toBigDecimal())
    }

    @Test
    fun transferFromPersonalAccount() {
        val account1 = createPersonalAccount("Mr. Foo", 1000)
        val account2 = createPersonalAccount("Mr. Bar", 1000)

        val paymentOrder: PaymentOrder = transactional.execute {
            paymentOrderDao.create(
                paymentOrder(
                    fromAccount = account1,
                    toAccount = account2,
                    state = PaymentOrderState.RECEIVED,
                    amount = 50
                )
            )
        }
        val result = fixture.process(paymentOrder)
        assertThat(result).isEqualTo(Transactor.ResultState.MONEY_SENT)

        val transaction = findTransaction(account1, paymentOrder.id)
        assertThat(transaction.paymentOrder.state).isEqualTo(PaymentOrderState.OK)
        assertThat(transaction.fromAccount.name).isEqualTo("Mr. Foo")
        assertThat(transaction.fromAccount.balance).isEqualTo(950.toBigDecimal())
        assertThat(transaction.toAccount.balance).isEqualTo(1050.toBigDecimal())
        assertThat(transaction.toAccount.name).isEqualTo("Mr. Bar")
        assertThat(transaction.amount).isEqualTo(50.toBigDecimal())
    }

    @Test
    fun personalAccountWithoutFunds() {
        val account1 = createPersonalAccount("Mr. Foo", 1000)
        val account2 = createPersonalAccount("Mr. Bar", 1000)

        val paymentOrder: PaymentOrder = transactional.execute {
            paymentOrderDao.create(
                paymentOrder(
                    state = PaymentOrderState.RECEIVED,
                    amount = 9999,
                    fromAccount = account1,
                    toAccount = account2
                )
            )
        }
        val result = fixture.process(paymentOrder)
        assertThat(result).isEqualTo(Transactor.ResultState.NO_FUNDS)

        assertNoPaymentOccurred(account1, account2, paymentOrder, PaymentOrderState.NO_FUNDS)
    }

    @Test
    fun paymentOrderInInvalidState() {
        val account1 = createPersonalAccount("Mr. Foo", 1000)
        val account2 = createPersonalAccount("Mr. Bar", 1000)

        val paymentOrder: PaymentOrder = transactional.execute {
            paymentOrderDao.create(
                paymentOrder(
                    fromAccount = account1,
                    toAccount = account2,
                    state = PaymentOrderState.OK
                )
            )
        }
        val result = fixture.process(paymentOrder)
        assertThat(result).isEqualTo(Transactor.ResultState.INVALID_STATE)

        assertNoPaymentOccurred(account1, account2, paymentOrder, PaymentOrderState.OK)
    }

    @Test
    fun paymentOrderNotFound() {
        assertThatThrownBy {
            fixture.process(
                paymentOrder(
                    id = 99999, fromAccount = account("Mr. X"), toAccount = account("Mr. Y")
                )
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Unable to find paymentOrder.id=99999")
    }

    @Test
    fun notRunning() {
        fixture.stop()
        val result = fixture.process(
            paymentOrder(
                id = 99999, fromAccount = account("A"), toAccount = account("B")
            )
        )
        assertThat(result).isEqualTo(Transactor.ResultState.STOPPED)
    }

    private fun paymentOrder(
        id: Int = 0,
        fromAccount: Account,
        toAccount: Account,
        state: PaymentOrderState = PaymentOrderState.OK,
        amount: Int = 100
    ): PaymentOrder = PaymentOrder(fromAccount, toAccount, amount.toBigDecimal(), state, OffsetDateTime.now(), id)

    private fun account(name: String) =
        Account(AccountType.PERSONAL, name, OffsetDateTime.now(), 1000.toBigDecimal())

    private fun createPersonalAccount(name: String, balance: Int): Account = transactional.execute {
        accountDao.create(Account(AccountType.PERSONAL, name, OffsetDateTime.now(), balance.toBigDecimal()))
    }

    private fun createTopUpAccount(): Account = transactional.execute {
        accountDao.create(Account(AccountType.TOP_UP, "Top-up", OffsetDateTime.now(), 0.toBigDecimal()))
    }

    private fun findTransaction(account: Account, paymentOrderId: Int): Transaction = transactional.execute {
        transactionDao.findAccountTransactions(account).first { it.paymentOrder.id == paymentOrderId }
    }

    private fun assertNoPaymentOccurred(
        account1: Account,
        account2: Account,
        paymentOrder: PaymentOrder,
        expectedState: PaymentOrderState,
        expectedAccount1Balance: Int = 1000
    ) {
        transactional.run {
            assertThat(transactionDao.findAccountTransactions(account1)).isEmpty()
            assertThat(transactionDao.findAccountTransactions(account2)).isEmpty()
            assertThat(paymentOrderDao.findPaymentOrder(paymentOrder.id)?.state).isEqualTo(expectedState)
            assertThat(accountDao.findAccount(account1.id)?.balance).isEqualTo(expectedAccount1Balance.toBigDecimal())
            assertThat(accountDao.findAccount(account2.id)?.balance).isEqualTo(1000.toBigDecimal())
        }
    }

    @AfterEach
    fun tearDown() {
        database.stop()
    }
}
