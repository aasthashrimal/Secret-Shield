package com.secretshield.exception;

public class DuplicateUserException extends ConflictException {
    public DuplicateUserException() { super("User already exists"); }
}
