package com.facilcomanda.ponoa.global.exception;

public class RateLimitExceededException extends RuntimeException {

  private final long retryAfterSeconds;
  private final String limitType;

  public RateLimitExceededException(String message, long retryAfterSeconds, String limitType) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
    this.limitType = limitType;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }

  public String getLimitType() {
    return limitType;
  }
}
