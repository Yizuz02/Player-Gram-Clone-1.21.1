package net.yizuz.playerogrammod.client;

import java.util.UUID;

/**
 * Tracks the hold-Shift interaction. Pure timing state so cancellation,
 * shift-click suppression, and one-shot opening can be tested without a client.
 */
public final class RingHoldTracker {
    public static final long HOLD_DURATION_MS = 1000L;

    private UUID ringId;
    private long startedAt;
    private boolean tracking;
    private boolean suppressed;
    private boolean opened;

    /**
     * @return true exactly when the hold threshold has been reached and the caller
     * should open the screen. Call {@link #opened()} immediately afterwards.
     */
    public boolean update(UUID hoveredRingId, boolean shiftDown, long now) {
        if (!shiftDown) {
            this.suppressed = false;
            this.opened = false;
            this.resetProgress();
            return false;
        }
        if (this.suppressed) {
            return false;
        }
        if (hoveredRingId == null) {
            this.resetProgress();
            return false;
        }
        if (!this.tracking || !hoveredRingId.equals(this.ringId)) {
            this.ringId = hoveredRingId;
            this.startedAt = now;
            this.tracking = true;
            this.opened = false;
            return false;
        }
        return !this.opened && now - this.startedAt >= HOLD_DURATION_MS;
    }

    public void click(boolean shiftDown) {
        this.resetProgress();
        if (shiftDown) {
            this.suppressed = true;
        }
    }

    public void opened() {
        this.opened = true;
        this.suppressed = true;
        this.resetProgress();
    }

    public void cancel() {
        this.resetProgress();
    }

    public float progress(long now) {
        if (!this.tracking) {
            return 0.0f;
        }
        float value = (now - this.startedAt) / (float) HOLD_DURATION_MS;
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public boolean isTracking() {
        return this.tracking;
    }

    public UUID ringId() {
        return this.ringId;
    }

    private void resetProgress() {
        this.tracking = false;
        this.ringId = null;
        this.startedAt = 0L;
    }
}
