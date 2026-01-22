pipeline {
    agent any

    environment {
        BACKEND_DIR = 'docq_backend'
        FRONTEND_DIR = 'docq-frontend'
        AI_DIR = 'docq-ai'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build: Backend') {
            steps {
                dir("${BACKEND_DIR}") {
                    sh 'docker build -t docq-backend .'
                }
            }
        }

        stage('Build: Frontend') {
            steps {
                dir("${FRONTEND_DIR}") {
                    sh 'docker build -t docq-frontend .'
                }
            }
        }

        stage('Build: AI') {
            steps {
                dir("${AI_DIR}") {
                    sh 'docker build -t docq-ai .'
                }
            }
        }

        stage('Deploy: All Containers') {
            steps {
                sh '''
                    docker stop docq-backend || true
                    docker rm docq-backend || true
                    docker run -d --name docq-backend --network app-network -p 8080:8080 docq-backend

                    docker stop docq-frontend || true
                    docker rm docq-frontend || true
                    docker run -d --name docq-frontend --network app-network -p 3000:80 docq-frontend

                    docker stop docq-ai || true
                    docker rm docq-ai || true
                    docker run -d --name docq-ai --network app-network -p 5000:8000 docq-ai
                '''
            }
        }
    }

    post {
        success {
            echo '✅ 파이프라인 성공!'
        }
        failure {
            echo '❌ 파이프라인 실패'
        }
        always {
            cleanWs()
        }
    }
}
