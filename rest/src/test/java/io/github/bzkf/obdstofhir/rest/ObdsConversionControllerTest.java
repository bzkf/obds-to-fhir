package io.github.bzkf.obdstofhir.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ObdsConversionControllerTest {

  @Autowired private MockMvc mockMvc;

  private String sampleXml() throws Exception {
    return Files.readString(new ClassPathResource("sample-obds.xml").getFile().toPath());
  }

  @Test
  void convert_withoutAcceptHeader_defaultsToFhirJson() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/fhir/convert").contentType(MediaType.APPLICATION_XML).content(sampleXml()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/fhir+json"))
            .andReturn();

    var body = result.getResponse().getContentAsString();
    assertThat(body).contains("\"resourceType\":\"Bundle\"");
    assertThat(body).contains("\"type\":\"transaction\"");
  }

  @Test
  void convert_withXmlAcceptHeader_returnsFhirXml() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/fhir/convert")
                    .contentType(MediaType.APPLICATION_XML)
                    .accept("application/fhir+xml")
                    .content(sampleXml()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/fhir+xml"))
            .andReturn();

    var body = result.getResponse().getContentAsString();
    assertThat(body).contains("<Bundle");
    assertThat(body).contains("value=\"transaction\"");
  }

  @Test
  void convert_withInvalidXml_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/fhir/convert")
                .contentType(MediaType.APPLICATION_XML)
                .content("<not-obds-or-adt/>"))
        .andExpect(status().isBadRequest());
  }

  @Nested
  @SpringBootTest(properties = "obds.rest-service.wrap-single-patient-bundle=true")
  @AutoConfigureMockMvc
  class WithWrapSinglePatientBundleEnabled {

    @Autowired private MockMvc mockMvc;

    @Test
    void convert_withSinglePatient_wrapsInCollectionBundle() throws Exception {
      var result =
          mockMvc
              .perform(
                  post("/fhir/convert").contentType(MediaType.APPLICATION_XML).content(sampleXml()))
              .andExpect(status().isOk())
              .andReturn();

      var body = result.getResponse().getContentAsString();
      assertThat(body).contains("\"type\":\"collection\"");
    }
  }
}
