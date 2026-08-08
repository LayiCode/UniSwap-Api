package com.olamide.UniSwap.Entity;

// Lifecycle of a "request to buy" placed on a listing. Stored as a VARCHAR
// (EnumType.STRING) so DB rows and API JSON keep the readable uppercase names.
// A request starts PENDING and is decided exactly once by one of the two
// parties: the seller ACCEPTs (which sells the product) or DECLINEs, or the
// buyer CANCELs before the seller has decided.
public enum PurchaseRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED
}
