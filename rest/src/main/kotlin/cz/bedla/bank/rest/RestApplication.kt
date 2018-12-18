package cz.bedla.bank.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.glassfish.jersey.jackson.JacksonFeature
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider
import org.glassfish.jersey.server.ResourceConfig

class RestApplication : ResourceConfig() {
    init {
        register(PaymentOrderEndpoint::class.java)
        register(AccountEndpoint::class.java)
        register(JacksonFeature::class.java)

        val jacksonProvider = JacksonJaxbJsonProvider()
        jacksonProvider.setMapper(
            ObjectMapper()
                .registerModule(JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        )
        register(jacksonProvider)
    }
}
