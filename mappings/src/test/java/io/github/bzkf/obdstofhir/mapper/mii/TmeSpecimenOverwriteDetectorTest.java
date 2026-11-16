package io.github.bzkf.obdstofhir.mapper.mii;

import static org.assertj.core.api.Assertions.assertThat;

import de.basisdatensatz.obds.v3.DiagnoseTyp;
import de.basisdatensatz.obds.v3.HistologieTyp;
import de.basisdatensatz.obds.v3.ModulDarmTyp;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import de.basisdatensatz.obds.v3.OPTyp;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TmeSpecimenOverwriteDetectorTest {

  private static HistologieTyp histologie(String histologieId) {
    var histologie = new HistologieTyp();
    histologie.setHistologieID(histologieId);
    return histologie;
  }

  private static @Nullable ModulDarmTyp modulDarm(@Nullable String tme) {
    if (tme == null) {
      return null;
    }
    var modulDarm = new ModulDarmTyp();
    modulDarm.setRektumQualitaetTME(tme);
    return modulDarm;
  }

  private static Meldung op(String meldungId, String histologieId, @Nullable String tme) {
    var op = new OPTyp();
    op.setHistologie(histologie(histologieId));
    op.setModulDarm(modulDarm(tme));
    var meldung = new Meldung();
    meldung.setMeldungID(meldungId);
    meldung.setOP(op);
    return meldung;
  }

  private static Meldung diagnose(String meldungId, String histologieId, @Nullable String tme) {
    var diagnose = new DiagnoseTyp();
    diagnose.setHistologie(histologie(histologieId));
    diagnose.setModulDarm(modulDarm(tme));
    var meldung = new Meldung();
    meldung.setMeldungID(meldungId);
    meldung.setDiagnose(diagnose);
    return meldung;
  }

  @Test
  void detect_withLaterMeldungWithoutTmeForSameHistologie_shouldReportRisk() {
    var findings =
        TmeSpecimenOverwriteDetector.detect(
            List.of(op("op-1", "H1", "2"), diagnose("diagnose-1", "H1", null)));

    assertThat(findings)
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.histologieId()).isEqualTo("H1");
              assertThat(finding.last().meldungId()).isEqualTo("diagnose-1");
              assertThat(finding.describeSections())
                  .isEqualTo("op-1:OP=2, diagnose-1:Diagnose=<none>");
            });
  }

  @Test
  void detect_withTmeInTheLastMeldung_shouldNotReportRisk() {
    var findings =
        TmeSpecimenOverwriteDetector.detect(
            List.of(diagnose("diagnose-1", "H1", null), op("op-1", "H1", "2")));

    assertThat(findings).isEmpty();
  }

  @Test
  void detect_withDifferentOrBlankHistologieIds_shouldNotReportRisk() {
    var findings =
        TmeSpecimenOverwriteDetector.detect(
            List.of(
                op("op-1", "H1", "2"),
                diagnose("diagnose-1", "H2", null),
                op("op-2", "", "1"),
                diagnose("diagnose-2", "", null)));

    assertThat(findings).isEmpty();
  }

  @Test
  void detect_withoutAnyTme_shouldNotReportRisk() {
    var findings =
        TmeSpecimenOverwriteDetector.detect(
            List.of(op("op-1", "H1", null), diagnose("diagnose-1", "H1", null)));

    assertThat(findings).isEmpty();
  }
}
