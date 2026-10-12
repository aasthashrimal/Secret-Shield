package com.secretshield.service;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.secretshield.dto.*;
import com.secretshield.entity.*;
import com.secretshield.exception.AuthenticationException;
import com.secretshield.exception.ResourceNotFoundException;
import com.secretshield.repository.CiCredentialRepository;
import com.secretshield.security.ResourceAuthorization;
import jakarta.validation.ConstraintViolationException;

@Service public class CiCredentialService {
 private final CiCredentialRepository credentials;private final ProjectRepositoryService repositories;private final ResourceAuthorization authorization;private final PasswordEncoder encoder;private final SecureRandom random=new SecureRandom();
 public CiCredentialService(CiCredentialRepository c,ProjectRepositoryService r,ResourceAuthorization a,PasswordEncoder e){credentials=c;repositories=r;authorization=a;encoder=e;}
 @Transactional public CiCredentialIssued issue(User actor,long repoId,CiCredentialRequest request){var repo=repositories.loadActiveAuthorized(actor,repoId);LocalDateTime expires=parseExpiry(request==null?null:request.expiresAt());byte[] bytes=new byte[32];random.nextBytes(bytes);String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);var row=credentials.saveAndFlush(CiCredential.builder().repository(repo).secretHash(encoder.encode(secret)).expiresAt(expires).build());String value="ssci_v1."+row.getId()+"."+secret;return new CiCredentialIssued(row.getId().toString(),repoId+"",value,utc(row.getCreatedAt()),utc(expires));}
 @Transactional public void revoke(User actor,long repoId,long id){var repo=repositories.loadAuthorized(actor,repoId);var row=credentials.findById(id).orElseThrow(()->new ResourceNotFoundException("Credential not found"));authorization.requireNestedResource(actor,repo.getOwner().getId(),repoId,row.getRepository().getId());if(row.getRevokedAt()==null){row.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));credentials.saveAndFlush(row);}}
 @Transactional(readOnly=true) public CiCredential authenticate(long repoId,String header){
  if(header==null||!header.matches("ssci_v1\\.[1-9][0-9]{0,18}\\.[A-Za-z0-9_-]{43}"))throw unauthorized();String[] p=header.split("\\.");long id;try{id=Long.parseLong(p[1]);}catch(Exception e){throw unauthorized();}
  var row=credentials.findById(id).orElseThrow(CiCredentialService::unauthorized);var now=LocalDateTime.now(ZoneOffset.UTC);if(row.getRepository().getId()!=repoId||row.getRevokedAt()!=null||(row.getExpiresAt()!=null&&!row.getExpiresAt().isAfter(now))||!encoder.matches(p[2],row.getSecretHash()))throw unauthorized();return row;
 }
 private static LocalDateTime parseExpiry(String v){if(v==null||v.isBlank())return null;try{var t=Instant.parse(v).atOffset(ZoneOffset.UTC).toLocalDateTime();if(!t.isAfter(LocalDateTime.now(ZoneOffset.UTC)))throw new Exception();return t;}catch(Exception e){throw new ConstraintViolationException("Invalid expiry",java.util.Set.of());}}
 private static String utc(LocalDateTime v){return v==null?null:v.toInstant(ZoneOffset.UTC).toString();}
 private static AuthenticationException unauthorized(){return new AuthenticationException("Invalid machine credential");}
}
