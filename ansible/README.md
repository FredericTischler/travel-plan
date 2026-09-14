# ansible/

Provisionnement de la stack. **Un rôle = une responsabilité** (pas de playbook
monolithique).

Modèle opératoire : **Ansible provisionne et rend des fragments Compose**,
**docker-compose orchestre**. Aucun rôle ne lance la stack lui-même : ils écrivent
des fichiers sous `/opt/travel-plan/` (et créent les réseaux Docker), puis c'est
`docker compose --profile <core|full> up -d` qui démarre les conteneurs.

Ce README décrit **ce qui existe réellement** dans `ansible/`. Ce qui est absent est
regroupé dans [Non implémenté](#non-implémenté).

## Contenu réel du dossier

```
ansible/
  roles/            6 rôles
  README.md
```

Il n'y a **ni `site.yml`, ni inventaire**. Chaque rôle s'applique via son propre
`test-local.yml` (`hosts: localhost`, `connection: local`) ou via son scénario
Molecule.

## Rôles existants

| Rôle | Ce qu'il fait réellement |
|---|---|
| `docker-host/` | Prépare l'hôte Ubuntu (jammy/noble) : pré-check **non destructif** des paquets distro conflictuels (`docker.io`, `docker-compose`, `docker-compose-v2`, `docker-doc`, `podman-docker`, `containerd`, `runc`) — le rôle échoue et demande un retrait manuel, il ne supprime rien ; clé GPG Docker dans `/etc/apt/keyrings/docker.asc` ; dépôt APT deb822 ; installe `docker-ce`, `docker-ce-cli`, `containerd.io`, `docker-buildx-plugin`, `docker-compose-plugin` ; démon `enabled` + `started` ; ajoute `docker_host_users` au groupe `docker`. Ne configure **pas** le démon et ne crée **aucun** réseau. |
| `postgres/` | Rend `compose.postgres.yml` (`postgres:17.5-bookworm`, réseau `data-net`, `mem_limit: 256m`, volume nommé, aucun port publié). Provisionne l'instance via `community.postgresql` : 2 comptes `NOSUPERUSER,NOCREATEDB,NOCREATEROLE,LOGIN` (`identity_user`, `payment_user`), 2 bases dont ils sont chacun propriétaire (`identity_db`, `payment_db`), `REVOKE CONNECT` à `PUBLIC` puis `GRANT CONNECT` au seul propriétaire. Aucune table métier (Flyway côté services). Peut lire ses credentials depuis Vault via `postgres_vault_read` (**`false` par défaut**). |
| `neo4j/` | Rend `compose.neo4j.yml` : `neo4j:5.26.6-community`, `data-net`, `mem_limit: 1g`, volume nommé, healthcheck, aucun port publié. Aucun schéma / contrainte / index métier. |
| `vault/` | Rend `compose.vault.yml` (`hashicorp/vault:1.18.3` en **dev mode**, `data-net`, `mem_limit: 96m`, aucun port publié). Vérifie le moteur KV v2 sur `secret/`, écrit 6 chemins (`infra/postgres-superuser`, `identity/db`, `payment/db`, `payment/stripe`, `payment/paypal`, `travel/db`) en read-before-write, et pose 3 policies cloisonnées (`identity-policy`, `payment-policy`, `travel-policy`), chacune limitée à son préfixe. |
| `traefik/` | La gateway : **seul rôle publiant un port** (443) et seul à chevaucher `edge-net` + `backend-net`. Rend `compose.traefik.yml` (`traefik:v3.7.1`, `mem_limit: 128m`, socket Docker monté en **lecture seule** `:ro`), la config statique (entrypoint `websecure`, provider Docker avec `exposedByDefault: false`, provider file, endpoint `ping`), la config dynamique TLS, et génère un certificat auto-signé (mkcert si présent, sinon openssl). Dashboard désactivé par défaut (`traefik_dashboard_enabled: false`). |
| `compose-assembly/` | Crée les 3 réseaux bridge via `community.docker.docker_network` et rend le `docker-compose.yml` top-level qui assemble les fragments (`include:`) et applique les profils. |

Aucun autre rôle n'existe : `common`, `observability` et `app-deploy`, annoncés dans
une version antérieure de ce fichier, **n'ont jamais été écrits**.

## Réseaux

Trois réseaux bridge créés et **possédés par Ansible** ; les fragments Compose les
déclarent `external: true`, donc `docker compose down` ne les supprime pas :

- `edge-net` — exposition publique (Traefik seul dessus côté extérieur)
- `backend-net` — Traefik ↔ services applicatifs
- `data-net` — services ↔ PostgreSQL / Neo4j / Vault

Aucun conteneur hors Traefik ne publie de port vers l'hôte.

## Assemblage Compose

`compose-assembly` rend `/opt/travel-plan/docker-compose.yml`, qui `include:` les
fragments puis applique les profils **par merge** sur les services inclus :

| Profil | Services |
|---|---|
| `core` | `postgres`, `vault`, `traefik` |
| `full` | `core` + `neo4j` + les services applicatifs activés |

Traefik est dans `core` parce qu'il est le seul point d'entrée : sans lui la stack
`core` n'est joignable de nulle part. Neo4j (poste RAM le plus lourd, `mem_limit: 1g`)
en est exclu.

Les fragments des rôles sont référencés par chemin **relatif** à
`assembly_compose_dir` (`/opt/travel-plan`) :
`postgres/compose.postgres.yml`, `vault/compose.vault.yml`, `neo4j/compose.neo4j.yml`,
`traefik/compose.traefik.yml`.

Les trois services applicatifs ont au contraire un fragment **statique versionné dans
le dépôt** (co-localisé avec leur `Dockerfile` et leurs sources, puisqu'ils buildent
depuis les sources) : il est donc inclus par **chemin absolu**, variable d'une machine
à l'autre, et pilotable par flag :

| Flag | Défaut | Variable de chemin (vide par défaut) |
|---|---|---|
| `assembly_identity_enabled` | `true` | `assembly_identity_fragment` |
| `assembly_payment_enabled` | `false` | `assembly_payment_fragment` |
| `assembly_travel_enabled` | `false` | `assembly_travel_fragment` |

Les `include:` Compose sont résolus **quel que soit le `--profile`** : un chemin
invalide casserait aussi le rendu `core`. D'où le couple flag + chemin explicite
plutôt qu'un include en dur. `identity` est à `true` (raccordement identity ↔ Traefik
prouvé bout-en-bout) ; `payment` et `travel` restent à `false` tant que leur
intégration n'a pas été rejouée — activés, leur `include:` **et** leur bloc de profil
sont émis, sinon le rendu est strictement inchangé.

