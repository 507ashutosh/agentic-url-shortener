package com.agentic.shortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), clock);

    @Test
    void allowsRequestsUpToTheLimitThenBlocks() {
        assertThat(limiter.tryAcquire("client-a")).isTrue();
        assertThat(limiter.tryAcquire("client-a")).isTrue();
        assertThat(limiter.tryAcquire("client-a")).isFalse();
    }

    @Test
    void countsEachClientSeparately() {
        limiter.tryAcquire("client-a");
        limiter.tryAcquire("client-a");

        assertThat(limiter.tryAcquire("client-a")).isFalse();
        assertThat(limiter.tryAcquire("client-b")).isTrue();
    }

    @Test
    void allowsRequestsAgainOnceTheWindowHasPassed() {
        limiter.tryAcquire("client-a");
        limiter.tryAcquire("client-a");
        assertThat(limiter.tryAcquire("client-a")).isFalse();

        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("client-a")).isTrue();
    }

    /** A clock the test can move forward by hand. */
    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-14T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
