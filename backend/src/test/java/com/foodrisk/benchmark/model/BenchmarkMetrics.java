package com.foodrisk.benchmark.model;

import java.util.*;

/**
 * Metric calculation utilities for Phase 3 benchmark evaluation.
 * Computes Levenshtein CER, Precision/Recall/F1, and Nutrition tolerances deterministically.
 */
public class BenchmarkMetrics {

    /**
     * Calculates the Levenshtein distance between ground truth and hypothesis string.
     * Edit distance = Substitutions + Deletions + Insertions.
     */
    public static int computeLevenshteinDistance(String groundTruth, String hypothesis) {
        if (groundTruth == null) groundTruth = "";
        if (hypothesis == null) hypothesis = "";

        int n = groundTruth.length();
        int m = hypothesis.length();

        if (n == 0) return m;
        if (m == 0) return n;

        int[][] dp = new int[n + 1][m + 1];

        for (int i = 0; i <= n; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= n; i++) {
            char c1 = groundTruth.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char c2 = hypothesis.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;
                dp[i][j] = Math.min(
                        dp[i - 1][j] + 1, // deletion
                        Math.min(
                                dp[i][j - 1] + 1, // insertion
                                dp[i - 1][j - 1] + cost // substitution
                        )
                );
            }
        }

        return dp[n][m];
    }

    /**
     * Calculates Character Error Rate: CER = (S + D + I) / N
     */
    public static double computeCer(String groundTruth, String hypothesis) {
        if (groundTruth == null || groundTruth.isEmpty()) {
            return (hypothesis == null || hypothesis.isEmpty()) ? 0.0 : 1.0;
        }
        int distance = computeLevenshteinDistance(groundTruth, hypothesis);
        return (double) distance / groundTruth.length();
    }

    /**
     * Standard normalization for matching additive codes.
     * Normalizes "INS 330", "INS330", "E330", "E 330", "330" -> "330".
     */
    public static String canonicalizeAdditiveCode(String code) {
        if (code == null) return "";
        String normalized = code.trim().toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[\\[\\]\\(\\)]", "");
        if (normalized.startsWith("INS")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("E")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    /**
     * Checks if a numeric value matches ground truth within documented tolerance:
     * - Within 5% relative difference, OR
     * - Within 0.5 absolute difference for small values (<= 1.0).
     */
    public static boolean isWithinTolerance(Double expected, Double actual) {
        if (expected == null && actual == null) return true;
        if (expected == null || actual == null) return false;

        double diff = Math.abs(expected - actual);
        if (expected <= 1.0 || actual <= 1.0) {
            return diff <= 0.51; // absolute tolerance for small/trace values
        }
        double relativeError = diff / expected;
        return relativeError <= 0.051; // 5% tolerance
    }

    /**
     * Precision, Recall, and F1 calculations from TP, FP, FN counts.
     */
    public record PRF1(int truePositives, int falsePositives, int falseNegatives) {
        public double precision() {
            int total = truePositives + falsePositives;
            return total == 0 ? 1.0 : (double) truePositives / total;
        }

        public double recall() {
            int total = truePositives + falseNegatives;
            return total == 0 ? 1.0 : (double) truePositives / total;
        }

        public double f1() {
            double p = precision();
            double r = recall();
            if (p + r == 0.0) return 0.0;
            return (2.0 * p * r) / (p + r);
        }
    }
}
