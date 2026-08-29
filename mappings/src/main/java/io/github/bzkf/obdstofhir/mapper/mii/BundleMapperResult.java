package io.github.bzkf.obdstofhir.mapper.mii;

import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Provenance;

public record BundleMapperResult(Bundle bundle, List<Provenance> provenances) {}
