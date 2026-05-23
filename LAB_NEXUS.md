# LAB : Nexus Repository Manager
### Étudiant B : Gestion des Artifacts avec Nexus

---

# Objectif du TP

Dans ce laboratoire, j’ai travaillé sur l’outil **Nexus Repository Manager** afin de comprendre la gestion centralisée des artifacts dans une chaîne CI/CD.

L’objectif principal était :

- Installer Nexus
- Créer un repository Maven
- Générer un artifact Java avec Maven
- Déployer l’artifact dans Nexus
- Vérifier le stockage et la gestion des versions

---

# 1. Installation de Nexus avec Docker

Pour éviter la perte des données après suppression du conteneur, j’ai utilisé un volume Docker.

Commande utilisée :

```bash
docker run -d \
--name nexus \
-p 8081:8081 \
-v nexus-data:/nexus-data \
sonatype/nexus3
```
### Capture — Container Nexus:

<img width="1605" height="152" alt="image" src="https://github.com/user-attachments/assets/1e0f1d94-9412-4e79-9e49-e1aacddc82de" />

# 2. Accès à Nexus

Après le démarrage du conteneur, j’ai accédé à Nexus via :

```bash
http://localhost:8081
```
Pour récupérer le mot de passe admin :

```bash
docker exec nexus cat /nexus-data/admin.password
```

### Capture — Interface Nexus

<img width="1855" height="909" alt="Capture d&#39;écran 2026-05-15 095951" src="https://github.com/user-attachments/assets/baccd547-94fe-44f1-ad96-7867a2c45b2f" />

# 3. Création du Repository Maven

J’ai créé un repository Maven de type :

**maven2 (hosted)**
<img width="1576" height="468" alt="Capture d&#39;écran 2026-05-15 230038" src="https://github.com/user-attachments/assets/066bf154-02b0-450a-a4b5-f09eb47a6c26" />

Nom du repository :

**maven-project-repo**
<img width="1573" height="473" alt="Capture d’écran 2026-05-15 230449" src="https://github.com/user-attachments/assets/e8f37fa9-02de-4b04-9c87-3a1984def8f2" />

Configuration choisie :
| Paramètre | Valeur |
|---|---|
| Version Policy | Snapshot |
| Deployment Policy | Allow redeploy |

Cette configuration permet de stocker les versions de développement (SNAPSHOT).

### Capture — Configuration du Repository
<img width="1569" height="814" alt="Capture d’écran 2026-05-15 231339" src="https://github.com/user-attachments/assets/6d6d2afe-fdf4-4793-8970-d4493176122a" />

# 4. Génération du projet Spring Boot

Pour créer le projet Java Maven, j’ai utilisé le site :

https://start.spring.io/

### Configuration choisie :
<img width="1098" height="772" alt="image" src="https://github.com/user-attachments/assets/821690bf-1551-4964-9f73-33e7ecefef0f" />

### Compilation du projet :
```bash
mvn clean package
```
<img width="1315" height="596" alt="Capture d&#39;écran 2026-05-15 115512" src="https://github.com/user-attachments/assets/46621f4f-8a9a-4500-8de7-9d24e24c0e45" />

###  Capture — BUILD SUCCESS

<img width="1319" height="246" alt="Capture d&#39;écran 2026-05-15 115441" src="https://github.com/user-attachments/assets/b84a6b10-0423-4882-a666-2025d8950fd0" />

Résultat obtenu :

### target/*.jar
<img width="1141" height="176" alt="Capture d&#39;écran 2026-05-15 115656" src="https://github.com/user-attachments/assets/3877ca9f-fca9-4453-87be-7653290ca0a2" />

Le fichier JAR représente l’artifact généré par Maven.


# 5. Configuration du fichier pom.xml

Ajout de la configuration Nexus dans le fichier pom.xml :
<img width="1163" height="91" alt="Capture d&#39;écran 2026-05-15 115835" src="https://github.com/user-attachments/assets/1eb8fab9-8e2e-4443-91c0-2d7d0a4c8f7c" />

```bash
<distributionManagement>
    <snapshotRepository>
        <id>nexus</id>
        <url>
            http://localhost:8081/repository/maven-project-repo/
        </url>
    </snapshotRepository>
</distributionManagement>
```
Cette configuration permet à Maven d’envoyer automatiquement les artifacts vers Nexus.

# 6. Configuration du fichier settings.xml

Modification du fichier :
<img width="1577" height="562" alt="Capture d’écran 2026-05-15 235053" src="https://github.com/user-attachments/assets/b74557b0-34aa-4865-90bc-d63e69b83bd3" />

```bash
~/.m2/settings.xml
```

Ajout des credentials Nexus :
```
<settings>
  <servers>
    <server>
      <id>nexus</id>
      <username>admin</username>
      <password>********</password>
    </server>
  </servers>
</settings>
```
# 7. Déploiement de l’artifact dans Nexus

