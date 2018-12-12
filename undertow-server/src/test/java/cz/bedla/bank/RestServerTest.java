package cz.bedla.bank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.servlet.ServletContextListener;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

class RestServerTest {
    private RestServer fixture = new RestServer("localhost", 0, new DummyServletContextListener(), FooApplication.class);

    @Test
    void startStopStart() throws InterruptedException {
        assertThat(fixture.isRunning()).isFalse();
        fixture.start();
        assertThat(fixture.isRunning()).isTrue();
        fixture.stop();
        assertThat(fixture.isRunning()).isFalse();
        fixture.start();
        assertThat(fixture.isRunning()).isTrue();
    }

    @Test
    void doubleStart() throws InterruptedException {
        fixture.start();
        assertThatThrownBy(() -> fixture.start())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Server already started");
    }

    @Test
    void doubleStop() {
        fixture.start();
        fixture.stop();
        assertThatThrownBy(() -> fixture.stop())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to stop stopped server");
    }

    @Test
    void restCallToFooEndpoint() {
        fixture.start();

        given()
                .log().all()
                .port(fixture.getPort())
                .when()
                .get("/api/foo")
                .then()
                .log().all()
                .statusCode(200)
                .body(
                        "number", greaterThan(0L),
                        "text", equalTo("foo"));
    }

    @AfterEach
    void tearDown() {
        try {
            fixture.stop();
        } catch (Exception e) {
            // ignore while stopping after test
        }
    }

    private static class DummyServletContextListener implements ServletContextListener {
    }
}
