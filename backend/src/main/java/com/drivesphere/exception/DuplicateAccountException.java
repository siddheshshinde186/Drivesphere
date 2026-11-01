package com.drivesphere.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an account registration conflicts with an existing email, phone number,
 * or combination of accounts in the system.
 */
public class DuplicateAccountException extends ApiException {

    private final String code;

    public DuplicateAccountException(String code, String message) {
        super(HttpStatus.CONFLICT, message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
