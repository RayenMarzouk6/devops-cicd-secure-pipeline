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
        // Prevent concurrent builds from doubling disk usage
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                // Clean workspace before checkout to avoid leftover artifacts
                cleanWs()
                checkout scm
            }
        }

        stage('Build Backend') {
            steps {
                dir('backend') {
                    // -Dmaven.repo.local avoids filling ~/.m2 on the controller
                    sh 'mvn clean package -Dmaven.repo.local=.m2 -DskipTests=false'
                }
            }
        }

        stage('SonarQube Backend') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('sonar-server') {
                        sh 'mvn sonar:sonar -Dsonar.projectKey=calculator-backend -Dmaven.repo.local=.m2'
                    }
                }
            }
        }

        stage('Build Frontend') {
            steps {
                dir('frontend') {
                    sh '''
                        # Clean previous build output before rebuilding
                        rm -rf dist build node_modules/.cache

                        npm ci --prefer-offline          
                        npm run build

                        # Remove dev dependencies after build (saves ~200-500MB)
                        npm prune --production
                    '''
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

        stage('Publish Backend to Nexus') {
            steps {
                dir('backend') {
                    withCredentials([usernamePassword(
                        credentialsId: 'nexus-credentials',
                        usernameVariable: 'NEXUS_USER',
                        passwordVariable: 'NEXUS_PASS'
                    )]) {
                        sh '''
                            mvn deploy \
                                -Dmaven.repo.local=.m2 \
                                -DaltDeploymentRepository=nexus::default::http://${NEXUS_USER}:${NEXUS_PASS}@nexus:8081/repository/maven-releases/ \
                                -DskipTests
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            // Clean Maven local repo created during build
            dir('backend') {
                sh 'rm -rf .m2'
            }
            // Clean npm cache
            dir('frontend') {
                sh 'rm -rf node_modules dist build .npm'
            }
            // Final workspace wipe
            cleanWs(
                cleanWhenSuccess: true,
                cleanWhenFailure: true,
                cleanWhenAborted: true,
                deleteDirs: true,
                notFailBuild: true
            )
        }
        failure {
            echo 'Pipeline failed — workspace cleaned to recover disk space.'
        }
    }
}
