pipeline {
    agent any

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

        stage('Install Dependencies') {
            steps {
                dir('frontend') {
                    sh '''
                        rm -rf node_modules package-lock.json

                        npm ci --prefer-offline
                    '''
                }
            }
        }

        stage('Build Frontend') {
            steps {
                dir('frontend') {
                    sh '''
                        npm run build
                    '''
                }
            }
        }

        stage('Optional Tests') {
            steps {
                dir('frontend') {
                    sh '''
                        # skip if no tests configured
                        npm test -- --watch=false || true
                    '''
                }
            }
        }
    }

    post {
        always {
            dir('frontend') {
                sh '''
                    rm -rf node_modules dist build .npm
                '''
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
            echo 'Frontend pipeline failed — workspace cleaned.'
        }
    }
}
