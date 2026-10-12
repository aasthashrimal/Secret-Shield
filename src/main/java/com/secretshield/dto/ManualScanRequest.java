package com.secretshield.dto;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
public record ManualScanRequest(@NotBlank String branch,@NotBlank String commitSha){
 @JsonAnySetter public void reject(String n,Object v){throw new IllegalArgumentException("Unknown scan field");}
}
