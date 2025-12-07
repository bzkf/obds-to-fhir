package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.NebenwirkungTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NebenwirkungMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(NebenwirkungMapper.class);

  public NebenwirkungMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<AdverseEvent> map(
      NebenwirkungTyp nebenwirkung,
      Reference patient,
      Reference suspectedEntity,
      String sourceElementId) {

    Objects.requireNonNull(nebenwirkung, "Nebenwirkung must not be null");
    Objects.requireNonNull(sourceElementId, "Source element id (SYST_ID, ST_ID) must not be null");
    verifyReference(patient, ResourceType.Patient);

    var result = new ArrayList<AdverseEvent>();
    if (nebenwirkung.getMengeNebenwirkung() != null) {
      result.addAll(createAdverseEvent(nebenwirkung, patient, suspectedEntity, sourceElementId));
    }
    if (nebenwirkung.getGradMaximal2OderUnbekannt() != null) {
      result.add(createAdverseEventMax2(nebenwirkung, patient, suspectedEntity, sourceElementId));
    }
    return result;
  }

  private List<AdverseEvent> createAdverseEvent(
      NebenwirkungTyp nebenwirkung,
      Reference patient,
      Reference suspectedEntity,
      String sourceElementId) {

    var adverseEvents = new ArrayList<AdverseEvent>();
    if (nebenwirkung.getMengeNebenwirkung() != null) {
      for (int i = 0; i < nebenwirkung.getMengeNebenwirkung().getNebenwirkung().size(); i++) {
        var adverseEvent = createAdverseEventBase(patient, suspectedEntity);
        //  Identifier and Id
        var identifier =
            new Identifier()
                .setSystem(
                    fhirProperties.getSystems().getIdentifiers().getNebenwirkungAdverseEventId())
                .setValue(
                    slugifier.slugify("mii-pr-onko-nebenwirkung-" + sourceElementId + "-" + i));
        adverseEvent.setIdentifier(identifier);
        adverseEvent.setId(computeResourceIdFromIdentifier(identifier));
        // event
        var code = new CodeableConcept();
        var nb = nebenwirkung.getMengeNebenwirkung().getNebenwirkung().get(i);
        if (nb.getArt().getMedDRACode() == null) {
          code.addExtension()
              .setUrl(fhirProperties.getExtensions().getDataAbsentReason())
              .setValue(new CodeType("unknown"));
          code.setText(nb.getArt().getBezeichnung());
        } else {
          code.addCoding(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getMeddra())
                  .setCode(nb.getArt().getMedDRACode())
                  .setDisplay(nb.getArt().getBezeichnung())
                  .setVersion(nb.getVersion()));
        }
        adverseEvent.setEvent(code);

        // seriousness
        var seriousness =
            new CodeableConcept(
                new Coding()
                    .setSystem(fhirProperties.getSystems().getMiiCsOnkoNebenwirkungCtcaeGrad())
                    .setCode(nb.getGrad())
                    .setDisplay(""));
        adverseEvent.setSeriousness(seriousness);
        adverseEvents.add(adverseEvent);
      }
    }
    return adverseEvents;
  }

  private AdverseEvent createAdverseEventMax2(
      NebenwirkungTyp nebenwirkung,
      Reference patient,
      Reference suspectedEntity,
      String sourceElementId) {
    var adverseEvent = createAdverseEventBase(patient, suspectedEntity);
    //  Identifier and Id
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getIdentifiers().getNebenwirkungAdverseEventId())
            .setValue(
                slugifier.slugify(
                    "mii-pr-onko-nebenwirkung-" + sourceElementId + "-Grad-maximal-2-unbekannt"));
    adverseEvent.setIdentifier(identifier);
    adverseEvent.setId(computeResourceIdFromIdentifier(identifier));
    // event
    // If the element used to create the AdverseEvent is of type `Grad_maximal_2_unbekannt`,
    // we can't fill the event code with a MedDRA code or similar.
    var code = new CodeableConcept();
    code.addExtension()
        .setUrl(fhirProperties.getExtensions().getDataAbsentReason())
        .setValue(new CodeType("not-applicable"));
    adverseEvent.setEvent(code);

    // seriousness
    var seriousness =
        new CodeableConcept(
            new Coding()
                .setSystem(fhirProperties.getSystems().getMiiCsOnkoNebenwirkungCtcaeGrad())
                .setCode(nebenwirkung.getGradMaximal2OderUnbekannt())
                .setDisplay(""));
    adverseEvent.setSeriousness(seriousness);
    return adverseEvent;
  }

  private AdverseEvent createAdverseEventBase(Reference patient, Reference suspectedEntity) {
    var adverseEvent = new AdverseEvent();
    adverseEvent
        .getMeta()
        .addProfile(fhirProperties.getProfiles().getMiiPrOnkoNebenwirkungAdverseEvent());
    adverseEvent.setActuality(AdverseEvent.AdverseEventActuality.ACTUAL);
    adverseEvent.setSubject(patient);
    adverseEvent.addSuspectEntity(
        new AdverseEvent.AdverseEventSuspectEntityComponent(suspectedEntity));
    return adverseEvent;
  }
}
