package org.miracum.streams.ume.obdstofhir.mapper.mii;

import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObdsConditionMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ObdsConditionMapper.class);

  @Autowired
  public ObdsConditionMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Condition map(/* DiagnoseType diagnose, */ Reference patient) {
    var condition = new Condition();
    condition.setSubject(patient);
    condition.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoDiagnosePrimaertumor());
    return condition;
  }
}
