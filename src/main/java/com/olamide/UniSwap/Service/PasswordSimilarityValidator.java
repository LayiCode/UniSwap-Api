package com.olamide.UniSwap.Service;

// Blocks passwords that are the same as, contain, or are too similar to the
// username. Uses containment plus a cheap Damerau-style distance so obvious
// tweaks ("john2024" for username "john") are rejected.
public final class PasswordSimilarityValidator {

    private static final double MAX_SIMILARITY = 0.6;

    private PasswordSimilarityValidator() {
    }

    public static boolean isRejected(String username, String password) {
        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            return false;
        }

        String u = normalize(username);
        String p = normalize(password);
        if (u.isEmpty() || p.isEmpty()) {
            return false;
        }

        if (u.equals(p)) {
            return true;
        }
        if (p.contains(u) || u.contains(p)) {
            return true;
        }

        // Usernames are typically short; distance 1 on the username means the
        // password is just the username with a single character changed.
        int distance = levenshtein(u, p.substring(0, Math.min(p.length(), u.length() + 4)));
        if (distance <= 1) {
            return true;
        }

        // Fuzzy similarity for longer usernames.
        int maxLen = Math.max(u.length(), p.length());
        if (maxLen > 0 && (1.0 - (double) levenshtein(u, p) / maxLen) >= MAX_SIMILARITY) {
            return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
