# role: jenkins

Le **controleur CI** de Travel-Plan, profil Compose **`ci`** (a la demande, cf.
`docs/architecture-decisions.md` §8). Ansible **provisionne et rend des fichiers** ;
docker-compose **orchestre**. Meme modele operatoire que `postgres` / `vault` /
`neo4j` / `traefik` : aucun `docker_container`, aucun `docker compose up` depuis
Ansible.

**Increment CI/CD n°1 — MINIMAL.** Un controleur, **un** job Pipeline declenche
**a la main**, qui joue `./mvnw test` sur `identity-service`. C'est tout.

## Ce que le role fait
- Rend `compose.jenkins.yml` : image **epinglee** `jenkins/jenkins:2.568.3-lts-jdk21`,
  `JENKINS_HOME` sur **volume nomme** `travel-plan-jenkins-home`, reseau
  `backend-net`, **aucun port publie**, `mem_limit: 1600m`, heap controleur
  `-Xmx512m`, healthcheck HTTP `/login`, **route Traefik par labels**.
- Resout le **GID du groupe `docker` de l'hote** (`getent group docker`) et
  l'injecte en `group_add` — sans quoi le process jenkins (uid 1000) ne peut pas
  ecrire sur le socket et Testcontainers echoue en `EACCES`.
- Rend `job-identity-service-test-config.xml` : la definition du job Pipeline,
  **prete a poster**, avec `<triggers/>` **vide**.

## Ce que le role NE fait PAS (assume, pas un oubli)
- **Pas de SonarQube** — increment separe.
- **Pas de webhook, pas de `pollSCM`, pas de multibranch scan** — declenchement
  **manuel strict**. Le `<triggers/>` vide du config.xml en est la preuve
  structurelle (asserte par Molecule).
- **Pas de pipeline payment-service / travel-service** — increments separes.
- **Pas de build ni de push d'image Docker** depuis Jenkins.
- **Pas d'agent Jenkins distinct** : les builds tournent sur le controleur.
- **Il ne CREE pas le job dans Jenkins, et n'installe pas les plugins.** Les deux
  exigent de s'authentifier aupres de Jenkins, donc un **secret cote Ansible**,
  avant que Vault n'y soit cable. Contrat du projet : on **s'arrete et on le
  signale** plutot que d'inliner un credential ou de poser un placeholder. Ce
  sont deux commandes documentees plus bas, jouees par l'operateur avec le mot
  de passe **genere par Jenkins a l'execution** (jamais commite).

## Decisions tranchees (et pourquoi)

### Image : `jenkins/jenkins:2.568.3-lts-jdk21`
LTS epinglee, **JDK 21** pour etre aligne sur `identity-service`
(`eclipse-temurin:21.*` dans son Dockerfile) : le `./mvnw test` du job tourne sur
la meme majeure Java que le build applicatif. Verifie empiriquement dans l'image :
`JENKINS_VERSION=2.568.3`, Temurin 21, et **`git` / `curl` / `unzip` presents**.
`unzip` est une dependance **dure** : le `mvnw` de ce depot est un wrapper shell
artisanal qui telecharge la distribution Maven en `.zip` et l'extrait avec `unzip`.

### Source du code : miroir local `file://`, PAS le remote GitHub
`git remote -v` du depot pointe sur `git@github.com:...` (**SSH**). Cloner depuis
Jenkins exigerait une cle de deploiement = **un secret dans Jenkins**, avant tout
cablage Vault. On monte donc le checkout de l'hote en **lecture seule** dans le
conteneur et le job fait un **vrai `checkout scm`** (git-plugin, branche,
`scriptPath`) en `file:///srv/travel-plan-repo`, avec **zero credential**.

- **Tradeoff** : le job teste ce qui est **commite dans le checkout local**, pas
  ce qui est pousse sur GitHub. C'est exactement l'usage d'un declenchement
  **manuel** en increment minimal. Le passage au remote + credential Vault est un
  increment ulterieur — celui qui amenera aussi les webhooks.
