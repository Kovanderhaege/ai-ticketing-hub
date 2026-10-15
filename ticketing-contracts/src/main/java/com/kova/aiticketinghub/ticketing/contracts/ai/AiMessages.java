package com.kova.aiticketinghub.ticketing.contracts.ai;

public final class AiMessages {
    private AiMessages() {}
    public static String forCode(String code) {
        if (code == null) return "Le test n’a pas abouti.";
        return switch (code) {
            case "CONFIGURATION_ERROR" -> "Vérifiez le fournisseur, le modèle, l’adresse et la clé API si nécessaire.";
            case "AUTHENTICATION_ERROR" -> "Le fournisseur a refusé les identifiants. Vérifiez la clé API.";
            case "PROVIDER_ERROR" -> "Le fournisseur a refusé la demande. Vérifiez le modèle et ses paramètres.";
            case "PROVIDER_UNAVAILABLE" -> "Le serveur du modèle est inaccessible ou le délai de réponse est dépassé.";
            case "INVALID_RESPONSE" -> "La réponse du modèle ne respecte pas le format attendu.";
            case "REFUSED" -> "Le modèle a refusé de traiter l’exemple.";
            default -> "Le test n’a pas abouti.";
        };
    }
}
