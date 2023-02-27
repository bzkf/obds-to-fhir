package org.miracum.streams.ume.onkoadttofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.util.BundleUtil;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import org.hl7.fhir.common.hapi.validation.support.*;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ResourceUtils;

@SpringBootTest(classes = {FhirProperties.class})
@EnableConfigurationProperties(value = {FhirProperties.class})
public class OnkoMedicationStatementProcessorTest {

  private static final Logger log = LoggerFactory.getLogger(OnkoMedicationStatementProcessorTest.class);

  private final FhirProperties fhirProps;
  private final FhirContext ctx = FhirContext.forR4();

  private static FhirValidator validator;

  @Autowired
  public OnkoMedicationStatementProcessorTest(FhirProperties fhirProperties) {
    this.fhirProps = fhirProperties;
  }


  @BeforeAll
  static void setUp() throws FileNotFoundException {
    var ctx = FhirContext.forR4();
    validator = ctx.newValidator();

    var validationSupportChain =
            new ValidationSupportChain(
                    new DefaultProfileValidationSupport(ctx),
                    getProfiles(ctx),
                    new SnapshotGeneratingValidationSupport(ctx),
                    new InMemoryTerminologyServerValidationSupport(ctx),
                    new CommonCodeSystemsTerminologyService(ctx));

    var instanceValidator = new FhirInstanceValidator(validationSupportChain);
    validator.registerValidatorModule(instanceValidator);
  }

  private static PrePopulatedValidationSupport getProfiles(FhirContext ctx)
          throws FileNotFoundException {
    var currentRelativePath = Paths.get("");
    var basePath = currentRelativePath.toAbsolutePath().toString();
    var parser = ctx.newJsonParser();

    // var folderCS = new File(basePath + "/src/test/resources/CodeSystems");
    var folder = new File(basePath + "/src/test/resources/profiles");
    var folderVS = new File(basePath + "/src/test/resources/ValueSets");
    var prepop = new PrePopulatedValidationSupport(ctx);

    for (final var fileEntry : Objects.requireNonNull(folder.listFiles())) {
      var struct =
              parser.parseResource(
                      StructureDefinition.class, new FileReader(fileEntry.getAbsolutePath()));
      prepop.addStructureDefinition(struct);
    }

    // ToDo: Often the valuesets are included in the codesystem resources.
    //  However, the validator somehow does not find them in there.
    // Validation against ValueSet
    for (final var fileEntry : Objects.requireNonNull(folderVS.listFiles())) {
      var valueSet =
              parser.parseResource(ValueSet.class, new FileReader(fileEntry.getAbsolutePath()));
      prepop.addValueSet(valueSet);
    }

    return prepop;
  }

  private boolean isValid(Bundle fhirBundle) {
    var result = validator.validateWithResult(fhirBundle);
    var valResultMessages = result.getMessages();

    for (SingleValidationMessage message : valResultMessages) {
      log.error(
              "issue: "
                      + message.getSeverity()
                      + " - "
                      + message.getLocationString()
                      + " - "
                      + message.getMessage());
    }

    return result.isSuccessful();
  }



  static Stream<Arguments> generateTestData() {
    return Stream.of(
        Arguments.of(Arrays.asList("008_Pat3_Tumor1_Behandlungsende_SYST.xml"), 5, "CI"),
        Arguments.of(
            Arrays.asList(
                "001_1.Pat_2Tumoren_TumorID_1_Diagnose.xml",
                "002_1.Pat_2Tumoren_TumorID_2_Diagnose.xml"),
            0,
            null));
  }

  @ParameterizedTest
  @MethodSource("generateTestData")
  void mapMedicationStatement_withGivenAdtXml(
      List<String> xmlFileNames, int expectedMedStCount, String expectedCategory)
      throws IOException {

    MeldungExportList meldungExportList = new MeldungExportList();

    int payloadId = 1;

    for (var xmlFileName : xmlFileNames) {
      File xmlFile = ResourceUtils.getFile("classpath:" + xmlFileName);
      String xmlContent = new String(Files.readAllBytes(xmlFile.toPath()));

      Map<String, Object> payloadOnkoRessource = new HashMap<>();
      payloadOnkoRessource.put("ID", payloadId);
      payloadOnkoRessource.put("REFERENZ_NUMMER", "012345678");
      payloadOnkoRessource.put("LKR_MELDUNG", 123);
      payloadOnkoRessource.put("VERSIONSNUMMER", 1);
      payloadOnkoRessource.put("XML_DATEN", xmlContent);

      MeldungExport meldungExport = new MeldungExport();
      meldungExport.getPayload(payloadOnkoRessource);
      meldungExportList.addElement(meldungExport);

      payloadId++;
    }

    OnkoMedicationStatementProcessor medicationStatementProcessor =
        new OnkoMedicationStatementProcessor(fhirProps);

    var resultBundle =
        medicationStatementProcessor.getOnkoToMedicationStBundleMapper().apply(meldungExportList);

    if (expectedMedStCount == 0) {
      assertThat(resultBundle).isNull();
    } else {
      var medicationStatementList =
          BundleUtil.toListOfResourcesOfType(ctx, resultBundle, MedicationStatement.class);

      assertThat(medicationStatementList).hasSize(expectedMedStCount);
      assertThat(medicationStatementList.get(0).getCategory().getCoding().get(0).getCode())
          .isEqualTo(expectedCategory);

      //TODO add missing structure definitions
      //assertThat(isValid(resultBundle)).isTrue();
    }
  }
}