- **Corollaire non evident, trouve en le cassant** : le git-plugin **refuse** par
  defaut un remote qui pointe sur un repertoire local
  (`... references a local directory, which may be insecure`). Il faut lever le
  garde-fou : `-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true`
  (variable `jenkins_allow_local_checkout`, injectee dans `JAVA_OPTS`).
  Ce garde-fou protege un Jenkins **multi-utilisateur** (un utilisateur pouvant
  configurer un job lirait n'importe quel repertoire local). **Accepte ici** :
  Jenkins solo, un seul compte, un seul job, et le seul repertoire local
  atteignable est un bind-mount **`:ro`**. **A repasser a `false`** le jour ou le
  job clonera un vrai remote.

### UI : route Traefik, PAS de port publie
`https://jenkins.localhost/` via les labels Docker, **meme idiome que
identity / payment / travel**. Justification : la convention du projet est que
**seul Traefik publie un port** (docs §4) ; router Jenkins coute 6 labels et rien
d'autre (le provider Docker de Traefik est deja actif), alors qu'un
`docker compose exec` / port-forward serait un contournement de cette convention
pour un service dont l'UI est justement faite pour etre ouverte dans un
navigateur. L'alternative n'aurait economise aucune complexite reelle.

### Socket Docker en **lecture-ECRITURE** (ecart assume vs `traefik`)
Le role `traefik` monte le socket en `:ro` — minimum vital pour la decouverte par
labels. **Ici c'est impossible** : les tests d'`identity-service` utilisent
**Testcontainers** (`PostgreSQLContainer`), qui doit **creer, demarrer et
supprimer** des conteneurs freres. `:ro` rendrait le job structurellement
impossible. Risque quasi-root **accepte**, borne par : profil `ci` a la demande,
machine de dev, UI derriere Traefik. **A reevaluer** si ce projet visait une vraie
prod (agent Jenkins dedie, `docker-socket-proxy`, ou rootless).

### Testcontainers depuis un conteneur (le piege reseau)
Le job tourne **dans** le conteneur Jenkins mais cree ses conteneurs de test sur
le demon de l'**hote**. Par defaut `getHost()` renvoie `localhost` — qui designe
le conteneur Jenkins lui-meme, jamais l'hote ou le port du postgres de test est
publie : connexion refusee. D'ou le couple
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` +
`extra_hosts: host.docker.internal:host-gateway`.

### RAM : `mem_limit: 1600m` (ecart **assume** vs les ~700 Mo de §8)
§8 chiffre « Jenkins ~700 Mo » = le controleur **au repos**. Ici les builds
tournent **sur** le controleur (pas d'agent separe) : la meme cgroup doit loger
le controleur (~700 Mo avec `-Xmx512m`) + la JVM Maven (~300 Mo) + la JVM forkee
par Surefire (~400 Mo). 700m OOM-killerait le premier `mvn test`. L'ecart tient
parce que le profil `ci` est explicitement « a la demande, idealement stack
applicative down » (§8). Mesure au repos apres le build de preuve : **445 MiB**.
Les conteneurs Testcontainers sont des **freres** sur le demon hote : ils ne
comptent pas dans cette cgroup (~250 Mo de plus, a cote).

### Reseau : `backend-net` seul
Necessaire et suffisant pour que Traefik decouvre et route l'UI. Jenkins ne parle
a aucune base du projet (pas de `data-net`) et ne publie rien (pas de `edge-net`).
Il n'a pas non plus besoin de joindre le conteneur `identity-service` : le job ne
teste pas le service **deploye**, il compile et teste les **sources**.

## Secrets
**Aucun dans le depot, aucun rendu par Ansible.** Le fragment n'expose que 3
variables d'environnement non sensibles (`JAVA_OPTS`, `TESTCONTAINERS_HOST_OVERRIDE`,
`DOCKER_HOST`) — asserte par Molecule. Le mot de passe admin est **genere par
Jenkins** dans `JENKINS_HOME/secrets/initialAdminPassword` (volume nomme) et lu
**a l'execution**. Le config.xml du job ne contient **aucun** identifiant : le
transport `file://` n'authentifie rien.

## Test

**Molecule : OUI**, mais sur un perimetre explicite. Le role ne fait que *rendre
des fichiers* : le scenario rend dans `/tmp` d'une plateforme `debian:bookworm-slim`
puis asserte la **structure** du fragment (`from_yaml`, pas du grep) et du
config.xml. Il **ne demarre pas** de vrai Jenkins : expansion du war (~90 s),
wizard, installation des plugins puis un `mvn test` Testcontainers (Maven Central
+ pull `postgres:17.5`) ne tiennent pas dans une boucle `molecule test`
raisonnable et exigeraient le reseau a chaque run. Le **vrai** demarrage et
l'execution du job sont prouves **hors Molecule**, par la sequence rejouable
ci-dessous.

`verify` asserte notamment : image epinglee (jamais `latest`), `mem_limit` +
`-Xmx`, `JENKINS_HOME` sur volume nomme, `backend-net` **seul**, **aucun**
`ports:`, route Traefik complete, socket **sans `:ro`**, `group_add`, cablage
Testcontainers, miroir SCM en `:ro`, healthcheck, **exactement 3** variables
d'environnement (zero secret), et cote job : `<triggers/>` **vide**, aucun
credential, aucune mention de payment/travel/Sonar.

```bash
export PATH="$PWD/.venv-ansible/bin:$PATH"
cd ansible/roles/jenkins && molecule test   # converge + idempotence + verify
```

Un 2e `ansible-playbook` rapporte **`changed=0`** (aucun `command:`/`shell:` dans
le role : uniquement `assert` / `getent` / `set_fact` / `file` / `template`).

---

# Sequence REJOUABLE : de zero a un build vert

> Prerequis : les roles `traefik` et `compose-assembly` ont deja ete appliques,
> les reseaux Docker existent, et `VAULT_DEV_ROOT_TOKEN_ID` / `NEO4J_PASSWORD`
> sont exportes dans le shell (l'interpolation Compose est **globale**, elle ne
> se limite pas au profil demande).

## 0. Le Jenkinsfile doit etre COMMITE sur la branche visee

Le job fait un vrai `checkout scm` : il ne voit que ce qui est **commite**. Le
fichier `services/identity-service/Jenkinsfile` doit donc etre commite sur
`main` (ou surcharger `jenkins_scm_branch`).

## 1. Provisionner (rend le fragment + le config.xml du job)

```bash
cd /chemin/vers/le/depot/travel-plan
source .venv-ansible/bin/activate

ansible-playbook ansible/roles/jenkins/test-local.yml -K \
  -e jenkins_scm_repo_host_path=/chemin/vers/le/depot/travel-plan

ansible-playbook ansible/roles/compose-assembly/test-local.yml -K \
  -e assembly_jenkins_enabled=true \
  -e assembly_identity_fragment=/chemin/vers/le/depot/travel-plan/services/identity-service/docker-compose.identity.yml
```

## 2. Demarrer le profil `ci`

```bash
cd /opt/travel-plan
docker compose --profile ci up -d
# -> demarre `jenkins` + `traefik` (traefik est dans [core, full, ci] car l'UI
#    Jenkins passe par lui).

# attendre que le healthcheck passe (premier demarrage : ~90 s)
until [ "$(docker inspect -f '{{.State.Health.Status}}' travel-plan-jenkins)" = healthy ]; do sleep 5; done
```

## 3. Deverrouiller Jenkins + installer les 3 plugins necessaires

Le mot de passe admin initial est genere par Jenkins **dans le volume**. On ne le
copie nulle part : on le lit a chaque commande, **dans** le conteneur.

```bash
docker exec travel-plan-jenkins bash -c '
PW=$(cat /var/jenkins_home/secrets/initialAdminPassword)
CRUMB=$(curl -s -u "admin:$PW" -c /tmp/ck \
  "http://localhost:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)")

# git (SCM) + workflow-aggregator (Pipeline declaratif) + junit (rapports).
# dynamicLoad:true => pas de redemarrage de Jenkins. Les dependances
# transitives sont resolues par l update center.
curl -s -u "admin:$PW" -b /tmp/ck -H "$CRUMB" -H "Content-Type: application/json" \
  -X POST --data "{\"dynamicLoad\":true,\"plugins\":[\"git\",\"workflow-aggregator\",\"junit\"]}" \
  http://localhost:8080/pluginManager/installPlugins; echo
'
```

Attendre que les 3 plugins soient actifs (~1 a 2 min) :

```bash
docker exec travel-plan-jenkins bash -c '
PW=$(cat /var/jenkins_home/secrets/initialAdminPassword)
curl -s -u "admin:$PW" "http://localhost:8080/pluginManager/api/json?depth=1&tree=plugins\[shortName,active\]" \
  | tr "{" "\n" | grep -E "\"(git|workflow-aggregator|junit)\""
'
# attendu : "active":true pour les 3
```

Terminer le wizard et fixer l'URL publique (sinon Jenkins genere des liens
absolus faux derriere le reverse proxy, et la CLI HTTP refuse de se connecter
avec `Jenkins URL is not configured`) :

