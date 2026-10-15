# Analyse des tickets avec un LLM

L'Analysis Service appelle le modèle choisi par l'installation client, valide sa réponse, puis publie le résultat sur Kafka. Il détermine la catégorie, la priorité, la sévérité, un sujet, un résumé et une personne éligible à l'attribution. Il ne classe plus les tickets par mots-clés.

## Connexion

La méthode recommandée est la page **Administration** (`/admin/ai`). Elle conserve la configuration de l’installation en base, protège les clés côté serveur et applique les changements aux prochaines analyses sans redémarrage. Suivre [le guide de mise en service](../AI_ADMIN.md) pour créer l’administrateur et configurer la liaison interne. Les clés enregistrées ne sont jamais renvoyées au navigateur ni placées dans les événements Kafka.

Les variables ci-dessous restent disponibles pour le fonctionnement historique sans liaison interne. Les définir dans le processus qui lance l'Analysis Service, puis redémarrer le service. Lorsque la liaison est activée, la configuration en base est utilisée ; ses valeurs initiales proviennent des variables `LLM_*` du Ticket Service.

| Variable | Valeur par défaut | Usage |
|---|---|---|
| LLM_ENABLED | true | Activer l’analyse automatique |
| LLM_PROVIDER | OLLAMA | OPENAI, ANTHROPIC, OLLAMA ou OPENAI_COMPATIBLE |
| LLM_MODEL | vide | Identifiant exact d'un modèle disponible chez le fournisseur |
| LLM_BASE_URL | selon fournisseur | URL de base, sans l'endpoint final |
| LLM_API_KEY | vide | Requise pour OpenAI et Anthropic ; facultative pour Ollama et une API personnelle |
| LLM_OUTPUT_FORMAT | SCHEMA | SCHEMA, JSON ou PROMPT |
| LLM_CONTEXT_FILE | vide | Chemin vers le contexte métier JSON |
| LLM_TIMEOUT | 30s | Délai d'une requête, maximum 120s |
| LLM_CONNECT_TIMEOUT | 5s | Délai de connexion |
| LLM_MAX_TOKENS | 4096 | Limite de sortie, entre 1 et 32768 ; respecter aussi la limite du modèle |
| LLM_MAX_ATTEMPTS | 2 | Nombre total de tentatives, entre 1 et 3 |

Le modèle n'est pas choisi automatiquement : une configuration manquante produit un échec explicite. Une erreur ne déclenche aucun basculement vers un autre fournisseur.

Exemples PowerShell, à compléter avec le modèle et les identifiants du client :

### Ollama local

~~~powershell
$env:LLM_PROVIDER = 'OLLAMA'
$env:LLM_MODEL = 'nom-du-modele-installe'
$env:LLM_BASE_URL = 'http://localhost:11434'
$env:LLM_CONTEXT_FILE = 'C:/configuration/analysis-context.json'
~~~

Le modèle doit être installé dans Ollama. Le service utilise POST /api/chat avec stream=false et un schéma dans format. Si l'Analysis Service est dans Docker, utiliser une adresse atteignable depuis le conteneur (par exemple host.docker.internal sur Docker Desktop pour joindre l'hôte). Une application hébergée à distance doit disposer d'une connexion au serveur Ollama du client ; localhost désigne la machine qui exécute le service.

### OpenAI

~~~powershell
$env:LLM_PROVIDER = 'OPENAI'
$env:LLM_MODEL = 'modele-autorise-dans-votre-compte'
$env:LLM_API_KEY = 'VOTRE_CLE_API'
~~~

L'URL par défaut est https://api.openai.com/v1. Le connecteur utilise POST /responses, store=false et text.format pour les sorties structurées. Choisir un modèle qui prend en charge le format demandé.

### Claude

~~~powershell
$env:LLM_PROVIDER = 'ANTHROPIC'
$env:LLM_MODEL = 'modele-autorise-dans-votre-compte'
$env:LLM_API_KEY = 'VOTRE_CLE_API'
~~~

L'URL par défaut est https://api.anthropic.com/v1. Le connecteur utilise POST /messages et output_config.format. SCHEMA demande un modèle compatible avec les sorties structurées. PROMPT permet une réponse JSON uniquement demandée dans les instructions ; JSON n'est pas un mode pris en charge par ce connecteur Claude.

### Modèle personnel

~~~powershell
$env:LLM_PROVIDER = 'OPENAI_COMPATIBLE'
$env:LLM_BASE_URL = 'http://serveur-client:8000/v1'
$env:LLM_MODEL = 'modele-specialise-tickets'
$env:LLM_OUTPUT_FORMAT = 'SCHEMA'
~~~

