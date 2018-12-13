package cz.bedla.bank.it

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import cz.bedla.bank.RestServer
import cz.bedla.bank.context.ApplicationContext
import cz.bedla.bank.context.impl.ApplicationContextImpl
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.rest.ApplicationServletContextListener
import cz.bedla.bank.rest.RestApplication
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.hamcrest.Matchers
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junitpioneer.jupiter.TempDirectory
import java.math.BigDecimal
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(TempDirectory::class)
class IntegrationTest {
    private lateinit var applicationContext: ApplicationContext
    private lateinit var server: RestServer

    @BeforeAll
    fun setUp(@TempDirectory.TempDir tempDir: Path) {
        applicationContext = ApplicationContextImpl(
            tempDir.toFile(),
            coordinatorInitDelaySeconds = 1,
            coordinatorPeriodSeconds = 3
        )
        server = RestServer(
            "localhost",
            0,
            ApplicationServletContextListener(applicationContext),
            RestApplication::class.java
        ).also { it.start() }
    }

    @Test
    fun bankAccountsInitialized() {
        assertThat(applicationContext.accountServiceBean().findTopUpAccount().balance)
            .isGreaterThan(0.toBigDecimal())
        assertThat(applicationContext.accountServiceBean().findWithdrawalAccount().balance)
            .isGreaterThan(0.toBigDecimal())
    }

    @Nested
    inner class `End to End` {
        @Test
        fun `create 2 personal accounts, top-up first, transfer money, withdraw second and try no-funds scenario`() {
            val accountId1 = createAccount("Mr. Foo")
            val accountId2 = createAccount("Mr. Bar")

            assertThat(accountBalance(accountId1)).isEqualTo(0.toBigDecimal())
            assertThat(accountBalance(accountId2)).isEqualTo(0.toBigDecimal())

            val waitingRoomTopUpId = topUpAccount(accountId1, 100)
            awaitUntilWaitingRoomState(waitingRoomTopUpId, "OK")
            assertThat(accountBalance(accountId1)).isEqualTo(100.toBigDecimal())
            assertThat(accountBalance(accountId2)).isEqualTo(0.toBigDecimal())

            val waitingRoomTransferId = transferMoney(accountId1, accountId2, 50)
            awaitUntilWaitingRoomState(waitingRoomTransferId, "OK")
            assertThat(accountBalance(accountId1)).isEqualTo(50.toBigDecimal())
            assertThat(accountBalance(accountId2)).isEqualTo(50.toBigDecimal())

            val waitingRoomWithdrawalId = withdrawalAccount(accountId2, 10)
            awaitUntilWaitingRoomState(waitingRoomWithdrawalId, "OK")
            assertThat(accountBalance(accountId1)).isEqualTo(50.toBigDecimal())
            assertThat(accountBalance(accountId2)).isEqualTo(40.toBigDecimal())

            val waitingRoomNoFundsId = transferMoney(accountId2, accountId1, 999)
            awaitUntilWaitingRoomState(waitingRoomNoFundsId, "NO_FUNDS")
            assertThat(accountBalance(accountId1)).isEqualTo(50.toBigDecimal())
            assertThat(accountBalance(accountId2)).isEqualTo(40.toBigDecimal())

            val waitingRoomCannotWithdrawalId = withdrawalAccount(accountId2, 999)
            awaitUntilWaitingRoomState(waitingRoomCannotWithdrawalId, "NO_FUNDS")
            assertThat(accountBalance(accountId1)).isEqualTo(50.toBigDecimal())
            assertThat(accountBalance(accountId2)).isEqualTo(40.toBigDecimal())
        }
    }

    @AfterAll
    fun tearDown() {
        applicationContext.stop()
        server.stop()
    }

    private fun transferMoney(accountId1: Int, accountId2: Int, amount: Int): Int {
        return given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .body(mapOf("fromAccountId" to accountId1, "toAccountId" to accountId2, "amount" to amount))
            .post("/api/waiting-room/transfer")
            .then()
            .log().all()
            .statusCode(200)
            .extract()
            .body()
            .jsonPath().getInt("waitingRoomId")
    }

    private fun awaitUntilWaitingRoomState(waitingRoomId: Int, expectedState: String) {
        Awaitility.await().atMost(5L, TimeUnit.SECONDS).until {
            waitingRoomState(waitingRoomId) == expectedState
        }
    }

    private fun waitingRoomState(waitingRoomId: Int): String {
        return given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .get("/api/waiting-room/$waitingRoomId/state")
            .then()
            .log().all()
            .statusCode(200)
            .extract()
            .body()
            .jsonPath()
            .getString("state")
    }

    private fun topUpAccount(accountId: Int, amount: Int): Int {
        return given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .body(mapOf("accountId" to accountId, "amount" to amount))
            .post("/api/waiting-room/top-up")
            .then()
            .log().all()
            .statusCode(200)
            .extract()
            .body()
            .jsonPath()
            .getInt("waitingRoomId")
    }

    private fun withdrawalAccount(accountId: Int, amount: Int): Int {
        return given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .body(mapOf("accountId" to accountId, "amount" to amount))
            .post("/api/waiting-room/withdrawal")
            .then()
            .log().all()
            .statusCode(200)
            .extract()
            .body()
            .jsonPath()
            .getInt("waitingRoomId")
    }

    private fun createAccount(name: String): Int {
        return given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .body(mapOf("name" to name))
            .post("/api/account")
            .then()
            .log().all()
            .statusCode(200)
            .body("name", Matchers.equalTo(name))
            .extract()
            .body()
            .jsonPath().getInt("id")
    }

    private fun accountBalance(id: Int): BigDecimal = findAccount(id).balance

    private fun findAccount(id: Int): AccountDto {
        return given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .get("/api/account/$id")
            .then()
            .log().all()
            .statusCode(200)
            .extract()
            .body()
            .`as`(AccountDto::class.java)
    }

    data class AccountDto @JsonCreator constructor(
        @JsonProperty("type") val type: AccountType,
        @JsonProperty("name") val name: String,
        @JsonProperty("dateOpened") val dateOpened: String,
        @JsonProperty("balance") val balance: BigDecimal,
        @JsonProperty("id") val id: Int,
        @JsonProperty("version") val version: Int
    )
}
