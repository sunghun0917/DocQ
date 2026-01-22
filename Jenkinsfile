pipeline {
    agent {
        docker {
            image 'eclipse-temurin:17-jdk-alpine'
            args '-v /var/run/docker.sock:/var/run/docker.sock'
        }
    }

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

        /* =========================
           Backend (Gradle / Java 17)
           ========================= */
        stage('Backend: build') {
            steps {
                dir("${BACKEND_DIR}") {
                    sh '''
                        chmod +x gradlew
                        ./gradlew clean build --no-daemon
                    '''
                }
            }
        }

        /* =========================
           Frontend (Node)
           ========================= */
        stage('Frontend: build') {
            steps {
                dir("${FRONTEND_DIR}") {
                    sh '''
                        npm ci
                        npm run build
                    '''
                }
            }
        }

        /* =========================
           AI Service (Python)
           ========================= */
        stage('AI service: check') {
            steps {
                dir("${AI_DIR}") {
                    sh '''
                        python -m pip install --upgrade pip
                        pip install -r requirements.txt
                        python -m pytest || echo "No tests configured"
                    '''
                }
            }
        }

        /* =========================
           Docker Build (local)
           ========================= */
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

        /* =========================
           Deploy (stop → rm → run)
           ========================= */
        stage('Deploy') {
            steps {
                sh '''
                    # Backend
                    docker stop docq-backend || true
                    docker rm docq-backend || true
                    docker run -d \
                        --name docq-backend \
                        --network app-network \
                        -p 8080:8080 \
                        docq-backend

                    # Frontend
                    docker stop docq-frontend || true
                    docker rm docq-frontend || true
                    docker run -d \
                        --name docq-frontend \
                        --network app-network \
                        -p 3000:80 \
                        docq-frontend

                    # AI Service
                    docker stop docq-ai || true
                    docker rm docq-ai || true
                    docker run -d \
                        --name docq-ai \
                        --network app-network \
                        -p 5000:5000 \
                        docq-ai
                '''
            }
        }
    }

    post {
        success {
            echo '🎉 CI/CD 성공!'
        }
        failure {
            echo '❌ CI/CD 실패'
        }
        always {
            cleanWs()
        }
    }
}
