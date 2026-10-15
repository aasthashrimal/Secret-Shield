package com.secretshield.dto;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
public record SanitizedFindingInput(@NotBlank @Size(max=200) String ruleId,@NotBlank @Size(max=1000) String filePath,
 @Min(1) int line,@NotBlank @Size(max=1000) String sanitizedDescription){
 @JsonAnySetter public void reject(String name,Object value){throw new IllegalArgumentException("Unknown finding field");}
}
