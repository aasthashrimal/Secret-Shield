package com.secretshield.dto;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record CiScanResultRequest(@NotBlank String runId,@NotNull @Min(1) Integer runAttempt,@NotBlank String commitSha,
 @NotBlank String branch,@NotBlank String trigger,@NotBlank String scannerVersion,@NotBlank String status,
 String failureCode,@NotNull @Size(max=1000) List<@NotNull @Valid SanitizedFindingInput> findings){
 @JsonAnySetter public void reject(String n,Object v){throw new IllegalArgumentException("Unknown result field");}
}
