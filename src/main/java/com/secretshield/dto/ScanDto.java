package com.secretshield.dto;
import java.time.*;
import com.secretshield.entity.Scan;
public record ScanDto(String id,String repositoryId,String commitSha,String branch,String trigger,String runId,Integer runAttempt,
 String scannerVersion,String status,String failureCode,String startedAt,String completedAt,String createdAt,String updatedAt){
 public static ScanDto from(Scan s){return new ScanDto(s.getId().toString(),s.getRepository().getId().toString(),s.getCommitSha(),s.getBranch(),s.getTrigger().name(),s.getRunId(),s.getRunAttempt(),s.getScannerVersion(),s.getStatus().name(),s.getFailureCode(),utc(s.getStartedAt()),utc(s.getCompletedAt()),utc(s.getCreatedAt()),utc(s.getUpdatedAt()));}
 private static String utc(LocalDateTime v){return v==null?null:v.toInstant(ZoneOffset.UTC).toString();}
}
