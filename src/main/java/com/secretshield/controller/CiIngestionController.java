package com.secretshield.controller;
import java.util.Set;
import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
import com.secretshield.dto.*;import com.secretshield.service.*;import jakarta.validation.*;
@RestController @RequestMapping("/api/ci/repositories") public class CiIngestionController {
 private final CiCredentialService credentials;private final CiIngestionService ingestion;
 public CiIngestionController(CiCredentialService c,CiIngestionService i){credentials=c;ingestion=i;}
 @PostMapping("/{repositoryId}/scan-results") public ResponseEntity<ScanDto> ingest(@PathVariable String repositoryId,@RequestHeader(name="X-Secret-Shield-Credential",required=false)String credential,@Valid @RequestBody CiScanResultRequest body){long id=id(repositoryId);var machine=credentials.authenticate(id,credential);var result=ingestion.ingest(id,body,machine);return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(result.scan());}
 private static long id(String v){if(v==null||!v.matches("[1-9][0-9]{0,18}"))throw bad();try{return Long.parseLong(v);}catch(Exception e){throw bad();}}
 private static ConstraintViolationException bad(){return new ConstraintViolationException("Invalid resource ID",Set.of());}
}
