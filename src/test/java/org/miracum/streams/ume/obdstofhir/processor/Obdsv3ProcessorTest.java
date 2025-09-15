package org.miracum.streams.ume.obdstofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serdes.StringSerde;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.ResourceType;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.miracum.kafka.serializers.KafkaFhirDeserializer;
import org.miracum.kafka.serializers.KafkaFhirSerde;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.Obdsv2v3MapperConfig;
import org.miracum.streams.ume.obdstofhir.Obdsv2v3MapperProperties;
import org.miracum.streams.ume.obdstofhir.WriteGroupedObdsToKafkaConfig;
import org.miracum.streams.ume.obdstofhir.mapper.mii.*;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportListV3;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportV3;
import org.miracum.streams.ume.obdstofhir.serde.MeldungExportV3Serde;
import org.miracum.streams.ume.obdstofhir.serde.Obdsv3Deserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.kafka.support.serializer.JsonSerializer;

@SpringBootTest(
    classes = {
      Obdsv3Processor.class,
      FhirProperties.class,
      ObdsToFhirBundleMapper.class,
      PatientMapper.class,
      ConditionMapper.class,
      SystemischeTherapieProcedureMapper.class,
      SystemischeTherapieMedicationStatementMapper.class,
      StrahlentherapieMapper.class,
      TodMapper.class,
      LeistungszustandMapper.class,
      OperationMapper.class,
      ResidualstatusMapper.class,
      Obdsv3Deserializer.class,
      JsonSerializer.class,
      FernmetastasenMapper.class,
      GradingObservationMapper.class,
      HistologiebefundMapper.class,
      LymphknotenuntersuchungMapper.class,
      SpecimenMapper.class,
      VerlaufshistologieObservationMapper.class,
      StudienteilnahmeObservationMapper.class,
      VerlaufObservationMapper.class,
      GenetischeVarianteMapper.class,
      TumorkonferenzMapper.class,
      TNMMapper.class,
      GleasonScoreMapper.class,
      WeitereKlassifikationMapper.class,
      Obdsv2v3MapperConfig.class,
      Obdsv2v3MapperProperties.class,
      ErstdiagnoseEvidenzListMapper.class,
    })
@EnableConfigurationProperties(value = {FhirProperties.class, WriteGroupedObdsToKafkaConfig.class})
public class Obdsv3ProcessorTest {

  private static final String INPUT_TOPIC_NAME = "meldung-obds";
  private static final String OUTPUT_TOPIC_NAME = "onko-fhir";
  private static final FhirContext ctx = FhirContext.forR4();

  @Autowired private Obdsv3Processor processor;

  @Value("classpath:obds3/*.xml")
  private Resource[] obdsResources;

  @Value("classpath:adt/*.xml")
  private Resource[] adtResources;

  public Resource getResourceByName(String filename, Resource[] resources) {
    return Arrays.stream(resources)
        .filter(resource -> Objects.equals(resource.getFilename(), filename))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Test OBDS resource not found: " + filename));
  }

  private void pipeInput(
      TestInputTopic<String, Object> inputTopic,
      String key,
      String resourceName,
      Resource[] resources,
      String lkrnum,
      int versnum)
      throws IOException, JSONException {
    inputTopic.pipeInput(
        key,
        buildMeldungExport(
            getResourceByName(resourceName, resources), key, "12356789", lkrnum, versnum));
  }

