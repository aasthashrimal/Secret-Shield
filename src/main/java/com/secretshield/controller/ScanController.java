package com.secretshield.controller;
import java.util.Set;
import org.springframework.http.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
import com.secretshield.dto.*;import com.secretshield.entity.User;import com.secretshield.service.*;import jakarta.validation.*;
@RestController @RequestMapping("/api/repositories/{repositoryId}") public class ScanController {
 private final ScanService scans;private final CiCredentialService credentials;
 public ScanController(ScanService s,CiCredentialService c){scans=s;credentials=c;}
 @PostMapping("/scans") @ResponseStatus(HttpStatus.CREATED) public ScanDto create(@AuthenticationPrincipal User a,@PathVariable String repositoryId,@Valid @RequestBody ManualScanRequest r){return scans.createManual(a,id(repositoryId),r);}
 @GetMapping("/scans") public PageResponse<ScanDto> list(@AuthenticationPrincipal User a,@PathVariable String repositoryId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String status,@RequestParam(required=false)String branch,@RequestParam(required=false)String commitSha,@RequestParam(required=false)String from,@RequestParam(required=false)String to,@RequestParam(defaultValue="createdAt,desc")String sort){return scans.list(a,id(repositoryId),page,size,status,branch,commitSha,from,to,sort);}
 @GetMapping("/scans/{scanId}") public ScanDto detail(@AuthenticationPrincipal User a,@PathVariable String repositoryId,@PathVariable String scanId){return scans.detail(a,id(repositoryId),id(scanId));}
 @PostMapping("/ci-credentials") @ResponseStatus(HttpStatus.CREATED) public CiCredentialIssued issue(@AuthenticationPrincipal User a,@PathVariable String repositoryId,@RequestBody(required=false) CiCredentialRequest r){return credentials.issue(a,id(repositoryId),r);}
 @PostMapping("/ci-credentials/{credentialId}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT) public void revoke(@AuthenticationPrincipal User a,@PathVariable String repositoryId,@PathVariable String credentialId){credentials.revoke(a,id(repositoryId),id(credentialId));}
 private static long id(String v){if(v==null||!v.matches("[1-9][0-9]{0,18}"))throw bad();try{return Long.parseLong(v);}catch(Exception e){throw bad();}}
 private static ConstraintViolationException bad(){return new ConstraintViolationException("Invalid resource ID",Set.of());}
}
