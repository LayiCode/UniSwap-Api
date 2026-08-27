package com.olamide.UniSwap.Service;

// Result of generating an email verification code: the raw 6-digit code plus
// whether it was handed to the SMTP relay. When delivery is impossible the
// caller can surface the code in the response so the user can still sign up /
// log in without waiting for an email that may never arrive.
public record CodeDelivery(String code, boolean delivered) {
}
