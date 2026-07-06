package io.github.bzkf.obdstofhir.patientreference;

import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

/**
 * A HAPI FHIR client interceptor that authenticates using the OAuth2 client credentials grant.
 * Fetches an access token from the configured token endpoint, caches it, and transparently
 * refreshes it once it is about to expire.
 */
public class OAuth2ClientCredentialsAuthInterceptor extends BearerTokenAuthInterceptor {
  // re-fetch the token this long before it actually expires, to avoid using a token that expires
  // mid-flight
  private static final Duration EXPIRY_SAFETY_MARGIN = Duration.ofSeconds(30);

  private final String tokenUrl;
  private final String clientId;
  private final String clientSecret;
  private final @Nullable String scope;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ReentrantLock lock = new ReentrantLock();

  private volatile Instant expiresAt = Instant.MIN;

  public OAuth2ClientCredentialsAuthInterceptor(
      String tokenUrl, String clientId, String clientSecret, @Nullable String scope) {
    this.tokenUrl = tokenUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.scope = scope;
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public void interceptRequest(IHttpRequest theRequest) {
    ensureValidAccessToken();
    super.interceptRequest(theRequest);
  }

  private void ensureValidAccessToken() {
    if (getToken() == null || Instant.now().isAfter(expiresAt)) {
      lock.lock();
      try {
        if (getToken() == null || Instant.now().isAfter(expiresAt)) {
          fetchAccessToken();
        }
      } finally {
        lock.unlock();
      }
    }
  }

  private void fetchAccessToken() {
    var formBody = new StringBuilder("grant_type=client_credentials");
    formBody.append("&client_id=").append(urlEncode(clientId));
    formBody.append("&client_secret=").append(urlEncode(clientSecret));
    if (StringUtils.hasText(scope)) {
      formBody.append("&scope=").append(urlEncode(scope));
    }

    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody.toString()))
            .build();

    try {
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Failed to obtain OAuth2 access token from '%s', server responded with status %d"
                .formatted(tokenUrl, response.statusCode()));
      }

      JsonNode json = objectMapper.readTree(response.body());
      setToken(json.required("access_token").asText());
      long expiresInSeconds = json.path("expires_in").asLong(60);
      expiresAt = Instant.now().plusSeconds(expiresInSeconds).minus(EXPIRY_SAFETY_MARGIN);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to obtain OAuth2 access token from " + tokenUrl, e);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to obtain OAuth2 access token from " + tokenUrl, e);
    }
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
