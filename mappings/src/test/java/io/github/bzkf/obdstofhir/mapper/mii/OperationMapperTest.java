package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.KomplikationICDTyp;
import de.basisdatensatz.obds.v3.KomplikationTyp;
import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.OPTyp;
import de.basisdatensatz.obds.v3.OPTyp.Komplikationen;
import de.basisdatensatz.obds.v3.OPTyp.Komplikationen.MengeKomplikation;
import de.basisdatensatz.obds.v3.OPTyp.MengeOPS.OPS;
import io.github.bzkf.obdstofhir.FhirProperties;
import java.io.IOException;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties
class OperationMapperTest extends MapperTest {
  private static OperationMapper sut;

  private static final Logger LOG = LoggerFactory.getLogger(OperationMapper.class);

  @BeforeAll
  static void beforeEach(@Autowired FhirProperties fhirProps) {
    sut = new OperationMapper(fhirProps);
  }

  @ParameterizedTest
  @CsvSource({
    "Testpatient_1.xml",
    "Testpatient_2.xml",
    "Testpatient_Duplicate_OPS_ICDO_Substanzen.xml",
    "GroupSequence04.xml"
  })
  void map_withGivenObds_shouldCreateValidProcedure(String sourceFile) throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/" + sourceFile);
    assertThat(resource).isNotNull();

