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
import java.time.OffsetDateTime
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

            val calculateBalance1 = calculateBalance(accountId1)
            assertThat(calculateBalance1.accountName).isEqualTo("Mr. Foo")
            assertThat(calculateBalance1.balance).isEqualTo(50.toBigDecimal())

            val calculateBalance2 = calculateBalance(accountId2)
            assertThat(calculateBalance2.accountName).isEqualTo("Mr. Bar")
            assertThat(calculateBalance2.balance).isEqualTo(40.toBigDecimal())

            val now = OffsetDateTime.now()

            val transactions1 = transactions(accountId1)
            assertThat(transactions1).hasSize(2)
            assertThat(transactions1[0].waitingRoomDateReceived).isAfter(now.minusDays(1))
            assertThat(transactions1[0].fromAccountName).isEqualTo("<internal top-up>")
            assertThat(transactions1[0].toAccountName).isEqualTo("Mr. Foo")
            assertThat(transactions1[0].dateTransacted).isAfter(now.minusDays(1))
            assertThat(transactions1[0].amount).isEqualTo(100.toBigDecimal())
            assertThat(transactions1[1].waitingRoomDateReceived).isAfter(now.minusDays(1))
            assertThat(transactions1[1].fromAccountName).isEqualTo("Mr. Foo")
            assertThat(transactions1[1].toAccountName).isEqualTo("Mr. Bar")
            assertThat(transactions1[1].dateTransacted).isAfter(now.minusDays(1))
            assertThat(transactions1[1].amount).isEqualTo(50.toBigDecimal())

            val transactions2 = transactions(accountId2)
            assertThat(transactions2).hasSize(2)
            assertThat(transactions2[0].waitingRoomDateReceived).isAfter(now.minusDays(1))
            assertThat(transactions2[0].fromAccountName).isEqualTo("Mr. Foo")
            assertThat(transactions2[0].toAccountName).isEqualTo("Mr. Bar")
            assertThat(transactions2[0].dateTransacted).isAfter(now.minusDays(1))
            assertThat(transactions2[0].amount).isEqualTo(50.toBigDecimal())
            assertThat(transactions2[1].waitingRoomDateReceived).isAfter(now.minusDays(1))
            assertThat(transactions2[1].fromAccountName).isEqualTo("Mr. Bar")
            assertThat(transactions2[1].toAccountName).isEqualTo("<internal withdrawal>")
            assertThat(transactions2[1].dateTransacted).isAfter(now.minusDays(1))
            assertThat(transactions2[1].amount).isEqualTo(10.toBigDecimal())
        }
    }

    @AfterAll
    fun tearDown() {
        applicationContext.stop()
        server.stop()
    }

    private fun calculateBalance(accountId: Int): AccountBalanceDto {
        return given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .get("/api/account/$accountId/calculated-balance")
            .then()
            .log().all()
            .statusCode(200)
            .extract()
            .body()
            .`as`(AccountBalanceDto::class.java)
    }

    private fun transactions(accountId: Int): List<TransactionDto> {
        return given()
            .log().all()
            .port(server.port)
            .`when`()
            .contentType(ContentType.JSON)
            .get("/api/account/$accountId/transactions")
            .then()
            .log().all()
            .statusCode(200)
            .extract()
            .body()
            .jsonPath()
            .getList(".", TransactionDto::class.java)
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

    data class TransactionDto @JsonCreator constructor(
        @JsonProperty("waitingRoomDateReceived") val waitingRoomDateReceived: OffsetDateTime,
        @JsonProperty("fromAccountName") val fromAccountName: String,
        @JsonProperty("toAccountName") val toAccountName: String,
        @JsonProperty("dateTransacted") val dateTransacted: OffsetDateTime,
        @JsonProperty("amount") val amount: BigDecimal
    )

    data class AccountBalanceDto @JsonCreator constructor(
        @JsonProperty("accountName") val accountName: String,
        @JsonProperty("balance") val balance: BigDecimal
    )
}