```bash
docker exec travel-plan-jenkins bash -c '
PW=$(cat /var/jenkins_home/secrets/initialAdminPassword)
CRUMB=$(curl -s -u "admin:$PW" -c /tmp/ck \
  "http://localhost:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)")
curl -s -o /dev/null -w "completeInstall: %{http_code}\n" -u "admin:$PW" -b /tmp/ck -H "$CRUMB" \
  -X POST http://localhost:8080/setupWizard/completeInstall
curl -s -u "admin:$PW" -b /tmp/ck -H "$CRUMB" -X POST \
  --data-urlencode "script=import jenkins.model.JenkinsLocationConfiguration; def c = JenkinsLocationConfiguration.get(); c.setUrl(\"https://jenkins.localhost/\"); c.save(); println(c.getUrl())" \
  http://localhost:8080/scriptText
'
```

> Le compte reste **`admin`** avec ce mot de passe initial (option « continuer en
> tant qu'admin »). Aucun mot de passe n'est invente ni ecrit dans le depot.

## 4. Creer le job Pipeline — **via l'API REST** (pas l'UI)

Le `config.xml` a ete **rendu par Ansible** a l'etape 1. On le pousse tel quel :

```bash
docker cp /opt/travel-plan/jenkins/job-identity-service-test-config.xml \
          travel-plan-jenkins:/tmp/job-config.xml

docker exec travel-plan-jenkins bash -c '
PW=$(cat /var/jenkins_home/secrets/initialAdminPassword)
CRUMB=$(curl -s -u "admin:$PW" -c /tmp/ck \
  "http://localhost:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)")
curl -s -o /dev/null -w "createItem: %{http_code}\n" -u "admin:$PW" -b /tmp/ck -H "$CRUMB" \
  -H "Content-Type: application/xml" --data-binary @/tmp/job-config.xml \
  "http://localhost:8080/createItem?name=identity-service-test"
'
# attendu : createItem: 200
```

> Equivalent UI, si tu preferes cliquer : *New Item* → nom `identity-service-test`
> → *Pipeline* → *Pipeline script from SCM* → SCM `Git`, URL
> `file:///srv/travel-plan-repo`, branche `*/main`, *Script Path*
> `services/identity-service/Jenkinsfile`, **aucun** trigger coche.

## 5. Declencher le build **a la main**

```bash
docker exec travel-plan-jenkins bash -c '
PW=$(cat /var/jenkins_home/secrets/initialAdminPassword)
CRUMB=$(curl -s -u "admin:$PW" -c /tmp/ck \
  "http://localhost:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)")
curl -s -D - -o /dev/null -u "admin:$PW" -b /tmp/ck -H "$CRUMB" \
  -X POST "http://localhost:8080/job/identity-service-test/build" | grep -iE "^HTTP|^location"
'
# attendu : HTTP/1.1 201 Created
```

(Equivalent UI : bouton **Build Now** sur la page du job.)

## 6. Suivre et confirmer

```bash
# etat du dernier build
docker exec travel-plan-jenkins bash -c '
PW=$(cat /var/jenkins_home/secrets/initialAdminPassword)
curl -s -u "admin:$PW" "http://localhost:8080/job/identity-service-test/lastBuild/api/json?tree=number,building,result"
'

# log complet
docker exec travel-plan-jenkins bash -c '
PW=$(cat /var/jenkins_home/secrets/initialAdminPassword)
curl -s -u "admin:$PW" "http://localhost:8080/job/identity-service-test/lastBuild/consoleText"
'
```

Le premier build telecharge Maven 3.9.9 + tout le repo de dependances
(~2 min) ; les suivants reutilisent le cache `~/.m2` du volume nomme.

### Resultat obtenu (build #2, machine de dev Linux)

```
+ ./mvnw test -B --no-transfer-progress
Downloading Maven from https://repo.maven.apache.org/maven2/.../apache-maven-3.9.9-bin.zip ...
...
INFO tc.postgres:17.5-bookworm -- Container postgres:17.5-bookworm started in PT1.10S
...
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  02:01 min
[Pipeline] junit
Recording test results
Finished: SUCCESS
```

## 7. UI (humain)

```bash
curl -k -I https://jenkins.localhost/login     # HTTP 200 (cert auto-signe Traefik)
# ou navigateur : https://jenkins.localhost/   (accepter le cert auto-signe)
```

`*.localhost` resout en 127.0.0.1 sur Linux ; sinon forcer l'en-tete :
`curl -k -H 'Host: jenkins.localhost' https://localhost/login`.

## 8. Arreter la CI (docs §8 : elle ne reste pas allumee)

```bash
cd /opt/travel-plan
docker compose stop jenkins        # garde Traefik et la stack applicative debout
# ou, si la stack applicative est deja down :
docker compose --profile ci down   # arrete aussi Traefik
```

Le volume `travel-plan-jenkins-home` **survit** : plugins, job, historique et
cache `~/.m2` sont deja la au prochain `up`. Les etapes 3 et 4 ne se rejouent pas.
