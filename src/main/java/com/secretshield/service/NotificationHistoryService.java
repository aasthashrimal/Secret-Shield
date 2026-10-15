package com.secretshield.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secretshield.dto.NotificationDto;
import com.secretshield.dto.PageResponse;
import com.secretshield.entity.NotificationDelivery;
import com.secretshield.entity.NotificationStatus;
import com.secretshield.entity.User;
import com.secretshield.exception.ResourceNotFoundException;
import com.secretshield.repository.IncidentRepository;
import com.secretshield.repository.NotificationDeliveryRepository;
import com.secretshield.security.ResourceAuthorization;

@Service
public class NotificationHistoryService {
    private final ProjectRepositoryService repositories;
    private final IncidentRepository incidents;
    private final NotificationDeliveryRepository deliveries;
    private final ResourceAuthorization authorization;

    public NotificationHistoryService(ProjectRepositoryService repositories,
            IncidentRepository incidents, NotificationDeliveryRepository deliveries,
            ResourceAuthorization authorization) {
        this.repositories = repositories;
        this.incidents = incidents;
        this.deliveries = deliveries;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(User actor, long repositoryId, long incidentId,
            int page, int size, String status, String sort) {
        var repository = repositories.loadAuthorized(actor, repositoryId);
        var incident = incidents.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
        authorization.requireNestedResource(actor, repository.getOwner().getId(), repositoryId,
                incident.getRepository().getId());
        QuerySupport.page(page, size);
        NotificationStatus state = QuerySupport.enumeration(status, NotificationStatus.class);
        Specification<NotificationDelivery> spec = (root, query, cb) ->
                cb.equal(root.get("incident").get("id"), incidentId);
        if (state != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), state));
        return PageResponse.from(deliveries.findAll(spec,
                PageRequest.of(page, size, QuerySupport.sort(sort, "createdAt"))),
                NotificationDto::from);
    }
}
