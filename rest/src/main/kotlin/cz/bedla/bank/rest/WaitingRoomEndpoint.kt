package cz.bedla.bank.rest

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import javax.servlet.ServletContext
import javax.ws.rs.*
import javax.ws.rs.core.Context


@Path("/waiting-room")
@Produces("application/json")
class WaitingRoomEndpoint(@Context override val servletContext: ServletContext) : Endpoint {
    @POST
    @Path("/transfer")
    fun receivePaymentRequest(request: ReceivePaymentRequest): ReceivePaymentResponse {
        val waitingRoom = applicationContext()
            .waitingRoomServiceBean()
            .receivePaymentRequest(request.fromAccountId, request.toAccountId, request.amount)
        return ReceivePaymentResponse(waitingRoom.id)
    }

    @POST
    @Path("/top-up")
    fun topUp(request: TopUpRequest): ReceivePaymentResponse {
        val waitingRoom = applicationContext()
            .waitingRoomServiceBean()
            .topUpRequest(request.accountId, request.amount)
        return ReceivePaymentResponse(waitingRoom.id)
    }

    @POST
    @Path("/withdrawal")
    fun withdrawal(request: WithdrawalRequest): ReceivePaymentResponse {
        val waitingRoom = applicationContext()
            .waitingRoomServiceBean()
            .withdrawalRequest(request.accountId, request.amount)
        return ReceivePaymentResponse(waitingRoom.id)
    }

    @GET
    @Path("{id}/state")
    fun waitingRoomState(@PathParam("id") id: Int): WaitingRoomStateResponse {
        val state = applicationContext()
            .waitingRoomServiceBean()
            .waitingRoomState(id)
        return WaitingRoomStateResponse(state.name)
    }

    data class TopUpRequest @JsonCreator constructor(
        @JsonProperty("accountId") val accountId: Int,
        @JsonProperty("amount") val amount: BigDecimal
    )

    data class WithdrawalRequest @JsonCreator constructor(
        @JsonProperty("accountId") val accountId: Int,
        @JsonProperty("amount") val amount: BigDecimal
    )

    data class ReceivePaymentRequest @JsonCreator constructor(
        @JsonProperty("fromAccountId") val fromAccountId: Int,
        @JsonProperty("toAccountId") val toAccountId: Int,
        @JsonProperty("amount") val amount: BigDecimal
    )

    data class ReceivePaymentResponse(val waitingRoomId: Int)

    data class WaitingRoomStateResponse(val state: String)
}

