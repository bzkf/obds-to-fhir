package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.SYSTTyp;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.SubstanzToAtcMapper;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class, SubstanzToAtcMapper.class})
@EnableConfigurationProperties
class SystemischeTherapieMedicationStatementMapperTest extends MapperTest {
  private static SystemischeTherapieMedicationStatementMapper sut;

  @BeforeAll
  static void beforeAll(
      @Autowired FhirProperties fhirProps, @Autowired SubstanzToAtcMapper substanzToAtcMapper) {
    sut = new SystemischeTherapieMedicationStatementMapper(fhirProps, substanzToAtcMapper);
  }

  @ParameterizedTest
  @CsvSource({
    "Testpatient_1.xml",
    "Testpatient_2.xml",
    "Testpatient_3.xml",
    "Testpatient_Duplicate_OPS_ICDO_Substanzen.xml",
    "Test_SysT_0.xml",
    "Test_SysT_1.xml",
    "Test_SysT_2.xml",
  })
  void map_withGivenObds_shouldCreateValidMedicationStatement(String sourceFile)
      throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var patient = new Reference("Patient/any");
    var procedure = new Reference("Procedure/any");
    var primaryCondition = new Reference("Condition/any");
    var systMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getSYST() != null)
            .findFirst()
            .get();
    var list = sut.map(systMeldung.getSYST(), patient, procedure, primaryCondition);

    List<Resource> flattened =
        list.stream()
            .flatMap(
                r -> Stream.concat(Stream.of(r.medicationStatement()), r.medication().stream()))
            .map(Resource.class::cast)
            .toList();

    verifyAll(flattened, sourceFile);
  }

  @Test
  void map_withSameAtcCode_shouldCreateTheSameMedicationRegardlessOfTheBezeichnung() {
    var first = mapMedication("L01XA03", "2024", "Oxaliplatin");
    var second = mapMedication("L01XA03", "2024", "Eloxatin");

    assertThat(first.equalsDeep(second)).isTrue();
    assertThat(first.getCode().hasText()).isFalse();
  }

  @Test
  void map_withSameAtcCodeButDifferentVersion_shouldCreateOneMedicationPerAtcCode() {
    var first = mapMedication("L01XA03", "2024", "Oxaliplatin");
    var second = mapMedication("L01XA03", "2025", "Oxaliplatin");

    assertThat(first.getId()).isEqualTo(second.getId());
    assertThat(first.getIdentifierFirstRep().getValue()).isEqualTo("L01XA03");
    assertThat(second.getIdentifierFirstRep().getValue()).isEqualTo("L01XA03");
  }

  @Test
  void map_shouldKeepTheBezeichnungOnTheMedicationStatement() {
    var statement = mapSubstanz("L01XA03", "2024", "Eloxatin").getFirst().medicationStatement();

    assertThat(statement.getMedicationReference().getDisplay()).isEqualTo("Eloxatin");
  }

  private static Medication mapMedication(String atcCode, String version, String bezeichnung) {
    return mapSubstanz(atcCode, version, bezeichnung).getFirst().medication().orElseThrow();
  }

  private static List<
          SystemischeTherapieMedicationStatementMapper.SystemischeTherapieMappingResults>
      mapSubstanz(String atcCode, String version, String bezeichnung) {
    var atc = new SYSTTyp.MengeSubstanz.Substanz.ATC();
    atc.setCode(atcCode);
    atc.setVersion(version);
    var substanz = new SYSTTyp.MengeSubstanz.Substanz();
    substanz.setATC(atc);
    substanz.setBezeichnung(bezeichnung);
    var menge = new SYSTTyp.MengeSubstanz();
    menge.getSubstanz().add(substanz);
    var syst = new SYSTTyp();
    syst.setSYSTID("1");
    syst.setMengeSubstanz(menge);

    return sut.map(
        syst,
        new Reference("Patient/any"),
        new Reference("Procedure/any"),
        new Reference("Condition/any"));
  }
}
