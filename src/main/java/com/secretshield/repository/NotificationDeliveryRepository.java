package com.secretshield.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import com.secretshield.entity.NotificationDelivery;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long>,
        JpaSpecificationExecutor<NotificationDelivery> {
    boolean existsByAuditEventIdAndRecipientConfigReference(Long auditEventId, String recipientConfigReference);

    @Query(value = """
            SELECT id FROM notification_deliveries
            WHERE attempt_count < 3 AND next_attempt_at <= :now
              AND (status IN ('QUEUED','FAILED') OR
                   (status = 'SENDING' AND lease_until <= :now))
            ORDER BY next_attempt_at, id
            FOR UPDATE SKIP LOCKED LIMIT 1
            """, nativeQuery = true)
    Optional<Long> lockNextDueId(@Param("now") LocalDateTime now);

    @Modifying
    @Query(value = """
            UPDATE notification_deliveries
            SET status='FAILED', safe_error_code='DELIVERY_TIMEOUT', lease_until=NULL,
                updated_at=:now
            WHERE status='SENDING' AND attempt_count=3 AND lease_until <= :now
            """, nativeQuery = true)
    int expireExhausted(@Param("now") LocalDateTime now);
}
