package com.secretshield.service;
import java.util.List;
import com.secretshield.dto.SanitizedFindingInput;
import com.secretshield.entity.Scan;
/** M07 implements persistence; M05 calls registered sinks in the ingestion transaction. */
public interface FindingResultSink { void persist(Scan scan,List<SanitizedFindingInput> findings); }
