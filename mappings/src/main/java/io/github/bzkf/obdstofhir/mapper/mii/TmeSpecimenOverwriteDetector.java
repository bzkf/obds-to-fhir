package io.github.bzkf.obdstofhir.mapper.mii;

import de.basisdatensatz.obds.v3.HistologieTyp;
import de.basisdatensatz.obds.v3.ModulDarmTyp;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

/**
 * Detects (but doesn't prevent) the risk of losing the {@code Modul_Darm/RektumQualitaetTME} of a
 * Histologie Specimen.
 *
 * <p>The TME quality is meant to be mapped to {@code Specimen.condition} of the Specimen created
 * from the Histologie of the same section. That Specimen's ID is derived from the {@code
 * Histologie_ID}, so every Meldung of a bundle reporting the same Histologie creates the same
 * Specimen, and the one processed last wins (see {@code ObdsToFhirBundleMapper#addToBundle}). If
 * that last Meldung doesn't contain a TME quality, while an earlier one does, the TME quality would
 * be lost.
 *
 * <p>This is only used for logging, to find out whether the case occurs in practice.
 */
final class TmeSpecimenOverwriteDetector {

  private TmeSpecimenOverwriteDetector() {}

  /** One Meldung section with a Histologie, and its TME quality, if any. */
  record Section(String meldungId, String sectionType, @Nullable String tme) {
    @Override
    public String toString() {
      return String.format("%s:%s=%s", meldungId, sectionType, tme == null ? "<none>" : tme);
    }
  }

  /**
   * All sections reporting the same Histologie, in processing order, where an earlier section's TME
   * quality would be overwritten by the last one, which has none.
   */
  record Finding(String histologieId, List<Section> sections) {
    Section last() {
      return sections.getLast();
    }

    String describeSections() {
      return sections.stream().map(Section::toString).collect(Collectors.joining(", "));
    }
  }

  private record SectionContent(
      String sectionType, @Nullable HistologieTyp histologie, @Nullable ModulDarmTyp modulDarm) {}

  /**
   * @param meldungen the Meldungen of a single bundle, in the order they are processed in
   */
  static List<Finding> detect(List<Meldung> meldungen) {
    var sectionsByHistologieId = new LinkedHashMap<String, List<Section>>();

    for (var meldung : meldungen) {
      var content = contentOf(meldung);
      if (content == null || content.histologie() == null) {
        continue;
      }

      var histologieId = content.histologie().getHistologieID();
      if (!StringUtils.hasText(histologieId)) {
        // the Specimen ID falls back to the Meldung_ID, so it can't be shared with other Meldungen
        continue;
      }

      var tme = content.modulDarm() != null ? content.modulDarm().getRektumQualitaetTME() : null;
      sectionsByHistologieId
          .computeIfAbsent(histologieId, k -> new ArrayList<>())
          .add(new Section(meldung.getMeldungID(), content.sectionType(), tme));
    }

    var findings = new ArrayList<Finding>();
    for (var entry : sectionsByHistologieId.entrySet()) {
      var sections = entry.getValue();
      var anyHasTme = sections.stream().anyMatch(s -> s.tme() != null);
      var lastHasNoTme = sections.getLast().tme() == null;
      if (sections.size() > 1 && anyHasTme && lastHasNoTme) {
        findings.add(new Finding(entry.getKey(), List.copyOf(sections)));
      }
    }
    return findings;
  }

  private static @Nullable SectionContent contentOf(Meldung meldung) {
    if (meldung.getDiagnose() != null) {
      return new SectionContent(
          "Diagnose", meldung.getDiagnose().getHistologie(), meldung.getDiagnose().getModulDarm());
    }
    if (meldung.getOP() != null) {
      return new SectionContent(
          "OP", meldung.getOP().getHistologie(), meldung.getOP().getModulDarm());
    }
    if (meldung.getVerlauf() != null) {
      return new SectionContent(
          "Verlauf", meldung.getVerlauf().getHistologie(), meldung.getVerlauf().getModulDarm());
    }
    if (meldung.getPathologie() != null) {
      return new SectionContent(
          "Pathologie",
          meldung.getPathologie().getHistologie(),
          meldung.getPathologie().getModulDarm());
    }
    // the remaining Meldung types (ST, SYST, Tod, Tumorkonferenz) don't contain a Histologie
    return null;
  }
}
