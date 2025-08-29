package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import java.util.*;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SpecimenMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(SpecimenMapper.class);

  public SpecimenMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Specimen map(HistologieTyp histologie, Reference patient) {
    Objects.requireNonNull(histologie, "HistologieTyp must not be null");
    verifyReference(patient, ResourceType.Patient);

    var specimen = new Specimen();

    // Identifier = HistologieId
    var identifier = new Identifier();
    identifier
        .setSystem(fhirProperties.getSystems().getSpecimenId())
        .setValue(slugifier.slugify(histologie.getHistologieID()));
    specimen.addIdentifier(identifier);
    // Id
    specimen.setId(computeResourceIdFromIdentifier(identifier));
    // Meta
    specimen.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoSpecimen());

    // accessionIdentifier=Histologie-Einsendenummer
    var accessionIdentifier = new Identifier();
    accessionIdentifier.setValue(histologie.getHistologieEinsendeNr());
    specimen.setAccessionIdentifier(accessionIdentifier);
    // Subject
    specimen.setSubject(patient);

    // Specimen.collection.collected[x] = Tumor Histologiedatum
    convertObdsDatumToDateTimeType(histologie.getTumorHistologiedatum())
        .ifPresent(
            date ->
                specimen.setCollection(
                    new Specimen.SpecimenCollectionComponent().setCollected(date)));

    return specimen;
  }
}
