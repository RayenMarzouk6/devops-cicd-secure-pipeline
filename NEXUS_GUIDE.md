
# Nexus Repository Manager — Technical Overview & Deep Dive

> **Filename:** `NEXUS_OVERVIEW.md`  
> **Role in the repo:** Reference document explaining *what* Nexus Repository Manager is, how it works internally, and how it integrates into a CI/CD pipeline — complementary to `LAB_NEXUS.md` which covers the hands-on steps.

---

## Table of Contents

1. [Origins & History (2008 → Today)](#1-origins--history)
2. [What is Nexus Repository Manager?](#2-what-is-nexus-repository-manager)
3. [Architecture Overview](#3-architecture-overview)
4. [Repository Types: Hosted, Proxy, Group](#4-repository-types-hosted-proxy-group)
5. [Supported Package Formats](#5-supported-package-formats)
6. [Artifact Lifecycle & Versioning](#6-artifact-lifecycle--versioning)
7. [SNAPSHOT vs RELEASE](#7-snapshot-vs-release)
8. [Storage & Blob Stores](#8-storage--blob-stores)
9. [Security: Roles, Privileges & Realms](#9-security-roles-privileges--realms)
10. [Search & Metadata](#10-search--metadata)
11. [CI/CD Integration Flow](#11-cicd-integration-flow)
12. [Docker Compose Example](#12-docker-compose-example)
13. [Jenkinsfile Example](#13-jenkinsfile-example)
14. [Nexus vs Alternatives](#14-nexus-vs-alternatives)
15. [Summary](#15-summary)

---

## 1. Origins & History

```
2004 ──────────────────────────────────────────────────────────────► 2024
  │         │         │           │          │          │          │
Maven     Maven     Nexus 1.x   Nexus 2.x  Nexus 3.x  Docker    Nexus 3.x
Central   began     (Sonatype)  (stable,   (ground-up  registry  Universal
created   (artifact  released   most used)  rewrite)   support   format
          problem   as OSS                  REST API            support
          becomes                           NXRM3
          clear)
```

| Year | Milestone |
|------|-----------|
| **2004** | Maven Central created — first public artifact repository for Java. But enterprises needed a **private** equivalent behind their firewall. |
| **2007** | **Sonatype** founded by Jason van Zyl (Maven co-creator). The mission: solve the artifact management problem for enterprises. |
| **2008** | **Nexus Repository Manager 1.x** released as open-source. First dedicated repository manager for Maven artifacts. |
| **2012** | **Nexus 2.x** — stable, widely adopted version. Becomes the industry standard for Java/Maven artifact management. |
| **2016** | **Nexus Repository Manager 3.x (NXRM3)** — complete ground-up rewrite. New architecture, REST API, multi-format support (npm, Docker, PyPI, NuGet…). |
| **2018** | Docker registry support added — Nexus becomes a universal artifact hub, not just for Java. |
| **2020** | **Nexus Repository Pro** — HA clustering, advanced security, audit logs for large enterprises. |
| **2022** | **Universal format support** expanded — Conda, Helm Charts, R packages, Conan (C/C++). |
| **2024** | **Nexus Repository 3.x latest** — improved UI, SBOM (Software Bill of Materials) support, enhanced security scanning integration. |

> **The problem that never changed:** teams needed a **private, centralized, controlled store** for their build artifacts — both to cache external dependencies and to publish their own.

---

## 2. What is Nexus Repository Manager?

Nexus Repository Manager is a **universal artifact repository** — a server that stores, organizes, and serves build artifacts for software development teams.

```
┌──────────────────────────────────────────────────────────────────┐
│              Without Nexus                                        │
│                                                                  │
│  Dev A ──────────────────────────────────► Maven Central        │
│  Dev B ──────────────────────────────────► Maven Central        │
│  Dev C ──────────────────────────────────► Maven Central        │
│  CI Server ──────────────────────────────► Maven Central        │
│                                                                  │
│  Problems: slow, bandwidth waste, no control, internet required  │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│              With Nexus                                          │
│                                                                  │
│  Dev A ──────┐                                                   │
│  Dev B ──────┤                          ┌──► Maven Central       │
│  Dev C ──────┼──► NEXUS ───────────────►│    (only when needed) │
│  CI Server ──┘   (cache + private repo) └──► npm Registry       │
│                                                                  │
│  Benefits: fast, controlled, offline-capable, auditable          │
└──────────────────────────────────────────────────────────────────┘
```

**What Nexus stores:**

| Category | Examples |
|----------|---------|
| Internal artifacts | Your team's compiled JARs, WARs, Docker images |
| Cached external deps | Spring Boot, JUnit, React — fetched once, served forever |
| Release artifacts | Stable versions ready for deployment |
| Snapshot artifacts | In-development versions for testing |
| Metadata | checksums (SHA1, MD5), POM files, index files |

---

## 3. Architecture Overview

```
┌───────────────────────────────────────────────────────────────────────┐
│                      Nexus Repository Manager                          │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐ │
│  │                        Web UI (port 8081)                        │ │
│  │              Browse · Upload · Search · Admin                    │ │
│  └──────────────────────────────┬───────────────────────────────────┘ │
│                                 │                                      │
│  ┌──────────────────────────────▼───────────────────────────────────┐ │
│  │                         REST API                                  │ │
│  │         /service/rest/v1/...  (search, upload, delete...)         │ │
│  └──────────┬──────────────────────────────────────────────────────┘ │
│             │                                                          │
│  ┌──────────▼───────────────────────────────────────────────────────┐ │
│  │                    Repository Engine                              │ │
│  │                                                                   │ │
│  │  ┌────────────┐   ┌────────────┐   ┌────────────────────────┐   │ │
│  │  │   Hosted   │   │   Proxy    │   │        Group           │   │ │
│  │  │ Repository │   │ Repository │   │      Repository        │   │ │
│  │  │            │   │            │   │  (Hosted + Proxy)      │   │ │
│  │  └────────────┘   └────────────┘   └────────────────────────┘   │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                 │                                      │
│  ┌──────────────────────────────▼───────────────────────────────────┐ │
│  │                      Blob Store (Storage)                         │ │
│  │              File System  /  AWS S3  /  Azure Blob               │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                                 │                                      │
│  ┌──────────────────────────────▼───────────────────────────────────┐ │
│  │                   Embedded Database (OrientDB)                    │ │
│  │          Stores metadata: names, versions, permissions            │ │
│  └──────────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────────┘
```

**Key components:**

| Component | Role |
|-----------|------|
| **Web UI** | Browser-based interface for browsing, uploading, administration |
| **REST API** | Programmatic access — used by CI/CD pipelines, scripts |
| **Repository Engine** | Manages all repository types and their routing rules |
| **Blob Store** | Physical storage backend where artifact files live on disk (or cloud) |
| **Embedded Database** | OrientDB stores metadata, component index, security config |

---

## 4. Repository Types: Hosted, Proxy, Group

This is the core concept of Nexus. Every repository is one of three types:

```
┌──────────────────────────────────────────────────────────────────────┐
│                       THREE REPOSITORY TYPES                          │
│                                                                        │
│  ┌──────────────────────┐                                             │
│  │       HOSTED         │  ← Your team uploads artifacts HERE         │
│  │                      │                                             │
│  │  Internal artifacts  │  Examples:                                  │
│  │  you OWN and PUBLISH │  - maven-releases                           │
│  │                      │  - maven-snapshots                          │
│  │  READ + WRITE        │  - docker-hosted                            │
│  └──────────────────────┘                                             │
│                                                                        │
│  ┌──────────────────────┐                                             │
│  │        PROXY         │  ← Nexus fetches & caches FROM the internet │
│  │                      │                                             │
│  │  Cache of a remote   │  Examples:                                  │
│  │  repository          │  - maven-central (→ repo1.maven.org)        │
│  │                      │  - npm-proxy (→ registry.npmjs.org)         │
│  │  READ ONLY           │  - docker-hub-proxy (→ hub.docker.com)      │
│  └──────────────────────┘                                             │
│                                                                        │
│  ┌──────────────────────┐                                             │
│  │        GROUP         │  ← Combines Hosted + Proxy under ONE URL    │
│  │                      │                                             │
│  │  Virtual aggregator  │  Examples:                                  │
│  │  — developers use    │  - maven-public                             │
│  │    ONE URL only      │  - npm-group                                │
│  │                      │                                             │
│  │  READ ONLY           │  Resolution order: Hosted first, then Proxy │
│  └──────────────────────┘                                             │
└──────────────────────────────────────────────────────────────────────┘
```

### How a dependency resolution works through a Group

```
Developer runs: mvn install
       │
       ▼
Maven contacts Group URL:
http://nexus:8081/repository/maven-public/
       │
       ▼
┌──────────────────────────────────────┐
│           GROUP: maven-public         │
│                                       │
│  1. Check HOSTED (maven-releases)    │─── Found? ──► Return it ✅
│  2. Check HOSTED (maven-snapshots)   │
│  3. Check PROXY  (maven-central)     │─── Not in cache?
│                                       │         │
└──────────────────────────────────────┘         ▼
                                        Fetch from Maven Central
                                        Store in Proxy cache
                                        Return to developer ✅
                                        (next request = instant cache hit)
```

---

## 5. Supported Package Formats

Nexus 3.x is **not just for Java**. It supports virtually every major package ecosystem:

```
┌──────────────────────────────────────────────────────────────────┐
│              Nexus 3.x — Universal Format Support                 │
│                                                                   │
│   Java / JVM        Frontend          DevOps & Cloud             │
│  ┌──────────────┐  ┌─────────────┐  ┌───────────────────────┐   │
│  │  Maven 2     │  │     npm     │  │  Docker / OCI images  │   │
│  │  (JAR/WAR/   │  │  (Node.js)  │  │  Helm Charts          │   │
│  │   EAR/POM)   │  │             │  │  (Kubernetes)         │   │
│  └──────────────┘  └─────────────┘  └───────────────────────┘   │
│                                                                   │
│   Python            .NET             Other                        │
│  ┌──────────────┐  ┌─────────────┐  ┌───────────────────────┐   │
│  │  PyPI        │  │   NuGet     │  │  Raw (any file)       │   │
│  │  (pip)       │  │             │  │  Conda (data science) │   │
│  │  Conda       │  │             │  │  Conan (C/C++)        │   │
│  └──────────────┘  └─────────────┘  │  R / CRAN             │   │
│                                      │  RubyGems             │   │
│                                      │  Yum / APT (Linux)    │   │
│                                      └───────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

> **One Nexus instance** can serve all your teams regardless of their technology stack — Java backend, Python ML, React frontend, Docker images — all in one place.

---

## 6. Artifact Lifecycle & Versioning

Every artifact published to Nexus has a set of **coordinates** that uniquely identify it:

### Maven Coordinates (GAV)

```
groupId : artifactId : version
   │            │          │
   │            │          └── 1.0.0-SNAPSHOT  or  1.0.0
   │            └─────────────── my-spring-app
   └──────────────────────────── com.example

Full path in Nexus storage:
  com/example/my-spring-app/1.0.0-SNAPSHOT/
    ├── my-spring-app-1.0.0-20240315.143022-1.jar   ← timestamped
    ├── my-spring-app-1.0.0-20240315.143022-1.pom
    ├── my-spring-app-1.0.0-20240315.143022-1.jar.sha1
    └── maven-metadata.xml                           ← tracks all snapshots
```

### Files stored alongside every artifact

| File | Purpose |
|------|---------|
| `.jar` / `.war` / `.ear` | The actual compiled artifact |
| `.pom` | Project Object Model — dependency descriptor |
| `maven-metadata.xml` | Tracks all available versions / snapshot timestamps |
| `.sha1` | SHA-1 checksum for integrity verification |
| `.md5` | MD5 checksum (legacy, still generated) |

---

## 7. SNAPSHOT vs RELEASE

This is one of the most important distinctions in Maven/Nexus artifact management:

```
┌──────────────────────────────────┬───────────────────────────────────┐
│           SNAPSHOT               │            RELEASE                 │
│       (development version)      │         (stable version)           │
├──────────────────────────────────┼───────────────────────────────────┤
│  Version: 1.0.0-SNAPSHOT         │  Version: 1.0.0                   │
│                                  │                                    │
│  ✔ Can be overwritten/redeployed │  ✔ Immutable — cannot be changed  │
│  ✔ Timestamped on each deploy    │  ✔ Permanent record                │
│  ✔ Maven re-downloads daily      │  ✔ Maven caches forever            │
│  ✔ Used during development       │  ✔ Used for production deployment  │
│  ✗ Not suitable for production   │  ✗ Cannot redeploy same version    │
│                                  │                                    │
│  Stored in: maven-snapshots      │  Stored in: maven-releases         │
│  Policy: Allow Redeploy          │  Policy: Disable Redeploy          │
└──────────────────────────────────┴───────────────────────────────────┘

  Development Flow:
  ─────────────────
  Feature coding  ──►  1.0.0-SNAPSHOT deploys  ──►  many redeploys
        │
        ▼
  Release ready   ──►  mvn release:prepare      ──►  1.0.0 (immutable)
        │
        ▼
  Next iteration  ──►  1.1.0-SNAPSHOT begins
```

**Deployment policies in Nexus:**

| Policy | Behavior |
|--------|----------|
| **Allow Redeploy** | Same version can be overwritten (for SNAPSHOT repos) |
| **Disable Redeploy** | Same version cannot be overwritten (for RELEASE repos) |
| **Read-Only** | No uploads allowed at all |

---

## 8. Storage & Blob Stores

Nexus separates **metadata** (database) from **file content** (Blob Store). The Blob Store is where the actual artifact bytes live.

```
┌─────────────────────────────────────────────────────────────────┐
│                        Blob Store Types                          │
│                                                                  │
│  ┌─────────────────────┐       ┌────────────────────────────┐   │
│  │   File System       │       │      Cloud Storage         │   │
│  │   (default)         │       │      (Pro edition)         │   │
│  │                     │       │                            │   │
│  │  /nexus-data/       │       │  AWS S3 Bucket             │   │
│  │  blobs/default/     │       │  Azure Blob Storage        │   │
│  │                     │       │  Google Cloud Storage      │   │
│  │  ✔ Simple setup     │       │  ✔ Unlimited scale         │   │
│  │  ✔ Fast local I/O   │       │  ✔ Cloud-native HA         │   │
│  │  ✗ Limited by disk  │       │  ✗ Needs Pro license       │   │
│  └─────────────────────┘       └────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

**Blob Store internals (file system):**

```
/nexus-data/blobs/default/
  ├── content/
  │     ├── vol-01/
  │     │     ├── chap-01/
  │     │     │     ├── a1b2c3d4-...bytes   ← actual artifact binary
  │     │     │     └── a1b2c3d4-...properties ← metadata sidecar
  │     │     └── ...
  │     └── ...
  └── metadata/
        └── ...
```

> **Key insight:** even if you delete a component from the Nexus UI, the bytes remain in the Blob Store until a **"Compact Blob Store"** admin task runs. This prevents accidental permanent deletion.

---

## 9. Security: Roles, Privileges & Realms

Nexus has a layered security model:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Security Model                                │
│                                                                  │
│  USER ──► assigned to ──► ROLE ──► contains ──► PRIVILEGES      │
│                                                                  │
│  Example:                                                        │
│  ┌──────────┐    ┌──────────────────┐    ┌──────────────────┐   │
│  │  jenkins │───►│ nx-deploy-role   │───►│ nx-repository-   │   │
│  │  (user)  │    │                  │    │ view-maven-*-*   │   │
│  └──────────┘    └──────────────────┘    │ nx-repository-   │   │
│                                          │ add-maven-*      │   │
│                                          └──────────────────┘   │
│                                                                  │
│  ┌──────────┐    ┌──────────────────┐    ┌──────────────────┐   │
│  │ developer│───►│ nx-readonly-role │───►│ nx-repository-   │   │
│  │  (user)  │    │                  │    │ view-maven-*-*   │   │
│  └──────────┘    └──────────────────┘    └──────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

**Built-in roles:**

| Role | Access |
|------|--------|
| `nx-admin` | Full system access |
| `nx-anonymous` | Read-only, public repositories only |
| Custom roles | Granular per-repository, per-format permissions |

**Realms (authentication sources):**

| Realm | Purpose |
|-------|---------|
| **Local** | Built-in Nexus user database (default) |
| **LDAP** | Authenticate against company Active Directory / LDAP |
| **SAML** | SSO via SAML providers (Okta, Azure AD) — Pro edition |
| **Docker Token** | Required to enable Docker `docker login` against Nexus |

---

## 10. Search & Metadata

Nexus provides powerful search across all stored components:

```
Search dimensions:
  ┌──────────────┬────────────────────────────────────────┐
  │ By GAV       │ groupId + artifactId + version         │
  │ By keyword   │ full-text across component names       │
  │ By checksum  │ SHA-1 or MD5 — find exact artifact     │
  │ By format    │ filter to maven2, npm, docker...       │
  │ By tag       │ custom labels added to components      │
  └──────────────┴────────────────────────────────────────┘
```

**Metadata files Nexus generates for Maven:**

```xml
<!-- maven-metadata.xml — tells Maven what versions exist -->
<metadata>
  <groupId>com.example</groupId>
  <artifactId>my-spring-app</artifactId>
  <versioning>
    <release>1.2.0</release>
    <versions>
      <version>1.0.0</version>
      <version>1.1.0</version>
      <version>1.2.0</version>
    </versions>
    <lastUpdated>20240315143022</lastUpdated>
  </versioning>
</metadata>
```

---

## 11. CI/CD Integration Flow

Nexus plays **two distinct roles** in a CI/CD pipeline simultaneously:

```
┌──────────────────────────────────────────────────────────────────────┐
│                   Nexus in the CI/CD Pipeline                         │
│                                                                        │
│                                                                        │
│  ① DEPENDENCY PULL (Proxy role)                                       │
│  ──────────────────────────────                                       │
│  CI Server                                                             │
│    │                                                                   │
│    │── mvn clean package ──► Maven needs spring-boot-starter           │
│    │                              │                                    │
│    │                              ▼                                    │
│    │                        NEXUS PROXY ──► Maven Central (1st time)   │
│    │                        (cache hit on subsequent builds)           │
│    │                                                                   │
│  ② ARTIFACT PUSH (Hosted role)                                        │
│  ─────────────────────────────                                        │
│    │                                                                   │
│    │── mvn deploy ──────────► NEXUS HOSTED ── my-app-1.0.0.jar        │
│    │   (after tests pass)     (maven-releases or maven-snapshots)      │
│    │                                                                   │
│  ③ ARTIFACT PULL (deployment phase)                                   │
│  ──────────────────────────────────                                   │
│    │                                                                   │
│    │── Ansible / Kubernetes ──► pulls my-app-1.0.0.jar from NEXUS     │
│         deploys to server        (controlled, versioned source)        │
│                                                                        │
└──────────────────────────────────────────────────────────────────────┘
```

**The complete artifact journey:**

```
Source Code
    │
    ▼ git push
CI triggered
    │
    ▼ mvn clean package
  .jar built (target/)
    │
    ▼ mvn test
  Tests pass ✅
    │
    ▼ mvn deploy
  .jar ──────────────────────────────────► Nexus (maven-snapshots)
  .pom                                         │
  .sha1                                        │ version controlled
                                               │ auditable
  (later, release)                             │
    │                                          ▼
    ▼ mvn release:perform              Deployment server
  1.0.0.jar ──────────────────────────► pulls from Nexus
                                        (not from dev machine!)
```

---

## 12. Docker Compose Example

A production-ready Nexus stack with persistent volume:

```yaml
# docker-compose.yml
version: "3.8"

services:

  # ──────────────────────────────────────────
  # Nexus Repository Manager
  # ──────────────────────────────────────────
  nexus:
    image: sonatype/nexus3:latest
    container_name: nexus
    ports:
      - "8081:8081"       # Nexus Web UI + Maven repositories
      - "8082:8082"       # Docker hosted registry (optional)
      - "8083:8083"       # Docker proxy registry (optional)
    volumes:
      - nexus-data:/nexus-data    # persists ALL data: artifacts, config, DB
    environment:
      # Increase JVM heap for production use
      INSTALL4J_ADD_VM_PARAMS: >-
        -Xms2g
        -Xmx2g
        -XX:MaxDirectMemorySize=3g
        -Djava.util.prefs.userRoot=/nexus-data/javaprefs
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/service/rest/v1/status"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 120s    # Nexus takes ~2 min to start

volumes:
  nexus-data:
    driver: local

# ─────────────────────────────────────────────────────────────────
# First-run: get the auto-generated admin password
#   docker exec nexus cat /nexus-data/admin.password
#
# Access: http://localhost:8081
# Default credentials: admin / (password from above)
# ─────────────────────────────────────────────────────────────────
```

**Combined CI/CD stack — Nexus + SonarQube + Jenkins:**

```yaml
# docker-compose.cicd.yml
version: "3.8"

services:

  nexus:
    image: sonatype/nexus3:latest
    container_name: nexus
    ports:
      - "8081:8081"
    volumes:
      - nexus-data:/nexus-data
    networks:
      - cicd-network
    restart: unless-stopped

  sonarqube-db:
    image: postgres:15
    container_name: sonarqube-db
    environment:
      POSTGRES_DB: sonarqube
      POSTGRES_USER: sonarqube
      POSTGRES_PASSWORD: sonarpass
    volumes:
      - sonar-db-data:/var/lib/postgresql/data
    networks:
      - cicd-network
    restart: unless-stopped

  sonarqube:
    image: sonarqube:10-community
    container_name: sonarqube
    depends_on:
      - sonarqube-db
    environment:
      SONAR_JDBC_URL: jdbc:postgresql://sonarqube-db:5432/sonarqube
      SONAR_JDBC_USERNAME: sonarqube
      SONAR_JDBC_PASSWORD: sonarpass
    ports:
      - "9000:9000"
    volumes:
      - sonar-data:/opt/sonarqube/data
      - sonar-logs:/opt/sonarqube/logs
    networks:
      - cicd-network
    restart: unless-stopped

  jenkins:
    image: jenkins/jenkins:lts
    container_name: jenkins
    ports:
      - "8080:8080"
    volumes:
      - jenkins-data:/var/jenkins_home
    networks:
      - cicd-network
    restart: unless-stopped

volumes:
  nexus-data:
  sonar-db-data:
  sonar-data:
  sonar-logs:
  jenkins-data:

networks:
  cicd-network:
    driver: bridge
```

---

## 13. Jenkinsfile Example

A complete pipeline that uses Nexus for both dependency resolution and artifact deployment:

```groovy
// Jenkinsfile — Maven project with Nexus integration

pipeline {
    agent any

    tools {
        maven 'Maven-3.9'
        jdk   'JDK-17'
    }

    environment {
        // Nexus connection settings
        NEXUS_URL      = 'http://nexus:8081'
        NEXUS_REPO     = 'maven-snapshots'
        NEXUS_CREDS_ID = 'nexus-credentials'   // Jenkins credential ID
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                echo "Building branch: ${env.BRANCH_NAME}"
            }
        }

        // ─── Stage 1: Build ──────────────────────────────────────────────
        // Maven pulls dependencies FROM Nexus proxy (not directly from internet)
        // This works because settings.xml mirrors point to Nexus Group URL
        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        // ─── Stage 2: Test ───────────────────────────────────────────────
        stage('Unit Tests') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                    jacoco(
                        execPattern: '**/target/jacoco.exec',
                        classPattern: '**/target/classes',
                        sourcePattern: '**/src/main/java'
                    )
                }
            }
        }

        // ─── Stage 3: Code Quality ───────────────────────────────────────
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube-Server') {
                    sh 'mvn sonar:sonar'
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ─── Stage 4: Publish to Nexus ───────────────────────────────────
        // Only if tests pass AND quality gate passes
        stage('Publish to Nexus') {
            steps {
                // Uses credentials configured in Jenkins + pom.xml distributionManagement
                withCredentials([usernamePassword(
                    credentialsId: "${NEXUS_CREDS_ID}",
                    usernameVariable: 'NEXUS_USER',
                    passwordVariable: 'NEXUS_PASS'
                )]) {
                    sh """
                        mvn deploy \
                          -DskipTests \
                          -Dusername=${NEXUS_USER} \
                          -Dpassword=${NEXUS_PASS}
                    """
                }
                echo "✅ Artifact published to Nexus: ${NEXUS_URL}/repository/${NEXUS_REPO}/"
            }
        }

        // ─── Stage 5: Deploy ─────────────────────────────────────────────
        // Deployment server pulls the artifact FROM Nexus (not from Jenkins)
        stage('Deploy to Staging') {
            when {
                branch 'main'
            }
            steps {
                sh """
                    # Pull the versioned artifact from Nexus and deploy
                    curl -u ${NEXUS_USER}:${NEXUS_PASS} \
                         -O ${NEXUS_URL}/repository/${NEXUS_REPO}/com/example/my-app/1.0.0-SNAPSHOT/my-app-1.0.0-SNAPSHOT.jar

                    # Deploy (example with scp or ansible)
                    scp my-app-1.0.0-SNAPSHOT.jar deploy@staging-server:/opt/app/
                """
            }
        }
    }

    post {
        success {
            echo "Pipeline completed — artifact available in Nexus."
        }
        failure {
            echo "Pipeline failed — artifact NOT published to Nexus."
        }
    }
}
```

**Maven `settings.xml` to route all downloads through Nexus:**

```xml
<!-- ~/.m2/settings.xml -->
<settings>
  <mirrors>
    <!-- Redirect ALL Maven Central requests to Nexus Group -->
    <mirror>
      <id>nexus-mirror</id>
      <mirrorOf>*</mirrorOf>
      <url>http://nexus:8081/repository/maven-public/</url>
    </mirror>
  </mirrors>

  <servers>
    <!-- Credentials for publishing artifacts -->
    <server>
      <id>nexus-snapshots</id>
      <username>admin</username>
      <password>your_password</password>
    </server>
    <server>
      <id>nexus-releases</id>
      <username>admin</username>
      <password>your_password</password>
    </server>
  </servers>
</settings>
```

**Maven `pom.xml` distribution configuration:**

```xml
<!-- pom.xml -->
<distributionManagement>

  <!-- SNAPSHOT versions go here -->
  <snapshotRepository>
    <id>nexus-snapshots</id>
    <url>http://nexus:8081/repository/maven-snapshots/</url>
  </snapshotRepository>

  <!-- RELEASE versions go here -->
  <repository>
    <id>nexus-releases</id>
    <url>http://nexus:8081/repository/maven-releases/</url>
  </repository>

</distributionManagement>
```

---

## 14. Nexus vs Alternatives

```
┌──────────────────┬──────────────────┬──────────────────┬──────────────────┐
│   Nexus 3.x      │   JFrog          │   GitHub         │   GitLab         │
│   (Sonatype)     │   Artifactory    │   Packages       │   Package        │
│                  │                  │                  │   Registry       │
├──────────────────┼──────────────────┼──────────────────┼──────────────────┤
│ ✔ Free Community │ ✔ Very mature    │ ✔ Integrated     │ ✔ Built into     │
│   edition        │ ✔ Best UI/search │   with GitHub    │   GitLab CI      │
│ ✔ Universal      │ ✔ Universal      │ ✔ Free for       │ ✔ Free tier      │
│   formats        │   formats        │   public repos   │                  │
│ ✔ Self-hosted    │ ✔ Cloud + self   │ ✔ Cloud          │ ✔ Self-hosted    │
│ ✔ Large          │   hosted         │ ✗ Limited        │   or cloud       │
│   community      │ ✗ Expensive Pro  │   format support │ ✗ Less mature    │
│ ✗ UI less        │ ✗ Complex setup  │ ✗ Not a full     │   than Nexus/    │
│   polished than  │                  │   artifact hub   │   Artifactory    │
│   Artifactory    │                  │                  │                  │
└──────────────────┴──────────────────┴──────────────────┴──────────────────┘

For the student/enterprise on-premise context → Nexus Community is the 
standard choice: free, self-hosted, supports all formats, and is the 
most widely taught in DevOps/CI/CD curricula.
```

---

## 15. Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Nexus Repository Manager at a Glance              │
│                                                                      │
│  WHAT:    Universal, self-hosted artifact repository manager         │
│  WHEN:    At every build (pull deps) + at deploy (push artifact)     │
│  HOW:     Three repo types: Hosted, Proxy, Group                    │
│  OUTPUT:  Versioned, checksummed, auditable artifact storage         │
│                                                                      │
│  Key concepts:                                                       │
│  ┌──────────────────────┬──────────────────────────────────────┐    │
│  │ Hosted Repository    │ Stores YOUR artifacts                 │    │
│  │ Proxy Repository     │ Caches external deps (Maven Central) │    │
│  │ Group Repository     │ One URL to rule them all              │    │
│  │ SNAPSHOT             │ Mutable, in-development version       │    │
│  │ RELEASE              │ Immutable, production-ready version   │    │
│  │ Blob Store           │ Physical artifact file storage        │    │
│  │ GAV Coordinates      │ groupId:artifactId:version identity   │    │
│  │ Deployment Policy    │ Allow / Disable redeploy per repo     │    │
│  └──────────────────────┴──────────────────────────────────────┘    │
│                                                                      │
│  Editions: OSS Community (free) → Pro → Enterprise (HA clustering)  │
└─────────────────────────────────────────────────────────────────────┘
```

---

*Document part of the CI/CD Security Chain project — M1 CCDAD.*  
*See also: `LAB_NEXUS.md` (hands-on steps), `LAB_SONARQUBE.md`, `SONARQUBE_OVERVIEW.md`, `README.md`*
