package org.miracum.streams.ume.onkoadttofhir.processor;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class OnkoProcessor {
  protected final FhirProperties fhirProperties;

  private static final Logger log = LoggerFactory.getLogger(OnkoProcessor.class);

  protected OnkoProcessor(final FhirProperties fhirProperties) {
    this.fhirProperties = fhirProperties;
  }

  protected String getHash(String type, String id) {
    String idToHash;
    switch (type) {
      case "Patient":
        idToHash = fhirProperties.getSystems().getPatientId();
        break;
      case "Condition":
        idToHash = fhirProperties.getSystems().getConditionId();
        break;
      case "Observation":
        idToHash = fhirProperties.getSystems().getObservationId();
        break;
      case "MedicationStatement":
        idToHash = fhirProperties.getSystems().getMedicationStatementId();
        break;
      case "Procedure":
        idToHash = fhirProperties.getSystems().getProcedureId();
        break;
      case "Surrogate":
        return Hashing.sha256().hashString(id, StandardCharsets.UTF_8).toString();
      default:
        return null;
    }
    return Hashing.sha256().hashString(idToHash + "|" + id, StandardCharsets.UTF_8).toString();
  }

  protected static String convertId(String id) {
    Pattern pattern = Pattern.compile("[^0]\\d{8}");
    Matcher matcher = pattern.matcher(id);
    var convertedId = "";
    if (matcher.find()) {
      convertedId = matcher.group();
    } else {
      log.warn("Identifier to convert does not have 9 digits without leading '0': " + id);
    }
    return convertedId;
  }

  public List<MeldungExport> prioritiseLatestMeldungExports(
      MeldungExportList meldungExports, List<String> priorityOrder) {
    var meldungen = meldungExports.getElements();

    var meldungExportMap = new HashMap<Integer, MeldungExport>();
    // meldeanlass bleibt in LKR Meldung immer gleich
    for (var meldung : meldungen) {
      var lkrId = meldung.getLkr_meldung();
      var currentMeldungVersion = meldungExportMap.get(lkrId);
      if (currentMeldungVersion == null
          || meldung.getVersionsnummer() > currentMeldungVersion.getVersionsnummer()) {
        meldungExportMap.put(lkrId, meldung);
      }
    }

    Collections.reverse(priorityOrder);

    Comparator<MeldungExport> meldungComparator =
        Comparator.comparing(
            m ->
                priorityOrder.indexOf(
                    m.getXml_daten()
                        .getMenge_Patient()
                        .getPatient()
                        .getMenge_Meldung()
                        .getMeldung()
                        .getMeldeanlass()));

    List<MeldungExport> meldungExportList = new ArrayList<>(meldungExportMap.values());
    meldungExportList.sort(meldungComparator);

    return meldungExportList;
  }

  public String getTumorIdFromAdt(MeldungExport meldung) {
    return meldung
        .getXml_daten()
        .getMenge_Patient()
        .getPatient()
        .getMenge_Meldung()
        .getMeldung()
        .getTumorzuordnung()
        .getTumor_ID();
  }

  public String getReportingReasonFromAdt(MeldungExport meldung) {
    return meldung
        .getXml_daten()
        .getMenge_Patient()
        .getPatient()
        .getMenge_Meldung()
        .getMeldung()
        .getMeldeanlass();
  }

  public String getReportingIdFromAdt(MeldungExport meldung) {
    return meldung
        .getXml_daten()
        .getMenge_Patient()
        .getPatient()
        .getMenge_Meldung()
        .getMeldung()
        .getMeldung_ID();
  }

  protected Bundle addResourceAsEntryInBundle(Bundle bundle, DomainResource resource) {
    bundle
        .addEntry()
        .setFullUrl(
            new Reference(
                    String.format("%s/%s", resource.getResourceType().name(), resource.getId()))
                .getReference())
        .setResource(resource)
        .setRequest(
            new Bundle.BundleEntryRequestComponent()
                .setMethod(Bundle.HTTPVerb.PUT)
                .setUrl(
                    String.format("%s/%s", resource.getResourceType().name(), resource.getId())));

    return bundle;
  }

  protected String generateProfileMetaSource(
      String senderId, String softwareId, String appVersion) {
    if (senderId != null && softwareId != null) {
      return String.format("%s.%s:onkoadt-to-fhir:%s", senderId, softwareId, appVersion);
    } else {
      return "onkoadt-to-fhir:" + appVersion;
    }
  }

  protected DateTimeType extractDateTimeFromADTDate(String adtDate) {

    if (Objects.equals(adtDate, "") || Objects.equals(adtDate, " ") || adtDate == null) {
      return null;
    }

    // 00.00.2022 -> 01.07.2022
    // 00.04.2022 -> 15.04.2022
    if (adtDate.matches("^00.00.\\d{4}$")) {
      adtDate = "01.07." + adtDate.substring(adtDate.length() - 4);
    } else if (adtDate.matches("^00.\\d{2}.\\d{4}$")) {
      adtDate = "15." + adtDate.substring(3); // TODO unit test
    }

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    LocalDate adtLocalDate = LocalDate.parse(adtDate, formatter);
    LocalDateTime adtLocalDateTime = adtLocalDate.atStartOfDay();
    var adtDateTime =
        new DateTimeType(
            Date.from(adtLocalDateTime.atZone(ZoneId.of("Europe/Berlin")).toInstant()));

    adtDateTime.setPrecision(TemporalPrecisionEnum.DAY);
    return adtDateTime;
  }
}
