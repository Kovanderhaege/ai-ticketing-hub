# Infrastructure locale

Depuis la racine du projet, avec `DB_USERNAME` et `DB_PASSWORD` définis dans
l'environnement ou dans `infra/.env` :

```sh
docker compose -f infra/docker-compose.yml up -d --wait
```

Kafka utilise KRaft avec un seul nœud cumulant les rôles de broker et de
contrôleur, pour le développement local. L'image `confluentinc/cp-kafka:7.5.0`
est conservée et prend déjà en charge ce mode.

- Applications lancées sur la machine : `localhost:9092`.
- Clients dans le réseau Docker Compose : `kafka:29092`.
- Les messages et métadonnées sont persistés dans le volume `kafka_data`.
  Conserver le même `CLUSTER_ID` tant que ce volume est réutilisé.

## Passage depuis l'ancienne configuration ZooKeeper

Cette configuration initialise un nouveau cluster KRaft ; elle ne migre pas
les topics, messages ou offsets de l'ancien cluster ZooKeeper. Si ces données
doivent être conservées, effectuer une migration avant de recréer Kafka.

Pour repartir avec un cluster Kafka vide en développement :

```sh
docker compose -f infra/docker-compose.yml up -d --wait --remove-orphans
```

Cette commande recrée Kafka et supprime l'ancien conteneur ZooKeeper. Les volumes
PostgreSQL sont conservés. Éviter `down --volumes` pour garder les données locales.
