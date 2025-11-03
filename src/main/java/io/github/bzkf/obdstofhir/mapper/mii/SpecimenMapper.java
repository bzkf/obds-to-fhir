package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.*;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SpecimenMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(SpecimenMapper.class);

  public SpecimenMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Specimen map(HistologieTyp histologie, Reference patient, String meldungsId) {
    Objects.requireNonNull(histologie, "HistologieTyp must not be null");
    verifyReference(patient, ResourceType.Patient);

    var specimen = new Specimen();

    var identifierValue = histologie.getHistologieID();
    if (!StringUtils.hasText(identifierValue)) {
      LOG.warn(
          "Histologie_ID is unset. Defaulting to Meldung_ID as the identifier for the Histologie Specimen.");
      identifierValue = meldungsId;
    }

    // Identifier = HistologieId
    var identifier = new Identifier();
    identifier
        .setSystem(fhirProperties.getSystems().getIdentifiers().getHistologieSpecimenId())
        .setValue(slugifier.slugify(identifierValue));
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
