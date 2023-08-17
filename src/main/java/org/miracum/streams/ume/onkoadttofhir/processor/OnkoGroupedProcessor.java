package org.miracum.streams.ume.onkoadttofhir.processor;

import java.util.*;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.Bundle;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.mapper.*;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportListSerde;
import org.miracum.streams.ume.onkoadttofhir.serde.MeldungExportSerde;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoGroupedProcessor extends OnkoToFhirMapper {

  private static final Logger LOG = LoggerFactory.getLogger(OnkoGroupedProcessor.class);

  @Value("${spring.profiles.active}")
  private String profile;

  private final OnkoMedicationStatementMapper onkoMedicationStatementMapper;
  private final OnkoObservationMapper onkoObservationMapper;
  private final OnkoPatientMapper onkoPatientMapper;
  private final OnkoProcedureMapper onkoProcedureMapper;

  @Autowired
  protected OnkoGroupedProcessor(
      FhirProperties fhirProperties,
      OnkoMedicationStatementMapper onkoMedicationStatementMapper,
      OnkoObservationMapper onkoObservationMapper,
      OnkoPatientMapper onkoPatientMapper,
      OnkoProcedureMapper onkoProcedureMapper) {
    super(fhirProperties);
    this.onkoMedicationStatementMapper = onkoMedicationStatementMapper;
    this.onkoObservationMapper = onkoObservationMapper;
    this.onkoPatientMapper = onkoPatientMapper;
    this.onkoProcedureMapper = onkoProcedureMapper;
  }

  @Bean
  public Function<KTable<String, MeldungExport>, KStream<String, Bundle>[]>
      getMeldungExportGroupedProcessor() {

    return stringOnkoMeldungExpTable -> {
      // return (stringOnkoMeldungExpTable) ->
      var output =
          stringOnkoMeldungExpTable
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
              .mapValues(this.getOnkoToBundleMapper())
              .filter((key, value) -> value != null)
              .toStream()
              .flatMapValues(listOfBundles -> listOfBundles)
              .filter((key, value) -> value != null)
              .split(Named.as("out-"))
              .branch(
                  (k, v) ->
                      v.getEntry()
                          .get(0)
                          .getResource()
                          .getResourceType()
                          .toString()
                          .equals("MedicationStatement"),
                  Branched.as("MedicationStatement"))
              .branch(
                  (k, v) ->
                      v.getEntry()
                          .get(0)
                          .getResource()
                          .getResourceType()
                          .toString()
                          .equals("Observation"),
                  Branched.as("Observation"))
              .branch(
                  (k, v) ->
                      v.getEntry()
                          .get(0)
                          .getResource()
                          .getResourceType()
                          .toString()
                          .equals("Procedure"),
                  Branched.as("Procedure"))
              .branch(
                  (k, v) ->
                      v.getEntry()
                              .get(0)
                              .getResource()
                              .getResourceType()
                              .toString()
                              .equals("Patient")
                          && Objects.equals(profile, "patient2"),
                  Branched.as("Patient"))
              .noDefaultBranch();

      return new KStream[] {
        output.get("out-MedicationStatement"),
        output.get("out-Observation"),
        output.get("out-Patient"),
        output.get("out-Procedure")
      };
    };
  }

  public ValueMapper<MeldungExportList, List<Bundle>> getOnkoToBundleMapper() {
    return meldungExporte -> {
      List<Bundle> bundleList = new ArrayList<>();

      List<MeldungExport> meldungExportList =
          prioritiseLatestMeldungExports(
              meldungExporte, Arrays.asList("behandlungsende", "behandlungsbeginn"));

      bundleList.add(
          onkoMedicationStatementMapper.mapOnkoResourcesToMedicationStatement(meldungExportList));
      bundleList.add(onkoProcedureMapper.mapOnkoResourcesToProcedure(meldungExportList));

      meldungExportList =
          prioritiseLatestMeldungExports(
              meldungExporte, Arrays.asList("behandlungsende", "statusaenderung", "diagnose"));

      bundleList.add(onkoObservationMapper.mapOnkoResourcesToObservation(meldungExportList));

      meldungExportList =
          prioritiseLatestMeldungExports(
              meldungExporte,
              Arrays.asList("behandlungsende", "statusaenderung", "diagnose", "tod"));

      bundleList.add(onkoPatientMapper.mapOnkoResourcesToPatient(meldungExportList));

      return bundleList;
    };
  }
}
