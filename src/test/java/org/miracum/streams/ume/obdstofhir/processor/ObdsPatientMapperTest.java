package org.miracum.streams.ume.obdstofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import org.approvaltests.Approvals;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.miracum.streams.ume.obdstofhir.mapper.*;
import org.miracum.streams.ume.obdstofhir.model.ADT_GEKID;
import org.miracum.streams.ume.obdstofhir.model.MeldungExport;
import org.miracum.streams.ume.obdstofhir.model.MeldungExportList;
import org.miracum.streams.ume.obdstofhir.model.Tupel;
import org.springframework.beans.factory.annotation.Autowired;

class ObdsPatientMapperTest extends ObdsProcessorTest {

  private final ObdsPatientMapper onkoPatientMapper;

  @Autowired
  public ObdsPatientMapperTest(ObdsPatientMapper onkoPatientMapper) {
    this.onkoPatientMapper = onkoPatientMapper;
  }

  private static MeldungExportList createMeldungExportListFromPLZ(String plz) {
    // TODO: we might want to introduce more concise builder patterns
    var meldung = new ADT_GEKID.Menge_Patient.Patient.Menge_Meldung.Meldung();
    meldung.setMeldung_ID("id-" + plz);

    var mengeMeldung = new ADT_GEKID.Menge_Patient.Patient.Menge_Meldung();
    mengeMeldung.setMeldung(meldung);

    var adresse = new ADT_GEKID.Menge_Patient.Patient.Patienten_Stammdaten.Menge_Adresse.Adresse();
    adresse.setPatienten_PLZ(plz);

    var mengeAdresse = new ADT_GEKID.Menge_Patient.Patient.Patienten_Stammdaten.Menge_Adresse();
    mengeAdresse.setAdresse(List.of(adresse));

    var stammdaten = new ADT_GEKID.Menge_Patient.Patient.Patienten_Stammdaten();
    stammdaten.setMenge_Adresse(mengeAdresse);

    var patient = new ADT_GEKID.Menge_Patient.Patient();
    patient.setPatienten_Stammdaten(stammdaten);
    patient.setMenge_Meldung(mengeMeldung);

    var mengePatient = new ADT_GEKID.Menge_Patient();
    mengePatient.setPatient(patient);

    var absender = new ADT_GEKID.Absender();
    absender.setAbsender_ID("id-" + plz);

    var obdsData = new ADT_GEKID();
    obdsData.setMenge_Patient(mengePatient);
    obdsData.setAbsender(absender);

    var meldungExport = new MeldungExport();
    meldungExport.setXml_daten(obdsData);

    var meldungExportList = new MeldungExportList();
    meldungExportList.addElement(meldungExport);

    return meldungExportList;
  }

  private static Stream<Arguments> generateTestData() {
    return Stream.of(
        Arguments.of(List.of(new Tupel<>("003_Pat1_Tumor1_Therapie1_Behandlungsende_OP.xml", 1))),
        Arguments.of(List.of(new Tupel<>("007_Pat2_Tumor1_Behandlungsende_ST.xml", 1))));
  }

  @ParameterizedTest
  @MethodSource("generateTestData")
  void mapOnkoResourcesToPatient_withGivenAdtXml(List<Tupel<String, Integer>> xmlFileNames)
      throws IOException {

    var meldungExportList = buildMeldungExportList(xmlFileNames);

    var resultBundle = onkoPatientMapper.mapOnkoResourcesToPatient(meldungExportList.getElements());

    var fhirJson = fhirParser.encodeResourceToString(resultBundle);
    Approvals.verify(
        fhirJson,
        Approvals.NAMES
            .withParameters(
                xmlFileNames.stream().map(t -> t.getFirst().substring(0, 5)).toArray(String[]::new))
            .forFile()
            .withExtension(".fhir.json"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "123", "00000", "12", "12345", "1234567890"})
  void mapOnkoResourcesToPatient_withMeldungExportWithValidPLZ_shouldSetAsPostalCode(String plz)
      throws IOException {

    var meldungExportList = createMeldungExportListFromPLZ(plz);

    var resultBundle = onkoPatientMapper.mapOnkoResourcesToPatient(meldungExportList.getElements());

    assertThat(resultBundle.getEntry()).hasSize(1);

    var patient = (Patient) resultBundle.getEntry().get(0).getResource();

    assertThat(patient.getAddress()).hasSize(1);
    assertThat(patient.getAddress().get(0).getPostalCode()).isEqualTo(plz);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {""})
  void mapOnkoResourcesToPatient_withMeldungExportWithInvalidPLZ_shouldCreateNotFillAddress(
      String plz) throws IOException {

    var meldungExportList = createMeldungExportListFromPLZ(plz);

    var resultBundle = onkoPatientMapper.mapOnkoResourcesToPatient(meldungExportList.getElements());

    assertThat(resultBundle.getEntry()).hasSize(1);

    var patient = (Patient) resultBundle.getEntry().get(0).getResource();

    assertThat(patient.getAddress()).isEmpty();
  }
}
