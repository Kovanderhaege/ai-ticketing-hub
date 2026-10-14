package com.kova.aiticketinghub.ticket.repository;

import com.kova.aiticketinghub.ticket.DTO.AttachmentResponse;
import com.kova.aiticketinghub.ticket.entity.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<TicketAttachment, UUID> {
    @Query("select new com.kova.aiticketinghub.ticket.DTO.AttachmentResponse(a.id, a.filename, a.contentType, a.size, a.createdAt, a.uploadedBy) from TicketAttachment a where a.ticket.id = :ticketId order by a.createdAt, a.id")
    List<AttachmentResponse> listByTicketId(UUID ticketId);
    Optional<TicketAttachment> findByIdAndTicketId(UUID id, UUID ticketId);
}
