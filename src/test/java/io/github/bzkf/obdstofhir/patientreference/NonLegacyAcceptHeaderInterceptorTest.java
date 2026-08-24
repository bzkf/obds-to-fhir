package io.github.bzkf.obdstofhir.patientreference;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class NonLegacyAcceptHeaderInterceptorTest {

  @Test
  void stripsLegacyMediaTypesFromDefaultAcceptHeader() throws Exception {
    var capturedAccept = new AtomicReference<String>();

    var server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/Patient",
        exchange -> {
          capturedAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
          var body =
              "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"entry\":[]}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/fhir+json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    try {
      var baseUrl = "http://localhost:" + server.getAddress().getPort();

      var fhirContext = FhirContext.forR4();
      fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
      IGenericClient client = fhirContext.newRestfulGenericClient(baseUrl);
      client.registerInterceptor(new NonLegacyAcceptHeaderInterceptor());

      client
          .search()
          .forResource(Patient.class)
          .where(Patient.IDENTIFIER.exactly().identifier("foo"))
          .execute();

      assertThat(capturedAccept.get())
          .isEqualTo("application/fhir+xml;q=1.0, application/fhir+json;q=1.0");
    } finally {
      server.stop(0);
    }
  }
}
