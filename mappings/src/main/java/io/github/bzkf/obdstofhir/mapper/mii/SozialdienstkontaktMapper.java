package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.DatumNTyp;
import de.medizininformatikinitiative.kerndatensatz.onkologie.Onkologie;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.ObdsToFhirMapper;
import java.util.Optional;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

/**
 * Maps the oBDS {@code Modul_Allgemein.Sozialdienstkontakt} to a Procedure of the {@code
 * MII_PR_Onko_Mamma_Sozialdienst} profile.
 *
 * <p>The profile is named after the Mamma module, but neither it nor the oBDS schema ties the
 * contact to a tumor entity: every Diagnose, Verlauf, OP, ST and SYST may carry a {@code
 * Modul_Allgemein}. The contact is mapped whatever the diagnosis is.
 *
 * <p>The profile derives from {@code MII_PR_Onko_Operation} and so requires the Intention extension
 * of an OP (oBDS 13.1). A Sozialdienstkontakt has no intention in oBDS: the one of a surrounding
 * OP, ST or SYST describes that therapy, and the Diagnose and Verlauf have none. The extension is
 * therefore set to {@code X} (fehlende Angabe), the one code of the bound value set that states no
 * intention.
 */
@Service
public class SozialdienstkontaktMapper extends ObdsToFhirMapper {

  public SozialdienstkontaktMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  /**
   * Creates the Procedure for a contact that took place.
   *
   * @return empty for {@code N} (kein Kontakt): the profile describes a contact and requires its
   *     date
   */
  public Optional<Procedure> map(
      @NonNull DatumNTyp sozialdienstkontakt,
      @NonNull String meldungId,
      @NonNull Reference patient,
      @NonNull Reference condition) {
    verifyReference(patient, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var performed = convertObdsDatumToDateTimeType(sozialdienstkontakt.getDatum());
    if (performed.isEmpty()) {
      return Optional.empty();
    }

    var procedure = new Procedure();
    procedure.getMeta().addProfile(Onkologie.Profiles.miiPrOnkoMammaSozialdienst());
    setIdentifierAndId(
        procedure,
        fhirProperties.getSystems().getIdentifiers().getSozialdienstkontaktProcedureId(),
        meldungId + "-modul-allgemein-sozialdienstkontakt");
    procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
    procedure.setSubject(patient);
    procedure.setPerformed(performed.get());
    procedure.addReasonReference(condition);

    // the display is pinned by the profile's patternCoding
    procedure.setCode(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("306238000")
                .setDisplay("Referral to Social Services (procedure)")));

    procedure.addExtension(
        Onkologie.Extensions.miiExOnkoOperationIntention(
            Onkologie.CodeSystems.MiiCsOnkoIntention.X));

    return Optional.of(procedure);
  }
}
