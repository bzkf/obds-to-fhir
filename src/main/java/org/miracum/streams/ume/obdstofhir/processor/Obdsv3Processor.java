package org.miracum.streams.ume.obdstofhir.processor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;
import de.basisdatensatz.obds.v3.OBDS;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.*;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Patient;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.miracum.streams.ume.obdstofhir.mapper.mii.ObdsToFhirBundleMapper;
import org.miracum.streams.ume.obdstofhir.model.MeldungExport;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportListV3;
import org.miracum.streams.ume.obdstofhir.serde.MeldungExportListV3Serde;
import org.miracum.streams.ume.obdstofhir.serde.MeldungExportSerde;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class Obdsv3Processor extends ObdsToFhirMapper {

  private static final FhirContext ctx = FhirContext.forR4();

  private final ObdsToFhirBundleMapper obdsToFhirBundleMapper;

  protected Obdsv3Processor(
      FhirProperties fhirProperties, ObdsToFhirBundleMapper obdsToFhirBundleMapper) {
    super(fhirProperties);
    this.obdsToFhirBundleMapper = obdsToFhirBundleMapper;
  }

  @Bean
  public Function<KTable<String, MeldungExport>, KStream<String, Bundle>>
      getMeldungExportObdsV3Processor() {

    return stringOnkoMeldungExpTable -> {
      // return (stringOnkoMeldungExpTable) ->
      var filtered =
          stringOnkoMeldungExpTable
              // only process adt v3.x.x
              .filter((key, value) -> value.getXml_daten().getSchema_Version().matches("^3\\..*"));

      return filtered
          .groupBy(
              (key, meldung) -> KeyValue.pair(getPatIdFromMeldung(meldung), meldung),
              Grouped.with(Serdes.String(), new MeldungExportSerde()))
          .aggregate(
              MeldungExportListV3::new,
              (key, value, aggregate) -> {
                aggregate.addElement(value);
                return retainLatestVersionOnly(aggregate);
              },
              (key, value, aggregate) -> {
                aggregate.removeElement(value);
                return retainLatestVersionOnly(aggregate);
              },
              Materialized.with(Serdes.String(), new MeldungExportListV3Serde()))
          .toStream()
          .flatMapValues(this.getMeldungExportListToBundleListMapper())
          .filter((key, value) -> value != null)
          .selectKey((key, value) -> patientBundleKeySelector(value));
    };
  }

  /**
   * Filters a list of MeldungExport objects to retain only the latest version of each unique
   * reporting ID. The latest version is determined by the highest versionsnummer.
   */
  private MeldungExportListV3 retainLatestVersionOnly(MeldungExportListV3 meldungExportList) {
    return meldungExportList.stream()
        .collect(
            Collectors.groupingBy(
                Obdsv3Processor::getReportingIdFromAdt,
                Collectors.collectingAndThen(
                    Collectors.maxBy(Comparator.comparingInt(MeldungExport::getVersionsnummer)),
                    optionalMeldung -> optionalMeldung.map(List::of).orElse(List.of()))))
        .values()
        .stream()
        .flatMap(List::stream)
        .collect(Collectors.toCollection(MeldungExportListV3::new));
  }

  public static String getReportingIdFromAdt(MeldungExport meldung) {
    return meldung
        .getXml_daten()
        .getMenge_Patient()
        .getPatient()
        .getMenge_Meldung()
        .getMeldung()
        .getMeldung_ID();
  }

  public ValueMapper<MeldungExportListV3, List<Bundle>> getMeldungExportListToBundleListMapper() {
    return meldungExportList -> {

      // TODO build OBDS object from MeldungExport
      var obds = new OBDS();

      return obdsToFhirBundleMapper.map(obds);
    };
  }

  private static String patientBundleKeySelector(Bundle bundle) {
    var patients = BundleUtil.toListOfResourcesOfType(ctx, bundle, Patient.class);
    var conditions = BundleUtil.toListOfResourcesOfType(ctx, bundle, Condition.class);

    if (patients.size() != 1) {
      throw new RuntimeException(
          String.format(
              "A patient bundle contains %d patient resources instead of 1", patients.size()));
    }
    if (conditions.size() != 1) {
      throw new RuntimeException(
          String.format(
              "A patient bundle contains %d condition resources instead of 1", conditions.size()));
    }
    var patient = patients.getFirst();
    var condition = conditions.getFirst();
    return String.format(
        "%s/%s - %s/%s",
        patient.getResourceType(), patient.getId(), condition.getResourceType(), condition.getId());
  }
}
