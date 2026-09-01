package com.stockflow.core;

import com.stockflow.models.Product;

import java.util.concurrent.ThreadLocalRandom;

/**
 * DemandModel — per-store-per-product demand generator.
 *
 * Demand = baseDemand
 *        * dayOfWeekMultiplier (weekends ~1.5x)
 *        * seasonalMultiplier (slow sine over the simulation day)
 *        * noise (uniform 0.7..1.3)
 *        + occasional spike (~2% chance, 2x..4x)
 *
 * The "Demand Explosion" button toggles a multiplier (set externally)
 * that the engine multiplies in — chosen this way so the GUI just flips
 * a flag, no engine rewrite.
 */
public class DemandModel {
    private volatile double explosionMultiplier = 1.0;

    public void triggerExplosion() { explosionMultiplier = 3.0; }
    public void clearExplosion() { explosionMultiplier = 1.0; }
    public double getExplosionMultiplier() { return explosionMultiplier; }

    /** Mean daily demand — tuned so a typical store draws through its stock. */
    public double baseDailyDemand(Product p) {
        // Heavier / pricier items sell slower; cheaper items faster.
        // 50 units/day for a $5 item, 5 units/day for a $200 item.
        double baseline = 50.0 / Math.max(0.5, Math.log10(p.getUnitPrice() + 1));
        return baseline;
    }

    /**
     * Sample demand for one simulated day at one store.
     * @param p product
     * @param dayIndex which simulated day (0-based)
     * @param storeSeed per-store jitter
     */
    public int sampleDailyDemand(Product p, int dayIndex, long storeSeed) {
        double base = baseDailyDemand(p);
        // Day-of-week cycle: weekends (dayIndex%7 == 5 or 6) sell more.
        int dow = dayIndex % 7;
        double dowMul = (dow == 5 || dow == 6) ? 1.5 : 1.0;

        // Seasonal: slow sinusoid over 30 days, ±20%.
        double seasonal = 1.0 + 0.2 * Math.sin(2 * Math.PI * dayIndex / 30.0);

        // Per-store bias — some stores are just busier.
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double storeBias = 0.5 + rng.nextDouble(); // 0.5..1.5

        // Noise.
        double noise = 0.7 + rng.nextDouble() * 0.6; // 0.7..1.3

        // Random spike.
        double spike = rng.nextDouble() < 0.02 ? (2.0 + rng.nextDouble() * 2.0) : 1.0;

        double d = base * dowMul * seasonal * storeBias * noise * spike * explosionMultiplier;
        return Math.max(0, (int) Math.round(d));
    }
}
