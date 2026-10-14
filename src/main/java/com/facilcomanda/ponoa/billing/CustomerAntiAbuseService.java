package com.facilcomanda.ponoa.billing;

import com.facilcomanda.ponoa.global.exception.RateLimitExceededException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CustomerAntiAbuseService {

  private static final Logger log = LoggerFactory.getLogger(CustomerAntiAbuseService.class);

  private static final int MAX_ENTRIES = 5000;
  private static final int CONSECUTIVE_MISS_THRESHOLD = 5;
  private static final long QUARANTINE_DURATION_SECONDS = 300; // 5 minutes
  private static final long NEGATIVE_CACHE_TTL_SECONDS = 900; // 15 minutes

  private final int userPerMinuteLimit;
  private final int branchPerMinuteLimit;
  private final int ipPerMinuteLimit;
  private final int dailyQuotaLimit;

  // Sliding window timestamps
  private final Map<String, ConcurrentLinkedDeque<Instant>> userWindows = new ConcurrentHashMap<>();
  private final Map<String, ConcurrentLinkedDeque<Instant>> branchWindows = new ConcurrentHashMap<>();
  private final Map<String, ConcurrentLinkedDeque<Instant>> ipWindows = new ConcurrentHashMap<>();

  // Abuse quarantine: key -> quarantine expiry
  private final Map<String, Instant> quarantineMap = new ConcurrentHashMap<>();
  private final Map<String, Integer> consecutiveMisses = new ConcurrentHashMap<>();

  // Negative cache: docType:docNumber -> expiry
  private final Map<String, Instant> negativeCache = new ConcurrentHashMap<>();

  // Daily quota guard
  private volatile LocalDate currentQuotaDay = LocalDate.now();
  private final AtomicInteger dailyBillableCalls = new AtomicInteger(0);

  public CustomerAntiAbuseService(
      @Value("${app.customer-api.rate-limit.user-per-minute:15}") int userPerMinuteLimit,
      @Value("${app.customer-api.rate-limit.branch-per-minute:40}") int branchPerMinuteLimit,
      @Value("${app.customer-api.rate-limit.ip-per-minute:20}") int ipPerMinuteLimit,
      @Value("${app.tax-api.daily-quota-limit:500}") int dailyQuotaLimit) {
    this.userPerMinuteLimit = userPerMinuteLimit;
    this.branchPerMinuteLimit = branchPerMinuteLimit;
    this.ipPerMinuteLimit = ipPerMinuteLimit;
    this.dailyQuotaLimit = dailyQuotaLimit;
  }

  public void checkRateLimitAndAbuse(Long branchId, UUID userId, String ipAddress) {
    Instant now = Instant.now();

    // 1. Check Abuse Quarantine
    String userKey = userId != null ? "USER:" + userId : null;
    String ipKey = ipAddress != null && !ipAddress.isBlank() ? "IP:" + ipAddress : null;

    checkQuarantine(userKey, now);
    checkQuarantine(ipKey, now);

    // 2. Sliding Window Rate Limits
    if (userKey != null) {
      applySlidingWindow(userWindows, userKey, userPerMinuteLimit, Duration.ofSeconds(60), now, "USER");
    }
    if (branchId != null) {
      String branchKey = "BRANCH:" + branchId;
      applySlidingWindow(branchWindows, branchKey, branchPerMinuteLimit, Duration.ofSeconds(60), now, "BRANCH");
    }
    if (ipKey != null) {
      applySlidingWindow(ipWindows, ipKey, ipPerMinuteLimit, Duration.ofSeconds(60), now, "IP");
    }
  }

  public boolean isNegativelyCached(String docType, String docNumber) {
    String key = docType + ":" + docNumber;
    Instant expiry = negativeCache.get(key);
    if (expiry != null) {
      if (Instant.now().isBefore(expiry)) {
        return true;
      }
      negativeCache.remove(key);
    }
    return false;
  }

  public void recordNegativeLookup(String docType, String docNumber, UUID userId, String ipAddress) {
    String key = docType + ":" + docNumber;
    ensureCapacity(negativeCache);
    negativeCache.put(key, Instant.now().plusSeconds(NEGATIVE_CACHE_TTL_SECONDS));

    recordMiss(userId != null ? "USER:" + userId : null);
    recordMiss(ipAddress != null && !ipAddress.isBlank() ? "IP:" + ipAddress : null);
  }

  public void recordSuccessfulLookup(UUID userId, String ipAddress) {
    if (userId != null) {
      consecutiveMisses.remove("USER:" + userId);
    }
    if (ipAddress != null && !ipAddress.isBlank()) {
      consecutiveMisses.remove("IP:" + ipAddress);
    }
  }

  public boolean tryAcquireDailyQuota() {
    LocalDate today = LocalDate.now();
    if (!today.equals(currentQuotaDay)) {
      synchronized (this) {
        if (!today.equals(currentQuotaDay)) {
          currentQuotaDay = today;
          dailyBillableCalls.set(0);
        }
      }
    }

    if (dailyBillableCalls.get() >= dailyQuotaLimit) {
      log.warn("Tax API daily quota reached: {} / {} calls. Fallback to manual entry.",
          dailyBillableCalls.get(), dailyQuotaLimit);
      return false;
    }

    dailyBillableCalls.incrementAndGet();
    return true;
  }

  public int getDailyBillableCallsCount() {
    return dailyBillableCalls.get();
  }

  public int getDailyQuotaLimit() {
    return dailyQuotaLimit;
  }

  private void checkQuarantine(String key, Instant now) {
    if (key == null) return;
    Instant quarantineExpiry = quarantineMap.get(key);
    if (quarantineExpiry != null) {
      if (now.isBefore(quarantineExpiry)) {
        long secondsRemaining = Duration.between(now, quarantineExpiry).toSeconds() + 1;
        throw new RateLimitExceededException(
            "Actividad sospechosa detectada (múltiples consultas fallidas consecutivas). Bloqueo temporal por "
                + secondsRemaining
                + " segundos.",
            secondsRemaining,
            "ABUSE_QUARANTINE");
      }
      quarantineMap.remove(key);
    }
  }

  private void recordMiss(String key) {
    if (key == null) return;
    int misses = consecutiveMisses.compute(key, (k, count) -> (count == null ? 1 : count + 1));
    if (misses >= CONSECUTIVE_MISS_THRESHOLD) {
      log.warn("Abuse detected on key {}: {} consecutive misses. Quarantining for {} seconds.",
          key, misses, QUARANTINE_DURATION_SECONDS);
      ensureCapacity(quarantineMap);
      quarantineMap.put(key, Instant.now().plusSeconds(QUARANTINE_DURATION_SECONDS));
      consecutiveMisses.remove(key);
    }
  }

  private void applySlidingWindow(
      Map<String, ConcurrentLinkedDeque<Instant>> windowMap,
      String key,
      int limit,
      Duration windowDuration,
      Instant now,
      String limitType) {

    ensureCapacity(windowMap);
    ConcurrentLinkedDeque<Instant> timestamps =
        windowMap.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

    Instant threshold = now.minus(windowDuration);

    // Evict timestamps older than the sliding window
    while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(threshold)) {
      timestamps.pollFirst();
    }

    if (timestamps.size() >= limit) {
      Instant oldest = timestamps.peekFirst();
      long secondsRemaining =
          oldest != null ? Math.max(1, Duration.between(threshold, oldest).toSeconds()) : windowDuration.toSeconds();

      throw new RateLimitExceededException(
          "Has superado el límite de consultas permitidas por minuto ("
              + limit
              + " req/min). Inténtalo de nuevo en "
              + secondsRemaining
              + " segundos.",
          secondsRemaining,
          limitType);
    }

    timestamps.addLast(now);
  }

  @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300_000)
  public void evictExpiredEntries() {
    Instant now = Instant.now();
    Instant threshold = now.minus(Duration.ofSeconds(60));

    cleanWindowMap(userWindows, threshold);
    cleanWindowMap(branchWindows, threshold);
    cleanWindowMap(ipWindows, threshold);

    quarantineMap.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    negativeCache.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
  }

  private void cleanWindowMap(Map<String, ConcurrentLinkedDeque<Instant>> windowMap, Instant threshold) {
    windowMap.entrySet().removeIf(entry -> {
      ConcurrentLinkedDeque<Instant> deque = entry.getValue();
      while (!deque.isEmpty() && deque.peekFirst().isBefore(threshold)) {
        deque.pollFirst();
      }
      return deque.isEmpty();
    });
  }

  private <K, V> void ensureCapacity(Map<K, V> map) {
    if (map.size() >= MAX_ENTRIES) {
      map.clear(); // OOM prevention guard
    }
  }
}
