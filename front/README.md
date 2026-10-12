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
| `/api/tickets` et `/api/tickets/:id` | `http://localhost:8083/ticket` et `/ticket/:id` |
| `/api/notifications`                 | `http://localhost:8082/notification`            |

Pour modifier les cibles, copier `.env.example` dans `.env.local` puis ajuster `TICKET_API_TARGET` et `NOTIFICATION_API_TARGET`. Redémarrer Vite après modification.

Pour un déploiement statique, configurer les mêmes routes `/api` dans le serveur web, ou définir `VITE_TICKET_API_URL` et `VITE_NOTIFICATION_API_URL` avant le build. Ces URL sont publiques et ne doivent contenir aucun secret. Avec des API sur une autre origine, configurer aussi CORS côté backend. Le proxy Vite est disponible uniquement pour le développement et `npm run preview`.

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
npm run preview
```

Le build vérifie les types avec `vue-tsc`, puis produit `dist/`. Le front ne contient aucune donnée de démonstration. Si les services sont arrêtés, un message d’indisponibilité est affiché.
