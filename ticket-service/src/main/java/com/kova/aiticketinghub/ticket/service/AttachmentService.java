package com.kova.aiticketinghub.ticket.service;

import com.kova.aiticketinghub.ticket.DTO.AttachmentResponse;
import com.kova.aiticketinghub.ticket.entity.*;
import com.kova.aiticketinghub.ticket.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AttachmentService {
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    public static final long MAX_BATCH_SIZE = 50 * 1024 * 1024;
    private final AttachmentRepository repository;
    private final TicketRepository tickets;

    public AttachmentService(AttachmentRepository repository, TicketRepository tickets) {
        this.repository = repository;
        this.tickets = tickets;
    }

    public List<AttachmentResponse> list(UUID ticketId) {
        return repository.listByTicketId(ticketId);
    }

    @Transactional
    public List<AttachmentResponse> upload(UUID ticketId, List<MultipartFile> files, String user) {
        Ticket ticket = findTicket(ticketId);
        store(ticket, files, user);
        ticket.setUpdatedAt(Instant.now());
        return list(ticketId);
    }

    @Transactional
    public void store(Ticket ticket, List<MultipartFile> files, String user) {
        if (files == null || files.isEmpty()) return;
        if (files.size() > 20 || files.stream().mapToLong(MultipartFile::getSize).sum() > MAX_BATCH_SIZE)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Maximum 20 fichiers et 50 Mo par envoi.");
        for (MultipartFile file : files) {
            if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les fichiers vides ne sont pas acceptés.");
            if (file.getSize() > MAX_FILE_SIZE)
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Chaque fichier doit faire au maximum 10 Mo.");
            String filename = file.getOriginalFilename();
            filename = filename == null ? "fichier" : filename.replace('\\', '/');
            filename = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
            if (filename.isBlank() || filename.length() > 255)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nom de fichier invalide (255 caractères maximum).");
            TicketAttachment attachment = new TicketAttachment();
            attachment.setTicket(ticket);
            attachment.setFilename(filename);
            String contentType = file.getContentType();
            attachment.setContentType(contentType != null && contentType.length() <= 255 ? contentType : "application/octet-stream");
            attachment.setSize(file.getSize());
            attachment.setCreatedAt(Instant.now());
            attachment.setUploadedBy(user);
            try { attachment.setData(file.getBytes()); }
            catch (IOException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de lire le fichier.", exception); }
            repository.save(attachment);
        }
    }

    public TicketAttachment download(UUID ticketId, UUID attachmentId) {
        return repository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pièce jointe introuvable."));
    }

    @Transactional
    public void delete(UUID ticketId, UUID attachmentId) {
        TicketAttachment attachment = download(ticketId, attachmentId);
        attachment.getTicket().setUpdatedAt(Instant.now());
        repository.delete(attachment);
    }

    private Ticket findTicket(UUID id) {
        return tickets.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket introuvable."));
    }
}
