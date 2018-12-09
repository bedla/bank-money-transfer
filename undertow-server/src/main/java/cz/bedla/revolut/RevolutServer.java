package cz.bedla.revolut;

import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletException;
import javax.ws.rs.core.Application;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;

import static org.apache.commons.lang3.Validate.notEmpty;
import static org.apache.commons.lang3.Validate.notNull;
import static org.apache.commons.lang3.Validate.validState;

public final class RevolutServer {
    private final String host;
    private final int port;
    private final Class<? extends Application> applicationClass;
    private final AtomicReference<Undertow> serverReference = new AtomicReference<>();

    public RevolutServer(String host, int port, Class<? extends Application> applicationClass) {
        this.host = notEmpty(host, "host cannot be empty");
        this.port = port;
        this.applicationClass = notNull(applicationClass, "applicationClass cannot be null");
    }

    public void start() {
        DeploymentInfo servletBuilder = Servlets.deployment()
                .setClassLoader(RevolutServer.class.getClassLoader())
                .setContextPath("/api")
                .setDeploymentName("revolut.war")
                .addServlets(
                        Servlets.servlet("Jersey", ServletContainer.class)
                                .addInitParam(ServletProperties.JAXRS_APPLICATION_CLASS, applicationClass.getName())
                                .addInitParam("jersey.config.server.wadl.disableWadl", "true")
                                .addMapping("/*"));

        DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
        manager.deploy();
        PathHandler path = null;
        try {
            path = Handlers.path(Handlers.redirect("/api")).addPrefixPath("/api", manager.start());
        } catch (ServletException e) {
            ExceptionUtils.rethrow(e);
        }

        Undertow server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(path)
                .build();

        if (serverReference.compareAndSet(null, server)) {
            server.start();
        } else {
            throw new IllegalStateException("Server already started");
        }
    }

    public void stop() {
        final Undertow undertow = serverReference.getAndSet(null);
        validState(undertow != null, "Unable to stop stopped server");
        undertow.stop();
    }

    public boolean isRunning() {
        return serverReference.get() != null;
    }

    public int getPort() {
        return port;
    }
}

