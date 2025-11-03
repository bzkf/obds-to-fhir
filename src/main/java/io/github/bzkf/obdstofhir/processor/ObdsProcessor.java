package io.github.bzkf.obdstofhir.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.mapper.*;
import io.github.bzkf.obdstofhir.model.Meldeanlass;
import io.github.bzkf.obdstofhir.model.MeldungExport;
import io.github.bzkf.obdstofhir.model.MeldungExportList;
import io.github.bzkf.obdstofhir.serde.MeldungExportListSerde;
import io.github.bzkf.obdstofhir.serde.MeldungExportSerde;
import java.util.*;
import java.util.function.BiFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    value = "obds.process-from-directory.enabled",
    havingValue = "false",
    matchIfMissing = true)
@Configuration
public class ObdsProcessor extends ObdsToFhirMapper {

  private static final FhirContext ctx = FhirContext.forR4();

  @Value("${spring.profiles.active}")
  private String profile;

  private final ObdsMedicationStatementMapper onkoMedicationStatementMapper;
  private final ObdsObservationMapper onkoObservationMapper;
  private final ObdsProcedureMapper onkoProcedureMapper;
  private final ObdsPatientMapper onkoPatientMapper;
  private final ObdsConditionMapper onkoConditionMapper;

  protected ObdsProcessor(
      FhirProperties fhirProperties,
      ObdsMedicationStatementMapper onkoMedicationStatementMapper,
      ObdsObservationMapper onkoObservationMapper,
      ObdsProcedureMapper onkoProcedureMapper,
      ObdsPatientMapper onkoPatientMapper,
      ObdsConditionMapper onkoConditionMapper) {
    super(fhirProperties);
    this.onkoMedicationStatementMapper = onkoMedicationStatementMapper;
    this.onkoObservationMapper = onkoObservationMapper;
    this.onkoProcedureMapper = onkoProcedureMapper;
    this.onkoPatientMapper = onkoPatientMapper;
    this.onkoConditionMapper = onkoConditionMapper;
  }

  @Bean
  public BiFunction<
          KTable<String, MeldungExport>, KTable<String, Bundle>, KStream<String, Bundle>[]>
      getMeldungExportObdsProcessor() {

    return (stringOnkoMeldungExpTable, stringOnkoObsBundles) -> {
      // return (stringOnkoMeldungExpTable) ->
      var filtered =
          stringOnkoMeldungExpTable
              // only process adt v2.x.x
              .filter((key, value) -> value.getXml_daten().getSchema_Version().matches("^2\\..*"))
              .filter(
                  (key, value) ->
                      value
                              .getXml_daten()
                              .getMenge_Patient()
                              .getPatient()
                              .getMenge_Meldung()
                              .getMeldung()
                              .getMenge_Tumorkonferenz()
                          == null); // ignore tumor conferences

      var output =
          filtered
              .groupBy(
                  (key, meldung) ->
                      KeyValue.pair(
                          "Struct{REFERENZ_NUMMER="
                              + getPatIdFromMeldung(meldung)
                              + ",TUMOR_ID="
                              + getTumorIdFromMeldung(meldung)
                              + "}",
                          meldung),
                  Grouped.with(Serdes.String(), new MeldungExportSerde()))
              .aggregate(
                  MeldungExportList::new,
                  (key, value, aggregate) -> aggregate.addElement(value),
                  (key, value, aggregate) -> aggregate.removeElement(value),
                  Materialized.with(Serdes.String(), new MeldungExportListSerde()))
              .toStream()
              .split(Named.as("out-"))
              .branch((k, v) -> v != null, Branched.as("Aggregate"))
              .noDefaultBranch();

      var branches = new ArrayList<KStream<String, Bundle>>();

      // always map Medication, Observation, Procedure, Condition...
      branches.addAll(
          List.of(
              output
                  .get("out-Aggregate")
                  .mapValues(this.getOnkoToMedicationStatementBundleMapper())
                  .filter((key, value) -> value != null),
              output
                  .get("out-Aggregate")
                  .mapValues(this.getOnkoToObservationBundleMapper())
                  .filter((key, value) -> value != null),
              output
                  .get("out-Aggregate")
                  .mapValues(this.getOnkoToProcedureBundleMapper())
                  .filter((key, value) -> value != null),
              output
                  .get("out-Aggregate")
                  .leftJoin(stringOnkoObsBundles, Pair::of)
                  .mapValues(this.getOnkoToConditionBundleMapper())
                  .filter((key, value) -> value != null)));

      // ...but only conditionally map Patient resources
      if (Objects.equals(profile, "patient")) {
        var patientStream =
            filtered
                // group by the patient number. This ensures that all meldungen of one patient
                // are processed by the same downstream consumer.
                .groupBy(
                    (key, meldung) -> KeyValue.pair(getPatIdFromMeldung(meldung), meldung),
                    Grouped.with(Serdes.String(), new MeldungExportSerde()))
                .aggregate(
                    MeldungExportList::new,
                    (key, value, aggregate) -> aggregate.addElement(value),
                    (key, value, aggregate) -> aggregate.removeElement(value),
                    Materialized.with(Serdes.String(), new MeldungExportListSerde()))
                .toStream()
                .mapValues(this.getOnkoToPatientBundleMapper())
                .filter((key, value) -> value != null)
                .selectKey((key, value) -> patientBundleKeySelector(value));

        branches.add(patientStream);
      }

      return branches.toArray(new KStream[branches.size()]);
    };
  }

