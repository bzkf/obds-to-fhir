package io.github.bzkf.obdstofhir.rest;

import ca.uhn.fhir.context.FhirContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

/**
 * Serializes FHIR resources as {@code application/fhir+json} or {@code application/fhir+xml},
 * picking the encoding based on the negotiated response media type (i.e. the client's {@code
 * Accept} header), defaulting to JSON.
 */
public class FhirResourceHttpMessageConverter extends AbstractHttpMessageConverter<IBaseResource> {

  public static final MediaType APPLICATION_FHIR_JSON = MediaType.valueOf("application/fhir+json");
  public static final MediaType APPLICATION_FHIR_XML = MediaType.valueOf("application/fhir+xml");

  private final FhirContext fhirContext;

  public FhirResourceHttpMessageConverter(FhirContext fhirContext) {
    super(APPLICATION_FHIR_JSON, APPLICATION_FHIR_XML);
    this.fhirContext = fhirContext;
  }

  @Override
  protected boolean supports(Class<?> clazz) {
    return IBaseResource.class.isAssignableFrom(clazz);
  }

  @Override
  protected IBaseResource readInternal(
      Class<? extends IBaseResource> clazz, HttpInputMessage inputMessage)
      throws IOException, HttpMessageNotReadableException {
    throw new HttpMessageNotReadableException(
        "Reading FHIR resources via this converter is not supported", inputMessage);
  }

  @Override
  protected void writeInternal(IBaseResource resource, HttpOutputMessage outputMessage)
      throws IOException, HttpMessageNotWritableException {
    var contentType = outputMessage.getHeaders().getContentType();
    var parser =
        contentType != null && APPLICATION_FHIR_XML.isCompatibleWith(contentType)
            ? fhirContext.newXmlParser()
            : fhirContext.newJsonParser();

    var body = parser.encodeResourceToString(resource);
    outputMessage.getBody().write(body.getBytes(StandardCharsets.UTF_8));
  }
}
