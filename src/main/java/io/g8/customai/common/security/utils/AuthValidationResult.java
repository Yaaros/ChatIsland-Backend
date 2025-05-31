package io.g8.customai.common.security.utils;

public record AuthValidationResult(boolean success, String uid, String errorMessage) {

    public static AuthValidationResult success(String uid) {
        return new AuthValidationResult(true, uid, null);
    }

    public static AuthValidationResult error(String errorMessage) {
        return new AuthValidationResult(false, null, errorMessage);
    }

}