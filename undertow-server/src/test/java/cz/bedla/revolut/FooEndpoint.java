package cz.bedla.revolut;

import java.util.Objects;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("/foo")
public class FooEndpoint {
    @GET
    @Produces("application/json")
    public Response get() {
        return new Response(System.currentTimeMillis(), "foo");
    }

    public static class Response {
        private final long number;
        private final String text;

        Response(long number, String text) {
            this.number = number;
            this.text = text;
        }

        public long getNumber() {
            return number;
        }

        public String getText() {
            return text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Response)) {
                return false;
            }
            Response response = (Response) o;
            return number == response.number &&
                    Objects.equals(text, response.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(number, text);
        }

        @Override
        public String toString() {
            return "Response{" +
                    "number=" + number +
                    ", text='" + text + '\'' +
                    '}';
        }
    }
}
