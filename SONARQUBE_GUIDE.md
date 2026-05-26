# SonarQube — Technical Overview & Deep Dive

> **Suggested filename:** `SONARQUBE_OVERVIEW.md`  
> **Role in the repo:** Reference document explaining *what* SonarQube is, how it works internally, and how it integrates into a CI/CD pipeline — complementary to `LAB_SONARQUBE.md` which covers the hands-on steps.

---

## Table of Contents

1. [Origins & History (2008 → Today)](#1-origins--history)
2. [What is SonarQube?](#2-what-is-sonarqube)
3. [Architecture Overview](#3-architecture-overview)
4. [Static Analysis vs Runtime Analysis](#4-static-analysis-vs-runtime-analysis)
5. [Scanner Types](#5-scanner-types)
6. [Rule Categories](#6-rule-categories)
7. [Quality Gate](#7-quality-gate)
8. [Clean as You Code](#8-clean-as-you-code)
9. [Technical Debt & Maintainability Rating](#9-technical-debt--maintainability-rating)
10. [Issue Lifecycle](#10-issue-lifecycle)
11. [Database: H2 → PostgreSQL](#11-database-h2--postgresql)
12. [CI/CD Integration](#12-cicd-integration)
13. [Docker Compose Example](#13-docker-compose-example)
14. [Jenkinsfile Example](#14-jenkinsfile-example)
15. [Summary](#15-summary)

---

## 1. Origins & History

```
2008 ──────────────────────────────────────────────────────────► 2024
  │         │           │            │           │           │
Born as   First      SonarLint    Branch     Security    SonarQube
"Sonar"   OSS        plugin for   Analysis   Hotspots    10.x
by SIG    Community  IDEs         (PRs)      introduced  (LTS)
```

| Year | Milestone |
|------|-----------|
| **2008** | Project started as **"Sonar"** by SonarSource (Olivier Gaudin, Freddy Mallet). The core idea: bring code quality measurement out of niche tools and into developer workflows, visible to everyone via a web dashboard. |
| **2010** | Renamed **SonarQube**. Plugin ecosystem grows (Java, C#, JavaScript, Python…). |
| **2014** | **SonarLint** introduced — real-time feedback inside the IDE (IntelliJ, Eclipse, VS Code). |
| **2016** | **Branch Analysis** added in Developer Edition — scan feature branches and Pull Requests before merging. |
| **2019** | **Security Hotspots** introduced — separate category for security-sensitive code that needs human review. |
| **2021** | **Clean as You Code** methodology officially formalized — shift focus to *new* code quality. |
| **2022** | SonarQube **9.x LTS** — improved language coverage, faster scanning engine. |
| **2024** | SonarQube **10.x** — AI-assisted issue explanation, SBOM support, enhanced SAST for cloud-native apps. |

> **The core idea that hasn't changed:** give teams a single, continuous, automated view of code quality so problems are caught early — not after deployment.

---

## 2. What is SonarQube?

SonarQube is a **self-hosted platform for continuous code quality inspection**. It performs **Static Application Security Testing (SAST)** and code quality analysis by examining source code *without executing it*.

```
┌─────────────────────────────────────────────────────────────────┐
│                        SonarQube Platform                        │
│                                                                  │
│   ┌─────────────┐    ┌──────────────┐    ┌──────────────────┐   │
│   │   Scanner   │───►│  Compute     │───►│   Web Server     │   │
│   │ (your code) │    │  Engine      │    │   (Dashboard)    │   │
│   └─────────────┘    └──────────────┘    └──────────────────┘   │
│                              │                                    │
│                       ┌──────▼──────┐                            │
│                       │  Database   │                            │
│                       │(PostgreSQL) │                            │
│                       └─────────────┘                            │
└─────────────────────────────────────────────────────────────────┘
```

**What it detects:**
- Bugs (logic errors, null pointer risks)
- Code Smells (maintainability issues)
- Security Vulnerabilities (OWASP Top 10, CWE)
- Security Hotspots (sensitive areas needing review)
- Duplicated code blocks
- Code coverage gaps
- Technical Debt estimation

---

## 3. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Developer Workstation / CI Server             │
│                                                                        │
│   Source Code ──► SonarScanner ──► Analysis Report (.json)            │
│                        │                                               │
│                        │  HTTP POST to SonarQube Server               │
└────────────────────────┼───────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────────────┐
│                         SonarQube Server                              │
│                                                                        │
│  ┌──────────────────┐   ┌───────────────────┐   ┌─────────────────┐  │
│  │   Web Server     │   │  Compute Engine    │   │  Elasticsearch  │  │
│  │  (UI + REST API) │   │ (Rule processing   │   │  (Search index) │  │
│  │  Port: 9000      │   │  + issue creation) │   │                 │  │
│  └──────────────────┘   └───────────────────┘   └─────────────────┘  │
│            │                      │                                    │
│            └──────────┬───────────┘                                    │
│                       ▼                                                │
│               ┌───────────────┐                                        │
│               │   Database    │  ← stores issues, rules, history       │
│               │  PostgreSQL   │                                        │
│               └───────────────┘                                        │
└──────────────────────────────────────────────────────────────────────┘
```

**Three internal processes:**

| Process | Role |
|---------|------|
| **Web Server** | Serves the UI on port 9000, handles REST API calls, user management |
| **Compute Engine** | Processes scanner reports asynchronously, applies rules, creates issues |
| **Elasticsearch** | Powers full-text search across issues, code, projects |

---

## 4. Static Analysis vs Runtime Analysis

This is a fundamental concept: SonarQube performs **static analysis only**.

```
┌──────────────────────────────┐      ┌──────────────────────────────┐
│      STATIC ANALYSIS         │      │      RUNTIME ANALYSIS        │
│      (SonarQube)             │      │      (e.g. JaCoCo, Dynatrace)│
│                              │      │                              │
│  ✔ Code is NOT executed      │      │  ✔ Code IS executed          │
│  ✔ Runs at build/commit time │      │  ✔ Runs during tests/prod    │
│  ✔ Catches logic errors,     │      │  ✔ Catches memory leaks,     │
│    bad patterns, smells      │      │    race conditions,          │
│  ✔ 100% code coverage        │      │    actual performance issues │
│    (every line is read)      │      │  ✔ Real behavior observed    │
│  ✗ Cannot detect runtime     │      │  ✗ Only covers executed paths│
│    behavior                  │      │  ✗ Needs running environment │
│  ✗ May have false positives  │      │                              │
└──────────────────────────────┘      └──────────────────────────────┘
           │                                        │
           └──────────────┬─────────────────────────┘
                          ▼
             Best practice: use BOTH together
             SonarQube catches issues at commit time,
             runtime tools catch what survives to tests.
```

> **Analogy:** Static analysis is like a grammar checker reading your essay — it catches errors without you needing to present the speech. Runtime analysis is someone actually listening to the speech and noticing when you stumble.

---

## 5. Scanner Types

SonarQube does **not** analyze code itself — a **scanner** runs in your build environment and sends results to the server.

```
┌────────────────────────────────────────────────────────────────────┐
│                        Scanner Ecosystem                            │
│                                                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────┐   │
│  │  SonarScanner   │  │  SonarScanner   │  │  SonarScanner    │   │
│  │   for Maven     │  │   for Gradle    │  │   for .NET       │   │
│  │                 │  │                 │  │                  │   │
│  │ mvn sonar:sonar │  │gradle sonarqube │  │ dotnet sonarscann│   │
│  └─────────────────┘  └─────────────────┘  └──────────────────┘   │
│                                                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────┐   │
│  │  SonarScanner   │  │    SonarLint    │  │  SonarScanner    │   │
│  │   CLI           │  │   (IDE Plugin)  │  │   for Jenkins /  │   │
│  │  (generic,      │  │  IntelliJ /     │  │   GitHub Actions │   │
│  │   any language) │  │  VS Code / etc. │  │                  │   │
│  └─────────────────┘  └─────────────────┘  └──────────────────┘   │
└────────────────────────────────────────────────────────────────────┘
```

| Scanner | Use case |
|---------|----------|
| **SonarScanner CLI** | Universal, works with any project, configured via `sonar-project.properties` |
| **SonarScanner for Maven** | Java/Maven projects, runs as `mvn sonar:sonar` |
| **SonarScanner for Gradle** | Java/Gradle projects |
| **SonarScanner for .NET** | C# and VB.NET projects |
| **SonarLint** | IDE plugin — gives real-time feedback *before* committing |
| **CI-integrated scanners** | Jenkins plugin, GitHub Actions, GitLab CI, Azure DevOps |

---

## 6. Rule Categories

SonarQube ships with thousands of rules, organized into four issue types:

```
┌─────────────────────────────────────────────────────────────┐
│                      Rule Categories                         │
│                                                              │
│  🐛 BUG                   🔒 VULNERABILITY                  │
│  ─────────────            ───────────────                   │
│  Code that will likely    Security flaw that attackers      │
│  cause wrong behavior     can exploit                       │
│  at runtime               (SQL injection, XSS, hardcoded    │
│                           credentials...)                   │
│  Severity: BLOCKER        Severity: CRITICAL / BLOCKER      │
│            CRITICAL                                          │
│                                                              │
│  💡 CODE SMELL            🔥 SECURITY HOTSPOT               │
│  ─────────────            ──────────────────                │
│  Maintainability issue:   Sensitive code that is not        │
│  confusing logic,         necessarily a bug, but needs      │
│  duplication, dead code,  a human security review           │
│  overly complex methods   (e.g. use of crypto APIs,         │
│                           password handling)                │
│  Severity: MAJOR                                            │
│            MINOR                                            │
│            INFO                                             │
└─────────────────────────────────────────────────────────────┘
```

**Severity levels (from highest to lowest):**

| Severity | Description |
|----------|-------------|
| **BLOCKER** | Must be fixed immediately — likely production crash or major security breach |
| **CRITICAL** | High probability of harm — fix before release |
| **MAJOR** | Significant impact on quality — fix in current sprint |
| **MINOR** | Small quality issue — fix when time allows |
| **INFO** | Informational — no immediate action needed |

---

## 7. Quality Gate

The **Quality Gate** is the go/no-go decision for your code. It is a set of conditions that must all pass before the code is considered acceptable.

```
                    ┌──────────────────────────────┐
                    │         Quality Gate          │
                    │                               │
                    │  Condition 1: Coverage ≥ 80%  │
                    │  Condition 2: Bugs = 0        │
                    │  Condition 3: Duplications    │
                    │              < 3%             │
                    │  Condition 4: Security        │
                    │              Hotspots = 0     │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────┴───────────────┐
                    │                               │
               ✅ PASSED                      ❌ FAILED
               (Pipeline continues)          (Pipeline fails /
                                              PR blocked)
```

**Default Quality Gate — "Sonar way":**

| Metric | Threshold |
|--------|-----------|
| Coverage on new code | ≥ 80% |
| Duplicated lines on new code | ≤ 3% |
| Maintainability rating on new code | A |
| Reliability rating on new code | A |
| Security rating on new code | A |
| Security Hotspots reviewed | 100% |

> The Quality Gate is configured in **Administration → Quality Gates** and can be customized per team or project.

---

## 8. Clean as You Code

**Clean as You Code** is SonarQube's core methodology introduced in 2021. The philosophy shifts focus from fixing all existing issues (which can be thousands) to ensuring *new* code added is always clean.

```
WITHOUT "Clean as You Code"
────────────────────────────
Legacy codebase: 5,000 issues
New code adds:      +50 issues
Team sees:        5,050 issues → overwhelming, no one knows where to start

WITH "Clean as You Code"
──────────────────────────
Legacy codebase: 5,000 issues → tracked but not blocking
New code adds:      +50 issues → BLOCKED by Quality Gate
Team focus:      Only new issues → manageable, actionable

Result: Debt doesn't grow, and shrinks over time as legacy code is touched.
```

**Two periods in SonarQube:**

| Period | What it covers |
|--------|---------------|
| **New Code** | Code added since a defined date, version, or reference branch — this is what the Quality Gate measures |
| **Overall Code** | All code in the project, including legacy — visible but not gate-blocking by default |

The "New Code" period is configurable: since a specific date, since a version tag, or since the previous analysis.

---

## 9. Technical Debt & Maintainability Rating

### Technical Debt

Technical Debt is SonarQube's estimate of **how long it would take a developer to fix all Code Smells** in the codebase. It is expressed in minutes, hours, or days.

```
Example:
  Method too long (+30 min to refactor)
  + Duplicate block (+15 min to remove)
  + Unused variable (+2 min to delete)
  + Complex condition (+45 min to simplify)
  ─────────────────────────────────────────
  Total Technical Debt = 1h 32min
```

The debt is calculated using **remediation effort** values attached to each rule (e.g. "fixing a duplicate block costs 10 minutes").

### Maintainability Rating

The **Maintainability Rating** (A to E) is derived from the ratio of Technical Debt to the total development time of the project (estimated as 30 minutes per line of code).

```
  Technical Debt Ratio = Technical Debt / (Total LoC × 30 min)

  ┌──────┬───────────────────────────────────┐
  │  A   │  Ratio ≤ 5%   — Excellent         │
  │  B   │  Ratio 6–10%  — Good              │
  │  C   │  Ratio 11–20% — Fair              │
  │  D   │  Ratio 21–50% — Poor              │
  │  E   │  Ratio > 50%  — Critical          │
  └──────┴───────────────────────────────────┘
```

---

## 10. Issue Lifecycle

Every issue detected by SonarQube goes through a defined lifecycle:

```
 [New code introduced]
         │
         ▼
    ┌─────────┐
    │  OPEN   │ ◄─── Default state when SonarQube detects a problem
    └────┬────┘
         │
    Developer reviews the issue
         │
    ┌────┴──────────────────────────────────────────┐
    │                                               │
    ▼                                               ▼
┌──────────┐                               ┌─────────────┐
│  IN      │                               │  CONFIRMED  │ ◄── Developer agrees
│ PROGRESS │                               │             │     it's a real issue
└──────┬───┘                               └──────┬──────┘
       │                                          │
       ▼                                          │
┌──────────┐                                      │
│ RESOLVED │ ◄── Code fixed + re-analyzed         │
└──────┬───┘                                      │
       │                         ┌────────────────┘
       ▼                         ▼
┌──────────┐              ┌──────────────┐
│  CLOSED  │              │  WON'T FIX   │ ◄── Accepted risk
└──────────┘              └──────────────┘

                          ┌──────────────┐
                          │  FALSE       │ ◄── Not a real issue
                          │  POSITIVE    │     (scanner mistake)
                          └──────────────┘
```

| Status | Meaning |
|--------|---------|
| **Open** | Newly detected, unreviewed |
| **Confirmed** | Developer confirmed it's a real problem |
| **Resolved** | Fixed — disappears after next clean analysis |
| **Won't Fix** | Accepted as-is (documented risk) |
| **False Positive** | SonarQube was wrong — marked to be ignored |
| **Closed** | Issue no longer exists in the code |

---

## 11. Database: H2 → PostgreSQL

### Why SonarQube needs a database

SonarQube stores in its database:
- All detected issues (current + historical)
- Project configurations and Quality Gates
- User accounts and permissions
- Rule sets and Quality Profiles
- Analysis history and metrics over time

### H2 (default, development only)

SonarQube ships with an **embedded H2 database** enabled by default. It requires zero configuration — just start SonarQube and it works.

```
⚠️  H2 is for LOCAL TESTING ONLY:
    - Data is lost when SonarQube is reinstalled
    - Cannot be used in production
    - Does not support multiple SonarQube nodes
    - Performance degrades quickly with large projects
```

### Migration to PostgreSQL (production)

```
┌────────────────────────────────────────────────────────────────┐
│                   Database Evolution Path                       │
│                                                                 │
│  Development          Staging              Production           │
│                                                                 │
│  ┌──────────┐        ┌──────────┐         ┌──────────────────┐ │
│  │    H2    │  ───►  │PostgreSQL│  ───►   │ PostgreSQL       │ │
│  │(embedded)│        │(single)  │         │ (with backups,   │ │
│  │          │        │          │         │  HA optional)    │ │
│  └──────────┘        └──────────┘         └──────────────────┘ │
│                                                                 │
│  Recommended production DB: PostgreSQL 13+                      │
│  Also supported: MS SQL Server, Oracle (Enterprise/DC only)     │
└────────────────────────────────────────────────────────────────┘
```

**PostgreSQL configuration in `sonar.properties`:**

```properties
# sonar.properties — stored at $SONARQUBE_HOME/conf/sonar.properties

sonar.jdbc.url=jdbc:postgresql://localhost:5432/sonarqube
sonar.jdbc.username=sonarqube
sonar.jdbc.password=your_secure_password

# Recommended pool settings for production
sonar.jdbc.maxActive=60
sonar.jdbc.minIdle=10
```

---

## 12. CI/CD Integration

SonarQube is designed to be a **gate in your CI/CD pipeline**, not an afterthought.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CI/CD Pipeline with SonarQube                    │
│                                                                         │
│  Developer        Git            CI Server           SonarQube         │
│     │              │                 │                    │             │
│     │── git push ──►│                │                    │             │
│     │              │── trigger ─────►│                    │             │
│     │              │                 │── mvn compile ──►  │             │
│     │              │                 │── run tests ────►  │             │
│     │              │                 │── sonar scan ───────────────────►│
│     │              │                 │                    │ (analysis)  │
│     │              │                 │◄── Quality Gate ───│             │
│     │              │                 │    PASSED / FAILED │             │
│     │              │                 │                    │             │
│     │              │    PASSED ──────►── mvn deploy       │             │
│     │◄─ FAILED ────│────────────────│                    │             │
└────────────────────────────────────────────────────────────────────────┘
```

**Key integration principle:** The scanner runs *after tests* (so coverage data is available) and *before deployment*. The Quality Gate result controls whether the pipeline proceeds.

---

## 13. Docker Compose Example

A complete SonarQube + PostgreSQL stack for local development or staging:

```yaml
# docker-compose.yml
version: "3.8"

services:

  # ──────────────────────────────────────────
  # PostgreSQL — persistent database
  # ──────────────────────────────────────────
  sonarqube-db:
    image: postgres:15
    container_name: sonarqube-db
    environment:
      POSTGRES_DB: sonarqube
      POSTGRES_USER: sonarqube
      POSTGRES_PASSWORD: sonarpass
    volumes:
      - sonarqube-db-data:/var/lib/postgresql/data
    networks:
      - sonarnet
    restart: unless-stopped

  # ──────────────────────────────────────────
  # SonarQube Server
  # ──────────────────────────────────────────
  sonarqube:
    image: sonarqube:10-community
    container_name: sonarqube
    depends_on:
      - sonarqube-db
    environment:
      SONAR_JDBC_URL: jdbc:postgresql://sonarqube-db:5432/sonarqube
      SONAR_JDBC_USERNAME: sonarqube
      SONAR_JDBC_PASSWORD: sonarpass
      SONAR_ES_BOOTSTRAP_CHECKS_DISABLE: "true"   # for local dev only
    ports:
      - "9000:9000"
    volumes:
      - sonarqube-data:/opt/sonarqube/data
      - sonarqube-logs:/opt/sonarqube/logs
      - sonarqube-extensions:/opt/sonarqube/extensions
    networks:
      - sonarnet
    restart: unless-stopped
    ulimits:
      nofile:
        soft: 65536
        hard: 65536

volumes:
  sonarqube-db-data:
  sonarqube-data:
  sonarqube-logs:
  sonarqube-extensions:

networks:
  sonarnet:
    driver: bridge
```

**Start the stack:**
```bash
# Linux prerequisite — Elasticsearch needs this:
sudo sysctl -w vm.max_map_count=524288

docker compose up -d
# Access at: http://localhost:9000
# Default credentials: admin / admin (change on first login)
```

---

## 14. Jenkinsfile Example

A complete declarative pipeline integrating SonarQube with a Java/Maven project:

```groovy
// Jenkinsfile

pipeline {
    agent any

    environment {
        // SonarQube server name as configured in Jenkins
        // Manage Jenkins → Configure System → SonarQube Servers
        SONAR_SERVER = 'SonarQube-Server'
    }

    tools {
        maven 'Maven-3.9'
        jdk   'JDK-17'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                echo "Branch: ${env.BRANCH_NAME}"
            }
        }

        stage('Build & Unit Tests') {
            steps {
                sh 'mvn clean package -DskipTests=false'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv("${SONAR_SERVER}") {
                    sh '''
                        mvn sonar:sonar \
                          -Dsonar.projectKey=my-java-project \
                          -Dsonar.projectName="My Java Project" \
                          -Dsonar.sources=src/main \
                          -Dsonar.tests=src/test \
                          -Dsonar.java.coveragePlugin=jacoco \
                          -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                    '''
                }
            }
        }

        stage('Quality Gate') {
            steps {
                // Wait for SonarQube to process the report (max 5 min)
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                    // abortPipeline: true → pipeline FAILS if gate fails
                }
            }
        }

        stage('Deploy to Nexus') {
            // Only runs if Quality Gate passed
            when {
                branch 'main'
            }
            steps {
                sh 'mvn deploy -DskipTests'
                echo "Artifact deployed to Nexus successfully."
            }
        }
    }

    post {
        failure {
            echo "Pipeline failed — check SonarQube dashboard for details."
            // emailext or Slack notification can go here
        }
        success {
            echo "Pipeline passed all quality gates."
        }
    }
}
```

**Required Jenkins plugins:**
- `SonarQube Scanner` — provides `withSonarQubeEnv` and `waitForQualityGate`
- `JaCoCo` — code coverage report integration

---

## 15. Summary

```
┌──────────────────────────────────────────────────────────────────────┐
│                     SonarQube at a Glance                             │
│                                                                        │
│  WHAT:    Continuous code quality & security inspection platform       │
│  WHEN:    At every commit / PR — before merge, before deploy           │
│  HOW:     Static analysis (no code execution required)                 │
│  OUTPUT:  Issues, metrics, ratings, Quality Gate decision              │
│                                                                        │
│  Key concepts:                                                         │
│  ┌──────────────────┬─────────────────────────────────────────────┐   │
│  │ Quality Gate     │ Pass/fail decision on your new code          │   │
│  │ Clean as You Code│ Focus on new code, not legacy debt           │   │
│  │ Technical Debt   │ Time estimate to fix all code smells         │   │
│  │ Rule Categories  │ Bug / Vulnerability / Code Smell / Hotspot   │   │
│  │ Issue Lifecycle  │ Open → Confirmed → Resolved → Closed         │   │
│  │ Database         │ H2 for dev → PostgreSQL for production       │   │
│  └──────────────────┴─────────────────────────────────────────────┘   │
│                                                                        │
│  Editions: Community (free, OSS) → Developer → Enterprise → DC        │
└──────────────────────────────────────────────────────────────────────┘
```

---

*Document part of the CI/CD Security Chain project — M1 CCDAD.*  
*See also: `LAB_SONARQUBE.md` (hands-on steps), `LAB_NEXUS.md`, `NEXUS_OVERVIEW.md`, `README.md`*

