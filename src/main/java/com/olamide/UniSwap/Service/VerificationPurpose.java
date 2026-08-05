package com.olamide.UniSwap.Service;

// What a given email verification code was minted for. Codes are one-time and
// scoped so a signup code can't be replayed as a login or reset code.
public enum VerificationPurpose {
    SIGNUP,
    LOGIN,
    RESET
}
