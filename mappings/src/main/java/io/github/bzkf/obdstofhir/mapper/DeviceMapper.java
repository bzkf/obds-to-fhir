package io.github.bzkf.obdstofhir.mapper;

import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.dizuker.tofhir.IdUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.Device.DeviceNameType;
import org.hl7.fhir.r4.model.Device.FHIRDeviceStatus;
import org.hl7.fhir.r4.model.Identifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeviceMapper extends ObdsToFhirMapper {

  @Value("${lib-version}")
  private String libVersion;

  public DeviceMapper(FhirProperties fhirProperties) {
    super(fhirProperties);
  }

  public Device map() {
    var device = new Device();
    var identifier =
        new Identifier()
            .setSystem(fhirProperties.getSystems().getIdentifiers().getObdsToFhirDeviceId())
            .setValue("obds-to-fhir-v" + libVersion);
    device.addIdentifier(identifier);
    device.setId(IdUtils.fromIdentifier(identifier));
    device.setStatus(FHIRDeviceStatus.ACTIVE);
    device.setManufacturer("https://github.com/bzkf/");
    device.addDeviceName().setName("oBDS-to-FHIR").setType(DeviceNameType.USERFRIENDLYNAME);
    device.setType(
        new CodeableConcept(
            fhirProperties
                .getCodings()
                .snomed()
                .setCode("706689003")
                .setDisplay("Application program software (physical object)")));

    device.addVersion().setValue(libVersion);
    device
        .addContact()
        .setSystem(ContactPointSystem.URL)
        .setValue("https://github.com/bzkf/obds-to-fhir/issues");

    return device;
  }
}
