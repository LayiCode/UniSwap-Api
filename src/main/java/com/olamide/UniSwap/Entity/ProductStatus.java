package com.olamide.UniSwap.Entity;

// The lifecycle states a listing can be in. Stored as a VARCHAR in the DB
// (EnumType.STRING) so existing rows and API JSON keep the "AVAILABLE"/"SOLD"
// string shape. Using an enum instead of a free-form String makes it
// impossible to store a typo like "Availble" and gives us a compile-time-safe
// set of transitions to hang logic off of later.
public enum ProductStatus {
    AVAILABLE,
    SOLD,
    // Set by moderation when a listing is found to violate the rules. Browse
    // only ever returns AVAILABLE, so a removed listing vanishes from the
    // marketplace; the seller still sees it in their inventory.
    REMOVED
}
