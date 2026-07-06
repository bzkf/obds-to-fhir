package io.github.bzkf.obdstofhir.patientreference;

import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

/**
 * A HAPI FHIR client interceptor that authenticates using the OAuth2 client credentials grant.
 * Delegates token acquisition, caching, and refresh to Spring Security's OAuth2 client support.
 */
public class OAuth2ClientCredentialsAuthInterceptor extends BearerTokenAuthInterceptor {
  private static final String REGISTRATION_ID = "fhir-server";
  private static final String PRINCIPAL_NAME = "fhir-server-client";

  private final OAuth2AuthorizedClientManager authorizedClientManager;
  private final OAuth2AuthorizeRequest authorizeRequest;

  public OAuth2ClientCredentialsAuthInterceptor(
      String tokenUrl, String clientId, String clientSecret, @Nullable String scope) {
    var clientRegistrationBuilder =
        ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .tokenUri(tokenUrl)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);

    if (StringUtils.hasText(scope)) {
      clientRegistrationBuilder.scope(scope.trim().split("\\s+"));
    }

    var clientRegistrationRepository =
        new InMemoryClientRegistrationRepository(clientRegistrationBuilder.build());
    var authorizedClientService =
        new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);

    var manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientService);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());

    this.authorizedClientManager = manager;
    this.authorizeRequest =
        OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
            .principal(PRINCIPAL_NAME)
            .build();
  }

  @Override
  public void interceptRequest(IHttpRequest theRequest) {
    var authorizedClient = authorizedClientManager.authorize(authorizeRequest);
    if (authorizedClient == null) {
      throw new IllegalStateException(
          "Failed to obtain an OAuth2 access token for the FHIR server client.");
    }

    setToken(authorizedClient.getAccessToken().getTokenValue());
    super.interceptRequest(theRequest);
  }
}
