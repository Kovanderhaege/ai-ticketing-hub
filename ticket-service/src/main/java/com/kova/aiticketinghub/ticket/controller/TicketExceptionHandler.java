package com.kova.aiticketinghub.ticket.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(basePackageClasses = TicketController.class)
public class TicketExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Vérifiez les champs obligatoires et les longueurs maximales (20 labels de 50 caractères)."));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge() {
        return ResponseEntity.status(413).body(Map.of("message", "Maximum 10 Mo par fichier et 50 Mo par envoi."));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason() == null ? "La demande a échoué." : exception.getReason()));
    }
}
