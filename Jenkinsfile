pipeline {
    agent any

    tools {
        maven 'Default'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Backend') {
            steps {
                dir('backend') {
                    sh 'mvn clean package'
                }
            }
        }

        stage('SonarQube Backend') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('sonar-server') {
                        sh 'mvn sonar:sonar -Dsonar.projectKey=calculator-backend'
                    }
                }
            }
        }

        stage('Build Frontend') {
            steps {
                dir('frontend') {
                    sh '''
                    npm install
                    npm run build
                    '''
                }
            }
        }

        stage('Quality Gate') {
            steps {
                waitForQualityGate abortPipeline: true
            }
        }

        stage('Publish Backend to Nexus') {
            steps {
                dir('backend') {
                    withCredentials([usernamePassword(credentialsId: 'nexus-credentials', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                        sh 'mvn deploy -DaltDeploymentRepository=nexus::default::http://${NEXUS_USER}:${NEXUS_PASS}@nexus:8081/repository/maven-releases/'
                    }
                }
            }
        }
    }
}
