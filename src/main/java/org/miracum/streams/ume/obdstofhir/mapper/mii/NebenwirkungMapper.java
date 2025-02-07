package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.NebenwirkungTyp;
import de.basisdatensatz.obds.v3.OBDS;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class NebenwirkungMapper extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(NebenwirkungMapper.class);

  @Autowired
  public NebenwirkungMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<AdverseEvent> map(
      OBDS.MengePatient.Patient.MengeMeldung.Meldung meldung, Reference patient, Reference suspectedEntity) {

    Objects.requireNonNull(meldung, "meldung must not be null");
    Objects.requireNonNull(patient, "Reference must not be null");

    var result = new ArrayList<AdverseEvent>();
    var st = meldung.getST();
    var syst = meldung.getSYST();
    if (st != null
        && st.getNebenwirkungen() != null
        && st.getNebenwirkungen().getMengeNebenwirkung() != null) {
      result.addAll(
          createAdverseEvent(
              meldung.getST().getNebenwirkungen(), meldung.getST().getSTID(), patient, suspectedEntity));
    }

    if (syst != null
        && syst.getNebenwirkungen() != null
        && syst.getNebenwirkungen().getMengeNebenwirkung() != null) {
      result.addAll(
          createAdverseEvent(
              meldung.getSYST().getNebenwirkungen(), meldung.getSYST().getSYSTID(), patient, suspectedEntity));
    }
    return result;
  }

  public List<AdverseEvent> createAdverseEvent(
      NebenwirkungTyp nebenwirkung, String identi, Reference patient, Reference suspectedEntity) {

    var adverseEvents = new ArrayList<AdverseEvent>();
    for (int i = 0; i < nebenwirkung.getMengeNebenwirkung().getNebenwirkung().size(); i++) {

      var adverseEvent = new AdverseEvent();
      adverseEvent
          .getMeta()
          .addProfile(fhirProperties.getProfiles().getMiiPrOnkoNebenwirkungAdverseEvent());

      //  Identifier and Id
      var identifier =
          new Identifier()
              .setSystem(fhirProperties.getSystems().getNebenwirkungAdverseEventId())
              .setValue("mii-pr-onko-nebenwirkung-" + identi + "_" + i);
      adverseEvent.setIdentifier(identifier);
      adverseEvent.setId(computeResourceIdFromIdentifier(identifier));

      // actuality
      adverseEvent.setActuality(AdverseEvent.AdverseEventActuality.ACTUAL);

      // event
      var code =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getMeddra())
                  .setCode(
                      nebenwirkung
                          .getMengeNebenwirkung()
                          .getNebenwirkung()
                          .get(i)
                          .getArt()
                          .getMedDRACode())
                  .setDisplay(
                      nebenwirkung
                          .getMengeNebenwirkung()
                          .getNebenwirkung()
                          .get(i)
                          .getArt()
                          .getBezeichnung())
                  .setVersion(
                      nebenwirkung.getMengeNebenwirkung().getNebenwirkung().get(i).getVersion()));
      adverseEvent.setEvent(code);

      // Subject
      adverseEvent.setSubject(patient);

      // seriousness
      var seriousness =
          new CodeableConcept(
              new Coding()
                  .setSystem(fhirProperties.getSystems().getCtcaeGrading())
                  .setCode(
                      nebenwirkung.getMengeNebenwirkung().getNebenwirkung().getFirst().getGrad())
                  .setDisplay(""));
      adverseEvent.setSeriousness(seriousness);

      // suspectIdentity
      var suspectedEntitys = new ArrayList<AdverseEvent.AdverseEventSuspectEntityComponent>();
      var aeseComponent =
          new AdverseEvent.AdverseEventSuspectEntityComponent().setInstance(suspectedEntity);
      suspectedEntitys.add(aeseComponent);
      adverseEvent.setSuspectEntity(suspectedEntitys);
      adverseEvents.add(adverseEvent);
    }
    return adverseEvents;
  }
}
