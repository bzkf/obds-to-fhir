package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.AllgemeinICDTyp;
import de.basisdatensatz.obds.v3.OBDS;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TodMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(TodMapper.class);
  private static final Pattern icdVersionPattern =
      Pattern.compile("^(10 (?<versionYear>20\\d{2}) ((GM)|(WHO))|Sonstige)$");

  @Autowired
  public TodMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Observation map(
      OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung, Reference patient) {
    // Validation
    Objects.requireNonNull(meldung.getTod());
    Objects.requireNonNull(patient);
    // Objects.requireNonNull(condition);

    Validate.notBlank(meldung.getTod().getAbschlussID(), "Required ABSCHLUSS_ID is unset");
    Validate.isTrue(
        Objects.equals(
            patient.getReferenceElement().getResourceType(),
            Enumerations.ResourceType.PATIENT.toCode()),
        "The subject reference should point to a Patient resource");
    /*
    Validate.isTrue(
      Objects.equals(
        condition.getReferenceElement().getResourceType(), Enumerations.ResourceType.CONDITION.toCode()),
      "The condition reference should point to a Condition resource");
    */

    var observation = new Observation();

    // Observation Code
    var snomedCode = new CodeableConcept();
    snomedCode.addCoding().setSystem(fhirProperties.getSystems().getSnomed()).setCode("184305005");
    observation.setCode(snomedCode);

    // Subject
    observation.setSubject(patient);

    // Effective | Todesdatum
    var todesZeitpunkt = convertObdsDatumToDateTimeType(meldung.getTod().getSterbedatum());
    if (todesZeitpunkt.isPresent()) {
      observation.setEffective(todesZeitpunkt.get());
    }

    /*
    // Focus | Bezugsdiagnose
    var focusList = new ArrayList<Reference>();
    focusList.add(condition);
    observation.setFocus(focusList);
    */

    // Value | Todesursache ICD10GM
    if (meldung.getTod().getMengeTodesursachen() != null) {
      for (AllgemeinICDTyp todesursache :
          meldung.getTod().getMengeTodesursachen().getTodesursacheICD()) {
        var todesursacheConcept = new CodeableConcept();
        todesursacheConcept
            .addCoding()
            .setSystem(fhirProperties.getSystems().getIcd10gm())
            .setCode(todesursache.getCode())
            .setDisplay(todesursache.getCode());
        observation.setCode(todesursacheConcept);
      }
    }

    // Interpretation
    // ToDo get display from system
    if (meldung.getTod().getTodTumorbedingt() != null) {
      var interpretation = new CodeableConcept();
      interpretation
          .addCoding()
          .setSystem(fhirProperties.getSystems().getMiiCsOnkoTodInterpretation())
          .setCode(meldung.getTod().getTodTumorbedingt().value())
          .setDisplay(meldung.getTod().getTodTumorbedingt().value());
      observation.setCode(interpretation);
    }

    return observation;
  }
}
