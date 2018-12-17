package cz.bedla.bank.rest

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import javax.servlet.ServletContext
import javax.ws.rs.*
import javax.ws.rs.core.Context


@Path("/payment-order")
@Produces("application/json")
class PaymentOrderEndpoint(@Context override val servletContext: ServletContext) : Endpoint {
    @POST
    @Path("/transfer")
    fun receivePaymentRequest(request: ReceivePaymentRequest): ReceivePaymentResponse {
        val paymentOrder = applicationContext()
            .paymentOrderServiceBean()
            .receivePaymentRequest(request.fromAccountId, request.toAccountId, request.amount)
        return ReceivePaymentResponse(paymentOrder.id)
    }

    @POST
    @Path("/top-up")
    fun topUp(request: TopUpRequest): ReceivePaymentResponse {
        val paymentOrder = applicationContext()
            .paymentOrderServiceBean()
            .topUpRequest(request.accountId, request.amount)
        return ReceivePaymentResponse(paymentOrder.id)
    }

    @POST
    @Path("/withdrawal")
    fun withdrawal(request: WithdrawalRequest): ReceivePaymentResponse {
        val paymentOrder = applicationContext()
            .paymentOrderServiceBean()
            .withdrawalRequest(request.accountId, request.amount)
        return ReceivePaymentResponse(paymentOrder.id)
    }

    @GET
    @Path("{id}/state")
    fun paymentOrderState(@PathParam("id") id: Int): PaymentOrderStateResponse {
        val state = applicationContext()
            .paymentOrderServiceBean()
            .paymentOrderState(id)
        return PaymentOrderStateResponse(state.name)
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

    data class ReceivePaymentResponse(val paymentOrderId: Int)

    data class PaymentOrderStateResponse(val state: String)
}