L'API du serveur doit accepter POST /chat/completions et renvoyer choices[0].message.content. Une clé peut être ajoutée avec LLM_API_KEY si nécessaire. Utiliser JSON si le serveur ne prend en charge que response_format=json_object ; PROMPT s'il ne prend en charge aucun format contraint. La validation métier reste identique dans tous les modes. Une API avec un autre protocole nécessite une implémentation de LlmClient.

Connecter un modèle déjà entraîné est couvert. L'entraînement, l'hébergement des poids et la recherche automatique de tickets similaires ne font pas partie de ce service.

## Contexte métier et attribution

Copier analysis-context.example.json vers un fichier de configuration du client et remplacer les personnes et exemples fictifs avant de définir LLM_CONTEXT_FILE. Le fichier est relu à chaque analyse, ce qui permet de mettre à jour les compétences sans redémarrer le service.

Le fichier contient businessRules, assignees et examples. Chaque personne doit avoir un id unique, un nom et une liste skills. L'id est la valeur enregistrée dans le champ assignee du ticket : utiliser une adresse ou un identifiant connu de l'organisation. Le répertoire fourni est la liste des personnes éligibles ; le modèle ne consulte pas automatiquement les comptes du logiciel. Retirer une personne de cette liste pour la rendre inéligible.

Sans fichier, les critères généraux sont utilisés et la liste des personnes est vide : l'attribution reste null. Un identifiant inventé par le modèle fait échouer la validation. Le fichier est limité à 256 Kio, 200 personnes et 20 exemples.

La priorité accepte LOW, MEDIUM, HIGH. La sévérité accepte MINOR, MAJOR, CRITICAL, BLOCKER. La catégorie accepte TECHNICAL, BILLING, ACCOUNT, OTHER. Le sujet est limité à 255 caractères, le résumé à 500 caractères. Les réponses tronquées, refusées, les JSON invalides, les champs inattendus et les valeurs non autorisées sont rejetés. Un schéma valide ne garantit pas la pertinence métier ; évaluer le modèle avec des tickets représentatifs et leurs classifications attendues.

## Parcours du ticket

- La création publie ticket.created.v1 avec un identifiant de demande d'analyse et le contexte textuel du ticket.
- L'analyse publie ticket.analyzed.v1 avec SUCCEEDED, FAILED ou DISABLED. Le ticket expose analysisStatus et un code d'échec sans contenu brut du fournisseur. DISABLED termine l’attente sans modifier les champs du ticket.
- POST /ticket/{id}/analysis relance l'analyse avec la session et le jeton CSRF habituels.
- Une modification du titre, de la description ou du contexte de diagnostic relance l'analyse.
- Les résultats d'une ancienne demande et les résultats déjà appliqués sont ignorés.
- Les notifications écoutent ticket.analysis-applied.v1, publié après la mise à jour du ticket. Elles utilisent la priorité réellement conservée et ignorent ainsi les échecs, les doublons et les analyses périmées.
- Les champs choisis manuellement à la création ou modifiés ensuite sont protégés. Enregistrer un formulaire sans modifier un champ automatique ne le transforme pas en choix manuel. Vider la sévérité ou l'attribution les rend de nouveau éligibles à l'analyse.
- Le sujet remplace le titre seulement si autoTitle=true à la création et si le titre n'a pas ensuite été modifié manuellement.
- La classification automatique peut être mise à jour lors d'une relance, tandis que le statut de traitement et les choix manuels restent conservés.
- Le contenu des pièces jointes n'est pas envoyé au modèle dans cette version.

La configuration est propre à une installation. Les comptes existants partagent un espace ; une plateforme accueillant plusieurs entreprises nécessiterait une isolation des entreprises, tickets, clés et contextes avant de proposer des configurations par entreprise.

## Installation et vérifications

Utiliser un JDK 21 compatible avec les dépendances actuelles du projet. Depuis la racine :

~~~powershell
.\analysis-service\mvnw.cmd -f pom.xml test
.\analysis-service\mvnw.cmd -f pom.xml install -DskipTests
~~~

Le pom racine construit les contrats partagés et les trois services dans le bon ordre. Mettre à jour et redémarrer ensemble les services utilisant ticketing-contracts. Les anciens tickets sont conservés ; les nouvelles colonnes et la table de suivi des champs manuels sont ajoutées par la configuration Hibernate existante.

Les tests utilisent des serveurs HTTP simulés et des clés fictives, sans appeler les fournisseurs réels. Depuis front, lancer npm run build puis npm run test:e2e.

Documentation des protocoles :
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [Claude Structured Outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs)
- [Ollama Chat](https://docs.ollama.com/api/chat)
- [Ollama Structured Outputs](https://docs.ollama.com/capabilities/structured-outputs)
