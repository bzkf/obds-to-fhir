package org.miracum.streams.ume.onkoadttofhir.processor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.*;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.lookup.DisplayAdtSeitenlokalisationLookup;
import org.miracum.streams.ume.onkoadttofhir.lookup.SnomedCtSeitenlokalisationLookup;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnkoConditionProcessor extends OnkoProcessor {

  private final SnomedCtSeitenlokalisationLookup snomedCtSeitenlokalisationLookup =
      new SnomedCtSeitenlokalisationLookup();

  private final DisplayAdtSeitenlokalisationLookup displayAdtSeitenlokalisationLookup =
      new DisplayAdtSeitenlokalisationLookup();

  @Value("${app.version}")
  private String appVersion;

  protected OnkoConditionProcessor(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  @Bean
  public Function<KTable<String, MeldungExport>, KStream<String, Bundle>>
      getMeldungExportConditionProcessor() {
    return stringOnkoMeldungExpTable ->
        stringOnkoMeldungExpTable
            .filter(
                (key, value) ->
                    Objects.equals(
                        value
                            .getXml_daten()
                            .getMenge_Patient()
                            .getPatient()
                            .getMenge_Meldung()
                            .getMeldung()
                            .getMeldeanlass(),
                        "diagnose"))
            .mapValues(getOnkoToConditionBundleMapper())
            .toStream()
            .filter((key, value) -> value != null);
  }

  public ValueMapper<MeldungExport, Bundle> getOnkoToConditionBundleMapper() {
    return meldungExport -> {
      var onkoCondition = new Condition();

      // TODO mehrere Tumor Ids berueksichtigen

      var meldung =
          meldungExport
              .getXml_daten()
              .getMenge_Patient()
              .getPatient()
              .getMenge_Meldung()
              .getMeldung();

      var diagnose = meldung.getDiagnose();

      var patId = meldungExport.getReferenz_nummer();

      var conIdentifier = patId + meldung.getMeldung_ID() + meldung.getMeldeanlass();

      onkoCondition.setId(this.getHash("Condition", conIdentifier));

      onkoCondition
          .getMeta()
          .setSource("DWH_ROUTINE.STG_ONKOSTAR_LKR_MELDUNG_EXPORT:onkostar-to-fhir:" + appVersion);
      onkoCondition
          .getMeta()
          .setProfile(List.of(new CanonicalType(fhirProperties.getProfiles().getCondition())));

      var coding = new Coding();
      var icd10Version = diagnose.getPrimaertumor_ICD_Version();
      // Aufbau: "10 2021 GM"
      String[] icdVersionArray = icd10Version.split(" ");

      if (icdVersionArray.length == 3 && icdVersionArray[1].matches("^20\\d{2}$")) {
        coding.setVersion(icdVersionArray[1]);
      } // FIXME: else throw exception?

      coding
          .setCode(diagnose.getPrimaertumor_ICD_Code())
          .setSystem(fhirProperties.getSystems().getIcd10gm());

      var conditionCode = new CodeableConcept().addCoding(coding);
      onkoCondition.setCode(conditionCode);

      var bodySiteADTCoding = new Coding();
      var bodySiteSNOMEDCoding = new Coding();

      var adtBodySite = diagnose.getSeitenlokalisation();

      bodySiteADTCoding
          .setSystem(fhirProperties.getSystems().getAdtSeitenlokalisation())
          .setCode(adtBodySite)
          .setDisplay(
              displayAdtSeitenlokalisationLookup.lookupAdtSeitenlokalisationDisplay(adtBodySite));
      bodySiteSNOMEDCoding
          .setSystem(fhirProperties.getSystems().getSnomed())
          .setCode(snomedCtSeitenlokalisationLookup.lookupSnomedCode(adtBodySite))
          .setDisplay(snomedCtSeitenlokalisationLookup.lookupSnomedDisplay(adtBodySite));

      var bodySiteConcept = new CodeableConcept();

      bodySiteConcept.addCoding(bodySiteADTCoding).addCoding(bodySiteSNOMEDCoding);

      onkoCondition.addBodySite(bodySiteConcept);

      var pid = convertId(patId);
      onkoCondition.setSubject(
          new Reference()
              .setReference("Patient/" + this.getHash("Patient", pid))
              .setIdentifier(
                  new Identifier()
                      .setSystem(fhirProperties.getSystems().getPatientId())
                      .setType(
                          new CodeableConcept(
                              new Coding(
                                  fhirProperties.getSystems().getIdentifierType(), "MR", null)))
                      .setValue(pid)));

      Date conditionDate = null;
      var conditionDateString = diagnose.getDiagnosedatum();

      if (conditionDateString != null) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN);
        try {
          conditionDate = formatter.parse(conditionDateString);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
      }

      if (conditionDate != null) {
        onkoCondition.setOnset(new DateTimeType(conditionDate));
      }

      var bundle = new Bundle();
      bundle
          .setType(Bundle.BundleType.TRANSACTION)
          .addEntry()
          .setFullUrl(new Reference("Condition/" + onkoCondition.getId()).getReference())
          .setResource(onkoCondition)
          .setRequest(
              new Bundle.BundleEntryRequestComponent()
                  .setMethod(Bundle.HTTPVerb.PUT)
                  .setUrl(
                      String.format(
                          "%s/%s", onkoCondition.getResourceType().name(), onkoCondition.getId())));

      return bundle;
    };
  }
}
