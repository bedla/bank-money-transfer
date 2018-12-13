package cz.bedla.bank.rest

import com.nhaarman.mockitokotlin2.*
import cz.bedla.bank.RestServer
import cz.bedla.bank.context.ApplicationContext
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

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

        RestAssured.given()
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

        RestAssured.given()
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

    @AfterEach
    fun tearDown() {
        server.stop()
    }
}
