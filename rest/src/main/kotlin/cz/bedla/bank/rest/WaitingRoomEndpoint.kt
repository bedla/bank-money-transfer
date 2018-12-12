package cz.bedla.bank.rest

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import javax.servlet.ServletContext
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Context


@Path("/waiting-room")
class WaitingRoomEndpoint(@Context override val servletContext: ServletContext) : Endpoint {
    @POST
    @Produces("application/json")
    fun receivePaymentRequest(request: ReceivePaymentRequest): ReceivePaymentResponse {
        val waitingRoom = applicationContext().waitingRoomServiceBean()
            .receivePaymentRequest(request.fromAccountId, request.toAccountId, request.amount)
        return ReceivePaymentResponse(waitingRoom.id)
    }

    data class ReceivePaymentRequest @JsonCreator constructor(
        @JsonProperty("fromAccountId") val fromAccountId: Int,
        @JsonProperty("toAccountId") val toAccountId: Int,
        @JsonProperty("amount") val amount: BigDecimal
    )

    data class ReceivePaymentResponse(val waitingRoomId: Int)
}

