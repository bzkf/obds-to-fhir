package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.BoostTyp;
import de.basisdatensatz.obds.v3.STTyp;
import de.basisdatensatz.obds.v3.STTyp.MengeBestrahlung.Bestrahlung;
import de.basisdatensatz.obds.v3.STTyp.MengeBestrahlung.Bestrahlung.Applikationsart;
import de.basisdatensatz.obds.v3.SeiteZielgebietTyp;
import de.basisdatensatz.obds.v3.StrahlendosisTyp;
import de.basisdatensatz.obds.v3.ZielgebietTyp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StrahlentherapieMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(StrahlentherapieMapper.class);

  private enum ApplikationsartCode {
    P("P"),
    P_ST("P-ST"),
    P_4D("P-4D"),
    P_ST4D("P-ST4D"),
    PRCN("PRCN"),
    PRCN_ST("PRCN-ST"),
    PRCN_4D("PRCN-4D"),
    PRCN_ST4D("PRCN-ST4D"),
    PRCJ("PRCJ"),
    PRCJ_4D("PRCJ-4D"),
    PRCJ_ST("PRCJ-ST"),
    K("K"),
    KHDR("KHDR"),
    KLDR("KLDR"),
    KPDR("KPDR"),
    I("I"),
    IHDR("IHDR"),
    ILDR("ILDR"),
    IPDR("IPDR"),
    MSIRT("MSIRT"),
    MPRRT("MPRRT"),
    MPSMA("MPSMA"),
    MRJT("MRJT"),
    MRIT("MRIT"),
    M("M"),
    S("S");

    private final String code;

    ApplikationsartCode(String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }

    public static ApplikationsartCode fromString(String code) {
      for (var b : ApplikationsartCode.values()) {
        if (b.getCode().equalsIgnoreCase(code)) {
          return b;
        }
      }
      throw new IllegalArgumentException("No constant with text " + code + " found");
    }
  }

  private record StrahlentherapieBestrahlung(
      ApplikationsartCode applikationsart,
      String zielgebiet,
      SeiteZielgebietTyp seiteZielgebiet,
      String strahlenart,
      StrahlendosisTyp einzeldosis,
      StrahlendosisTyp gesamtdosis,
      BoostTyp boost) {}

  private record StrahlenTherapieCategoryAndCode(CodeableConcept category, CodeableConcept code) {}

  public StrahlentherapieMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public List<Procedure> map(STTyp st, Reference subject, Reference condition, String meldungsId) {
    Objects.requireNonNull(st);
    Validate.notBlank(st.getSTID(), "Required ST_ID is unset");
    verifyReference(subject, ResourceType.Patient);
    verifyReference(condition, ResourceType.Condition);

    var idBase = st.getSTID();
    if (!StringUtils.hasText(st.getSTID())) {
      LOG.warn(
          "ST_ID is unset. Defaulting to Meldung_ID as the identifier for the created Procedures");
      idBase = meldungsId;
    }

    var procedure = new Procedure();
    procedure.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoStrahlentherapie());

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getStrahlentherapieProcedureId())
            .setValue(slugifier.slugify(idBase));
    procedure.addIdentifier(identifier);
    procedure.setId(computeResourceIdFromIdentifier(identifier));

    // Status
    if (st.getMeldeanlass() == STTyp.Meldeanlass.BEHANDLUNGSENDE) {
      procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
    } else {
      procedure.setStatus(Procedure.ProcedureStatus.INPROGRESS);
    }

    procedure.setSubject(subject);

    procedure.addReasonReference(condition);

    var performed =
        computeTreatmentPeriodFromAllBestrahlung(st.getMengeBestrahlung().getBestrahlung());
    if (!performed.hasStart()) {
      LOG.warn("Bestrahlung Beginn is unset. Setting data absent extension.");
      var absentDateTime = new DateTimeType();
      absentDateTime.addExtension(
          fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
      performed.setStartElement(absentDateTime);
    }

    procedure.setPerformed(performed);

    var categoryAndCode =
        getCategoryAndCodeFromBestrahlungen(st.getMengeBestrahlung().getBestrahlung());
    procedure.setCategory(categoryAndCode.category());
    procedure.setCode(categoryAndCode.code());

    if (st.getEndeGrund() != null) {
      var outcome =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsTherapieGrundEnde())
              .setCode(st.getEndeGrund());
      procedure.setOutcome(new CodeableConcept(outcome));
    }

    var intention =
        new Coding()
            .setSystem(fhirProperties.getSystems().getMiiCsOnkoIntention())
            .setCode(st.getIntention());
    procedure
        .addExtension()
        .setUrl(fhirProperties.getExtensions().getMiiExOnkoStrahlentherapieIntention())
        .setValue(new CodeableConcept(intention));

    var stellungZurOp =
        new Coding()
            .setSystem(fhirProperties.getSystems().getMiiCsOnkoTherapieStellungzurop())
            .setCode(st.getStellungOP());
    procedure
        .addExtension()
        .setUrl(fhirProperties.getExtensions().getMiiExOnkoStrahlentherapieStellungzurop())
        .setValue(new CodeableConcept(stellungZurOp));

    var mainStrahlentherapieProcedureReference =
        new Reference(procedure.getResourceType().name() + "/" + procedure.getId());

    var result = new ArrayList<Procedure>();

    result.add(procedure);

    for (int i = 0; i < st.getMengeBestrahlung().getBestrahlung().size(); i++) {
      var bestrahlung = st.getMengeBestrahlung().getBestrahlung().get(i);
      if (bestrahlung.getApplikationsart() == null) {
        LOG.warn("Skipping Bestrahlung with unset Applikationsart");
        continue;
      }

      // we could also try to construct the identifier value from ST_ID + Bestrahlung.beginn() +
      // Applikationsart, but it's possible that all those values are unset or even change
      // across multiple reports. Still, the use of indices feels fragile, similar to how
      // we're handling multiple Fernmetastasen right now.
      var identifierValue = idBase + "-" + i;

      var bestrahlungsProcedure =
          mapBestrahlung(
              bestrahlung, subject, identifierValue, mainStrahlentherapieProcedureReference);

      result.add(bestrahlungsProcedure);
    }

    return result;
  }

  private Procedure mapBestrahlung(
      Bestrahlung bestrahlung, Reference subject, String identifierValue, Reference partOf) {
    var procedure = new Procedure();

    if (bestrahlung.getApplikationsart().getMetabolisch() != null) {
      procedure
          .getMeta()
          .addProfile(
              fhirProperties.getProfiles().getMiiPrOnkoStrahlentherapieBestrahlungNuklearmedizin());
    } else {
      procedure
          .getMeta()
          .addProfile(
              fhirProperties
                  .getProfiles()
                  .getMiiPrOnkoStrahlentherapieBestrahlungStrahlentherapie());
    }

    var bestrahlungsData = getBestrahlungsData(bestrahlung.getApplikationsart());
    if (bestrahlungsData == null) {
      throw new IllegalStateException("Unable to extract Bestrahlung data.");
    }

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getStrahlentherapieBestrahlungProcedureId())
            .setValue(slugifier.slugify(identifierValue));
    procedure.addIdentifier(identifier);
    procedure.setId(computeResourceIdFromIdentifier(identifier));

    // Status
    if (bestrahlung.getEnde() != null) {
      procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
    } else {
      procedure.setStatus(Procedure.ProcedureStatus.INPROGRESS);
    }

    // Subject
    procedure.setSubject(subject);

    // PartOf
    procedure.addPartOf(partOf);

    // Category and Code
    var categoryAndCode = getCategoryAndCodeFromBestrahlungen(List.of(bestrahlung));
    procedure.setCategory(categoryAndCode.category());
    procedure.setCode(categoryAndCode.code());

    // Performed
    var performed = new Period();
    if (bestrahlung.getBeginn() != null) {
      var begin = convertObdsDatumToDateTimeType(bestrahlung.getBeginn());
      begin.ifPresent(performed::setStartElement);
    } else {
      LOG.warn("Bestrahlung Beginn is unset. Setting data absent extension.");
      var absentDateTime = new DateTimeType();
      absentDateTime.addExtension(
          fhirProperties.getExtensions().getDataAbsentReason(), new CodeType("unknown"));
      performed.setStartElement(absentDateTime);
    }

    if (bestrahlung.getEnde() != null) {
      var end = convertObdsDatumToDateTimeType(bestrahlung.getEnde());
      end.ifPresent(performed::setEndElement);
    }

    procedure.setPerformed(performed);

    // usedCode
    if (bestrahlungsData.strahlenart() != null) {
      var strahlenartCoding =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoStrahlentherapieStrahlenart())
              .setCode(bestrahlungsData.strahlenart());
      procedure.addUsedCode(new CodeableConcept(strahlenartCoding));
    }

    if (bestrahlungsData.applikationsart() != null) {
      var applikationsartCoding =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoStrahlentherapieApplikationsart())
              .setCode(bestrahlungsData.applikationsart().getCode());
      procedure.addUsedCode(new CodeableConcept(applikationsartCoding));
    }

    // bodySite
    if (bestrahlungsData.zielgebiet() != null) {
      var bodySiteCoding =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoStrahlentherapieZielgebiet())
              .setCode(bestrahlungsData.zielgebiet());

      procedure.addBodySite(new CodeableConcept(bodySiteCoding));
    }

    // this ensures that if the zielgebiet is unset and only the seiteZielgebiet is set,
    // we still create a bodySite element with just that extension.
    if (bestrahlungsData.seiteZielgebiet() != null) {
      var sideCoding =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoSeitenlokalisation())
              .setCode(bestrahlungsData.seiteZielgebiet().value());
      procedure
          .getBodySiteFirstRep()
          .addExtension()
          .setUrl(
              fhirProperties
                  .getExtensions()
                  .getMiiExOnkoStrahlentherapieBestrahlungSeitenlokalisation())
          .setValue(new CodeableConcept(sideCoding));
    }

    // Extensions
    var bestrahlungExtensions = mapExtensions(bestrahlung);
    for (var extension : bestrahlungExtensions) {
      procedure.addExtension(extension);
    }

    return procedure;
  }

  private StrahlenTherapieCategoryAndCode getCategoryAndCodeFromBestrahlungen(
      List<Bestrahlung> bestrahlungen) {
    var allMetabolic =
        bestrahlungen.stream().allMatch(b -> b.getApplikationsart().getMetabolisch() != null);
    var allRadiotherapy =
        bestrahlungen.stream().allMatch(b -> b.getApplikationsart().getMetabolisch() == null);

    if (allMetabolic) {
      var category =
          fhirProperties
              .getCodings()
              .snomed()
              .setCode("399315003")
              .setDisplay("Radionuclide therapy (procedure)");

      var code =
          fhirProperties
              .getCodings()
              .ops()
              .setCode("8-53")
              .setDisplay("Nuklearmedizinische Therapie");

      return new StrahlenTherapieCategoryAndCode(
          new CodeableConcept(category), new CodeableConcept(code));
    } else {
      if (!allRadiotherapy) {
        LOG.warn(
            "Bestrahlung contains a mixture of radionuclide and radiotherapy entries. "
                + "Defaulting to radiotherapy for the whole-resource code and category.");
      }

      var category =
          fhirProperties
              .getCodings()
              .snomed()
              .setCode("1287742003")
              .setDisplay("Radiotherapy (procedure)");
      var code = fhirProperties.getCodings().ops().setCode("8-52").setDisplay("Strahlentherapie");

      return new StrahlenTherapieCategoryAndCode(
          new CodeableConcept(category), new CodeableConcept(code));
    }
  }

  private Period computeTreatmentPeriodFromAllBestrahlung(List<Bestrahlung> bestrahlungen) {
    // find the smallest begin date...
    var earliestBestrahlungBeginn =
        bestrahlungen.stream()
            .map(x -> x.getBeginn())
            .filter(Objects::nonNull)
            .min(Comparator.comparing(e -> e.toGregorianCalendar()));

    // ... and the largest end date
    var latestBestrahlungEnde =
        bestrahlungen.stream()
            .map(x -> x.getEnde())
            .filter(Objects::nonNull)
            .max(Comparator.comparing(e -> e.toGregorianCalendar()));

    var performed = new Period();
    if (earliestBestrahlungBeginn.isPresent()) {
      var earliest = convertObdsDatumToDateTimeType(earliestBestrahlungBeginn.get());
      if (earliest.isPresent()) {
        performed.setStartElement(earliest.get());
      }
    }

    if (latestBestrahlungEnde.isPresent()) {
      var latest = convertObdsDatumToDateTimeType(latestBestrahlungEnde.get());
      if (latest.isPresent()) {
        performed.setEndElement(latest.get());
      }
    }

    return performed;
  }

  private List<Extension> mapExtensions(Bestrahlung bestrahlung) {
    var extensions = new ArrayList<Extension>();

    var data = getBestrahlungsData(bestrahlung.getApplikationsart());

    if (data == null) {
      LOG.warn("Unable to extract Bestrahlung data. Likely Malformed Applikationsart element.");
      return List.of();
    }

    var gesamtdosis = data.gesamtdosis();
    if (gesamtdosis != null) {
      var value =
          new Quantity()
              .setUnit(gesamtdosis.getEinheit())
              .setValue(gesamtdosis.getDosis())
              .setSystem(fhirProperties.getSystems().getUcum())
              .setCode(gesamtdosis.getEinheit());
      extensions.add(
          new Extension(
              fhirProperties.getExtensions().getMiiExOnkoStrahlentherapieBestrahlungGesamtdosis(),
              value));
    }

    var einzeldosis = data.einzeldosis();
    if (einzeldosis != null) {
      var value =
          new Quantity()
              .setUnit(einzeldosis.getEinheit())
              .setValue(einzeldosis.getDosis())
              .setSystem(fhirProperties.getSystems().getUcum())
              .setCode(einzeldosis.getEinheit());
      extensions.add(
          new Extension(
              fhirProperties.getExtensions().getMiiExOnkoStrahlentherapieBestrahlungEinzeldosis(),
              value));
    }

    var boost = data.boost();
    if (boost != null) {
      var value =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoStrahlentherapieBoost())
              .setCode(boost.value());
      extensions.add(
          new Extension(
              fhirProperties.getExtensions().getMiiExOnkoStrahlentherapieBestrahlungBoost(),
              new CodeableConcept(value)));
    }

    // extra handling for metabolisch, quite ugly, should instead be moved to a
    // "StrahlentherapieBestrahlung"-based type hierarchy
    if (bestrahlung.getApplikationsart().getMetabolisch() != null) {
      var metabolisch = bestrahlung.getApplikationsart().getMetabolisch();
      if (metabolisch.getEinzeldosis() != null) {
        var value =
            new Quantity()
                .setUnit(metabolisch.getEinzeldosis().getEinheit())
                .setValue(metabolisch.getEinzeldosis().getDosis())
                .setSystem(fhirProperties.getSystems().getUcum())
                .setCode(metabolisch.getEinzeldosis().getEinheit());
        extensions.add(
            new Extension(
                fhirProperties.getExtensions().getMiiExOnkoStrahlentherapieBestrahlungEinzeldosis(),
                value));
      }

      if (metabolisch.getGesamtdosis() != null) {
        var value =
            new Quantity()
                .setUnit(metabolisch.getGesamtdosis().getEinheit())
                .setValue(metabolisch.getGesamtdosis().getDosis())
                .setSystem(fhirProperties.getSystems().getUcum())
                .setCode(metabolisch.getGesamtdosis().getEinheit());

        extensions.add(
            new Extension(
                fhirProperties.getExtensions().getMiiExOnkoStrahlentherapieBestrahlungGesamtdosis(),
                value));
      }
    }

    return extensions;
  }

  private static StrahlentherapieBestrahlung getBestrahlungsData(Applikationsart applikationsart) {
    var applikationsartCode = mapApplikationsartToCode(applikationsart);

    if (applikationsart.getPerkutan() != null) {
      var applikation = applikationsart.getPerkutan();
      var strahlenart =
          applikation.getStrahlenart() != null ? applikation.getStrahlenart().value() : null;
      return new StrahlentherapieBestrahlung(
          applikationsartCode,
          getZielgebiet(applikation.getZielgebiet()),
          applikation.getSeiteZielgebiet(),
          strahlenart,
          applikation.getEinzeldosis(),
          applikation.getGesamtdosis(),
          applikation.getBoost());
    }

    if (applikationsart.getKontakt() != null) {
      var applikation = applikationsart.getKontakt();
      var strahlenart =
          applikation.getStrahlenart() != null ? applikation.getStrahlenart().value() : null;
      return new StrahlentherapieBestrahlung(
          applikationsartCode,
          getZielgebiet(applikation.getZielgebiet()),
          applikation.getSeiteZielgebiet(),
          strahlenart,
          applikation.getEinzeldosis(),
          applikation.getGesamtdosis(),
          applikation.getBoost());
    }

    if (applikationsart.getMetabolisch() != null) {
      var applikation = applikationsart.getMetabolisch();
      var strahlenart =
          applikation.getStrahlenart() != null ? applikation.getStrahlenart().value() : null;
      return new StrahlentherapieBestrahlung(
          applikationsartCode,
          getZielgebiet(applikation.getZielgebiet()),
          applikation.getSeiteZielgebiet(),
          strahlenart,
          null,
          null,
          null);
    }

    if (applikationsart.getSonstige() != null) {
      var applikation = applikationsart.getSonstige();
      return new StrahlentherapieBestrahlung(
          applikationsartCode,
          getZielgebiet(applikation.getZielgebiet()),
          applikation.getSeiteZielgebiet(),
          null,
          applikation.getEinzeldosis(),
          applikation.getGesamtdosis(),
          null);
    }

    return null;
  }

  private static String getZielgebiet(ZielgebietTyp zielgebiet) {
    if (zielgebiet == null) {
      return null;
    }

    return zielgebiet.getCodeVersion2014() != null
        ? zielgebiet.getCodeVersion2014()
        : zielgebiet.getCodeVersion2021();
  }

  private static ApplikationsartCode mapApplikationsartToCode(Applikationsart applikationsart) {
    if (applikationsart.getPerkutan() != null) {
      var perkutan = applikationsart.getPerkutan();

      // P
      var sb = new StringBuilder("P");

      // P, PRCN, PRCJ
      sb.append(Objects.toString(perkutan.getRadiochemo(), ""));

      if (perkutan.getStereotaktisch() != null || perkutan.getAtemgetriggert() != null) {
        // P-, PRCN-, PRCJ-
        sb.append("-");
      }

      // P, P-ST, PRCN-ST, PRCJ-ST (not valid)
      sb.append(Objects.toString(perkutan.getStereotaktisch(), ""));
      // P, P-ST4D, P-4D, PRCN-ST4D, PRCN-4D, PRCJ-ST4D, PRCJ-4D
      sb.append(Objects.toString(perkutan.getAtemgetriggert(), ""));

      return ApplikationsartCode.fromString(sb.toString());
    }

    if (applikationsart.getKontakt() != null) {
      var kontakt = applikationsart.getKontakt();

      // I, K
      var sb = new StringBuilder(kontakt.getInterstitiellEndokavitaer());

      // I, K, KHDR, KLDR, KPDR, IHDR, ILDR, IPDR
      sb.append(Objects.toString(kontakt.getRateType(), ""));

      return ApplikationsartCode.fromString(sb.toString());
    }

    if (applikationsart.getMetabolisch() != null) {
      var metabolisch = applikationsart.getMetabolisch();

      // M
      var sb = new StringBuilder("M");

      // M, MSIRT, MPRRT, MPSMA, MRJT, MRIT
      sb.append(Objects.toString(metabolisch.getMetabolischTyp(), ""));

      return ApplikationsartCode.fromString(sb.toString());
    }

    if (applikationsart.getSonstige() == null) {
      LOG.warn("Neither Kontakt, Metabolisch, Perkutan and Sonstige are set. Defaulting to 'S'");
    }

    return ApplikationsartCode.S;
  }
}
