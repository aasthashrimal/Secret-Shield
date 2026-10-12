package com.secretshield.dto;
import jakarta.validation.constraints.*;
public record SanitizedFindingInput(@NotBlank @Size(max=200) String ruleId,@NotBlank @Size(max=1000) String filePath,
 @Min(1) int line,@NotBlank @Size(max=1000) String sanitizedDescription){}