Les chemins de fragment doivent être fournis en `-e`, par exemple :

```bash
ansible-playbook ansible/roles/compose-assembly/test-local.yml -K \
  -e assembly_identity_fragment=/chemin/absolu/vers/le/depot/services/identity-service/docker-compose.identity.yml
```

**Ordre d'application** : les rôles de service (`postgres`, `vault`, `neo4j`,
`traefik`) d'abord, `compose-assembly` **en dernier** — il assemble des fragments déjà
rendus. Cet ordre est documenté en commentaire dans `compose-assembly/test-local.yml`,
il n'est pas automatisé (pas de `site.yml`).

## État de validation par rôle

| Rôle | Test réel |
|---|---|
| `postgres` | **Molecule** (`molecule/default/`), driver docker, `test_sequence` incluant `idempotence` + `verify` |
| `vault` | **Molecule**, même structure, `verify` en approche contrôleur |
| `neo4j` | **Molecule**, même structure |
| `traefik` | **Molecule** : rend certs/configs/fragment puis démarre un **vrai** conteneur `traefik:v3.7.1` contre cette config (Traefik fail-fast sur clé statique inconnue → healthy = preuve de validité) |
| `docker-host` | **Pas de Molecule** : tester l'installation de Docker exigerait du DinD, écarté (documenté dans `roles/docker-host/README.md`). Validation = `test-local.yml` appliqué réellement + 2ᵉ run manuel pour l'idempotence |
| `compose-assembly` | **Pas de Molecule** : validation via `test-local.yml` (crée réellement les réseaux et rend le `docker-compose.yml` top-level) |

