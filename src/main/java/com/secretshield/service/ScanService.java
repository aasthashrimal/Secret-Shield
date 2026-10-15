package com.secretshield.service;

import java.time.*;
import java.util.Set;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.secretshield.dto.*;
import com.secretshield.entity.*;
import com.secretshield.exception.*;
import com.secretshield.repository.ScanRepository;
import com.secretshield.security.ResourceAuthorization;
import com.secretshield.validation.GitHubRepositoryMetadata;
import jakarta.validation.ConstraintViolationException;

@Service
public class ScanService {
 private final ScanRepository scans; private final ProjectRepositoryService repositories; private final ResourceAuthorization authorization; private final AuditService audit;
 public ScanService(ScanRepository s,ProjectRepositoryService r,ResourceAuthorization a,AuditService audit){scans=s;repositories=r;authorization=a;this.audit=audit;}
 @Transactional public ScanDto createManual(User actor,long repositoryId,ManualScanRequest request){
  var repo=repositories.loadActiveAuthorized(actor,repositoryId);
  var scan=Scan.builder().repository(repo).branch(strictBranch(request.branch())).commitSha(sha(request.commitSha()))
    .trigger(ScanTrigger.MANUAL).status(ScanStatus.QUEUED).build();
  scans.saveAndFlush(scan);
  audit.user(repo,actor,AuditOperation.SCAN_CREATED,AuditTargetType.SCAN,scan.getId(),"Manual scan queued");
  return ScanDto.from(scan);
 }
 @Transactional(readOnly=true) public PageResponse<ScanDto> list(User actor,long repositoryId,int page,int size,String status,String branch,String commit,String from,String to,String sort){
  var repo=repositories.loadAuthorized(actor,repositoryId); validatePage(page,size); ScanStatus state=parseStatus(status);
  String b=blankNull(branch), c=blankNull(commit); if(c!=null)c=sha(c); LocalDateTime f=time(from),t=time(to); if(f!=null&&t!=null&&!f.isBefore(t))throw invalid();
  Specification<Scan> spec=(root,q,cb)->cb.equal(root.get("repository").get("id"),repo.getId());
  if(state!=null)spec=spec.and((r,q,cb)->cb.equal(r.get("status"),state));
  if(b!=null)spec=spec.and((r,q,cb)->cb.equal(r.get("branch"),b));
  if(c!=null){String value=c;spec=spec.and((r,q,cb)->cb.equal(r.get("commitSha"),value));}
  if(f!=null)spec=spec.and((r,q,cb)->cb.greaterThanOrEqualTo(r.get("createdAt"),f));
  if(t!=null)spec=spec.and((r,q,cb)->cb.lessThan(r.get("createdAt"),t));
  return PageResponse.from(scans.findAll(spec,PageRequest.of(page,size,ordering(sort))),ScanDto::from);
 }
 @Transactional(readOnly=true) public ScanDto detail(User actor,long repositoryId,long scanId){return ScanDto.from(load(actor,repositoryId,scanId));}
 @Transactional public Scan markRunning(User actor,long repositoryId,long scanId){var s=load(actor,repositoryId,scanId);transition(s,ScanStatus.RUNNING,null,null);scans.saveAndFlush(s);audit.user(s.getRepository(),actor,AuditOperation.SCAN_STARTED,AuditTargetType.SCAN,s.getId(),"Manual scan started");return s;}
 @Transactional public Scan complete(User actor,long repositoryId,long scanId,ScanStatus target,String scannerVersion){var s=load(actor,repositoryId,scanId);transition(s,target,scannerVersion,null);scans.saveAndFlush(s);audit.system(s.getRepository(),AuditOperation.SCAN_COMPLETED,AuditTargetType.SCAN,s.getId(),"Scan completed with status "+target.name());return s;}
 @Transactional public Scan fail(User actor,long repositoryId,long scanId,String scannerVersion,String failureCode){var s=load(actor,repositoryId,scanId);transition(s,ScanStatus.FAILED,scannerVersion,failure(failureCode));scans.saveAndFlush(s);audit.system(s.getRepository(),AuditOperation.SCAN_FAILED,AuditTargetType.SCAN,s.getId(),"Scan failed");return s;}
 public static void transition(Scan s,ScanStatus target,String version,String failure){
  boolean allowed=s.getStatus()==ScanStatus.QUEUED&&(target==ScanStatus.RUNNING||target==ScanStatus.FAILED)
    ||s.getStatus()==ScanStatus.RUNNING&&(target==ScanStatus.CLEAN||target==ScanStatus.FINDINGS_DETECTED||target==ScanStatus.FAILED);
  if(!allowed)throw new ConflictException("Illegal scan transition"); var now=LocalDateTime.now(ZoneOffset.UTC);
  if(target==ScanStatus.RUNNING)s.setStartedAt(now); else {if(s.getStartedAt()==null)s.setStartedAt(now);s.setCompletedAt(now);}
  s.setStatus(target);s.setScannerVersion(version);s.setFailureCode(target==ScanStatus.FAILED?failure:null);
 }
 private Scan load(User actor,long repoId,long id){var repo=repositories.loadAuthorized(actor,repoId);var scan=scans.findById(id).orElseThrow(()->new ResourceNotFoundException("Scan not found"));authorization.requireNestedResource(actor,repo.getOwner().getId(),repoId,scan.getRepository().getId());return scan;}
 public static String sha(String v){if(v==null||!v.matches("[0-9a-f]{40}"))throw invalid();return v;}
 public static String strictBranch(String v){if(v==null||v.isBlank())throw invalid();return GitHubRepositoryMetadata.defaultBranch(v);}
 public static String failure(String v){if(v==null||!v.matches("[A-Z0-9_]{1,64}"))throw invalid();return v;}
 private static ScanStatus parseStatus(String v){if(v==null||v.isBlank())return null;try{return ScanStatus.valueOf(v);}catch(Exception e){throw invalid();}}
 private static LocalDateTime time(String v){if(v==null||v.isBlank())return null;try{return Instant.parse(v).atOffset(ZoneOffset.UTC).toLocalDateTime();}catch(Exception e){throw invalid();}}
 private static String blankNull(String v){return v==null||v.isBlank()?null:v;}
 private static void validatePage(int p,int s){if(p<0||s<1||s>100)throw invalid();}
 private static Sort ordering(String v){String x=v==null||v.isBlank()?"createdAt,desc":v;String[] p=x.split(",",-1);if(p.length!=2||!(p[0].equals("createdAt")||p[0].equals("id"))||!(p[1].equalsIgnoreCase("asc")||p[1].equalsIgnoreCase("desc")))throw invalid();var d=Sort.Direction.fromString(p[1]);var first=Sort.by(d,p[0]);return p[0].equals("id")?first:first.and(Sort.by(d,"id"));}
 private static ConstraintViolationException invalid(){return new ConstraintViolationException("Invalid scan request",Set.of());}
}
