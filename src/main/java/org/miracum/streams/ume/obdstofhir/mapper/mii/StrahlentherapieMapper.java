package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.STTyp;
import org.hl7.fhir.r4.model.Procedure;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class StrahlentherapieMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(StrahlentherapieMapper.class);

  @Autowired
  public StrahlentherapieMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Procedure map(STTyp st) {
    var procedure = new Procedure();
    procedure.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoStrahlentherapie());
    return procedure;
  }
}
