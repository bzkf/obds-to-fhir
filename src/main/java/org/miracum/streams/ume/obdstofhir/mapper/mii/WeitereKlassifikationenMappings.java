package org.miracum.streams.ume.obdstofhir.mapper.mii;

import java.util.HashMap;
import java.util.Map;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "fhir.mappings")
public class WeitereKlassifikationenMappings {

  public record WeitereKlassifikationCodeMappings(
      Coding coding, HashMap<String, Coding> mappings) {}

  HashMap<String, WeitereKlassifikationCodeMappings> weitereKlassifikationen;

  public Map<String, WeitereKlassifikationCodeMappings> getWeitereKlassifikationen() {
    return weitereKlassifikationen;
  }

  public void setWeitereKlassifikationen(
      HashMap<String, WeitereKlassifikationCodeMappings> weitereKlassifikationen) {
    this.weitereKlassifikationen = weitereKlassifikationen;
  }
}
