package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.OPTyp;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OperationMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(OperationMapper.class);

  @Autowired
  public OperationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Procedure map(OPTyp op, Reference patient, Reference condition) {
    var procedure = new Procedure();
    procedure.setSubject(patient);

    procedure.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoOperation());

    return procedure;
  }
}
