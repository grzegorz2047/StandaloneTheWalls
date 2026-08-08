package pl.grzegorz2047.standalonethewalls.client.performance;

/** Maps measured p95 frame time to the bounded automatic recommendation from issue #200. */
public final class BenchmarkQualitySelector {
    public static final long PRIMARY_TARGET_P95_NANOS = 16_700_000L;
    public static final long MINIMUM_TARGET_P95_NANOS = 33_300_000L;

    private BenchmarkQualitySelector() {}

    public static GraphicsBenchmarkResult select(
            FrameTimeStatistics statistics, int width, int height, double renderScale) {
        long p95 = statistics.p95Nanos();
        if (p95 <= PRIMARY_TARGET_P95_NANOS) {
            return new GraphicsBenchmarkResult(
                    GraphicsQualityPreset.MEDIUM,
                    GraphicsBenchmarkResult.TargetStatus.MEETS_PRIMARY_TARGET,
                    statistics,
                    width,
                    height,
                    renderScale);
        }
        if (p95 <= MINIMUM_TARGET_P95_NANOS) {
            return new GraphicsBenchmarkResult(
                    GraphicsQualityPreset.LOW,
                    GraphicsBenchmarkResult.TargetStatus.MEETS_MINIMUM_TARGET,
                    statistics,
                    width,
                    height,
                    renderScale);
        }
        return new GraphicsBenchmarkResult(
                GraphicsQualityPreset.LOW,
                GraphicsBenchmarkResult.TargetStatus.BELOW_MINIMUM_TARGET,
                statistics,
                width,
                height,
                renderScale);
    }
}
