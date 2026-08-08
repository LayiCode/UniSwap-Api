package com.olamide.UniSwap.Entity;

// Why a listing was reported. Fixed set (stored as VARCHAR) so moderation can
// filter and reason about reports without free-text ambiguity.
public enum ReportReason {
    SPAM,
    INAPPROPRIATE,
    SCAM,
    DUPLICATE,
    OTHER
}
