# Travel-Plan

Environnement microservices + Admin Dashboard pour un système de gestion de voyages.

**Statut : implémentation en cours.** Les décisions d'architecture sont tranchées
(voir [`docs/architecture-decisions.md`](docs/architecture-decisions.md)) et une
partie de l'infrastructure ainsi que trois services métier + le front
d'administration sont écrits et exécutables.

Ce README décrit **ce qui existe réellement dans le dépôt**. Ce qui est prévu par le
sujet mais absent ou partiel est regroupé — et uniquement regroupé — dans la section
[Non implémenté / écarts avec le sujet](#non-implémenté--écarts-avec-le-sujet).

## Arborescence

```
services/        3 services Spring Boot + le front Angular
ansible/roles/   8 rôles de provisionnement (1 rôle = 1 responsabilité)
ci/              Jenkins / SonarQube — README uniquement, aucune config écrite
docs/            Sujet, grille d'audit, décisions d'architecture
```

## Stack et versions effectivement utilisées

| Brique | Version épinglée | Où |
|---|---|---|
| PostgreSQL | `postgres:17.5-bookworm` | rôle `postgres` |
| Neo4j | `neo4j:5.26.6-community` | rôle `neo4j` |
| Traefik | `traefik:v3.7.1` | rôle `traefik` |
| Vault (dev mode) | `hashicorp/vault:1.18.3` | rôle `vault` |
| Spring Boot | `3.4.3` | les 3 services |
| Angular | `22.x` (Tailwind 4, Vitest) | `admin-dashboard` |

Aucune image en `latest`. Orchestration : Docker Compose uniquement.

---

## Infrastructure (Ansible)

Modèle opératoire : **Ansible provisionne et rend des fragments Compose**,
**docker-compose orchestre**. Les rôles ne lancent pas la stack eux-mêmes.

### Rôles existants

| Rôle | Ce qu'il fait réellement |
|---|---|
| `docker-host` | Prépare l'hôte Ubuntu : pré-check non destructif des paquets distro conflictuels (échoue au lieu de supprimer), clé GPG + dépôt APT Docker en deb822, installe `docker-ce`/`cli`/`containerd.io`/`buildx`/`compose-plugin`, active le démon, ajoute les utilisateurs au groupe `docker`. |
| `postgres` | Rend `compose.postgres.yml` (image épinglée, `data-net`, `mem_limit: 256m`, healthcheck, **aucun port publié**). Crée 2 bases (`identity_db`, `payment_db`) et 2 comptes `NOSUPERUSER/NOCREATEDB/NOCREATEROLE` propriétaires chacun de leur seule base. Révoque `CONNECT` à `PUBLIC` puis l'accorde au seul propriétaire → cloisonnement croisé effectif. Peut lire depuis Vault les mots de passe des comptes applicatifs qu'il provisionne (`postgres_vault_read`, `false` par défaut) — lecture cantonnée à son bloc `provision`, le rôle **ne touche pas** au `.env`. Ne crée **aucune** table (Flyway côté services). |
| `app-secrets` | Propriétaire **unique** de `/opt/travel-plan/.env`, le fichier d'environnement runtime auto-chargé par Compose. Lit les secrets dans Vault (KV v2, mount `secret` : `infra/postgres-superuser`, les `*/db` des services, `shared/jwt`) et les matérialise dans un `.env` rendu intégralement par un seul `template` — un fichier, un propriétaire, sinon deux rôles s'écraseraient l'un l'autre à chaque run. Piloté par `app_secrets_vault_read` (`false` par défaut) : à `false`, no-op intégral (aucune lecture Vault, aucun fichier écrit), ce qui le rend Molecule-safe comme `postgres_vault_read`. |
| `neo4j` | Rend `compose.neo4j.yml` : image épinglée, `data-net`, `mem_limit: 1g`, volume nommé, healthcheck, aucun port publié. |
| `vault` | Rend `compose.vault.yml` (Vault **dev mode**, `data-net`, `mem_limit: 96m`, root token via variable d'env, aucun port publié). Vérifie le moteur KV v2 sur `secret/`, écrit 7 chemins de secrets runtime (`infra/postgres-superuser`, `identity/db`, `payment/db`, `payment/stripe`, `payment/paypal`, `travel/db`, `shared/jwt`) et pose 3 policies cloisonnées (`identity-policy`, `payment-policy`, `travel-policy`), chacune limitée à son propre préfixe. |
| `traefik` | La gateway : **seul rôle publiant un port** (443) et seul à chevaucher `edge-net` + `backend-net`. Rend la config statique (entrypoint `websecure`, provider Docker avec `exposedByDefault: false`, provider file, endpoint `ping`), la config dynamique TLS, et génère un certificat auto-signé (mkcert si présent, sinon openssl ; idempotent via `creates:`). Socket Docker monté en lecture seule (`:ro`). Dashboard Traefik désactivé par défaut. |
| `compose-assembly` | Crée les 3 réseaux bridge via `community.docker.docker_network` (Ansible en est propriétaire, les fragments les déclarent `external: true`) et rend le `docker-compose.yml` top-level qui assemble les fragments via `include:` et applique les profils. |
| `jenkins` | Le contrôleur CI (profil Compose `ci`) : rend `compose.jenkins.yml` (image épinglée, volume nommé pour `JENKINS_HOME`, `backend-net`, aucun port publié, route Traefik par labels) et provisionne 3 jobs Pipeline déclenchés à la main, un par service. |

### Réseaux

Trois réseaux bridge, créés et possédés par Ansible (`docker compose down` ne les
supprime pas) :

- `edge-net` — exposition publique, Traefik seul dessus côté extérieur
- `backend-net` — Traefik ↔ services applicatifs
- `data-net` — services ↔ PostgreSQL / Neo4j / Vault

Aucun conteneur hors Traefik ne publie de port vers l'hôte.

### Profils Compose

| Profil | Contenu |
|---|---|
| `core` | `postgres`, `vault`, `traefik` |
| `full` | `core` + `neo4j` + les services applicatifs activés |
| `ci` | `jenkins` (inclus seulement si `assembly_jenkins_enabled=true`, `false` par défaut) |

Neo4j (poste RAM le plus lourd) est exclu de `core`. Les trois services applicatifs
sont en profil `full` uniquement.

Leurs fragments Compose sont **statiques et vivent dans le dépôt** (à côté du
Dockerfile, puisqu'ils buildent depuis les sources) et sont inclus par **chemin
absolu**, via des flags dans `compose-assembly/defaults/main.yml` :

| Flag | Défaut | Signification |
|---|---|---|
| `assembly_identity_enabled` | `true` | Raccordement identity ↔ Traefik prouvé bout-en-bout |
| `assembly_payment_enabled` | `false` | Intégration non rejouée par défaut, à activer explicitement |
| `assembly_travel_enabled` | `false` | Idem |

Les chemins (`assembly_*_fragment`) sont vides par défaut et doivent être fournis en
`-e` : ils varient d'une machine à l'autre.

### Tests d'infrastructure

| Rôle | Test |
|---|---|
| `postgres`, `neo4j`, `vault`, `traefik`, `app-secrets`, `jenkins` | Scénario **Molecule** (driver docker, conteneurs frères) avec étape `idempotence` (2ᵉ converge = `changed=0`) et `verify` assertant l'état réel. Pour `app-secrets`, le scénario couvre le no-op avec `app_secrets_vault_read=false` et l'idempotence, **pas** le chemin Vault (qui exigerait un Vault provisionné et des secrets réels) |
| `docker-host` | Pas de Molecule (testerait l'install Docker → exigerait DinD, écarté et documenté) ; playbook `test-local.yml` + 2ᵉ run manuel |
| `compose-assembly` | Pas de Molecule ; playbook `test-local.yml` |

```bash
source .venv-ansible/bin/activate
cd ansible/roles/<rôle> && molecule test
```

---

## Services

Les trois services partagent le même socle : variables d'environnement obligatoires
(fail-fast au démarrage, aucun défaut silencieux pour un secret), soft-delete
(`deleted_at`/`deletedAt`, jamais de suppression physique ; toute lecture filtre les
lignes actives), `GlobalExceptionHandler` dédié, healthcheck `/actuator/health`,
routage Traefik par labels Docker (`identity.localhost`, `payment.localhost`,
`travel.localhost`, HTTPS 443, TLS terminé au bord).

### identity-service — PostgreSQL (`identity_db` / `identity_user`)

Schéma géré par Flyway (`V1__init.sql`, `V2__add_password.sql`), Hibernate en
`ddl-auto: validate`.

| Endpoint | Auth | Comportement |
|---|---|---|
| `POST /users` | public | Création (email + password). Mot de passe haché BCrypt avant persistance. 409 si email déjà actif. |
| `POST /login` | public | Vérifie email + password, retourne `id`, `email` et un **JWT HS256** (15 min). 401 générique et indistinguable (y compris en timing) sur tout échec. |
| `GET /me` | Bearer | Résout l'utilisateur actif du token. |
| `GET /users` | Bearer | Liste des utilisateurs actifs. |
| `GET /users/{id}` | Bearer | 404 si absent ou soft-deleted. |
| `PATCH /users/{id}` | Bearer | Modification de l'email. 409 si déjà pris par un autre actif. |
| `DELETE /users/{id}` | Bearer | Soft-delete **puis cascade** vers payment-service. |

- **Pas de filter chain Spring Security** : la validation du token est manuelle et
  identique dans chaque contrôleur (lecture de l'en-tête → `AuthService`/`JwtService`),
  avec le même corps 401 générique. Spring Security n'est utilisé que pour
  `BCryptPasswordEncoder`.
- **Cascade User → Payment implémentée** : après commit du soft-delete,
  `PaymentServiceClient` appelle `DELETE /payments/by-user/{userId}` sur
  `backend-net` (HTTP interne, pas de TLS) avec un **token service-à-service dédié**
  (`generateServiceToken()`, sujet `service:identity`, 15 min, sans identité
  utilisateur). L'appel est volontairement hors transaction ; un échec est loggé et
  avalé — l'utilisateur reste supprimé, cohérence best-effort assumée (pas de
  transaction distribuée).
- Le JWT ne porte **aucun rôle** : le subject est l'id utilisateur, la seule claim
  custom est l'email. « Protégé » signifie donc « n'importe quel utilisateur
  authentifié », sans notion de propriétaire.
- CORS via `WebMvcConfigurer` (pas de `CorsConfigurationSource`, faute de filter
  chain), `CORS_ALLOWED_ORIGINS` surchargeable.

### payment-service — PostgreSQL (`payment_db` / `payment_user`)

Schéma Flyway (`V1__init.sql`, `V2__add_user_id.sql`), `ddl-auto: validate`.

| Endpoint | Auth | Comportement |
|---|---|---|
| `POST /payments` | Bearer utilisateur | Création manuelle. Statut initial toujours `PENDING`, non influençable par le client. |
| `GET /payments` | Bearer utilisateur | Liste des paiements actifs. |
| `GET /payments/{id}` | Bearer utilisateur | 404 si absent ou soft-deleted. |
| `PATCH /payments/{id}/status` | Bearer utilisateur | `PENDING → COMPLETED` ou `PENDING → FAILED` uniquement. |
| `DELETE /payments/{id}` | Bearer utilisateur | Soft-delete. |
| `DELETE /payments/by-user/{userId}` | Bearer utilisateur **ou** token `service:identity` | Soft-delete de tous les paiements actifs d'un utilisateur, retourne le nombre supprimé. |

- **Immutabilité des statuts terminaux** : une fois `COMPLETED` ou `FAILED`, plus
  aucune transition n'est acceptée (409), pas même vers la même valeur.
- **Ownership** : `user_id` UUID `NOT NULL` non modifiable. Aucune FK vers
  `identity_db` (bases séparées par conception, une FK inter-bases n'existe pas en
  Postgres) : l'intégrité référentielle est assumée au niveau applicatif. Index
  partiel `idx_payments_user_id_active` ciblant exactement la requête de cascade.
- **Cloisonnement du token de service** : `requireValidToken` (toutes les autres
  routes) **rejette explicitement** le sujet `service:identity`. Seul
  `requireUserOrServiceToken`, utilisé par la seule route `by-user`, l'accepte. Le
  token de service est donc scopé à un unique endpoint.
- Le service valide la signature et l'expiration du JWT, sans accès à `identity_db`
  (il ne peut donc pas vérifier que le sujet correspond encore à un compte actif).

### travel-service — Neo4j (Community Edition)

Pas de Flyway : `Neo4jSchemaInitializer` exécute au démarrage un
`CREATE CONSTRAINT ... IF NOT EXISTS` idempotent garantissant l'unicité de
`Destination.id` (UUID applicatif, pas l'element id interne de Neo4j).

| Endpoint | Auth | Comportement |
|---|---|---|
| `POST /destinations` | Bearer | Création (`name`, `country`). |
| `GET /destinations` | Bearer | Liste des destinations actives. |
| `GET /destinations/{id}` | Bearer | 404 si absente ou soft-deleted. |
| `PUT /destinations/{id}` | Bearer | Remplace `name` / `country`. |
| `DELETE /destinations/{id}` | Bearer | Soft-delete. |
| `POST /destinations/{fromId}/transports` | Bearer | Relation dirigée `TRANSPORT` vers `toDestinationId` (`mode` ∈ TRAIN/PLANE/BUS/CAR/BOAT, `durationMinutes > 0`). 400 si boucle sur soi-même ou paramètres invalides, 404 si une extrémité est absente/supprimée. |
| `GET /destinations/{id}/transports` | Bearer | **Traversée 1-hop** : destinations atteignables par une relation `TRANSPORT` sortante, filtrant `deletedAt IS NULL` aux deux bouts. |

- Le graphe contient **un seul type de nœud** (`Destination`) et **un seul type de
  relation** (`TRANSPORT`), dirigée et non symétrique.
- Création et traversée passent par du **Cypher explicite via `Neo4jClient`**, pas par
  `repository.save()` : SDN réécrit une collection `@Relationship` en supprimant puis
  recréant toutes les relations du type, ce qui effacerait silencieusement des
  relations non chargées.
- **Dette documentée** : Neo4j Community Edition n'a ni RBAC ni multi-tenant — le
  service se connecte avec l'unique compte administrateur `neo4j`, contrairement aux
  comptes à droits limités côté PostgreSQL.

---

## admin-dashboard (Angular 22)

Application standalone, routes lazy-loaded, Tailwind 4, signals.

| Route | Écran | Opérations réellement câblées |
|---|---|---|
| `/login` | Connexion | `POST /login`, stockage du JWT en `localStorage` |
| `/users` | Utilisateurs | Liste, création (email + password), modification de l'email, suppression |
| `/payments` | Paiements | Liste, création (montant + devise, `userId` lu depuis la claim `sub` du JWT), transition de statut `COMPLETED`/`FAILED`, suppression |
| `/destinations` | Destinations | Liste, création, suppression, + création et liste des `TRANSPORT` sortants |

- **Layout partagé** (`AppShellComponent`) : navigation entre les 3 écrans + bascule
  de thème. Les routes métier sont protégées par `authGuard`.
- **Thème clair/sombre** : `ThemeService` (signal + effect), classe `dark` sur
  `<html>`, persisté en `localStorage`, appliqué par un script inline dans
  `index.html` avant le bootstrap Angular pour éviter le flash.
- **Composants réutilisables** sous `shared/ui/` : `alert`, `button`, `card`, `input`,
  utilisés par les écrans.
- **Intercepteur HTTP** : injecte l'en-tête `Authorization`, redirige vers `/login` sur
  401 (pas de refresh token — le JWT backend dure 15 min).
- Les URLs backend pointent vers la gateway (`https://identity.localhost`,
  `https://payment.localhost`, `https://travel.localhost`) dans les deux
  environnements.

---

## Tests existants

| Périmètre | Nature |
|---|---|
| `identity-service` | 7 classes / 23 `@Test` — tests d'**intégration** Spring Boot + Testcontainers PostgreSQL (auth, JWT, cycle de vie user, autorisation, cascade nominale et cascade en échec) |
| `payment-service` | 3 classes / 14 `@Test` — intégration Testcontainers (cycle de vie et statuts, ownership `user_id`) |
| `travel-service` | 3 classes / 14 `@Test` — intégration Testcontainers Neo4j (cycle de vie destination, graphe `TRANSPORT`) |
| `admin-dashboard` | 1 spec (`app.spec.ts`, Vitest) |
| Ansible | Molecule sur 6 rôles sur 8 (cf. tableau plus haut) |

---

## Non implémenté / écarts avec le sujet

Cette section liste ce que le sujet ou [`docs/audit_grille.md`](docs/audit_grille.md)
attendent et qui est **absent ou partiel** dans le dépôt aujourd'hui.

### CI/CD
- **Jenkins : partiel.** Le rôle Ansible `jenkins` provisionne un contrôleur (profil
  Compose `ci`) et 3 jobs Pipeline, un par service, jouant `./mvnw test`. Ils sont
  **déclenchés à la main** : pas de webhook, pas de build d'image, pas de
  déploiement. `ci/jenkins/` ne contient toujours qu'un README placeholder.
- **SonarQube : absent.** `ci/sonarqube/` ne contient qu'un README placeholder.
- Aucun pipeline de build/déploiement automatisé, donc aucune exécution de tests
  déclenchée par une PR.

### Sécurité / secrets
- **Vault non consommé au runtime par les services** : les services ne parlent jamais
  à Vault et n'ont aucun mécanisme d'authentification côté service (ni AppRole, ni
  Vault Agent). C'est Ansible, avec son token, qui lit les secrets : le rôle
  `app-secrets` les matérialise dans `/opt/travel-plan/.env` (uniquement si
  `app_secrets_vault_read=true`, défaut `false`), et le rôle `postgres` en relit
  certains pour provisionner ses comptes (`postgres_vault_read`, qui dérive
  désormais par défaut de `app_secrets_vault_read`).
  **Règle opérationnelle** : comme il n'existe pas de `site.yml` et que chaque rôle
  est joué par une invocation `ansible-playbook` séparée (aucun état partagé), un
  run réel doit passer `-e app_secrets_vault_read=true` sur **les deux**
  invocations — y compris celle du rôle `postgres`, avec ce **même** nom de flag.
  Activer l'un sans l'autre fait démarrer le conteneur Postgres avec le vrai secret
  Vault pendant que le provisioning tente le placeholder `changeme_postgres_superuser`
  → échec d'authentification immédiat, et mismatch runtime silencieux des comptes
  `identity_user` / `payment_user` côté identity/payment-service.
  Avec les deux flags à `false`, aucun `.env` n'est rendu : les fragments Compose
  utilisent `${VAR:?...}` sans fallback et la stack refuse alors de démarrer plutôt
  que de tourner sur un secret par défaut.
- **Secrets de développement versionnés dans les `defaults/`** : les valeurs écrites
  dans Vault par le rôle `vault` (dont `shared/jwt`, la clé de signature partagée par
  les trois services) sont des placeholders de dev lisibles dans le dépôt. Aucun
  credential réel n'est versionné, mais toute valeur réelle doit être fournie en
  surcharge (`-e` ou fichier chiffré `ansible-vault`).
- **RBAC absent partout** : le JWT ne porte aucune claim de rôle ; il n'existe pas de
  profil « Admin ». Toute route protégée est ouverte à n'importe quel token valide, et
  aucune route n'est *ownership-aware*. L'`authGuard` du dashboard ne vérifie que la
  présence d'un token.
- **TLS partiel** : Traefik termine bien le TLS en 443 avec certificat auto-signé
  (mkcert/openssl) et aucun entrypoint HTTP en clair, mais les communications
  internes (Traefik → services, identity → payment) sont en HTTP simple sur
  `backend-net`, choix assumé.
- Le socket Docker est monté dans Traefik (lecture seule) : risque quasi-root accepté
  et documenté, pas de docker-socket-proxy.

### Fonctionnel
- **Aucune intégration Stripe ni PayPal.** Aucun SDK, aucun appel HTTP vers ces
  fournisseurs dans le code. Seuls existent des chemins de secrets réservés
  (`secret/payment/stripe`, `secret/payment/paypal`) côté Vault et une colonne
  `external_reference` inutilisée. Tous les paiements sont créés manuellement.
- **Pas d'entité `Travel`.** Le sujet demande des voyages avec dates, durée,
  activités, hébergement et transport. Le graphe ne contient que `Destination` et la
  relation `TRANSPORT` : ni `Travel`, ni `Activity`, ni `Accommodation`, aucune date,
  aucune durée de séjour.
- **Pas de CRUD complet sur `TRANSPORT`** : ni mise à jour, ni suppression, ni
  protection anti-doublon ; traversée limitée à 1 saut (pas de pathfinding multi-hop).
- **Pas de « méthodes de paiement »** au sens du sujet : l'entité est une transaction
  (`amount`, `currency`, `status`), pas un moyen de paiement enregistré.
- **Le dashboard ne modifie pas les destinations** alors que `PUT /destinations/{id}`
  existe côté travel-service : l'écran n'expose que création, liste et suppression.
- **Pas de bouton de déconnexion** dans le dashboard (`AuthService.logout()` existe
  mais n'est câblé à aucun contrôle du layout).

### Infrastructure / exploitation
- **Aucun playbook d'orchestration global ni inventaire.** `ansible/` ne contient que
  `roles/` et un README : pas de `site.yml`, pas de `inventory`. Chaque rôle
  s'applique via son propre `test-local.yml` ou son scénario Molecule, et l'ordre
  d'application (rôles de service puis `compose-assembly`) est documenté en commentaire,
  pas automatisé.
- **Pas de réplicas, pas de load balancing effectif, pas de failover.** Aucun
  `deploy.replicas`, un seul conteneur par service. Traefik est capable d'équilibrer
  (provider Docker, découverte par labels), mais aucun pool de réplicas n'existe.
- **Pas de logging traçable inter-services.** Aucun `X-Request-Id`, aucune
  corrélation, aucun MDC, et ni Loki ni Promtail ne sont provisionnés — alors que la
  grille d'audit vérifie explicitement la traçabilité d'une requête à travers les
  services.
- **Le dashboard n'est pas conteneurisé** : pas de Dockerfile, pas de fragment
  Compose, pas de route Traefik. Il ne tourne aujourd'hui que via `ng serve`
  (`http://localhost:4200`, autorisé par la config CORS des services).
- **`payment-service` et `travel-service` désactivés par défaut** dans l'assemblage
  Compose (`assembly_payment_enabled` / `assembly_travel_enabled` à `false`) : seule
  l'intégration d'`identity-service` est considérée prouvée bout-en-bout.
- Pas de rôle `observability`, pas de rôle `common` (tous deux annoncés dans
  `ansible/README.md`, aucun des deux n'existe).
- **Pas de Kubernetes** (bonus du sujet) : aucun manifeste, aucun chart. Choix assumé
  (contrainte 8 Go, Compose seul).

### Process
- **Pas de workflow de PR visible** : l'historique est linéaire sur `main`, sans
  branche ni commit de merge, alors que le sujet et la grille d'audit demandent des
  PRs revues et approuvées.
- Les tests existants sont des **tests d'intégration** (Testcontainers), pas des tests
  unitaires par fonctionnalité comme demandé ; le front n'a qu'une seule spec.

### Documentation
- `ansible/README.md`, `services/README.md` et `services/admin-dashboard/README.md`
  ont été réalignés sur l'état réel (le « Placeholder Phase 0 » a disparu) ; seuls
  `ci/jenkins/README.md` et `ci/sonarqube/README.md` restent des placeholders. Les
  README de
  `identity-service`, `payment-service` et `travel-service` sont à jour ou quasi (celui
  de `payment-service` ne mentionne pas encore `user_id`, l'authentification par token
  ni `DELETE /payments/by-user/{userId}` ; celui d'`identity-service` ne mentionne pas
  `PATCH /users/{id}` ni la cascade).
