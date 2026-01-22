pipeline {
    agent any

    environment {
        BACKEND_DIR = 'docq_backend'
        FRONTEND_DIR = 'docq-frontend'
        AI_DIR = 'docq-ai'
        COMPOSE_DIR = 'deploy/compose' // <- docker-compose.yml 위치
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Images') {
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
                dir("${COMPOSE_DIR}") {
                    sh '''
                        docker compose down
                        docker compose up -d --build
                    '''
                }
            }
        }
    }

    post {
        success {
            echo '✅ 배포 성공!'
        }
        failure {
            echo '❌ 배포 실패'
        }
        always {
            cleanWs()
        }
    }
}
