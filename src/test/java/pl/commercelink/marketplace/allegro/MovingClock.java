package pl.commercelink.marketplace.allegro;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock that stands still until a test moves it, e.g. from a stubbed call that takes long. */
class MovingClock extends Clock {

    private Instant now = Instant.parse("2026-11-06T12:00:00Z");

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
