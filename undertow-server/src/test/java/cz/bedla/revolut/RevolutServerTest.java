package cz.bedla.revolut;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class RevolutServerTest {
    private RevolutServer fixture = new RevolutServer("localhost", 8181, FooApplication.class);

    @Test
    void startStopStart() {
        assertTimeoutPreemptively(ofSeconds(20), () -> {
            assertThat(fixture.isRunning()).isFalse();
            fixture.start();
            assertThat(fixture.isRunning()).isTrue();
            TimeUnit.SECONDS.sleep(3);
            fixture.stop();
            assertThat(fixture.isRunning()).isFalse();
            TimeUnit.SECONDS.sleep(3);
            fixture.start();
            assertThat(fixture.isRunning()).isTrue();
        });
    }

    @Test
    void doubleStart() {
        assertTimeoutPreemptively(ofSeconds(5), () -> {
            fixture.start();
            assertThatThrownBy(() -> fixture.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Server already started");
        });
    }

    @Test
    void doubleStop() {
        assertTimeoutPreemptively(ofSeconds(5), () -> {
            fixture.start();
            fixture.stop();
            assertThatThrownBy(() -> fixture.stop())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Unable to stop stopped server");
        });
    }

    @Test
    void restCallToFooEndpoint() {
        assertTimeoutPreemptively(ofSeconds(5), () -> {
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
        });
    }

    @AfterEach
    void tearDown() {
        try {
            fixture.stop();
        } catch (Exception e) {
            // graceful teardown after test
        }
    }
}
