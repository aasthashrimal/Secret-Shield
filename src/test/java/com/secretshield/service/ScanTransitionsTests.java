package com.secretshield.service;
import org.junit.jupiter.api.Test;
import com.secretshield.entity.*;import com.secretshield.exception.ConflictException;
import static org.junit.jupiter.api.Assertions.*;
class ScanTransitionsTests {
 @Test void queuedCanRunThenCompleteClean(){var s=Scan.builder().status(ScanStatus.QUEUED).build();ScanService.transition(s,ScanStatus.RUNNING,null,null);assertNotNull(s.getStartedAt());ScanService.transition(s,ScanStatus.CLEAN,"8.0.0",null);assertEquals(ScanStatus.CLEAN,s.getStatus());assertNotNull(s.getCompletedAt());}
 @Test void queuedCanFailButFailureIsNotFindings(){var s=Scan.builder().status(ScanStatus.QUEUED).build();ScanService.transition(s,ScanStatus.FAILED,"8.0.0","TOOL_ERROR");assertEquals("TOOL_ERROR",s.getFailureCode());assertEquals(ScanStatus.FAILED,s.getStatus());}
 @Test void terminalAndIllegalTransitionsAreRejected(){for(var state:new ScanStatus[]{ScanStatus.CLEAN,ScanStatus.FINDINGS_DETECTED,ScanStatus.FAILED}){var s=Scan.builder().status(state).build();assertThrows(ConflictException.class,()->ScanService.transition(s,ScanStatus.RUNNING,null,null));}var queued=Scan.builder().status(ScanStatus.QUEUED).build();assertThrows(ConflictException.class,()->ScanService.transition(queued,ScanStatus.CLEAN,null,null));}
}
