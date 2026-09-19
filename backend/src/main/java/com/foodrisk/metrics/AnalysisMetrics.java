package com.foodrisk.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Milestone M12: Prometheus Metrics & Observability.
 *
 * Exposes core operational metrics for monitoring throughput, pipeline latencies,
 * duplicate product cache hit ratios, and failure rates under load.
 */
@Component
public class AnalysisMetrics {

    private final Timer analysisDurationTimer;
    private final Timer ocrDurationTimer;
    private final Timer geminiDurationTimer;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter failures;

    public AnalysisMetrics(MeterRegistry registry) {
        this.analysisDurationTimer = Timer.builder("foodrisk.analysis.duration")
                .description("Total duration of food analysis pipeline")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.ocrDurationTimer = Timer.builder("foodrisk.ocr.duration")
                .description("Duration of OCR extraction")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.geminiDurationTimer = Timer.builder("foodrisk.gemini.duration")
                .description("Duration of Gemini AI normalization")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.cacheHits = Counter.builder("foodrisk.cache.hits")
                .description("Number of duplicate product cache hits")
                .register(registry);

        this.cacheMisses = Counter.builder("foodrisk.cache.misses")
                .description("Number of duplicate product cache misses")
                .register(registry);

        this.failures = Counter.builder("foodrisk.analysis.failures")
                .description("Number of failed analysis sessions")
                .register(registry);
    }

    public void recordAnalysisDuration(Duration duration) {
        analysisDurationTimer.record(duration);
    }

    public void recordOcrDuration(Duration duration) {
        ocrDurationTimer.record(duration);
    }

    public void recordGeminiDuration(Duration duration) {
        geminiDurationTimer.record(duration);
    }

    public void recordCacheHit() {
        cacheHits.increment();
    }

    public void recordCacheMiss() {
        cacheMisses.increment();
    }

    public void recordFailure() {
        failures.increment();
    }
}
