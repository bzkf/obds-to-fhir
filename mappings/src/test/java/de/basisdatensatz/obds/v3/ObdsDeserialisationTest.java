package de.basisdatensatz.obds.v3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ObdsDeserialisationTest {

  @Test
  void testShouldDeserializeObds3() throws IOException {
    final var resource = this.getClass().getClassLoader().getResource("obds3/test1.xml");
    final var xmlMapper =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .addModule(new JakartaXmlBindAnnotationModule())
            .addModule(new Jdk8Module())
            .build();

    final var actual = xmlMapper.readValue(resource.openStream(), OBDS.class);

    assertThat(actual).isInstanceOf(OBDS.class);
    assertThat(actual.getMengePatient().getPatient()).hasSize(1);

    final var patient = actual.getMengePatient().getPatient().getFirst();
    assertThat(patient).isInstanceOf(OBDS.MengePatient.Patient.class);
    assertThat(patient.getMengeMeldung().getMeldung()).hasSize(1);

    final var meldung = patient.getMengeMeldung().getMeldung().getFirst();
    assertThat(meldung).isInstanceOf(OBDS.MengePatient.Patient.MengeMeldung.Meldung.class);
    assertThat(meldung.getTumorzuordnung()).isInstanceOf(TumorzuordnungTyp.class);
  }
}
