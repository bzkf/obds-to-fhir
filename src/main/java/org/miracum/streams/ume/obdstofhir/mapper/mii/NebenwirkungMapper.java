package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.NebenwirkungTyp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    Objects.requireNonNull(patient, "Reference must not be null");

    var result = new ArrayList<AdverseEvent>();
    if (nebenwirkung.getMengeNebenwirkung() != null) {
      result.addAll(createAdverseEvent(nebenwirkung, patient, suspectedEntity, sourceElementId));
    }
    if (nebenwirkung.getGradMaximal2OderUnbekannt() != null) {
      result.add(createAdverseEventMax2(nebenwirkung, patient, suspectedEntity, sourceElementId));
    }
    return result;
  }

  public List<AdverseEvent> createAdverseEvent(
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
                .setSystem(fhirProperties.getSystems().getNebenwirkungAdverseEventId())
                .setValue("mii-pr-onko-nebenwirkung_" + sourceElementId + "_" + i);
        adverseEvent.setIdentifier(identifier);
        adverseEvent.setId(computeResourceIdFromIdentifier(identifier));
        // event
        var code = new CodeableConcept();
        var nb = nebenwirkung.getMengeNebenwirkung().getNebenwirkung().get(i);
        if (nb.getArt().getMedDRACode() == null) {
          code.addExtension()
              .setUrl(fhirProperties.getExtensions().getDataAbsentReason())
              .setValue(new CodeType("unknown"));
          adverseEvent.setEvent(code);
          adverseEvent.getEvent().setText(nb.getArt().getBezeichnung());
        } else {

          code.addCoding(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getMeddra())
                  .setCode(nb.getArt().getMedDRACode())
                  .setDisplay(nb.getArt().getBezeichnung())
                  .setVersion(nb.getVersion()));
          adverseEvent.setEvent(code);
        }

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

  public AdverseEvent createAdverseEventMax2(
      NebenwirkungTyp nebenwirkung,
      Reference patient,
      Reference suspectedEntity,
      String sourceElementId) {
    var adverseEvent = createAdverseEventBase(patient, suspectedEntity);
    //  Identifier and Id
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getNebenwirkungAdverseEventId())
            .setValue("mii-pr-onko-nebenwirkung_" + sourceElementId + "Grad_maximal_2_unbekannt");
    adverseEvent.setIdentifier(identifier);
    adverseEvent.setId(computeResourceIdFromIdentifier(identifier));
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
