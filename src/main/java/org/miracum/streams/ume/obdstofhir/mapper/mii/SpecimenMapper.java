package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import java.util.*;
import org.apache.commons.lang3.Validate;
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
    Objects.requireNonNull(patient, "Reference to Patient must not be null");
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");

    var specimen = new Specimen();

    // Identifier = HistologieId
    var identifier = new Identifier();
    identifier
        .setSystem(fhirProperties.getSystems().getSpecimenId())
        .setValue(histologie.getHistologieID());
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
