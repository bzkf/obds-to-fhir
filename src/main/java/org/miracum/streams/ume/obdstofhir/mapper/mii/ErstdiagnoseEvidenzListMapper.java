package org.miracum.streams.ume.obdstofhir.mapper.mii;

import java.util.List;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.ListResource.ListMode;
import org.hl7.fhir.r4.model.ListResource.ListStatus;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
public class ErstdiagnoseEvidenzListMapper extends ObdsToFhirMapper {

  protected ErstdiagnoseEvidenzListMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public ListResource map(
      @NonNull String patientId,
      @NonNull String tumorId,
      @NonNull Reference patient,
      @NonNull List<Reference> evidenceReferences) {
    var list = new ListResource();
    list.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoListeEvidenzErstdiagnose());

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getErstdiagnoseEvidenzListId())
            .setValue(slugifier.slugify(patientId + "-" + tumorId));
    list.addIdentifier(identifier);
    list.setId(computeResourceIdFromIdentifier(identifier));

    list.setSubject(patient);

    list.setStatus(ListStatus.CURRENT);
    list.setMode(ListMode.SNAPSHOT);

    for (var reference : evidenceReferences) {
      list.addEntry().setItem(reference);
    }

    return list;
  }
}
