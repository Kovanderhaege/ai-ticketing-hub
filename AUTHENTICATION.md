# Authentification

Les comptes sont persistés dans `ticketdb.app_users` avec une adresse e-mail unique, normalisée en minuscules, et un mot de passe haché avec BCrypt (coût 12). L'inscription est ouverte. Le mot de passe doit comporter au moins 12 caractères et au plus 72 octets UTF-8.

Les utilisateurs connectés partagent les tickets et notifications de l'espace support. Il n'y a pas de cloisonnement par propriétaire. Le rôle `ADMIN` réserve la configuration du modèle aux administrateurs ; une inscription publique crée toujours un compte `USER`. Le premier administrateur est initialisé au déploiement avec `APP_ADMIN_EMAIL` et `APP_ADMIN_PASSWORD` ; voir [le guide d’administration de l’IA](AI_ADMIN.md).

## API du ticket-service

| Route | Format | Résultat |
| --- | --- | --- |
| `GET /auth/csrf` | Public | `{headerName, token}` ; initialise la session et fournit le jeton CSRF |
| `POST /auth/register` | JSON `{email, password}` + en-tête CSRF | 201 `{id, email, role}`, 400 si invalide, 409 si l'adresse existe |
| `POST /auth/login` | Formulaire URL-encodé `email`, `password` + en-tête CSRF | 204 et session authentifiée ; 401 si identifiants invalides |
| `GET /auth/me` | Session | 200 `{id, email, role}` ou 401 |
| `POST /auth/logout` | Session + en-tête CSRF | 204, invalidation serveur et suppression du cookie |
| `GET /ticket`, `GET /ticket/{id}` | Session | Tickets accessibles uniquement après connexion |
| `POST /ticket` | Session + en-tête CSRF | Création accessible uniquement après connexion |
| `GET /admin/ai/configuration` | Session administrateur | Paramètres publics, sans clé fournisseur |
| `PUT /admin/ai/configuration`, `POST /admin/ai/*` | Session administrateur + CSRF | Enregistrement ou diagnostics ; 403 pour un utilisateur ordinaire |

L'inscription ne connecte pas automatiquement. Récupérer un nouveau jeton CSRF après une connexion ou déconnexion : Spring Security renouvelle le jeton et protège contre la fixation de session. Les écritures sans CSRF valide sont refusées (401 sans session authentifiée, 403 avec session). Les erreurs d'authentification ne redirigent jamais vers une page HTML.

Le cookie `TICKETING_SESSION` est HttpOnly, SameSite=Lax, limité au chemin `/`. La session expire après 30 minutes d'inactivité. Aucun mot de passe ou jeton de session ne doit être conservé dans localStorage. Les réponses protégées utilisent les en-têtes de non-mise en cache de Spring Security.

## Notifications et déploiement

Le service notifications vérifie chaque cookie auprès de `GET /auth/me` du ticket-service. Il renvoie 401 sans session valide et 503 si la vérification est indisponible, sans retourner de données. Une déconnexion révoque ainsi immédiatement l'accès aux deux services. `TICKET_SERVICE_URL` configure cette liaison serveur (défaut : `http://localhost:8083` ; utiliser un réseau de confiance ou HTTPS).

En production, servir le front et les API sous la même origine HTTPS et définir `SESSION_COOKIE_SECURE=true` sur le ticket-service. Le reverse proxy doit transmettre les cookies et les en-têtes CSRF, conserver `Set-Cookie` et mapper `/api/auth/*` vers `/auth/*`, `/api/tickets/*` vers `/ticket/*`, `/api/admin/*` vers `/admin/*` du ticket-service, et `/api/notifications/*` vers `/notification/*`. `FRONTEND_ORIGIN` configure l'unique origine CORS autorisée (défaut : `http://localhost:5173`). Les domaines tiers ne sont pas pris en charge par les cookies SameSite=Lax.

Les sessions sont en mémoire dans le ticket-service : un redémarrage impose une reconnexion. Pour plusieurs instances, prévoir des sessions partagées ou l'affinité de session. Les comptes restent persistés en PostgreSQL. La création de table suit le mécanisme Hibernate `ddl-auto=update` déjà présent dans le projet.

## Vérification

Installer les contrats partagés, puis lancer les tests avec Java 21 :

```sh
./ticket-service/mvnw -f ticketing-contracts/pom.xml install
./ticket-service/mvnw -f ticket-service/pom.xml test
./notification-service/mvnw -f notification-service/pom.xml test
```

Les tests d'authentification utilisent H2 et désactivent les consommateurs Kafka. Ils couvrent les accès anonymes, les comptes invalides/doublons, le hachage, la connexion, CSRF, CORS, la fixation de session, la déconnexion et les refus d'accès aux notifications.
