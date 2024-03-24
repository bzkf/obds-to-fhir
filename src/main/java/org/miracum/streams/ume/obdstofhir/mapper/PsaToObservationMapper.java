package org.miracum.streams.ume.obdstofhir.mapper;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsObservationMapper.ModulProstataMappingParams;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PsaToObservationMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(PsaToObservationMapper.class);

  public PsaToObservationMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      ModulProstataMappingParams modulProstataParams,
      Reference patientReference,
      String metaSource) {
    var modulProstata = modulProstataParams.modulProstata();
    if (modulProstata.getPSA().isEmpty()) {
      throw new IllegalArgumentException("Modul_Prostata_PSA is unset.");
    }

    var psaObservation = new Observation();
    psaObservation.getMeta().setSource(metaSource);

    if (modulProstataParams.meldeanlass() == Meldeanlass.STATUSAENDERUNG) {
      psaObservation.setStatus(ObservationStatus.AMENDED);
    } else {
      psaObservation.setStatus(ObservationStatus.FINAL);
    }

    var identifierValue =
        String.format("%s-%s-psa", modulProstataParams.patientId(), modulProstataParams.baseId());

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getPsaObservationId())
            .setValue(identifierValue);

    psaObservation.setId(computeResourceIdFromIdentifier(identifier));

    psaObservation.setSubject(patientReference);

    var psaConcept = new CodeableConcept();
    psaConcept
        .addCoding()
        .setSystem(fhirProperties.getSystems().getLoinc())
        .setCode("2857-1")
        .setVersion("2.77")
        .setDisplay(fhirProperties.getDisplay().getPsaLoinc());
    psaObservation.setCode(psaConcept);

    if (modulProstata.getDatumPSA().isPresent()) {
      psaObservation.setEffective(extractDateTimeFromADTDate(modulProstata.getDatumPSA().get()));
    } else if (modulProstataParams.baseDatum() != null) {
      psaObservation.setEffective(modulProstataParams.baseDatum());
    } else {
      LOG.warn("Unable to set effective date for the PSA observation.");
    }

    var psaValue = modulProstata.getPSA().get().doubleValue();
    var quantity =
        new Quantity()
            .setValue(psaValue)
            .setSystem(fhirProperties.getSystems().getUcum())
            .setCode("ng/mL")
            .setUnit("ng/mL");
    psaObservation.setValue(quantity);

    return psaObservation;
  }
}