    System.out.println("Loaded resource: " + resource.getPath());

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);

    var obdsPatient = obds.getMengePatient().getPatient().getFirst();

    var subject = new Reference("Patient/any");
    var condition = new Reference("Condition/any");

    var opMeldung =
        obdsPatient.getMengeMeldung().getMeldung().stream()
            .filter(m -> m.getOP() != null)
            .findFirst()
            .get();

    // Map and get the list of procedures
    final var resultResources = sut.map(opMeldung.getOP(), subject, condition);

    assertThat(resultResources).isNotEmpty();
    LOG.info("Length of resultResources {}", resultResources.size());
    LOG.info("Number of OPS codes: {}", opMeldung);

    verifyAll(resultResources, sourceFile);
  }

  private static final String KOMPLIKATION_SYSTEM =
      "https://www.medizininformatik-initiative.de/fhir/ext/modul-onko/CodeSystem/mii-cs-onko-operation-komplikation";
  private static final String SEITENLOKALISATION_URL =
      "http://fhir.de/StructureDefinition/seitenlokalisation";

  @Test
  void map_withSideSuffix_shouldMoveItToTheSeitenlokalisationExtension() throws IOException {
    var op = readOp();
    var ops = op.getMengeOPS().getOPS();
    ops.clear();
    ops.add(ops("5-870.aL"));
    ops.add(ops("5-870.aR"));

    var children = childProcedures(map(op));

    assertThat(children).hasSize(2);
    // the left and the right one stay separate resources
    assertThat(children.stream().map(Procedure::getId).distinct()).hasSize(2);
    assertThat(children.stream().map(p -> p.getCode().getCodingFirstRep().getCode()))
        .containsOnly("5-870.a");
    assertThat(
            children.stream()
                .map(
                    p ->
                        (Coding)
                            p.getCode()
                                .getCodingFirstRep()
                                .getExtensionByUrl(SEITENLOKALISATION_URL)
                                .getValue()))
        .allMatch(
            c ->
                c.getSystem()
                    .equals("https://fhir.kbv.de/CodeSystem/KBV_CS_SFHIR_ICD_SEITENLOKALISATION"))
        .extracting(Coding::getCode)
        .containsExactlyInAnyOrder("L", "R");
  }

  @Test
  void map_withoutSideSuffix_shouldNotAddTheSeitenlokalisationExtension() throws IOException {
    var op = readOp();
    var ops = op.getMengeOPS().getOPS();
    ops.clear();
    ops.add(ops("5-870.a2"));

    var coding = childProcedures(map(op)).getFirst().getCode().getCodingFirstRep();

    assertThat(coding.getCode()).isEqualTo("5-870.a2");
    assertThat(coding.hasExtension(SEITENLOKALISATION_URL)).isFalse();
  }

  @Test
  void map_withKomplikationNeinOderUnbekannt_shouldSetItOnAllProcedures() throws IOException {
    var op = readOp();
    var komplikationen = new Komplikationen();
    komplikationen.setKomplikationNeinOderUnbekannt("N");
    op.setKomplikationen(komplikationen);

    var procedures = map(op);

    assertThat(procedures).hasSizeGreaterThan(1);
    assertThat(procedures)
        .allSatisfy(
            procedure -> {
              assertThat(procedure.getComplication()).hasSize(1);
              var coding = procedure.getComplicationFirstRep().getCodingFirstRep();
              assertThat(coding.getSystem()).isEqualTo(KOMPLIKATION_SYSTEM);
              assertThat(coding.getCode()).isEqualTo("N");
            });
  }

  @Test
  void map_withMengeKomplikation_shouldMapKuerzelAndIcd() throws IOException {
    var op = readOp();
    op.setKomplikationen(
        komplikationen(
            kuerzel("ABD"),
            icd("T81.4", "10 2021 GM"),
            icd("T81.5", "10 2019 WHO"),
            icd("T81.6", "Sonstige")));

    var complications = map(op).getFirst().getComplication();

    assertThat(complications).hasSize(4);

    var kuerzel = complications.get(0).getCodingFirstRep();
    assertThat(kuerzel.getSystem()).isEqualTo(KOMPLIKATION_SYSTEM);
    assertThat(kuerzel.getCode()).isEqualTo("ABD");

    var gm = complications.get(1).getCodingFirstRep();
    assertThat(gm.getSystem()).isEqualTo("http://fhir.de/CodeSystem/bfarm/icd-10-gm");
    assertThat(gm.getCode()).isEqualTo("T81.4");
    assertThat(gm.getVersion()).isEqualTo("2021");

    var who = complications.get(2).getCoding();
    assertThat(who).hasSize(2);
    assertThat(who.get(0).getCode()).isNull();
    assertThat(who.get(1).getSystem()).isEqualTo("http://hl7.org/fhir/sid/icd-10");
    assertThat(who.get(1).getCode()).isEqualTo("T81.5");
    assertThat(who.get(1).getVersion()).isEqualTo("2019");

    var sonstige = complications.get(3);
    assertThat(sonstige.getCodingFirstRep().getCode()).isNull();
    assertThat(sonstige.getText()).isEqualTo("T81.6");
  }

  @Test
  void map_withUnknownKuerzel_shouldKeepItAsTextInsteadOfThrowing() throws IOException {
    var op = readOp();
    op.setKomplikationen(komplikationen(kuerzel("XYZ")));

    CodeableConcept complication = map(op).getFirst().getComplicationFirstRep();

    assertThat(complication.hasCoding()).isFalse();
    assertThat(complication.getText()).isEqualTo("XYZ");
  }

  @Test
  void map_withoutKomplikationen_shouldNotSetComplication() throws IOException {
    var op = readOp();
    op.setKomplikationen(null);

    assertThat(map(op)).noneMatch(Procedure::hasComplication);
  }

  private OPTyp readOp() throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/Testpatient_1.xml");
    assertThat(resource).isNotNull();

    final var obds = xmlMapper().readValue(resource.openStream(), OBDS.class);
    return obds.getMengePatient().getPatient().getFirst().getMengeMeldung().getMeldung().stream()
        .filter(m -> m.getOP() != null)
        .findFirst()
        .orElseThrow()
        .getOP();
  }

  private static List<Procedure> map(OPTyp op) {
    return sut.map(op, new Reference("Patient/any"), new Reference("Condition/any"));
  }

  private static List<Procedure> childProcedures(List<Procedure> procedures) {
    return procedures.subList(1, procedures.size());
  }

  private static OPS ops(String code) {
    var ops = new OPS();
    ops.setCode(code);
    ops.setVersion("2024");
    return ops;
  }

  private static Komplikationen komplikationen(KomplikationTyp... komplikationen) {
    var menge = new MengeKomplikation();
    menge.getKomplikation().addAll(List.of(komplikationen));
    var result = new Komplikationen();
    result.setMengeKomplikation(menge);
    return result;
  }

  private static KomplikationTyp kuerzel(String kuerzel) {
    var komplikation = new KomplikationTyp();
    komplikation.setKuerzel(kuerzel);
    return komplikation;
  }

  private static KomplikationTyp icd(String code, String version) {
    var icd = new KomplikationICDTyp();
    icd.setCode(code);
    icd.setVersion(version);
    var komplikation = new KomplikationTyp();
    komplikation.setICD(icd);
    return komplikation;
  }
}
