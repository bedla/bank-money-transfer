package cz.bedla.bank.rest

import com.nhaarman.mockitokotlin2.*
import cz.bedla.bank.RestServer
import cz.bedla.bank.context.ApplicationContext
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.WaitingRoom
import cz.bedla.bank.domain.WaitingRoomState
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class WaitingRoomEndpointTest {
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
    fun receivePaymentRequest() {
        mock(applicationContext.waitingRoomServiceBean()) {
            on { receivePaymentRequest(eq(123), eq(456), eq(3.14.toBigDecimal())) } doReturn waitingRoom(111)
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .body(mapOf("fromAccountId" to 123, "toAccountId" to 456, "amount" to 3.14))
            .post("/api/waiting-room/transfer")
            .then()
            .log().all()
            .statusCode(200)
            .body("waitingRoomId", equalTo(111))

        verify(applicationContext.waitingRoomServiceBean())
            .receivePaymentRequest(eq(123), eq(456), eq(3.14.toBigDecimal()))
        verifyNoMoreInteractions(applicationContext.waitingRoomServiceBean())
    }

    @Test
    fun topUp() {
        mock(applicationContext.waitingRoomServiceBean()) {
            on { topUpRequest(eq(123), eq(3.14.toBigDecimal())) } doReturn waitingRoom(111)
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .body(mapOf("accountId" to 123, "amount" to 3.14))
            .post("/api/waiting-room/top-up")
            .then()
            .log().all()
            .statusCode(200)
            .body("waitingRoomId", equalTo(111))

        verify(applicationContext.waitingRoomServiceBean())
            .topUpRequest(eq(123), eq(3.14.toBigDecimal()))
        verifyNoMoreInteractions(applicationContext.waitingRoomServiceBean())
    }

    @Test
    fun withdrawal() {
        mock(applicationContext.waitingRoomServiceBean()) {
            on { withdrawalRequest(eq(123), eq(3.14.toBigDecimal())) } doReturn waitingRoom(111)
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .body(mapOf("accountId" to 123, "amount" to 3.14))
            .post("/api/waiting-room/withdrawal")
            .then()
            .log().all()
            .statusCode(200)
            .body("waitingRoomId", equalTo(111))

        verify(applicationContext.waitingRoomServiceBean())
            .withdrawalRequest(eq(123), eq(3.14.toBigDecimal()))
        verifyNoMoreInteractions(applicationContext.waitingRoomServiceBean())
    }

    @Test
    fun state() {
        mock(applicationContext.waitingRoomServiceBean()) {
            on { waitingRoomState(eq(123)) } doReturn WaitingRoomState.NO_FUNDS
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .get("/api/waiting-room/123/state")
            .then()
            .log().all()
            .statusCode(200)
            .body("state", equalTo("NO_FUNDS"))

        verify(applicationContext.waitingRoomServiceBean())
            .waitingRoomState(eq(123))
        verifyNoMoreInteractions(applicationContext.waitingRoomServiceBean())
    }

    private fun <T> mock(mock: T, stubbing: KStubbing<T>.(T) -> Unit) {
        return KStubbing(mock).stubbing(mock)
    }

    private fun waitingRoom(id: Int): WaitingRoom =
        WaitingRoom(
            account(222),
            account(333),
            999.toBigDecimal(),
            WaitingRoomState.RECEIVED,
            OffsetDateTime.now(),
            id,
            1
        )

    private fun account(id: Int): Account {
        return Account(AccountType.PERSONAL, "foo", OffsetDateTime.now(), 0.toBigDecimal(), id, 1)
    }

    @AfterEach
    internal fun tearDown() {
        server.stop()
    }
}
