package com.kova.aiticketinghub.ticket.ai;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {AiAdminController.class, AiInternalController.class})
public class AiApiErrors {
    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Map<String,String>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message",
                "Vérifiez le fournisseur, le modèle, l’adresse du serveur et les paramètres. Un changement de fournisseur ou d’adresse nécessite de ressaisir la clé si elle est requise."));
    }
    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Map<String,String>> conflict(Exception exception) {
        return ResponseEntity.status(409).body(Map.of("message", "La configuration a été modifiée. Rechargez-la avant d’enregistrer."));
    }
    @ExceptionHandler(AiSecretVault.AiStorageException.class)
    ResponseEntity<Map<String,String>> storage() {
        return ResponseEntity.status(503).body(Map.of("message", "Le serveur ne peut pas accéder aux identifiants protégés de l’IA."));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message",
                exception.getReason() == null ? "Le service de configuration est indisponible." : exception.getReason()));
    }
}
