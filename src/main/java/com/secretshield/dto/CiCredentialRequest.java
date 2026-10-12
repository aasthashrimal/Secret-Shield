package com.secretshield.dto;
import com.fasterxml.jackson.annotation.JsonAnySetter;
public record CiCredentialRequest(String expiresAt){@JsonAnySetter public void reject(String n,Object v){throw new IllegalArgumentException("Unknown credential field");}}
