package cz.bedla.bank.rest

import com.nhaarman.mockitokotlin2.*
import cz.bedla.bank.RestServer
import cz.bedla.bank.context.ApplicationContext
import cz.bedla.bank.domain.*
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AccountEndpointTest {
    private lateinit var server: RestServer
    private lateinit var applicationContext: ApplicationContext

    @BeforeEach
    fun setUp() {
        applicationContext = MockApplicationContext()

        val servletContextListener = ApplicationServletContextListener(applicationContext)
        server = RestServer(
            "localhost", 0, servletContextListener, RestApplication::class.java
        ).also { it.start() }
    }

    @Test
    fun create() {
        mock(applicationContext.accountServiceBean()) {
            on { createPersonalAccount(any()) } doAnswer {
                val name = it.getArgument(0) as String
                Account(AccountType.PERSONAL, name, OffsetDateTime.now()!!, 0.toBigDecimal(), 123)
            }
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .body(mapOf("name" to "Mr. Foo"))
            .post("/api/account")
            .then()
            .log().all()
            .statusCode(200)
            .body("name", equalTo("Mr. Foo"))

        verify(applicationContext.accountServiceBean()).createPersonalAccount(any())
        verifyNoMoreInteractions(applicationContext.accountServiceBean())
    }

    @Test
    fun accountInfo() {
        mock(applicationContext.accountServiceBean()) {
            on { findAccount(eq(123)) } doReturn Account(
                AccountType.PERSONAL, "Xxx", OffsetDateTime.now()!!, 0.toBigDecimal(), 123
            )
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .get("/api/account/123")
            .then()
            .log().all()
            .statusCode(200)
            .body("name", equalTo("Xxx"))

        verify(applicationContext.accountServiceBean()).findAccount(eq(123))
        verifyNoMoreInteractions(applicationContext.accountServiceBean())
    }

    @Test
    fun calculateBalance() {
        mock(applicationContext.accountServiceBean()) {
            on { findAccount(eq(123)) } doReturn Account(
                AccountType.PERSONAL, "Xxx", OffsetDateTime.now()!!, 0.toBigDecimal(), 123
            )
        }
        mock(applicationContext.transactionServiceBean()) {
            on { calculateBalance(any()) } doReturn 999.toBigDecimal()
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .get("/api/account/123/calculated-balance")
            .then()
            .log().all()
            .statusCode(200)
            .body(
                "accountName", equalTo("Xxx"),
                "balance", equalTo(999)
            )

        verify(applicationContext.accountServiceBean()).findAccount(eq(123))
        verifyNoMoreInteractions(applicationContext.accountServiceBean())
    }

    @Test
    fun transactions() {
        mock(applicationContext.accountServiceBean()) {
            on { findAccount(eq(123)) } doReturn Account(
                AccountType.PERSONAL, "Xxx", OffsetDateTime.now()!!, 0.toBigDecimal(), 123
            )
        }
        mock(applicationContext.transactionServiceBean()) {
            on { findAccountTransactions(any()) } doReturn createTransactions()
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .get("/api/account/123/transactions")
            .then()
            .log().all()
            .statusCode(200)
            .body(
                "[0].waitingRoomDateReceived", equalTo("2018-01-02T10:42:01+01:00"),
                "[0].fromAccountName", equalTo("<internal top-up>"),
                "[0].toAccountName", equalTo("Mr. Foo"),
                "[0].amount", equalTo(100),
                "[0].dateTransacted", equalTo("2018-01-02T11:42:01+01:00"),
                "[1].waitingRoomDateReceived", equalTo("2018-01-03T10:42:01+01:00"),
                "[1].fromAccountName", equalTo("Mr. Foo"),
                "[1].toAccountName", equalTo("Mr. Bar"),
                "[1].amount", equalTo(42),
                "[1].dateTransacted", equalTo("2018-01-03T12:42:01+01:00"),
                "[2].waitingRoomDateReceived", equalTo("2018-01-04T10:42:01+01:00"),
                "[2].fromAccountName", equalTo("Mr. Foo"),
                "[2].toAccountName", equalTo("<internal withdrawal>"),
                "[2].amount", equalTo(6),
                "[2].dateTransacted", equalTo("2018-01-04T13:42:01+01:00")
            )

        verify(applicationContext.accountServiceBean()).findAccount(eq(123))
        verifyNoMoreInteractions(applicationContext.accountServiceBean())
    }

    private fun createTransactions(): List<Transaction> {
        val now = OffsetDateTime.of(2018, 1, 1, 10, 42, 1, 0, ZoneOffset.ofHours(1))
        val mainAccount = Account(AccountType.PERSONAL, "Mr. Foo", now, 123.toBigDecimal())
        val account1 = Account(AccountType.PERSONAL, "Mr. Bar", now, 456.toBigDecimal())
        val topUpAccount = Account(AccountType.TOP_UP, "Bank top-up account", now, 789.toBigDecimal())
        val withdrawalAccount = Account(AccountType.WITHDRAWAL, "Bank withdrwaral account", now, 101112.toBigDecimal())

        val waitingRoom1 = WaitingRoom(
            topUpAccount, mainAccount, 100.toBigDecimal(), WaitingRoomState.OK, now.plusDays(1)
        )
        val waitingRoom2 = WaitingRoom(
            mainAccount, account1, 42.toBigDecimal(), WaitingRoomState.OK, now.plusDays(2)
        )
        val waitingRoom3 = WaitingRoom(
            mainAccount, withdrawalAccount, 6.toBigDecimal(), WaitingRoomState.OK, now.plusDays(3)
        )
        return listOf(
            Transaction(
                waitingRoom1,
                waitingRoom1.fromAccount,
                waitingRoom1.toAccount,
                waitingRoom1.amount,
                waitingRoom1.dateCreated.plusHours(1)
            ),
            Transaction(
                waitingRoom2,
                waitingRoom2.fromAccount,
                waitingRoom2.toAccount,
                waitingRoom2.amount,
                waitingRoom2.dateCreated.plusHours(2)
            ),
            Transaction(
                waitingRoom3,
                waitingRoom3.fromAccount,
                waitingRoom3.toAccount,
                waitingRoom3.amount,
                waitingRoom3.dateCreated.plusHours(3)
            )
        )
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }
}