  @Test
  void testMeldungExportObdsV3Processor_MapsObdsToBundle() throws IOException {
    try (var driver =
        buildStream(
            processor.getMeldungExportObdsV3Processor(), INPUT_TOPIC_NAME, OUTPUT_TOPIC_NAME)) {

      var inputTopic =
          driver.createInputTopic(INPUT_TOPIC_NAME, new StringSerializer(), new JsonSerializer<>());
      var outputTopic =
          driver.createOutputTopic(
              OUTPUT_TOPIC_NAME, new StringDeserializer(), new KafkaFhirDeserializer());

      // pipe test data
      pipeInput(inputTopic, "key1", "test1.xml", obdsResources, "123", 1);

      assertThat(outputTopic.readRecordsToList()).isNotEmpty();
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testMeldungExportObdsV3Processor_MapsAdtToBundle() throws IOException {
    try (var driver =
        buildStream(
            processor.getMeldungExportObdsV3Processor(), INPUT_TOPIC_NAME, OUTPUT_TOPIC_NAME)) {

      var inputTopic =
          driver.createInputTopic(INPUT_TOPIC_NAME, new StringSerializer(), new JsonSerializer<>());
      var outputTopic =
          driver.createOutputTopic(
              OUTPUT_TOPIC_NAME, new StringDeserializer(), new KafkaFhirDeserializer());

      // pipe test data
      pipeInput(inputTopic, "key1", "Testpatient_ADT_Diagnose.xml", adtResources, "123", 1);

      assertThat(outputTopic.readRecordsToList()).isNotEmpty();
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testVersionsnummerPrioritization() throws IOException {
    try (var driver =
        buildStream(
            processor.getMeldungExportObdsV3Processor(), INPUT_TOPIC_NAME, OUTPUT_TOPIC_NAME)) {

      var inputTopic =
          driver.createInputTopic(INPUT_TOPIC_NAME, new StringSerializer(), new JsonSerializer<>());
      var outputTopic =
          driver.createOutputTopic(
              OUTPUT_TOPIC_NAME, new StringDeserializer(), new KafkaFhirDeserializer());

      // pipe test data
      pipeInput(inputTopic, "key1", "Priopatient_1.xml", obdsResources, "123", 1); // gitleaks:allow
      pipeInput(inputTopic, "key2", "Priopatient_2.xml", obdsResources, "123", 3); // gitleaks:allow
      pipeInput(inputTopic, "key3", "Priopatient_3.xml", obdsResources, "123", 2); // gitleaks:allow

      var outputRecords = outputTopic.readKeyValuesToList();
      assertThat(outputRecords).hasSize(3);

      var kafkaKeys = outputRecords.stream().map(record -> record.key).distinct().toList();
      assertThat(kafkaKeys).hasSize(1); // Ensure all records have the same key

      var conditions =
          outputRecords.stream()
              .map(
                  record ->
                      BundleUtil.toListOfResourcesOfType(
                              ctx, (Bundle) record.value, Condition.class)
                          .getFirst()
                          .getCode()
                          .getCoding()
                          .getFirst()
                          .getCode())
              .toList();

      // validate prioritization
      assertThat(conditions).containsExactly("C50.9", "C61", "C61");
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  private TopologyTestDriver buildStream(
      Function<KTable<String, MeldungExportV3>, KStream<String, Bundle>> processor,
      String inputTopic,
      String outputTopic) {

    // StreamConfig props
    var props = new Properties();
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, StringSerde.class);
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, KafkaFhirSerde.class);

    var builder = new StreamsBuilder();
    final KTable<String, MeldungExportV3> stream =
        builder.table(inputTopic, Consumed.with(Serdes.String(), new MeldungExportV3Serde()));

    processor.apply(stream).to(outputTopic);

    return new TopologyTestDriver(builder.build(), props);
  }

  private MeldungExportV3 buildMeldungExport(
      Resource xmlObds, String id, String refNummer, String lkrMeldung, int versionsnummer)
      throws IOException, JSONException {
    var meldung =
        new JSONObject()
            .put("ID", id)
            .put("REFERENZ_NUMMER", refNummer)
            .put("LKR_MELDUNG", lkrMeldung)
            .put("VERSIONSNUMMER", versionsnummer)
            .put("XML_DATEN", xmlObds.getContentAsString(StandardCharsets.UTF_8))
            .toString();

    var mapper = new ObjectMapper();
    return mapper.readValue(meldung, MeldungExportV3.class);
  }

  private MeldungExportV3 getTestObdsFromString(int id, String testObds)
      throws IOException, JSONException {

    var meldung =
        new JSONObject().put("ID", String.valueOf(id)).put("XML_DATEN", testObds).toString();

    var mapper = new ObjectMapper();
    return mapper.readValue(meldung, MeldungExportV3.class);
  }

  @Test
  void getMeldungExportObdsV3Processor_MultiInputToBundle() throws IOException {
    try (var driver =
        buildStream(
            processor.getMeldungExportObdsV3Processor(), INPUT_TOPIC_NAME, OUTPUT_TOPIC_NAME)) {

      var inputTopic =
          driver.createInputTopic(INPUT_TOPIC_NAME, new StringSerializer(), new JsonSerializer<>());
      var outputTopic =
          driver.createOutputTopic(
              OUTPUT_TOPIC_NAME, new StringDeserializer(), new KafkaFhirDeserializer());

      // pipe test data
      pipeInput(inputTopic, "key1", "GroupSequence01.xml", obdsResources, "123", 1);
      pipeInput(inputTopic, "key2", "GroupSequence02.xml", obdsResources, "124", 1);
      pipeInput(inputTopic, "key3", "GroupSequence03.xml", obdsResources, "125", 1);
      pipeInput(inputTopic, "key4", "GroupSequence04.xml", obdsResources, "126", 1);

      // get records from output topic
      var outputRecords = outputTopic.readKeyValuesToList();

      // get bundle with more than one entry
      Bundle bundle =
          (Bundle)
              outputRecords.stream()
                  .filter(a -> ((Bundle) a.value).getEntry().size() > 1)
                  .findAny()
                  .get()
                  .value;

      // assert coding exists
      assertThat(outputRecords).isNotEmpty();
      assertThat(
              bundle.getEntry().stream()
                  .filter(a -> a.getResource().getResourceType() != ResourceType.Patient)
                  .count())
          .as("result should contain more than a Patient resource")
          .isGreaterThan(0);

      var keys = outputRecords.stream().map(entry -> entry.key).toList();

      var kafkaMessageKeys = new HashSet<>();
      AtomicInteger dulpicateKeyCount = new AtomicInteger();
      keys.forEach(
          keyInTopic -> {
            if (kafkaMessageKeys.add(keyInTopic)) {
              dulpicateKeyCount.getAndIncrement();
            }
          });
      assertThat(dulpicateKeyCount.get()).isLessThan(2);

    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "Testpatient_Tumorkonferenz2.xml:BEHANDLUNGSENDE",
        "Testpatient_Tumorkonferenz1.xml:BEHANDLUNGSBEGINN",
      },
      delimiter = ':')
  void testTumorkonferenz(String inputXmlResourceName, String expectedMeldeAnlass)
      throws IOException, JSONException {
    final MeldungExportListV3 meldungExportListV3 = new MeldungExportListV3();
    meldungExportListV3.add(
        buildMeldungExport(
            getResourceByName(inputXmlResourceName, obdsResources), "1", "12356789", "123", 1));

    var result = processor.getTumorKonferenzmeldungen(meldungExportListV3);
    assertThat(result.getFirst().getTumorkonferenz().getMeldeanlass().name())
        .isEqualTo(Meldeanlass.valueOf(expectedMeldeAnlass).name());
  }

  @Test
  public void testMultipleStMeldungen() throws IOException {
    try (var driver =
        buildStream(
            processor.getMeldungExportObdsV3Processor(), INPUT_TOPIC_NAME, OUTPUT_TOPIC_NAME)) {

      var inputTopic =
          driver.createInputTopic(INPUT_TOPIC_NAME, new StringSerializer(), new JsonSerializer<>());
      var outputTopic =
          driver.createOutputTopic(
              OUTPUT_TOPIC_NAME, new StringDeserializer(), new KafkaFhirDeserializer());

      // pipe test data
      pipeInput(inputTopic, "key1", "Test_ST_1.xml", obdsResources, "1", 1);
      pipeInput(inputTopic, "key2", "Test_ST_2.xml", obdsResources, "2", 1);
      pipeInput(inputTopic, "key3", "Test_ST_2_2.xml", obdsResources, "3", 1);

      var outputRecords = outputTopic.readKeyValuesToList();
      assertThat(outputRecords).hasSize(3);

      // Patient + ST_1-Procedure behandlungsende
      validateMultiStBundle(
          (Bundle) outputRecords.getFirst().value, 4, Procedure.ProcedureStatus.COMPLETED);
      // Patient + ST_1-Procedure behandlungsende + ST_2-Procedure behandlungsbeginn
      validateMultiStBundle(
          (Bundle) outputRecords.get(1).value,
          5,
          Procedure.ProcedureStatus.COMPLETED,
          Procedure.ProcedureStatus.INPROGRESS);
      // Patient + ST_1-Procedure behandlungsende + ST_2-Procedure behandlungsbeginn
      validateMultiStBundle(
          (Bundle) outputRecords.getLast().value,
          5,
          Procedure.ProcedureStatus.COMPLETED,
          Procedure.ProcedureStatus.COMPLETED);
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  private void validateMultiStBundle(
      Bundle bundle, int expectedSize, Procedure.ProcedureStatus... expectedStatuses) {
    assertThat(bundle.getEntry()).hasSize(expectedSize);

    var procedureStatuses =
        BundleUtil.toListOfResourcesOfType(ctx, bundle, Procedure.class).stream()
            .map(Procedure::getStatus)
            .toList();

    assertThat(procedureStatuses).containsExactly(expectedStatuses);
  }

  @Test
  void testMultiSystTherapieIds() throws IOException, JSONException {
    final String description =
        "Es wurden 2 Systemtherapie mit unterschiedlicher Id durchgeführt. "
            + "Daher auch 2 Meldungen erwartet";

    final MeldungExportListV3 meldungExportListV3 = new MeldungExportListV3();
    meldungExportListV3.add(
        buildMeldungExport(
            getResourceByName("Test_SysT_0.xml", obdsResources), "1", "12356789", "123", 1));
    meldungExportListV3.add(
        buildMeldungExport(
            getResourceByName("Test_SysT_1.xml", obdsResources), "2", "12356789", "124", 1));
    meldungExportListV3.add(
        buildMeldungExport(
            getResourceByName("Test_SysT_2.xml", obdsResources), "3", "12356789", "125", 1));
    var result = processor.getSystemtherapieMeldungen(meldungExportListV3);

    assertThat(result).as(description).isNotNull();
    assertThat(result.size()).as(description).isEqualTo(2);
    final Optional<Meldung> firstMeldung_Id1 =
        result.stream()
            .filter(meldung -> "101_IN-1".equals(meldung.getSYST().getSYSTID()))
            .findAny();
    assertThat(firstMeldung_Id1)
        .as("Meldung zu SystemTherapie mit ID 101_IN-1 muss vorhanden sein.")
        .isNotEmpty();
    assertThat(firstMeldung_Id1.get().getSYST().getMeldeanlass().name())
        .as(
            "Meldeanlass 'behandlungsende' wird Meldung mit Meldeanlass 'behandlungsbeginn' vorgezogen.")
        .isEqualTo(Meldeanlass.BEHANDLUNGSENDE.name());

    final Optional<Meldung> secondMeldung_Id1 =
        result.stream()
            .filter(meldung -> "101_IN-2".equals(meldung.getSYST().getSYSTID()))
            .findAny();
    assertThat(secondMeldung_Id1)
        .as("Meldung zu SystemTherapie mit ID 101_IN-2 muss vorhanden sein.")
        .isNotEmpty();
    assertThat(firstMeldung_Id1.get().getSYST().getMeldeanlass().name())
        .isEqualTo(Meldeanlass.BEHANDLUNGSENDE.name());
  }
}
