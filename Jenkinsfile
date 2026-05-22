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

        stage('Deploy to Nexus') {
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
                            -DaltDeploymentRepository=nexus::default::http://${NEXUS_USER}:${NEXUS_PASS}@nexus:8081/repository/maven-releases/
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
