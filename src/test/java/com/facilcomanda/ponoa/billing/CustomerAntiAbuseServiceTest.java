package com.facilcomanda.ponoa.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.facilcomanda.ponoa.global.exception.RateLimitExceededException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerAntiAbuseServiceTest {

  private CustomerAntiAbuseService antiAbuseService;

  @BeforeEach
  void setUp() {
    // 5 per minute for user, 10 for branch, 8 for IP, 3 daily quota
    antiAbuseService = new CustomerAntiAbuseService(5, 10, 8, 3);
  }

  @Test
  @DisplayName("Should allow requests within rate limits")
  void shouldAllowRequestsWithinLimit() {
    UUID userId = UUID.randomUUID();
    Long branchId = 1L;
    String ip = "192.168.1.10";

    for (int i = 0; i < 5; i++) {
      antiAbuseService.checkRateLimitAndAbuse(branchId, userId, ip);
    }
  }

  @Test
  @DisplayName("Should throw RateLimitExceededException when user exceeds per-minute limit")
  void shouldThrowWhenUserExceedsLimit() {
    UUID userId = UUID.randomUUID();
    Long branchId = 1L;
    String ip = "192.168.1.10";

    for (int i = 0; i < 5; i++) {
      antiAbuseService.checkRateLimitAndAbuse(branchId, userId, ip);
    }

    assertThatThrownBy(() -> antiAbuseService.checkRateLimitAndAbuse(branchId, userId, ip))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessageContaining("límite de consultas permitidas")
        .satisfies(
            ex -> {
              RateLimitExceededException rle = (RateLimitExceededException) ex;
              assertThat(rle.getLimitType()).isEqualTo("USER");
              assertThat(rle.getRetryAfterSeconds()).isGreaterThan(0);
            });
  }

  @Test
  @DisplayName("Should throw RateLimitExceededException when branch exceeds per-minute limit")
  void shouldThrowWhenBranchExceedsLimit() {
    Long branchId = 2L;

    for (int i = 0; i < 10; i++) {
      antiAbuseService.checkRateLimitAndAbuse(branchId, UUID.randomUUID(), "192.168.1." + i);
    }

    assertThatThrownBy(
            () -> antiAbuseService.checkRateLimitAndAbuse(branchId, UUID.randomUUID(), "192.168.1.99"))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessageContaining("límite de consultas permitidas")
        .satisfies(
            ex -> {
              RateLimitExceededException rle = (RateLimitExceededException) ex;
              assertThat(rle.getLimitType()).isEqualTo("BRANCH");
            });
  }

  @Test
  @DisplayName("Should quarantine user after 5 consecutive misses (anti-enumeration)")
  void shouldQuarantineAfterConsecutiveMisses() {
    UUID userId = UUID.randomUUID();
    String ip = "10.0.0.5";

    for (int i = 0; i < 5; i++) {
      antiAbuseService.recordNegativeLookup("DNI", "0000000" + i, userId, ip);
    }

    assertThatThrownBy(() -> antiAbuseService.checkRateLimitAndAbuse(1L, userId, ip))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessageContaining("Actividad sospechosa detectada")
        .satisfies(
            ex -> {
              RateLimitExceededException rle = (RateLimitExceededException) ex;
              assertThat(rle.getLimitType()).isEqualTo("ABUSE_QUARANTINE");
            });
  }

  @Test
  @DisplayName("Should reset consecutive misses counter on successful lookup")
  void shouldResetConsecutiveMissesOnSuccess() {
    UUID userId = UUID.randomUUID();
    String ip = "10.0.0.5";

    for (int i = 0; i < 4; i++) {
      antiAbuseService.recordNegativeLookup("DNI", "0000000" + i, userId, ip);
    }

    antiAbuseService.recordSuccessfulLookup(userId, ip);

    antiAbuseService.recordNegativeLookup("DNI", "11111111", userId, ip);
    antiAbuseService.recordNegativeLookup("DNI", "22222222", userId, ip);

    antiAbuseService.checkRateLimitAndAbuse(1L, userId, ip);
  }

  @Test
  @DisplayName("Should store and detect negatively cached documents")
  void shouldManageNegativeCache() {
    assertThat(antiAbuseService.isNegativelyCached("DNI", "99999999")).isFalse();

    antiAbuseService.recordNegativeLookup("DNI", "99999999", null, null);

    assertThat(antiAbuseService.isNegativelyCached("DNI", "99999999")).isTrue();
  }

  @Test
  @DisplayName("Should enforce daily quota cap on third-party calls")
  void shouldEnforceDailyQuotaCap() {
    assertThat(antiAbuseService.tryAcquireDailyQuota()).isTrue();
    assertThat(antiAbuseService.tryAcquireDailyQuota()).isTrue();
    assertThat(antiAbuseService.tryAcquireDailyQuota()).isTrue();

    assertThat(antiAbuseService.tryAcquireDailyQuota()).isFalse();
    assertThat(antiAbuseService.getDailyBillableCallsCount()).isEqualTo(3);
  }
}
