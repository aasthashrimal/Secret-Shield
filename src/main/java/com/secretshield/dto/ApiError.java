package com.secretshield.dto;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** Public error contract; messages never include exception or request values. */
public record ApiError(boolean success, int status, String code, String message) {
    public static ApiError of(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> new ApiError(false, 400, "BAD_REQUEST", "Invalid request");
            case 401 -> new ApiError(false, 401, "UNAUTHORIZED", "Authentication required or credentials invalid");
            case 403 -> new ApiError(false, 403, "FORBIDDEN", "Access denied");
            case 404 -> new ApiError(false, 404, "NOT_FOUND", "Resource not found");
            case 409 -> new ApiError(false, 409, "CONFLICT", "Resource already exists or conflicts with current state");
            case 413 -> new ApiError(false, 413, "PAYLOAD_TOO_LARGE", "Request payload too large");
            default -> {
                if (status.is5xxServerError()) {
                    yield new ApiError(false, status.value(), "INTERNAL_SERVER_ERROR", "An unexpected error occurred");
                }
                HttpStatus known = HttpStatus.resolve(status.value());
                yield new ApiError(false, status.value(), known == null ? "REQUEST_ERROR" : known.name(),
                        known == null ? "Request could not be processed" : known.getReasonPhrase());
            }
        };
    }
}
