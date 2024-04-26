package org.miracum.streams.ume.obdstofhir.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import java.util.*;
import java.util.function.BiFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.*;
import org.miracum.streams.ume.obdstofhir.model.Meldeanlass;
import org.miracum.streams.ume.obdstofhir.model.MeldungExport;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportList;
import org.miracum.streams.ume.obdstofhir.serde.MeldungExportListSerde;
import org.miracum.streams.ume.obdstofhir.serde.MeldungExportSerde;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class ObdsProcessor extends ObdsToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(ObdsProcessor.class);
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
      var output =
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
                          == null) // ignore tumor conferences
              .groupBy(
                  (key, value) ->
                      KeyValue.pair(
                          "Struct{REFERENZ_NUMMER="
                              + getPatIdFromAdt(value)
                              + ",TUMOR_ID="
                              + getTumorIdFromAdt(value)
                              + "}",
                          value),
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

      if (Objects.equals(profile, "patient")) {
        return new KStream[] {
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
              .filter((key, value) -> value != null),
          output
              .get("out-Aggregate")
              .mapValues(this.getOnkoToPatientBundleMapper())
              .filter((key, value) -> value != null)
              .selectKey((key, value) -> patientBundleKeySelector(value))
        };
      } else {
        return new KStream[] {
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
              .filter((key, value) -> value != null)
        };
      }
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
    return meldungExporte -> {
      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungExporte,
              Arrays.asList(
                  Meldeanlass.TOD,
                  Meldeanlass.BEHANDLUNGSENDE,
                  Meldeanlass.STATUSAENDERUNG,
                  Meldeanlass.DIAGNOSE),
              null);

      return onkoPatientMapper.mapOnkoResourcesToPatient(meldungExportList);
    };
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

    if (patients.isEmpty() || patients.size() > 1) {
      throw new RuntimeException("A patient bundle contains more or less than 1 resource");
    }
    var patient = patients.get(0);
    return String.format("%s/%s", patient.getResourceType(), patient.getId());
  }
}
