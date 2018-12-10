package cz.bedla.revolut;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.concurrent.AtomicInitializer;
import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.ws.rs.core.Application;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.commons.lang3.Validate.notEmpty;
import static org.apache.commons.lang3.Validate.validState;

public final class RestServer {
    private static final Logger LOG = LoggerFactory.getLogger(RestServer.class);

    private final String host;
    private final int requestedPort;
    private final Class<? extends Application> applicationClass;
    private final AtomicReference<LazyServer> serverReference = new AtomicReference<>();

    public RestServer(String host, int requestedPort, Class<? extends Application> applicationClass) {
        this.host = notEmpty(host, "host cannot be empty");
        this.requestedPort = requestedPort;
        this.applicationClass = Validate.notNull(applicationClass, "applicationClass cannot be null");
    }

    public void start() {
        if (serverReference.compareAndSet(null, new LazyServer(host, requestedPort, applicationClass))) {
            final Undertow undertow = serverReference.get().getServer();
            LOG.info("Starting server");
            undertow.start();
        } else {
            throw new IllegalStateException("Server already started");
        }
    }

    public void stop() {
        final LazyServer lazyServer = serverReference.getAndSet(null);
        validState(lazyServer != null, "Unable to stop stopped server");
        LOG.info("Stopping server");
        lazyServer.getServer().stop();
    }

    public boolean isRunning() {
        return serverReference.get() != null;
    }

    public int getPort() {
        final LazyServer lazyServer = Validate.notNull(serverReference.get(), "Server not started");
        final List<Undertow.ListenerInfo> listeners = lazyServer.getServer().getListenerInfo();
        validState(listeners.size() == 1, "Expecting one http listener");
        final SocketAddress address = listeners.get(0).getAddress();
        validState(address instanceof InetSocketAddress, "Expecting address of " + InetSocketAddress.class);
        return ((InetSocketAddress) address).getPort();
    }

    private static class LazyServer extends AtomicInitializer<Undertow> {
        private final String host;
        private final int requestedPort;
        private final Class<? extends Application> applicationClass;

        private LazyServer(String host, int requestedPort, Class<? extends Application> applicationClass) {
            this.host = host;
            this.requestedPort = requestedPort;
            this.applicationClass = applicationClass;
        }

        public Undertow getServer() {
            try {
                return get();
            } catch (ConcurrentException e) {
                return ExceptionUtils.rethrow(e);
            }
        }

        @Override
        protected Undertow initialize() throws ConcurrentException {
            LOG.info("Creating server");
            DeploymentInfo servletBuilder = Servlets.deployment()
                    .setClassLoader(RestServer.class.getClassLoader())
                    .setContextPath("/api")
                    .setDeploymentName("revolut.war")
                    .addServlets(
                            Servlets.servlet("Jersey", ServletContainer.class)
                                    .addInitParam(ServletProperties.JAXRS_APPLICATION_CLASS, applicationClass.getName())
                                    .addInitParam(ServerProperties.WADL_FEATURE_DISABLE, Boolean.TRUE.toString())
                                    .addMapping("/*"));

            DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
            manager.deploy();
            PathHandler path = null;
            try {
                path = Handlers.path(Handlers.redirect("/api")).addPrefixPath("/api", manager.start());
            } catch (ServletException e) {
                ExceptionUtils.rethrow(e);
            }

            return Undertow.builder()
                    .addHttpListener(requestedPort, host)
                    .setHandler(path)
                    .build();
        }
    }
}

