package dev.sacdj.scdi.combat;

import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;

public final class CombatState {

    private Instant expiresAt;
    private BukkitTask expiryTask;

    public CombatState(Instant expiresAt, BukkitTask expiryTask) {
        this.expiresAt = expiresAt;
        this.expiryTask = expiryTask;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public void reschedule(Instant newExpiresAt, BukkitTask newTask) {
        if (expiryTask != null) {
            expiryTask.cancel();
        }
        this.expiresAt = newExpiresAt;
        this.expiryTask = newTask;
    }

    public void cancel() {
        if (expiryTask != null) {
            expiryTask.cancel();
        }
    }

    public long secondsRemaining() {
        return Math.max(0, (millisRemaining() + 999) / 1000);
    }

    public long millisRemaining() {
        return Math.max(0, expiresAt.toEpochMilli() - Instant.now().toEpochMilli());
    }
}
