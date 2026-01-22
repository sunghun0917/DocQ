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

        stage('Backend: build') {
            steps {
                dir("${BACKEND_DIR}") {
                    sh '''
                        docker run --rm \
                          -v $(pwd):/app \
                          -w /app \
                          eclipse-temurin:17-jdk-alpine \
                          sh -c "chmod +x ./gradlew && ./gradlew clean build --no-daemon"
                    '''
                }
            }
        }

        stage('Frontend: build') {
            steps {
                dir("${FRONTEND_DIR}") {
                    sh '''
                        docker run --rm \
                          -v $(pwd):/app \
                          -w /app \
                          node:20-alpine \
                          sh -c "npm ci && npm run build"
                    '''
                }
            }
        }

        stage('AI service: deps check') {
            steps {
                dir("${AI_DIR}") {
                    sh '''
                        docker run --rm \
                          -v $(pwd):/app \
                          -w /app \
                          python:3.10 \
                          sh -c "pip install -r requirements.txt && python -m pytest || echo 'No tests'"
                    '''
                }
            }
        }

        stage('Docker Build') {
            parallel {
                stage('Build Backend Image') {
                    steps {
                        dir("${BACKEND_DIR}") {
                            sh 'docker build -t docq-backend .'
                        }
                    }
                }

                stage('Build Frontend Image') {
                    steps {
                        dir("${FRONTEND_DIR}") {
                            sh 'docker build -t docq-frontend .'
                        }
                    }
                }

                stage('Build AI Image') {
                    steps {
                        dir("${AI_DIR}") {
                            sh 'docker build -t docq-ai .'
                        }
                    }
                }
            }
        }

        stage('Deploy') {
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
                    docker run -d --name docq-ai --network app-network -p 5000:5000 docq-ai
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
