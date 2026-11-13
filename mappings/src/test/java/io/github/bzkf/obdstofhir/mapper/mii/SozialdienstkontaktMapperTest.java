package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.ModulAllgemeinTyp;
import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.ArrayList;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class SozialdienstkontaktMapperTest extends MapperTest {
  private static SozialdienstkontaktMapper sut;

  @BeforeAll
  static void beforeAll(@Autowired FhirProperties fhirProps) {
    sut = new SozialdienstkontaktMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({
    // a contact in the Verlauf, OP, ST and SYST; "N" in the Diagnose
    "Testpatient_Mamma.xml, 4",
    // a contact in the Diagnose of a tumor other than Mamma
    "Testpatient_Rektum.xml, 1",
    // only "N": in the Diagnose, the Verlauf and both SYST
    "Testpatient_CLL.xml, 0",
  })
  void map_withGivenObds_shouldCreateValidProcedures(String sourceFile, int expectedCount)
      throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();
    var subject = new Reference("Patient/any");
    var diagnose = new Reference("Condition/any");

    final var procedures = new ArrayList<Procedure>();
    for (var meldung : obdsPatient.getMengeMeldung().getMeldung()) {
      var modulAllgemein = getModulAllgemein(meldung);
      if (modulAllgemein != null && modulAllgemein.getSozialdienstkontakt() != null) {
        sut.map(modulAllgemein.getSozialdienstkontakt(), meldung.getMeldungID(), subject, diagnose)
            .ifPresent(procedures::add);
      }
    }

    assertThat(procedures).hasSize(expectedCount);
    verifyAll(procedures, sourceFile);
  }

  /** A Meldung has exactly one section, and only these five can contain a Modul_Allgemein. */
  private static ModulAllgemeinTyp getModulAllgemein(Meldung meldung) {
    if (meldung.getDiagnose() != null) {
      return meldung.getDiagnose().getModulAllgemein();
    }
    if (meldung.getVerlauf() != null) {
      return meldung.getVerlauf().getModulAllgemein();
    }
    if (meldung.getOP() != null) {
      return meldung.getOP().getModulAllgemein();
    }
    if (meldung.getST() != null) {
      return meldung.getST().getModulAllgemein();
    }
    if (meldung.getSYST() != null) {
      return meldung.getSYST().getModulAllgemein();
    }
    return null;
  }
}
