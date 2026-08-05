package com.olamide.UniSwap.Dto;

import lombok.Builder;
import lombok.Getter;

// Public bootstrap config the frontend reads on the login/register pages so it
// knows which auth options to render (e.g. hide the Google button until the
// backend has a real Google client configured).
@Getter
@Builder
public class AuthConfigResponse {

    private final boolean googleEnabled;

    // Full URL on the backend origin that starts the Google OAuth dance
    // (browser leaves the frontend and comes back via /auth/callback).
    private final String googleAuthorizationUrl;
}
