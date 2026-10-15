# Tickets et pièces jointes

Les tickets contiennent le rapporteur (adresse du compte connecté, conservée à la modification), l'assignation, le type (anomalie, tâche, amélioration, support), la sévérité, le composant, les versions affectée/corrective, l'échéance, l'environnement, les étapes de reproduction, les résultats attendu/observé, l'analyse de cause, la solution technique et les labels. Le statut, la catégorie et la priorité sont modifiables depuis la fiche. Une analyse IA tardive préserve les valeurs déjà renseignées.

## Base de données

Au redémarrage du service, la configuration existante `spring.jpa.hibernate.ddl-auto=update` ajoute les colonnes et les tables `ticket_labels` et `ticket_attachment` sans supprimer les tickets existants. Le rapporteur des anciens tickets reste vide : aucune identité n'est déduite rétrospectivement.

Les pièces jointes (métadonnées et contenu binaire `bytea` sous PostgreSQL) sont conservées dans la base des tickets et couvertes par ses sauvegardes. Le contenu n'est pas chargé lors de la consultation des métadonnées. Aucun répertoire de fichiers ou volume supplémentaire n'est requis.

## API

Toutes les routes nécessitent une session. Les mutations nécessitent également le jeton obtenu via `GET /auth/csrf`.

| Méthode | Route | Contenu |
| --- | --- | --- |
| POST | `/ticket` | JSON : titre et description obligatoires, champs de suivi facultatifs |
| POST | `/ticket` | Multipart : partie `ticket` de type `application/json`, parties `files` facultatives |
| PUT | `/ticket/{id}` | JSON : formulaire complet ; les champs de suivi facultatifs absents sont vidés, statut/catégorie/priorité absents sont conservés |
| POST | `/ticket/{id}/attachments` | Multipart : une ou plusieurs parties `files` |
| GET | `/ticket/{id}/attachments/{attachmentId}` | Téléchargement binaire forcé, fichier rattaché au ticket indiqué |
| DELETE | `/ticket/{id}/attachments/{attachmentId}` | Suppression, réponse 204 |

Limites : 10 Mio par fichier, 20 fichiers et 50 Mio de contenu par envoi ; 20 labels de 50 caractères. Les fichiers vides sont refusés. Les limites de longueur des champs sont validées côté serveur. Un lot invalide annule toute l'opération, y compris la création du ticket lorsqu'il est envoyé avec des fichiers.

Les droits restent ceux de l'espace partagé existant : tout utilisateur authentifié peut consulter et modifier les tickets et leurs pièces jointes.

## Vérifications

Avec un JDK 21 : `./mvnw test` (ou `mvnw.cmd test` sous Windows). Les tests utilisent H2 en mode PostgreSQL et désactivent les listeners Kafka ; ils couvrent la persistance, la modification, les fichiers, les téléchargements, les suppressions, les limites et la protection session/CSRF.

Depuis `front` : `npm run build`, puis `npm run test:e2e`. Les tests Playwright vérifient les formulaires, la conservation des saisies en cas d'erreur, les pièces jointes, la recherche et la largeur sur mobile avec une API simulée.
