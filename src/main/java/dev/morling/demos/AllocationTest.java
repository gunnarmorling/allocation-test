package dev.morling.demos;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.HdrHistogram.Histogram;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

public class AllocationTest {

    private static Optional<String> JFR_RECORDING = Optional.ofNullable(System.getenv("JFR_RECORDING"));
    private static final int WARMUP_SAMPLES = 15_000;
    private static final Duration DURATION = Duration.ofSeconds(Integer.valueOf(Optional.ofNullable(System.getenv("DURATION")).orElse("30")));
    private static final int THREADS = Integer.valueOf(Optional.ofNullable(System.getenv("THREADS")).orElse("4"));
    private static final int OUTER_SIZE = Integer.valueOf(Optional.ofNullable(System.getenv("RANDOM_COUNT")).orElse("50"));
    private static final int INNER_SIZE = 1000;

    private ConcurrentHashMap<Integer, Long> results = new ConcurrentHashMap<Integer, Long>();

    private static final Histogram HDR_HISTOGRAM = new Histogram(TimeUnit.MINUTES.toNanos(1), 3);

    public static void main(String[] args) throws Exception {
        new AllocationTest().run();
    }

    private void run() throws Exception {
        warmup();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        Instant benchmarkStart = Instant.now();

        Recording recording = null;
        if (JFR_RECORDING.isPresent()) {
            recording = new Recording(Configuration.getConfiguration("default"));
            recording.start();
        }

        for (int t = 0; t < THREADS; t++) {
            final int worker = t;
            executor.submit(() -> {

                Instant lastLog = Instant.now();

                while (true) {
                    if (Duration.between(benchmarkStart, Instant.now()).compareTo(DURATION) > 0) {
                        return;
                    }

                    long start = System.nanoTime();
                    long result = benchmark();

                    long duration = System.nanoTime() - start;
                    HDR_HISTOGRAM.recordValue(duration);
                    results.put(worker, result);

                    if (Duration.between(lastLog, Instant.now()).compareTo(Duration.ofSeconds(1)) > 0) {
                        System.err.println(worker + " " + Duration.between(benchmarkStart, Instant.now()));
                        System.err.println("Sample: " + duration);
                        lastLog = Instant.now();
                    }
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        if (JFR_RECORDING.isPresent()) {
            recording.stop();
            recording.dump(Paths.get(JFR_RECORDING.get()));
        }

        System.err.println(results);

        HDR_HISTOGRAM.outputPercentileDistribution(new PrintStream(System.out), 1000_000.0);
        HDR_HISTOGRAM.reset();

    }

    private void warmup() {
        int warmup_samples = WARMUP_SAMPLES;

        Instant start = Instant.now();
        System.err.println("Warmup (" + warmup_samples + ") ### Start: " + start);
        Instant lastLog = Instant.now();
        long[] res = new long[warmup_samples];
        for (int i = 0; i < warmup_samples; i++) {
            res[i] = benchmark();

            if (Duration.between(lastLog, Instant.now()).compareTo(Duration.ofSeconds(1)) > 0) {
                System.err.println(i);
                lastLog = Instant.now();
            }
        }
        System.err.println(res[ThreadLocalRandom.current().nextInt(res.length)]);
        System.err.println("Warmup ### End: " + Instant.now());
    }

    private long benchmark() {
        List<List<Long>> randoms = new ArrayList<>(OUTER_SIZE);
        for (int i = 0; i < OUTER_SIZE; i++) {
            List<Long> l = new ArrayList<>(INNER_SIZE);
            for (int j = 0; j < INNER_SIZE; j++) {
                l.add(ThreadLocalRandom.current().nextLong());
            }
            randoms.add(l);
        }
        return randoms.get(ThreadLocalRandom.current().nextInt(OUTER_SIZE)).get(ThreadLocalRandom.current().nextInt(INNER_SIZE));
    }
}
