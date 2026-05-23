# Lab Jenkins: Intégration Continue avec SonarQube et Nexus

Ce document détaille la configuration et l'exécution d'un pipeline CI/CD avec Jenkins, intégrant l'analyse de code via SonarQube et le déploiement des artéfacts via Nexus.

## 1. Exécution de Jenkins, SonarQube et Nexus sur un même réseau
Pour que les outils puissent communiquer entre eux via leurs noms d'hôtes (ex: `http://sonarqube:9000`), ils doivent être exécutés au sein d'un même réseau Docker (ex: `cicd-net`). 

![Réseau Docker](./docs/screenshots/j7.png)

## 2. Installation des plugins Git et SonarQube dans Jenkins
Depuis l'interface de Jenkins (Gérer Jenkins > Gestion des plugins), il est nécessaire d'installer les plugins **Git plugin** et **SonarQube Scanner** afin que Jenkins puisse cloner le dépôt source et communiquer avec le serveur de qualité.

![Installation des plugins Jenkins](./docs/screenshots/j3.png)

## 3. Génération du Token SonarQube et ajout dans Jenkins
Un jeton d'authentification (Token) doit être généré sur SonarQube (My Account > Security > Generate Tokens). Ce jeton est ensuite ajouté dans Jenkins via `Manage Credentials` en tant que `Secret text` (ex: ID `sonar-token`).

![Ajout du Token SonarQube](./docs/screenshots/j5.png)

## 4. Ajout de l'installation SonarQube dans les paramètres Jenkins
Dans `Manage Jenkins > System`, la section *SonarQube servers* est complétée avec l'URL du conteneur SonarQube (`http://sonarqube:9000`) et liée au Token créé à l'étape précédente.

![Configuration SonarQube dans Jenkins](./docs/screenshots/j6.png)

## 5. Test de la connexion au Scanner SonarQube (Simulation)
Une simulation d'analyse ou un test de connectivité est réalisé pour vérifier que Jenkins (ou le SonarQube Scanner local) arrive bien à s'authentifier et à envoyer des données au serveur SonarQube.

![Test de connexion SonarQube](./docs/screenshots/j9.png)
![Test de connexion SonarQube](./docs/screenshots/j10.png)

## 6. Connexion de Nexus à Jenkins via l'ajout de nouveaux credentials
Pour que le pipeline puisse publier l'artéfact compilé vers Nexus, les identifiants Nexus (utilisateur `admin` et son mot de passe) sont ajoutés dans Jenkins (Credentials) en tant que `Username with password` (ID: `nexus-credentials`).

![Ajout des Credentials Nexus](./docs/screenshots/j8.png)

## 7. Étapes d'ajout du pipeline dans le tableau de bord Jenkins
Dans Jenkins, un nouveau projet de type "Pipeline" est créé. Dans la section *Pipeline script*, le code source du Jenkinsfile est collé pour définir les différentes étapes de l'intégration continue.

**Code du Jenkinsfile :**
```groovy
pipeline {
    agent any

    tools {
        maven 'Default'
    }

    options {
        buildDiscarder(logRotator(
            numToKeepStr: '5',
            artifactNumToKeepStr: '2',
            daysToKeepStr: '15'
        ))
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                cleanWs()
                checkout scm
            }
        }

        stage('Build Backend') {
            steps {
                dir('backend') {
                    sh '''
                        mvn clean package \
                        -Dmaven.repo.local=.m2 \
                        -DskipTests=false
                    '''
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('sonar-server') {
                        sh '''
                            mvn sonar:sonar \
                            -Dsonar.projectKey=calculator-backend \
                            -Dmaven.repo.local=.m2
                        '''
                    }
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

        stage('Deploy to Nexus (SNAPSHOT FIXED)') {
            steps {
                dir('backend') {
                    withCredentials([usernamePassword(
                        credentialsId: 'nexus-credentials',
                        usernameVariable: 'NEXUS_USER',
                        passwordVariable: 'NEXUS_PASS'
                    )]) {

                        sh '''
                            mvn deploy \
                            -DskipTests \
                            -Dmaven.repo.local=.m2 \
                            -DaltDeploymentRepository=nexus::default::http://${NEXUS_USER}:${NEXUS_PASS}@nexus:8081/repository/maven-snapshots/
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            dir('backend') {
                sh 'rm -rf .m2'
            }

            cleanWs(
                cleanWhenSuccess: true,
                cleanWhenFailure: true,
                cleanWhenAborted: true,
                deleteDirs: true,
                notFailBuild: true
            )
        }

        failure {
            echo 'Pipeline failed — workspace cleaned.'
        }
    }
}
```

![Configuration du Pipeline](./docs/screenshots/j12.png)
![Configuration du Pipeline](./docs/screenshots/j13.png)
![Configuration du Pipeline](./docs/screenshots/j14.png)

## 8. Affichage du résultat dans le tableau de bord Jenkins
Après avoir cliqué sur "Build Now", le tableau de bord de Jenkins (Stage View) affiche l'exécution séquentielle de chaque étape (Checkout, Build, SonarQube, Quality Gate, Deploy), toutes au vert si l'opération est réussie.

![Résultat du Pipeline](./docs/sreenshots/j15.png)
![Résultat du Pipeline](./docs/sreenshots/j16.png)

## 9. Affichage du résultat dans le tableau de bord SonarQube

![Résultat du Pipeline](./docs/sreenshots/j17.png)
