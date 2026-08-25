package io.github.bzkf.obdstofhir.rest;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
    // added ahead of the default converters so it takes precedence over the generic Jackson
    // converter, which would otherwise also claim `application/fhir+json` and serialize the
    // FHIR model classes as plain POJOs instead of using HAPI's FHIR-conformant parser.
    builder.addCustomConverter(new FhirResourceHttpMessageConverter(FhirContext.forR4()));
  }
}
