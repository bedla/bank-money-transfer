package cz.bedla.bank.rest

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import cz.bedla.bank.domain.Account
import cz.bedla.bank.domain.AccountType
import cz.bedla.bank.domain.Transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import javax.servlet.ServletContext
import javax.ws.rs.*
import javax.ws.rs.core.Context


@Path("/account")
@Produces("application/json")
class AccountEndpoint(@Context override val servletContext: ServletContext) : Endpoint {
    @POST
    fun create(request: CreateAccount): AccountInfo {
        val account = applicationContext()
            .accountServiceBean()
            .createPersonalAccount(request.name)
        return AccountInfo(account.type.name, account.name, account.dateOpened, account.balance, account.id)
    }

    @GET
    @Path("/{id}")
    fun accountInfo(@PathParam("id") id: Int): AccountInfo {
        val account = findAccount(id)
        return AccountInfo(account.type.name, account.name, account.dateOpened, account.balance, account.id)
    }

    @GET
    @Path("/{id}/calculated-balance")
    fun calculateBalance(@PathParam("id") id: Int): AccountBalanceResponse {
        val account = findAccount(id)
        val balance = applicationContext()
            .transactionServiceBean()
            .calculateBalance(account)
        return AccountBalanceResponse(account.name, balance)
    }


    @GET
    @Path("/{id}/transactions")
    fun transactions(@PathParam("id") id: Int): List<TransactionResponse> {
        val account = findAccount(id)
        val list = applicationContext()
            .transactionServiceBean()
            .findAccountTransactions(account)
        return list.map { it.toTransactionResponse() }
    }

    private fun findAccount(id: Int) = applicationContext().accountServiceBean().findAccount(id)

    data class CreateAccount @JsonCreator constructor(
        @JsonProperty("name") val name: String
    )

    data class AccountInfo(
        val type: String,
        val name: String,
        val dateOpened: OffsetDateTime,
        val balance: BigDecimal,
        val id: Int
    )

    data class AccountBalanceResponse(val accountName: String, val balance: BigDecimal)

    data class TransactionResponse(
        val paymentOrderDateReceived: OffsetDateTime,
        val fromAccountName: String,
        val toAccountName: String,
        val amount: BigDecimal,
        val dateTransacted: OffsetDateTime
    )

    private fun Transaction.toTransactionResponse(): TransactionResponse {
        fun Account.safeName(): String = when (type) {
            AccountType.PERSONAL -> name
            AccountType.WITHDRAWAL -> "<internal withdrawal>"
            AccountType.TOP_UP -> "<internal top-up>"
        }

        return TransactionResponse(
            paymentOrder.dateCreated,
            fromAccount.safeName(),
            toAccount.safeName(),
            amount,
            dateTransacted
        )
    }
}

