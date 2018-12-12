package cz.bedla.bank;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class FooApplication extends ResourceConfig {
    public FooApplication() {
        register(FooEndpoint.class);
        register(JacksonFeature.class);
    }
}
