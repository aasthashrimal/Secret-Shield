package com.secretshield.scanner;
import java.util.List;
import com.secretshield.dto.SanitizedFindingInput;
import com.secretshield.entity.ProjectRepository;
public interface LocalScanner {
 record Result(String version,List<SanitizedFindingInput> findings){}
 Result scan(ProjectRepository repository,String commitSha,String branch);
}
