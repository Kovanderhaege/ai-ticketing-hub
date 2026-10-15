# Administration du modèle d’analyse

La page **Administration**, à l’adresse `/admin/ai`, permet à un administrateur de cette installation de choisir le modèle qui classe les tickets. Elle propose OpenAI, Claude (Anthropic), Ollama et un serveur personnel exposant une API compatible OpenAI.

Le navigateur communique uniquement avec le Ticket Service. Celui-ci conserve les paramètres dans PostgreSQL et chiffre la clé du fournisseur avec AES-256-GCM. La clé enregistrée n’est jamais renvoyée au front. L’Analysis Service récupère la configuration par une API interne authentifiée avant chaque analyse.

## Première mise en service

Utiliser Java 21. Reconstruire les modules depuis le pom racine et redémarrer les services ensemble, car le contrat partagé ajoute le statut `DISABLED`.

### Ticket Service

Configurer dans son environnement de démarrage :

| Variable | Description |
|---|---|
| `APP_ADMIN_EMAIL` | Adresse du premier administrateur |
| `APP_ADMIN_PASSWORD` | Mot de passe d’au moins 12 caractères, au plus 72 octets UTF-8 |
| `AI_INTERNAL_TOKEN` | Secret aléatoire partagé entre les deux services, au moins 32 caractères |
| `ANALYSIS_SERVICE_URL` | Adresse de l’Analysis Service ; défaut `http://localhost:8081` |
| `AI_SETTINGS_ENCRYPTION_KEY` | Facultatif : clé de chiffrement de 32 octets encodée en Base64 |
| `AI_SETTINGS_KEY_FILE` | À défaut de clé explicite : fichier persistant ; défaut `./data/ai-settings.key` |

Exemple de génération de secrets distincts avec PowerShell 7, à conserver dans la configuration du déploiement :

~~~powershell
$env:AI_INTERNAL_TOKEN = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:AI_SETTINGS_ENCRYPTION_KEY = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:APP_ADMIN_EMAIL = 'administrateur@entreprise.fr'
$env:APP_ADMIN_PASSWORD = 'REMPLACER_PAR_UN_MOT_DE_PASSE_PERSONNEL'
$env:ANALYSIS_SERVICE_URL = 'http://localhost:8081'
~~~

Les secrets générés doivent rester identiques au redémarrage. Ne pas régénérer la clé de chiffrement après avoir enregistré une clé de fournisseur.

Au démarrage, le compte administrateur est créé. Si l’adresse existe déjà, son mot de passe doit correspondre avant que le rôle administrateur lui soit accordé. Une inscription publique crée toujours un utilisateur ordinaire. Les comptes existants restent utilisateurs.

Après cette initialisation, les deux variables `APP_ADMIN_EMAIL` et `APP_ADMIN_PASSWORD` peuvent être retirées ensemble. Le rôle est conservé en base. Se reconnecter pour obtenir les droits dans la session.

Sans `AI_SETTINGS_ENCRYPTION_KEY`, un fichier de clé est créé lors du premier enregistrement d’un identifiant fournisseur. Son chemin est relatif au répertoire de démarrage du Ticket Service. Monter ce fichier dans un volume persistant, limiter sa lecture au compte du service et le sauvegarder séparément de la base. Pour plusieurs instances, fournir la même clé explicite à toutes les instances. Une clé perdue empêche de relire les identifiants existants ; l’administrateur peut les remplacer depuis la page.

### Analysis Service

Configurer **la même valeur** de `AI_INTERNAL_TOKEN`, ainsi que :

~~~text
AI_SETTINGS_URL=http://localhost:8083/internal/ai/configuration
~~~

Cette URL doit pointer sur le Ticket Service. Dans des conteneurs ou sur des machines distinctes, remplacer les adresses localhost par les noms de services accessibles. Les API `/internal/ai/**` sont réservées aux services : ne pas les publier via le front. Utiliser HTTPS pour transporter des identifiants entre machines ; placer aussi l’interface utilisateur derrière HTTPS dans le déploiement d’entreprise.

