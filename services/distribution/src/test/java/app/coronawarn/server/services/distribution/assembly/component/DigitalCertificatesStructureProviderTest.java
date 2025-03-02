package app.coronawarn.server.services.distribution.assembly.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import app.coronawarn.server.common.shared.collection.ImmutableStack;
import app.coronawarn.server.services.distribution.assembly.structure.Writable;
import app.coronawarn.server.services.distribution.assembly.structure.WritableOnDisk;
import app.coronawarn.server.services.distribution.assembly.structure.archive.decorator.signing.DistributionArchiveSigningDecorator;
import app.coronawarn.server.services.distribution.assembly.structure.directory.Directory;
import app.coronawarn.server.services.distribution.assembly.structure.directory.DirectoryOnDisk;
import app.coronawarn.server.services.distribution.config.DistributionServiceConfig;
import app.coronawarn.server.services.distribution.dgc.DigitalGreenCertificateToCborMapping;
import app.coronawarn.server.services.distribution.dgc.DigitalGreenCertificateToProtobufMapping;
import app.coronawarn.server.services.distribution.dgc.client.DigitalCovidCertificateClient;
import app.coronawarn.server.services.distribution.dgc.client.TestDigitalCovidCertificateClient;
import app.coronawarn.server.services.distribution.dgc.dsc.DigitalCovidValidationCertificateToProtobufMapping;
import app.coronawarn.server.services.distribution.dgc.dsc.DigitalSigningCertificatesClient;
import app.coronawarn.server.services.distribution.dgc.dsc.DigitalSigningCertificatesToProtobufMapping;
import app.coronawarn.server.services.distribution.dgc.dsc.errors.InvalidContentResponseException;
import app.coronawarn.server.services.distribution.dgc.dsc.errors.InvalidFingerprintException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@EnableConfigurationProperties(
    value = { DistributionServiceConfig.class })
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
    classes = { DigitalCertificatesStructureProvider.class, DigitalGreenCertificateToProtobufMapping.class,
        DigitalGreenCertificateToCborMapping.class, DigitalCovidValidationCertificateToProtobufMapping.class,
        CryptoProvider.class, DistributionServiceConfig.class, TestDigitalCovidCertificateClient.class,
        DigitalSigningCertificatesToProtobufMapping.class, DigitalSigningCertificatesClient.class,
        BusinessRulesArchiveBuilder.class },
    initializers = ConfigDataApplicationContextInitializer.class)
@ActiveProfiles({ "fake-dcc-client", "fake-dsc-client" })
class DigitalCertificatesStructureProviderTest {

  private static final String PARENT_TEST_FOLDER = "parent";

  @Autowired
  DistributionServiceConfig distributionServiceConfig;

  @Autowired
  CryptoProvider cryptoProvider;

  @Autowired
  DigitalGreenCertificateToProtobufMapping dgcToProtobufMapping;

  @Autowired
  DigitalGreenCertificateToCborMapping dgcToCborMappingMock;

  @MockBean
  OutputDirectoryProvider outputDirectoryProvider;

  @Autowired
  DigitalSigningCertificatesToProtobufMapping digitalSigningCertificatesToProtobufMapping;

  @Autowired
  DigitalCovidValidationCertificateToProtobufMapping digitalCovidValidationCertificateToProtobufMapping;

  @MockBean
  DigitalSigningCertificatesClient digitalSigningCertificatesClient;

  @Autowired
  DigitalCovidCertificateClient digitalCovidCertificateClient;

  @Rule
  TemporaryFolder testOutputFolder = new TemporaryFolder();

  @Autowired
  DigitalCertificatesStructureProvider underTest;

  @BeforeEach
  public void setup() throws IOException {
    // create a specific test folder for later assertions of structures.
    testOutputFolder.create();
    File outputDirectory = testOutputFolder.newFolder(PARENT_TEST_FOLDER);
    Directory<WritableOnDisk> testDirectory = new DirectoryOnDisk(outputDirectory);
    when(outputDirectoryProvider.getDirectory()).thenReturn(testDirectory);
  }

  @Test
  void shouldCreateCorrectFileStructureForValueSets() {
    DirectoryOnDisk digitalGreenCertificates = underTest.getDigitalGreenCertificates();
    digitalGreenCertificates.prepare(new ImmutableStack<>());

    assertEquals("ehn-dgc", digitalGreenCertificates.getName());
    List<String> supportedLanguages = digitalGreenCertificates.getWritables().stream().map(Writable::getName).collect(
        Collectors.toList());
    List<String> expectedLanguages = Arrays.asList("de", "en", "bg", "pl", "ro", "tr");
    assertTrue(supportedLanguages.containsAll(expectedLanguages));

    digitalGreenCertificates.getWritables().stream()
        .filter(writableOnDisk -> writableOnDisk instanceof DirectoryOnDisk)
        .map(directory -> ((DirectoryOnDisk) directory).getWritables().iterator().next())
        .forEach(valueSet -> {
          assertEquals("value-sets", valueSet.getName());
          List<String> archiveContent = ((DistributionArchiveSigningDecorator) valueSet).getWritables().stream()
              .map(Writable::getName).collect(Collectors.toList());
          assertTrue((archiveContent).containsAll(Set.of("export.bin", "export.sig")));
        });
  }

  private Predicate<Writable<WritableOnDisk>> filterByArchiveName(String archiveName) {
    return writable -> writable.getName().equals(archiveName);
  }

  @Test
  void shouldCreateCorrectFileStructureForBusinessRules() {
    DirectoryOnDisk digitalGreenCertificates = underTest.getDigitalGreenCertificates();
    digitalGreenCertificates.prepare(new ImmutableStack<>());

    assertEquals("ehn-dgc", digitalGreenCertificates.getName());

    List<Writable<WritableOnDisk>> businessRulesArchives = digitalGreenCertificates.getWritables().stream()
        .filter(writableOnDisk -> writableOnDisk instanceof DistributionArchiveSigningDecorator)
        .collect(Collectors.toList());

    assertThat(businessRulesArchives).hasSize(5);

    assertThat(businessRulesArchives.stream()
        .filter(filterByArchiveName(DigitalCertificatesStructureProvider.ONBOARDED_COUNTRIES))).hasSize(1);
    assertThat(businessRulesArchives.stream()
        .filter(filterByArchiveName(DigitalCertificatesStructureProvider.ACCEPTANCE_RULES))).hasSize(1);
    assertThat(businessRulesArchives.stream()
        .filter(filterByArchiveName(DigitalCertificatesStructureProvider.INVALIDATION_RULES))).hasSize(1);
    assertThat(businessRulesArchives.stream()
        .filter(filterByArchiveName(DigitalCertificatesStructureProvider.DIGITAL_CERTIFICATES_STRUCTURE_PROVIDER)))
            .hasSize(1);
    assertThat(businessRulesArchives.stream()
        .filter(filterByArchiveName(DigitalCertificatesStructureProvider.VALIDATION_SERVICES))).hasSize(1);
  }

  @Test
  void testExceptionResults() {
    try {
      throw new InvalidFingerprintException();
    } catch (InvalidFingerprintException e) {
      assertEquals("Obtaining service provider allow list failed", e.getMessage());
    }

    try {
      throw new InvalidFingerprintException(new IllegalStateException());
    } catch (InvalidFingerprintException e) {
      assertEquals("Obtaining service provider allow list failed", e.getMessage());
    }

    try {
      throw new InvalidContentResponseException();
    } catch (InvalidContentResponseException e) {
      assertEquals("Obtaining providers from content response failed", e.getMessage());
    }
  }
}
