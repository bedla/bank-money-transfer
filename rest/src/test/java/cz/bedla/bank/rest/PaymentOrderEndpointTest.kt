package cz.bedla.bank.rest

import com.nhaarman.mockitokotlin2.*
import cz.bedla.bank.RestServer
import cz.bedla.bank.context.ApplicationContext
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.PaymentOrder
import cz.bedla.bank.domain.PaymentOrderState
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class PaymentOrderEndpointTest {
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
        mock(applicationContext.paymentOrderServiceBean()) {
            on { receivePaymentRequest(eq(123), eq(456), eq(3.14.toBigDecimal())) } doReturn paymentOrder(111)
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .body(mapOf("fromAccountId" to 123, "toAccountId" to 456, "amount" to 3.14))
            .post("/api/payment-order/transfer")
            .then()
            .log().all()
            .statusCode(200)
            .body("paymentOrderId", equalTo(111))

        verify(applicationContext.paymentOrderServiceBean())
            .receivePaymentRequest(eq(123), eq(456), eq(3.14.toBigDecimal()))
        verifyNoMoreInteractions(applicationContext.paymentOrderServiceBean())
    }

    @Test
    fun topUp() {
        mock(applicationContext.paymentOrderServiceBean()) {
            on { topUpRequest(eq(123), eq(3.14.toBigDecimal())) } doReturn paymentOrder(111)
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .body(mapOf("accountId" to 123, "amount" to 3.14))
            .post("/api/payment-order/top-up")
            .then()
            .log().all()
            .statusCode(200)
            .body("paymentOrderId", equalTo(111))

        verify(applicationContext.paymentOrderServiceBean())
            .topUpRequest(eq(123), eq(3.14.toBigDecimal()))
        verifyNoMoreInteractions(applicationContext.paymentOrderServiceBean())
    }

    @Test
    fun withdrawal() {
        mock(applicationContext.paymentOrderServiceBean()) {
            on { withdrawalRequest(eq(123), eq(3.14.toBigDecimal())) } doReturn paymentOrder(111)
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .body(mapOf("accountId" to 123, "amount" to 3.14))
            .post("/api/payment-order/withdrawal")
            .then()
            .log().all()
            .statusCode(200)
            .body("paymentOrderId", equalTo(111))

        verify(applicationContext.paymentOrderServiceBean())
            .withdrawalRequest(eq(123), eq(3.14.toBigDecimal()))
        verifyNoMoreInteractions(applicationContext.paymentOrderServiceBean())
    }

    @Test
    fun state() {
        mock(applicationContext.paymentOrderServiceBean()) {
            on { paymentOrderState(eq(123)) } doReturn PaymentOrderState.NO_FUNDS
        }

        given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .get("/api/payment-order/123/state")
            .then()
            .log().all()
            .statusCode(200)
            .body("state", equalTo("NO_FUNDS"))

        verify(applicationContext.paymentOrderServiceBean())
            .paymentOrderState(eq(123))
        verifyNoMoreInteractions(applicationContext.paymentOrderServiceBean())
    }

    private fun paymentOrder(id: Int): PaymentOrder =
        PaymentOrder(
            account(222),
            account(333),
            999.toBigDecimal(),
            PaymentOrderState.RECEIVED,
            OffsetDateTime.now(),
            id,
            1
        )

    private fun account(id: Int): Account {
        return Account(AccountType.PERSONAL, "foo", OffsetDateTime.now(), 0.toBigDecimal(), id, 1)
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }
}
