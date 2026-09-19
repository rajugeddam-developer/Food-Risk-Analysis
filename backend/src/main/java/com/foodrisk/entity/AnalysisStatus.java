package com.foodrisk.entity;

/**
 * Represents the lifecycle status of a transient food analysis session.
 *
 * Sessions transition through these states during temporary processing
 * and expire after their configured TTL.
 */
public enum AnalysisStatus {
    CREATED,
    PROCESSING,
    COMPLETED,
    FAILED,
    EXPIRED
}
