package com.rams.web;

public final class Text {

    private Text() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String required(String value, int max, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw ApiException.badRequest(label + " is required");
        }
        if (trimmed.length() > max) {
            throw ApiException.badRequest(label + " must be at most " + max + " characters");
        }
        return trimmed;
    }

    public static String optional(String value, int max, String label) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > max) {
            throw ApiException.badRequest(label + " must be at most " + max + " characters");
        }
        return trimmed;
    }
}