  public ValueMapper<MeldungExportList, Bundle> getOnkoToMedicationStatementBundleMapper() {
    return meldungExporte -> {
      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungExporte,
              Arrays.asList(Meldeanlass.BEHANDLUNGSENDE, Meldeanlass.BEHANDLUNGSBEGINN),
              List.of(Meldeanlass.BEHANDLUNGSENDE, Meldeanlass.BEHANDLUNGSBEGINN));

      return onkoMedicationStatementMapper.mapOnkoResourcesToMedicationStatement(meldungExportList);
    };
  }

  public ValueMapper<MeldungExportList, Bundle> getOnkoToProcedureBundleMapper() {
    return meldungExporte -> {
      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungExporte,
              Arrays.asList(Meldeanlass.BEHANDLUNGSENDE, Meldeanlass.BEHANDLUNGSBEGINN),
              List.of(Meldeanlass.BEHANDLUNGSENDE, Meldeanlass.BEHANDLUNGSBEGINN));
      return onkoProcedureMapper.mapOnkoResourcesToProcedure(meldungExportList);
    };
  }

  public ValueMapper<MeldungExportList, Bundle> getOnkoToObservationBundleMapper() {
    return meldungExporte -> {
      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungExporte,
              Arrays.asList(
                  Meldeanlass.BEHANDLUNGSENDE,
                  Meldeanlass.STATUSAENDERUNG,
                  Meldeanlass.DIAGNOSE,
                  Meldeanlass.TOD),
              null);

      return onkoObservationMapper.mapOnkoResourcesToObservation(meldungExportList);
    };
  }

  public ValueMapper<MeldungExportList, Bundle> getOnkoToPatientBundleMapper() {
    return meldungExporte ->
        onkoPatientMapper.mapOnkoResourcesToPatient(meldungExporte.getElements());
  }

  public ValueMapper<Pair<MeldungExportList, Bundle>, Bundle> getOnkoToConditionBundleMapper() {
    return meldungPair -> {
      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungPair.getLeft(),
              Arrays.asList(
                  Meldeanlass.DIAGNOSE, Meldeanlass.BEHANDLUNGSENDE, Meldeanlass.STATUSAENDERUNG),
              null);

      return onkoConditionMapper.mapOnkoResourcesToCondition(
          meldungExportList, meldungPair.getRight());
    };
  }

  private static String patientBundleKeySelector(Bundle bundle) {
    var patients = BundleUtil.toListOfResourcesOfType(ctx, bundle, Patient.class);

    if (patients.size() != 1) {
      throw new RuntimeException(
          String.format("A patient bundle contains %d resources instead of 1", patients.size()));
    }
    var patient = patients.getFirst();
    return String.format("%s/%s", patient.getResourceType(), patient.getId());
  }
}