Le proxy de développement Vite ajoute `/api/admin` vers `/admin` du Ticket Service. Le reverse proxy de production doit fournir la même correspondance. Une URL front différente peut être définie avec `VITE_AI_ADMIN_API_URL`.

Si le jeton interne n’est pas configuré, la page indique que la liaison serveur manque et empêche l’enregistrement et les tests.

## Utilisation

1. Se connecter avec le compte administrateur et ouvrir **Administration**.
2. Choisir le fournisseur, le modèle et éventuellement l’adresse du serveur. Pour Ollama, **Charger les modèles** liste les modèles installés, même avant d’avoir choisi un modèle.
3. Saisir une clé pour OpenAI ou Claude. Elle est facultative pour Ollama ou un serveur personnel.
4. Utiliser **Tester la connexion**, puis **Analyser un ticket fictif** pour voir les champs validés par le service.
5. Enregistrer. Les analyses suivantes utilisent ces paramètres sans redémarrage.

Les tests utilisent la saisie courante, sans l’enregistrer et sans créer de ticket. Ils effectuent une seule tentative et peuvent consommer des crédits fournisseur. La réponse fictive est soumise aux mêmes règles de validation que les tickets réels.

Le champ de clé reste vide après enregistrement : le laisser vide conserve la clé uniquement pour le même fournisseur et la même adresse effective. Un changement de fournisseur ou d’adresse exige de ressaisir une clé si elle est nécessaire. La case de suppression permet d’effacer la clé ; désactiver l’analyse permet d’enregistrer un fournisseur cloud sans clé ni modèle.

Les paramètres avancés exposent le format JSON, les délais, la limite de sortie et le nombre de tentatives. Un modèle personnel doit accepter le protocole Chat Completions ; choisir le format PROMPT s’il ne propose pas de sorties structurées. L’entraînement du modèle reste externe à l’application.

La configuration appartient à toute l’installation. Chaque analyse en prend une copie avant de commencer, conservée pour ses éventuelles tentatives. Une modification ne mélange pas deux modèles dans la même analyse. Il n’y a aucun basculement automatique vers un autre fournisseur en cas d’erreur.

Un enregistrement concurrent produit un conflit et conserve la saisie dans le navigateur. Recharger les paramètres permet de reprendre la dernière version enregistrée.

## Désactivation et attribution

Quand l’analyse est désactivée, les nouveaux traitements se terminent avec `analysisStatus=DISABLED`. Les tickets restent modifiables manuellement. Les champs existants sont conservés et aucune notification de priorité issue d’une analyse désactivée n’est créée.

Les personnes éligibles, leurs compétences et les règles métier restent dans le fichier JSON défini par `LLM_CONTEXT_FILE` dans l’Analysis Service ; voir [le guide d’analyse](analysis-service/README.md). Sans répertoire de personnes, le modèle laisse l’attribution vide. La page ne crée pas de comptes et ne configure pas l’entraînement.

## Compatibilité et persistance

Les tables `ai_configuration` et la colonne de rôle des comptes sont ajoutées par le mécanisme Hibernate existant (`ddl-auto=update`). Les configurations enregistrées priment sur les variables `LLM_*`.

Avec la liaison interne activée, les valeurs initiales proviennent des variables `LLM_*` du **Ticket Service**, avant le premier enregistrement. Il est donc possible de renseigner directement la page sans reproduire l’ancienne configuration d’environnement.

Sans jeton interne dans l’Analysis Service, le fonctionnement historique par variables `LLM_*` de ce service reste disponible. Ce mode ne lit pas les paramètres de la page ; activer la liaison dans les deux services pour utiliser l’administration.

Les essais automatisés utilisent des serveurs HTTP simulés et des clés fictives. Un test avec le modèle réellement installé reste à effectuer sur le réseau de l’entreprise.