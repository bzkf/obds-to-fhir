package io.github.bzkf.obdstofhir.patientreference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ca.uhn.fhir.rest.client.api.IHttpRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OAuth2ClientCredentialsAuthInterceptorTest {

  private HttpServer server;
  private AtomicInteger tokenRequestCount;
  private volatile String tokenResponseBody;
  private volatile int tokenResponseStatus;

  @BeforeEach
  void startServer() throws IOException {
    tokenRequestCount = new AtomicInteger();
    tokenResponseBody = "{\"access_token\":\"token-1\",\"expires_in\":3600}";
    tokenResponseStatus = 200;

    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/token",
        exchange -> {
          tokenRequestCount.incrementAndGet();
          var body = tokenResponseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(tokenResponseStatus, body.length);
          try (var os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private String tokenUrl() {
    return "http://localhost:" + server.getAddress().getPort() + "/token";
  }

  @Test
  void shouldAddBearerTokenFetchedFromTokenEndpoint() {
    var interceptor =
        new OAuth2ClientCredentialsAuthInterceptor(tokenUrl(), "client-id", "client-secret", null);
    var request = mock(IHttpRequest.class);

    interceptor.interceptRequest(request);

    verify(request).addHeader("Authorization", "Bearer token-1");
    assertThat(tokenRequestCount.get()).isEqualTo(1);
  }

  @Test
  void shouldCacheTokenUntilExpiry() {
    var interceptor =
        new OAuth2ClientCredentialsAuthInterceptor(tokenUrl(), "client-id", "client-secret", null);

    interceptor.interceptRequest(mock(IHttpRequest.class));
    interceptor.interceptRequest(mock(IHttpRequest.class));
    interceptor.interceptRequest(mock(IHttpRequest.class));

    assertThat(tokenRequestCount.get()).isEqualTo(1);
  }

  @Test
  void shouldRefetchTokenOnceExpired() {
    tokenResponseBody = "{\"access_token\":\"token-1\",\"expires_in\":0}";
    var interceptor =
        new OAuth2ClientCredentialsAuthInterceptor(tokenUrl(), "client-id", "client-secret", null);

    interceptor.interceptRequest(mock(IHttpRequest.class));
    interceptor.interceptRequest(mock(IHttpRequest.class));

    assertThat(tokenRequestCount.get()).isEqualTo(2);
  }

  @Test
  void shouldThrowWhenTokenEndpointReturnsNonOkStatus() {
    tokenResponseStatus = 401;
    tokenResponseBody = "{\"error\":\"invalid_client\"}";
    var interceptor =
        new OAuth2ClientCredentialsAuthInterceptor(tokenUrl(), "client-id", "client-secret", null);

    assertThatThrownBy(() -> interceptor.interceptRequest(mock(IHttpRequest.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("401");
  }
}
