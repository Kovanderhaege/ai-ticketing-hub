package com.kova.aiticketinghub.analysis.service;

import com.kova.aiticketinghub.analysis.DTO.TicketAnalysisResult;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketCreatedEvent;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketCategory;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketPriority;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class TicketAnalysisService {

    public TicketAnalysisResult analyze(TicketCreatedEvent event) {
        String title = safe(event.title());
        String description = safe(event.description());
        String text = (title + " " + description).toLowerCase(Locale.ROOT);

        TicketCategory category = determineCategory(text);
        TicketPriority priority = determinePriority(text);
        String summary = buildSummary(title, description);

        return new TicketAnalysisResult(category, priority, summary);
    }

    private TicketCategory determineCategory(String text) {
        if (containsAny(text, "erreur", "bug", "500", "exception", "timeout", "technique")) {
            return TicketCategory.TECHNICAL;
        }
        if (containsAny(text, "paiement", "facture", "remboursement", "carte", "billing")) {
            return TicketCategory.BILLING;
        }
        if (containsAny(text, "login", "connexion", "mot de passe", "compte", "account")) {
            return TicketCategory.ACCOUNT;
        }
        return TicketCategory.OTHER;
    }

    private TicketPriority determinePriority(String text) {
        if (containsAny(text, "urgent", "bloqué", "bloque", "impossible", "500", "critique")) {
            return TicketPriority.HIGH;
        }
        if (containsAny(text, "lent", "problème", "probleme", "erreur", "bug")) {
            return TicketPriority.MEDIUM;
        }
        return TicketPriority.LOW;
    }

    private String buildSummary(String title, String description) {
        String combined = (safe(title) + " - " + safe(description)).trim();
        if (combined.length() <= 140) {
            return combined;
        }
        return combined.substring(0, 137) + "...";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}