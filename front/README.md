# Kova · Ticketing Hub

Interface de suivi des tickets en Vue 3, TypeScript et Vite. L’interface est en français et s’adapte aux écrans mobiles.

## Démarrage

Prérequis : Node.js 20.19+ ou 22.12+ et npm.

```sh
cd front
npm ci
npm run dev
```

Ouvrir l’adresse indiquée par Vite (habituellement `http://localhost:5173`). Démarrer les services et leurs dépendances pour disposer des données réelles.

## Connexion aux services

Le proxy Vite utilise les routes définies par les contrôleurs Java :

| Requête du front                     | Service local                                   |
| ------------------------------------ | ----------------------------------------------- |
| `/api/auth/*`                        | `http://localhost:8083/auth/*`                  |
| `/api/tickets` et `/api/tickets/:id` | `http://localhost:8083/ticket` et `/ticket/:id` |
| `/api/notifications`                 | `http://localhost:8082/notification`            |

Pour modifier les cibles, copier `.env.example` dans `.env.local` puis ajuster `TICKET_API_TARGET` et `NOTIFICATION_API_TARGET`. Redémarrer Vite après modification.

Pour un déploiement statique, configurer les mêmes routes `/api` dans le serveur web, ou définir `VITE_AUTH_API_URL`, `VITE_TICKET_API_URL` et `VITE_NOTIFICATION_API_URL` avant le build. Ces URL sont publiques et ne doivent contenir aucun secret. Privilégier une origine HTTPS commune au front et aux API, transmettre les cookies et définir `SESSION_COOKIE_SECURE=true` côté ticket-service. Pour une autre origine sur le même site, configurer `FRONTEND_ORIGIN` dans les deux services. Le proxy Vite est disponible uniquement pour le développement et `npm run preview`.

## Comptes et connexion

Les pages de tickets et notifications nécessitent une session authentifiée. Depuis `/login`, choisir « Créer un compte », saisir une adresse e-mail et un mot de passe d'au moins 12 caractères (72 octets UTF-8 maximum), puis se connecter. La connexion renvoie vers la page initialement demandée. L'adresse de l'utilisateur et le bouton de déconnexion apparaissent dans la barre supérieure.

La session est restaurée auprès du serveur à chaque navigation ; les réponses 401 masquent les données et renvoient vers la connexion. Le cookie de session est HttpOnly : aucun identifiant de session ni mot de passe n'est stocké dans localStorage. Le client envoie les cookies et récupère un jeton CSRF avant chaque écriture, y compris la connexion et la déconnexion. Le back doit fournir les routes `/auth` et protéger les routes de tickets et de notifications : la garde du routeur seule ne constitue pas une protection de l'API.

## Fonctionnalités

- Indicateurs calculés à partir des tickets chargés.
- Recherche sans distinction de casse ou d’accents, filtres par statut et priorité, tri et pagination.
- Création d’un ticket : titre et description obligatoires, limites de 255 et 2 000 caractères alignées sur les colonnes du serveur.
- Fiche détaillée avec résumé, classification et dates ; actualisation manuelle après l’analyse asynchrone.
- Notifications avec accès au ticket concerné.
- États de chargement, erreurs avec nouvelle tentative, listes vides et page introuvable.
- Navigation clavier, libellés accessibles et prise en compte de la réduction des animations.

## Vérification

```sh
npm run build
npx playwright install chromium
npm run test:e2e
npm run preview
```

Le build vérifie les types avec `vue-tsc`, puis produit `dist/`. Le front ne contient aucune donnée de démonstration. Si les services sont arrêtés, un message d’indisponibilité est affiché.

Les tests Playwright utilisent une API simulée pour vérifier les pages privées, la connexion, l'inscription, les erreurs, l'expiration, la déconnexion, le retour arrière et le format mobile. Les tests Java vérifient séparément la protection réelle des API.
