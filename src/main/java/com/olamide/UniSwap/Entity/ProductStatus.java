package com.olamide.UniSwap.Entity;

// The lifecycle states a listing can be in. Stored as a VARCHAR in the DB
// (EnumType.STRING) so existing rows and API JSON keep the "AVAILABLE"/"SOLD"
// string shape. Using an enum instead of a free-form String makes it
// impossible to store a typo like "Availble" and gives us a compile-time-safe
// set of transitions to hang logic off of later.
public enum ProductStatus {
    AVAILABLE,
    SOLD
}
