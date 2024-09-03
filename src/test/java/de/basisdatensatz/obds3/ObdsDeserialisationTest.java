package de.basisdatensatz.obds3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ObdsDeserialisationTest {

  @Test
  void testShouldDeserializeObds3() throws IOException {
    var resource = this.getClass().getClassLoader().getResource("obds3/test1.xml");
    var xmlMapper =
        XmlMapper.builder()
            .defaultUseWrapper(false)
            .addModule(new JakartaXmlBindAnnotationModule())
            .addModule(new Jdk8Module())
            .build();

    var actual = xmlMapper.readValue(resource.openStream(), OBDS.class);

    assertThat(actual).isInstanceOf(OBDS.class);
    assertThat(actual.mengePatient.getPatient().get(0).patientID).isEqualTo("00001234");
  }
}
