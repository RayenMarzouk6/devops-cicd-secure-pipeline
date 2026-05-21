pipeline {
    agent any

    tools {
        maven 'Maven3'
        jdk 'JDK21'
        nodejs 'Node18'
    }

    environment {
        BACKEND_DIR = 'backend'
        FRONTEND_DIR = 'frontend'
    }

    stages {

        stage('Backend - Build') {
            steps {
                dir("${BACKEND_DIR}") {
                    sh 'mvn clean compile'
                }
            }
        }

        stage('Backend - Test') {
            steps {
                dir("${BACKEND_DIR}") {
                    sh 'mvn test'
                }
            }
        }

        stage('Backend - SonarQube') {
            steps {
                dir("${BACKEND_DIR}") {
                    withSonarQubeEnv('SonarQube') {
                        sh '''
                        mvn sonar:sonar \
                        -Dsonar.projectKey=backend_project
                        '''
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Backend - Package') {
            steps {
                dir("${BACKEND_DIR}") {
                    sh 'mvn package -DskipTests'
                }
            }
        }

        stage('Backend - Deploy to Nexus') {
            steps {
                dir("${BACKEND_DIR}") {
                    sh 'mvn deploy -DskipTests'
                }
            }
        }

        stage('Frontend - Install Dependencies') {
            steps {
                dir("${FRONTEND_DIR}") {
                    sh 'npm ci'
                }
            }
        }

        stage('Frontend - Build') {
            steps {
                dir("${FRONTEND_DIR}") {
                    sh 'npm run build'
                }
            }
        }

        stage('Pipeline Success') {
            steps {
                echo 'Build successful 🚀'
            }
        }
    }

    post {
        success {
            echo '✅ CI/CD completed successfully'
        }

        failure {
            echo '❌ Pipeline failed'
        }

        always {
            cleanWs()
        }
    }
}
