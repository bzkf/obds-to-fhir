package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import io.github.dizuker.tofhir.IdUtils;
import java.util.*;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

@Service
public class SpecimenMapper extends ObdsToFhirMapper {

  public SpecimenMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Specimen map(HistologieTyp histologie, Reference patient, String meldungsId) {
    Objects.requireNonNull(histologie, "HistologieTyp must not be null");
    verifyReference(patient, ResourceType.Patient);

    var specimen = new Specimen();

    var identifierValue = orMeldungId(histologie.getHistologieID(), meldungsId);

    // Identifier = HistologieId
    var identifier = new Identifier();
    identifier
        .setSystem(fhirProperties.getSystems().getIdentifiers().getHistologieSpecimenId())
        .setValue(slugifier.slugify(identifierValue));
    specimen.addIdentifier(identifier);
    // Id
    specimen.setId(IdUtils.fromIdentifier(identifier));
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