Commande utilisée :

```bash
mvn clean deploy
```
<img width="1575" height="581" alt="Capture d’écran 2026-05-15 235732" src="https://github.com/user-attachments/assets/b217e55a-d63d-4b62-8e43-93f7da84b0e3" />

Résultat :

**BUILD SUCCESS**
<img width="1584" height="339" alt="Capture d&#39;écran 2026-05-15 235849" src="https://github.com/user-attachments/assets/c899de2d-e624-4562-bb32-8b9f9f2dd149" />

L’artifact a été envoyé avec succès vers le repository Nexus.


# 8. Vérification dans Nexus

Dans la section :
```bash
Browse → maven-project-repo
```
J’ai retrouvé les fichiers suivants :

.jar
.pom
maven-metadata.xml
.sha1
.md5

Cela confirme le bon fonctionnement du déploiement Maven vers Nexus.

### Capture — Artifact dans Nexus
<img width="975" height="592" alt="Capture d&#39;écran 2026-05-15 152050" src="https://github.com/user-attachments/assets/e3eee7c4-c3cd-46a2-ac0a-12055b78a066" />

# 9. Test manuel du Hosted Repository

En plus du déploiement Maven automatique, j’ai également testé l’upload manuel d’un fichier dans Nexus.

Pour cela, j’ai utilisé le repository :

```text
maven-project-repos
```

Puis j’ai utilisé l’option :

```text
Upload
```

dans l’interface Nexus afin d’ajouter manuellement un fichier artifact.

Cette méthode permet d’ajouter des artifacts sans utiliser Maven.

---

### Capture — Upload manuel dans Nexus

<img width="1570" height="783" alt="Capture d’écran 2026-05-16 005016" src="https://github.com/user-attachments/assets/5fe49364-b3d4-4730-9ae6-38ad7f0c9796" />

---

# 10. Test du Proxy Repository

J’ai aussi exploré le fonctionnement des repositories de type Proxy.
<img width="1589" height="405" alt="Capture d&#39;écran 2026-05-16 005533" src="https://github.com/user-attachments/assets/fdd16346-8582-4ea3-817a-d09e38a5dd15" />
Un Proxy Repository permet à Nexus de récupérer et mettre en cache les dépendances provenant d’un repository distant comme Maven Central.

Exemple :

```text
maven-central-proxy
```

Avantages :

- Réduction du temps de téléchargement
- Mise en cache locale
- Accès centralisé aux dépendances
- Amélioration des performances CI/CD

---

### Capture — Proxy Repository

<img width="1341" height="820" alt="Capture d&#39;écran 2026-05-16 010932" src="https://github.com/user-attachments/assets/9d26f906-2148-4cff-9d6b-a6cf3adeb5e2" />


---

# 11. Test du Group Repository

Enfin, j’ai testé les Group Repositories.
<img width="1363" height="408" alt="Capture d&#39;écran 2026-05-16 011406" src="https://github.com/user-attachments/assets/8fdb48b0-2554-4c02-852d-8c79fc1ecd30" />


Un Group Repository permet de regrouper plusieurs repositories dans une seule URL.

Exemple :

- Hosted repositories
- Proxy repositories
- Snapshot repositories

Cela simplifie l’utilisation des repositories dans Maven.

Exemple de group repository :

```text
maven-public-group
```

Le développeur peut utiliser une seule URL Maven au lieu de plusieurs repositories différents.

---

### Capture — Group Repository

<img width="1348" height="824" alt="Capture d&#39;écran 2026-05-16 011741" src="https://github.com/user-attachments/assets/6d959d30-30b1-4725-8375-db4d2cb7c4d3" />


---

# 📚 Types de repositories étudiés

| Type | Rôle |
|---|---|
| Hosted | Stockage des artifacts internes |
| Proxy | Cache des repositories distants |
| Group | Regroupement de plusieurs repositories |

<img width="1358" height="443" alt="Capture d&#39;écran 2026-05-16 012029" src="https://github.com/user-attachments/assets/4ef9fce6-30f6-4674-af1e-7bd9abec3e5e" />

---

# ✅ Conclusion générale

Ce laboratoire m’a permis de comprendre le rôle de Nexus dans une chaîne CI/CD moderne.

J’ai appris à :

- Installer Nexus avec Docker
- Utiliser les volumes Docker pour la persistance
- Créer des repositories Maven
- Déployer des artifacts avec Maven
- Utiliser les repositories Hosted, Proxy et Group
- Comprendre la gestion des versions SNAPSHOT
- Centraliser et gérer les artifacts d’une application

Cette expérience m’a permis de mieux comprendre la gestion des artifacts dans les environnements DevOps et CI/CD.


