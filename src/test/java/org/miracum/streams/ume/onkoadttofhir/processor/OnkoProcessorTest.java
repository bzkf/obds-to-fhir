package org.miracum.streams.ume.onkoadttofhir.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.Objects;
import org.hl7.fhir.common.hapi.validation.support.*;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class OnkoProcessorTest {

  private static final Logger log = LoggerFactory.getLogger(OnkoProcessorTest.class);

  private static FhirValidator validator;

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

  protected boolean isValid(Bundle fhirBundle) {
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
}
