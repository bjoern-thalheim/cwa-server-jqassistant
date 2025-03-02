package app.coronawarn.server.common.persistence.domain;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.coronawarn.server.common.protocols.external.exposurenotification.ReportType;
import app.coronawarn.server.common.protocols.internal.SubmissionPayload.SubmissionType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiagnosisKeyTest {

  final static byte[] expKeyData = "testKey111111111".getBytes(StandardCharsets.US_ASCII);
  final static SubmissionType expSubmissionType = SubmissionType.SUBMISSION_TYPE_PCR_TEST;
  final static int expRollingStartIntervalNumber = 1;
  final static int expRollingPeriod = 2;
  final static int expTransmissionRiskLevel = 3;
  final static long expSubmissionTimestamp = 4L;
  static final String originCountry = "DE";
  static final Set<String> visitedCountries = Set.of("DE");
  static final ReportType reportType = ReportType.CONFIRMED_TEST;
  static final int daysSinceOnsetOfSymptoms = 1;

  final static DiagnosisKey diagnosisKey = new DiagnosisKey(expKeyData, expSubmissionType,
      expRollingStartIntervalNumber, expRollingPeriod, expTransmissionRiskLevel, expSubmissionTimestamp, false,
      originCountry, visitedCountries, reportType, daysSinceOnsetOfSymptoms);

  @Test
  void testRollingStartIntervalNumberGetter() {
    assertThat(diagnosisKey.getRollingStartIntervalNumber()).isEqualTo(expRollingStartIntervalNumber);
  }

  @Test
  void testRollingPeriodGetter() {
    assertThat(diagnosisKey.getRollingPeriod()).isEqualTo(expRollingPeriod);
  }

  @Test
  void testTransmissionRiskLevelGetter() {
    assertThat(diagnosisKey.getTransmissionRiskLevel()).isEqualTo(expTransmissionRiskLevel);
  }

  @Test
  void testSubmissionTimestampGetter() {
    assertThat(diagnosisKey.getSubmissionTimestamp()).isEqualTo(expSubmissionTimestamp);
  }

  @Test
  void testIsYoungerThanRetentionThreshold() {
    int fiveDaysAgo = (int) (LocalDateTime
        .of(LocalDate.now(UTC), LocalTime.MIDNIGHT)
        .minusDays(5).minusMinutes(10)
        .toEpochSecond(UTC) / (60 * 10));
    DiagnosisKey diagnosisKeyFiveDays = new DiagnosisKey(expKeyData, expSubmissionType, fiveDaysAgo,
        expRollingPeriod, expTransmissionRiskLevel, expSubmissionTimestamp, false, originCountry, visitedCountries,
        reportType, daysSinceOnsetOfSymptoms);

    assertThat(diagnosisKeyFiveDays.isYoungerThanRetentionThreshold(4)).isFalse();
    assertThat(diagnosisKeyFiveDays.isYoungerThanRetentionThreshold(5)).isFalse();
    assertThat(diagnosisKeyFiveDays.isYoungerThanRetentionThreshold(6)).isTrue();
  }

  @DisplayName("Test retention threshold accepts positive value")
  @ValueSource(ints = { 0, 1, Integer.MAX_VALUE })
  @ParameterizedTest
  void testRetentionThresholdAcceptsPositiveValue(int daysToRetain) {
    assertThatCode(() -> diagnosisKey.isYoungerThanRetentionThreshold(daysToRetain))
        .doesNotThrowAnyException();
  }

  @DisplayName("Test retention threshold rejects negative value")
  @ValueSource(ints = { Integer.MIN_VALUE, -1 })
  @ParameterizedTest
  void testRetentionThresholdRejectsNegativeValue(int daysToRetain) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> diagnosisKey.isYoungerThanRetentionThreshold(daysToRetain));
  }

  @Test
  void testSetTransmissionRiskLevel() {
    diagnosisKey.setTransmissionRiskLevel(0);
  }

  @Test
  void testSetReportType() {
    diagnosisKey.setReportType(null);
  }

  @Test
  void testSetDaysSinceOnsetOfSymptoms() {
    diagnosisKey.setDaysSinceOnsetOfSymptoms(0);
  }

  @Test
  void testGetVisitedCountries() {
    assertEquals("DE", diagnosisKey.getVisitedCountries().iterator().next());
  }

  @Test
  void testEquals() {
    DiagnosisKey other = new DiagnosisKey("testKey222222222".getBytes(StandardCharsets.US_ASCII), expSubmissionType,
        expRollingStartIntervalNumber, expRollingPeriod, expTransmissionRiskLevel, expSubmissionTimestamp, false,
        originCountry, visitedCountries, reportType, daysSinceOnsetOfSymptoms);
    assertFalse(diagnosisKey.equals(other));
  }

  @Test
  void testHashCode() {
    assertTrue(0 != diagnosisKey.hashCode());
  }
}
