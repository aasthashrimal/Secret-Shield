package com.secretshield.scanner;
public class ScannerFailureException extends RuntimeException {
 private final String code;
 public ScannerFailureException(String code){super("Controlled scanner failure");this.code=code;}
 public String code(){return code;}
}
