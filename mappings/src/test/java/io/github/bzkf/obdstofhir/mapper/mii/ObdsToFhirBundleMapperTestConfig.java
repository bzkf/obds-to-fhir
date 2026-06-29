package io.github.bzkf.obdstofhir.mapper.mii;

import io.github.bzkf.obdstofhir.FhirProperties;
import io.github.bzkf.obdstofhir.SubstanzToAtcMapper;
import io.github.bzkf.obdstofhir.WeitereKlassifikationCodingMapper;
import io.github.bzkf.obdstofhir.mapper.DeviceMapper;
import io.github.bzkf.obdstofhir.mapper.ProvenanceMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Bundles every mapper bean required to wire up an {@link ObdsToFhirBundleMapper} for tests, so
 * that {@code @SpringBootTest(classes = ...)} sites only need to reference this single class
 * instead of repeating the full mapper list.
 */
@Configuration
@Import({
  FhirProperties.class,
  ObdsToFhirBundleMapper.class,
  PatientMapper.class,
  ConditionMapper.class,
  SystemischeTherapieProcedureMapper.class,
  SystemischeTherapieMedicationStatementMapper.class,
  StrahlentherapieMapper.class,
  TodMapper.class,
  LeistungszustandMapper.class,
  OperationMapper.class,
  ResidualstatusMapper.class,
  HistologiebefundMapper.class,
  FernmetastasenMapper.class,
  GradingObservationMapper.class,
  LymphknotenuntersuchungMapper.class,
  SpecimenMapper.class,
  VerlaufshistologieObservationMapper.class,
  StudienteilnahmeObservationMapper.class,
  VerlaufObservationMapper.class,
  GenetischeVarianteMapper.class,
  TumorkonferenzMapper.class,
  TNMMapper.class,
  GleasonScoreMapper.class,
  ModulProstataMapper.class,
  WeitereKlassifikationMapper.class,
  ErstdiagnoseEvidenzListMapper.class,
  NebenwirkungMapper.class,
  SubstanzToAtcMapper.class,
  WeitereKlassifikationCodingMapper.class,
  FruehereTumorerkrankungenMapper.class,
  ProvenanceMapper.class,
  VitalStatusMapper.class,
  DeviceMapper.class,
})
class ObdsToFhirBundleMapperTestConfig {}
