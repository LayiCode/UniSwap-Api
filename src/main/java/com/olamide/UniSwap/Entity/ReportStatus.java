package com.olamide.UniSwap.Entity;

// Lifecycle of a report. Starts OPEN, is decided exactly once by a moderator:
// RESOLVED (listing kept, or removed if the moderator asked for it) or
// DISMISSED (no action needed).
public enum ReportStatus {
    OPEN,
    RESOLVED,
    DISMISSED
}
