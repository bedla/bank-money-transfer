package cz.bedla.bank.rest

import org.glassfish.jersey.jackson.JacksonFeature
import org.glassfish.jersey.server.ResourceConfig

class RestApplication : ResourceConfig() {
    init {
        register(WaitingRoomEndpoint::class.java)
        register(JacksonFeature::class.java)
    }
}
