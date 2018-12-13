package cz.bedla.bank.rest

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
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
        val account = applicationContext()
            .accountServiceBean()
            .findAccount(id)
        return AccountInfo(account.type.name, account.name, account.dateOpened, account.balance, account.id)
    }

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
}

