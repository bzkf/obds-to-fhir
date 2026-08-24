package io.github.bzkf.obdstofhir.patientreference;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * HAPI's generic client always appends {@code application/xml+fhir} / {@code application/json+fhir}
 * media types as a low-priority fallback to the {@code Accept} header. Some FHIR servers reject the
 * entire {@code Accept} header if it contains any media type they don't recognize, rather than
 * negotiating the highest-priority type they do support. This interceptor strips the legacy
 * fallback entries so only {@code application/fhir+xml} / {@code application/fhir+json} remain.
 */
public class NonLegacyAcceptHeaderInterceptor implements IClientInterceptor {

  @Override
  public void interceptRequest(IHttpRequest theRequest) {
    var acceptValues =
        theRequest.getAllHeaders().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(Constants.HEADER_ACCEPT))
            .flatMap(entry -> entry.getValue().stream())
            .collect(Collectors.toList());
    if (acceptValues.isEmpty()) {
      return;
    }

    var filtered =
        acceptValues.stream()
            .map(NonLegacyAcceptHeaderInterceptor::removeLegacyMediaTypes)
            .collect(Collectors.toList());

    theRequest.removeHeaders(Constants.HEADER_ACCEPT);
    filtered.forEach(value -> theRequest.addHeader(Constants.HEADER_ACCEPT, value));
  }

  @Override
  public void interceptResponse(IHttpResponse theResponse) {
    // nothing to do
  }

  private static String removeLegacyMediaTypes(String acceptHeaderValue) {
    return Arrays.stream(acceptHeaderValue.split(","))
        .map(String::trim)
        .filter(entry -> !entry.startsWith(Constants.CT_FHIR_XML))
        .filter(entry -> !entry.startsWith(Constants.CT_FHIR_JSON))
        .collect(Collectors.joining(", "));
  }
}