Les 4 scénarios Molecule utilisent le driver **docker en conteneurs frères** (Linux
natif, pas de DinD) et une `test_sequence` explicite contenant `idempotence` : un 2ᵉ
converge doit rapporter `changed=0`.

```bash
source .venv-ansible/bin/activate
cd ansible/roles/<postgres|vault|neo4j|traefik>/molecule/default && molecule test
```

```bash
# Rôles sans Molecule
ansible-playbook ansible/roles/docker-host/test-local.yml --syntax-check
ansible-playbook ansible/roles/docker-host/test-local.yml -K
```

> Piège `docker-host` : l'ajout au groupe `docker` exige un re-login (ou
> `newgrp docker`) pour prendre effet.

## Idempotence

Modules natifs partout. Il n'existe que **trois** `ansible.builtin.command` dans tout
`roles/`, chacun neutralisé pour l'idempotence :

| Où | Justification |
|---|---|
| `docker-host` — refresh APT ciblé sur le seul dépôt Docker | `changed_when: false` (refresh de cache, état applicatif inchangé) |
| `postgres` — check `import psycopg2` sur le Python du contrôleur | `changed_when: false`, `when: ansible_connection == 'local'` |
| `traefik` — génération du certificat auto-signé | `creates:` sur le `.crt` |

Aucun `shell:`. Tags disponibles selon les rôles : `compose_fragment`, `provision`,
`config`, `tls`.

## Versions épinglées

Images (jamais `latest`) :

| Image | Rôle |
|---|---|
| `postgres:17.5-bookworm` | `postgres` |
| `neo4j:5.26.6-community` | `neo4j` |
| `hashicorp/vault:1.18.3` | `vault` |
| `traefik:v3.7.1` | `traefik` |

Collections, déclarées dans les `requirements.yml` **des scénarios Molecule**
(il n'y a pas de `requirements.yml` global au niveau `ansible/`) :
`community.docker >=5.2.1,<6.0.0`, `community.postgresql >=4.2.0,<5.0.0`,
`community.hashi_vault >=7.0.0,<8.0.0`, `ansible.posix >=2.2.0,<3.0.0`.

## Secrets

Les `defaults/` portent des valeurs `changeme_*` explicitement marquées à surcharger
(`postgres_superuser_password`, mots de passe applicatifs, `vault_dev_root_token`,
clés Stripe/PayPal). Ce sont des **placeholders de développement**, pas des secrets :
aucun credential réel n'est versionné. Toute valeur réelle doit être fournie en
surcharge (`-e`, ou fichier chiffré `ansible-vault`).

## Non implémenté

- **Pas de `site.yml` ni d'inventaire** : aucun playbook d'orchestration global,
  l'ordre d'application est documenté mais pas automatisé.
- **Pas de rôle `common`, `observability` ni `app-deploy`** — annoncés dans une
  version antérieure de ce README, jamais écrits. Aucun Loki/Promtail provisionné.
- **Pas de Molecule sur `docker-host` ni `compose-assembly`** (justifications
  ci-dessus).
- **Pas de réplicas ni de load balancing effectif** : aucun `deploy.replicas`, un seul
  conteneur par service. Traefik sait équilibrer (provider Docker, découverte par
  labels) mais aucun pool n'existe.
- **`admin-dashboard` n'est pas provisionné** : aucun fragment Compose, aucune route
  Traefik pour le front.
- **Pas de Kubernetes** : aucun manifeste, aucun chart.
