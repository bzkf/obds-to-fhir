package org.miracum.streams.ume.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.BoostTyp;
import de.basisdatensatz.obds.v3.STTyp;
import de.basisdatensatz.obds.v3.STTyp.MengeBestrahlung.Bestrahlung;
import de.basisdatensatz.obds.v3.STTyp.MengeBestrahlung.Bestrahlung.Applikationsart;
import de.basisdatensatz.obds.v3.SeiteZielgebietTyp;
import de.basisdatensatz.obds.v3.StrahlendosisTyp;
import de.basisdatensatz.obds.v3.ZielgebietTyp;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.obdstofhir.FhirProperties;
import org.miracum.streams.ume.obdstofhir.mapper.ObdsToFhirMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StrahlentherapieMapper extends ObdsToFhirMapper {
  private static final Logger LOG = LoggerFactory.getLogger(StrahlentherapieMapper.class);

  private enum ApplikationsartCode {
    P("P"),
    P_ST("P-ST"),
    P_4D("P-4D"),
    P_ST4D("P-ST4D"),
    // XXX: this is technically not a valid combination but present in the sample data
    // ("Testpatient_Mamma.xml")
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

  public StrahlentherapieMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Procedure map(STTyp st, Reference subject, Reference condition) {
    Objects.requireNonNull(st);
    Validate.notBlank(st.getSTID(), "Required ST_ID is unset");

    verifyReference(subject, ResourceType.PATIENT);
    verifyReference(condition, ResourceType.CONDITION);

    var procedure = new Procedure();
    procedure.getMeta().addProfile(fhirProperties.getProfiles().getMiiPrOnkoStrahlentherapie());

    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getStrahlentherapieProcedureId())
            .setValue(st.getSTID());
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
    procedure.setPerformed(performed);

    var allMetabolic =
        st.getMengeBestrahlung().getBestrahlung().stream()
            .allMatch(b -> b.getApplikationsart().getMetabolisch() != null);
    var allRadiotherapy =
        st.getMengeBestrahlung().getBestrahlung().stream()
            .allMatch(b -> b.getApplikationsart().getMetabolisch() == null);

    if (allMetabolic) {
      var category =
          new Coding()
              .setSystem(fhirProperties.getSystems().getSnomed())
              .setCode("399315003")
              .setDisplay("Radionuclide therapy (procedure)");
      procedure.setCategory(new CodeableConcept(category));

      var code =
          new Coding()
              .setSystem(fhirProperties.getSystems().getOps())
              .setCode("8-53")
              .setVersion("2025")
              .setDisplay("Nuklearmedizinische Therapie");
      procedure.setCode(new CodeableConcept(code));
    } else {
      if (!allRadiotherapy) {
        LOG.warn(
            "Bestrahlung contains a mixture of radionuclide and radiotherapy entries. "
                + "Defaulting to radiotherapy for the whole-resource code and category.");
      }

      var category =
          new Coding()
              .setSystem(fhirProperties.getSystems().getSnomed())
              .setCode("1287742003")
              .setDisplay("Radiotherapy (procedure)");
      procedure.setCategory(new CodeableConcept(category));

      var code =
          new Coding()
              .setSystem(fhirProperties.getSystems().getOps())
              .setCode("8-52")
              .setVersion("2025")
              .setDisplay("Strahlentherapie");
      procedure.setCode(new CodeableConcept(code));
    }

    if (st.getEndeGrund() != null) {
      var outcome =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoTherapieEndeGrund())
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

    for (var bestrahlung : st.getMengeBestrahlung().getBestrahlung()) {
      var bestrahlungExtension = createBestrahlungExtension(bestrahlung);
      if (bestrahlungExtension.isPresent()) {
        procedure.addExtension(bestrahlungExtension.get());
      }
    }

    return procedure;
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

  private Optional<Extension> createBestrahlungExtension(Bestrahlung bestrahlung) {
    var bestrahlungExtension =
        new Extension(fhirProperties.getExtensions().getMiiExOnkoStrahlentherapieBestrahlung());

    var data = getBestrahlungsData(bestrahlung.getApplikationsart());

    if (data == null) {
      LOG.warn("Unable to extract Bestrahlung data. Likely Malformed Applikationsart element.");
      return Optional.empty();
    }

    // per profile, Applikationsart, Strahlenart, and Zielgebiet must be set
    var applikationsartCode = data.applikationsart();
    if (applikationsartCode != null) {
      var applikationsart =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoStrahlentherapieApplikationsart())
              .setCode(applikationsartCode.getCode());
      bestrahlungExtension.addExtension("Applikationsart", new CodeableConcept(applikationsart));
    }

    var strahlenartCode = data.strahlenart();
    if (strahlenartCode != null) {
      var value =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoStrahlentherapieStrahlenart())
              .setCode(strahlenartCode);
      bestrahlungExtension.addExtension("Strahlenart", new CodeableConcept(value));
    }

    var zielgebietCode = data.zielgebiet();
    if (zielgebietCode != null) {
      var value =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoStrahlentherapieZielgebiet())
              .setCode(zielgebietCode);
      bestrahlungExtension.addExtension("Zielgebiet", new CodeableConcept(value));
    }

    var seiteZielgebiet = data.seiteZielgebiet();
    if (seiteZielgebiet != null) {
      var value =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoSeitenlokalisation())
              .setCode(seiteZielgebiet.value());
      bestrahlungExtension.addExtension("Zielgebiet_Lateralitaet", new CodeableConcept(value));
    }

    var gesamtdosis = data.gesamtdosis();
    if (gesamtdosis != null) {
      var value =
          new Quantity()
              .setUnit(gesamtdosis.getEinheit())
              .setValue(gesamtdosis.getDosis())
              .setSystem(fhirProperties.getSystems().getUcum())
              .setCode(gesamtdosis.getEinheit());
      bestrahlungExtension.addExtension("Gesamtdosis", value);
    }

    var einzeldosis = data.einzeldosis();
    if (einzeldosis != null) {
      var value =
          new Quantity()
              .setUnit(einzeldosis.getEinheit())
              .setValue(einzeldosis.getDosis())
              .setSystem(fhirProperties.getSystems().getUcum())
              .setCode(einzeldosis.getEinheit());
      bestrahlungExtension.addExtension("Einzeldosis", value);
    }

    var boost = data.boost();
    if (boost != null) {
      var value =
          new Coding()
              .setSystem(fhirProperties.getSystems().getMiiCsOnkoStrahlentherapieBoost())
              .setCode(boost.value());
      bestrahlungExtension.addExtension("Boost", new CodeableConcept(value));
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
        bestrahlungExtension.addExtension("Einzeldosis", value);
      }

      if (metabolisch.getGesamtdosis() != null) {
        var value =
            new Quantity()
                .setUnit(metabolisch.getGesamtdosis().getEinheit())
                .setValue(metabolisch.getGesamtdosis().getDosis())
                .setSystem(fhirProperties.getSystems().getUcum())
                .setCode(metabolisch.getGesamtdosis().getEinheit());
        bestrahlungExtension.addExtension("Gesamtdosis", value);
      }
    }

    return Optional.of(bestrahlungExtension);
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
      sb.append(Objects.toString(kontakt.getRateType()));

      return ApplikationsartCode.fromString(sb.toString());
    }

    if (applikationsart.getMetabolisch() != null) {
      var metabolisch = applikationsart.getMetabolisch();

      // M
      var sb = new StringBuilder("M");

      // M, MSIRT, MPRRT, MPSMA, MRJT, MRIT
      sb.append(metabolisch.getMetabolischTyp());

      return ApplikationsartCode.fromString(sb.toString());
    }

    if (applikationsart.getSonstige() == null) {
      LOG.warn("Neither Kontakt, Metabolisch, Perkutan and Sonstige are set. Defaulting to 'S'");
    }

    return ApplikationsartCode.S;
  }
}
