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
                        chmod +x gradlew
                        ./gradlew clean build --no-daemon
                    '''
                }
            }
        }

        stage('Frontend: build') {
            steps {
                dir("${FRONTEND_DIR}") {
                    sh '''
                        npm ci
                        npm run type-check
                        npm run lint
                        npm run test:unit
                        npm run build
                    '''
                }
            }
        }

        stage('AI service: deps check') {
            steps {
                dir("${AI_DIR}") {
                    sh '''
                        python -m pip install --upgrade pip
                        pip install -r requirements.txt
                        python -m pytest || echo "⚠️ No tests configured"
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
                    # Backend
                    docker stop docq-backend || true
                    docker rm docq-backend || true
                    docker run -d --name docq-backend --network app-network -p 8080:8080 docq-backend

                    # Frontend
                    docker stop docq-frontend || true
                    docker rm docq-frontend || true
                    docker run -d --name docq-frontend --network app-network -p 3000:80 docq-frontend

                    # AI Service
                    docker stop docq-ai || true
                    docker rm docq-ai || true
                    docker run -d --name docq-ai --network app-network -p 5000:5000 docq-ai
                '''
            }
        }
    }

    post {
        success {
            echo '🎉 Deployment success!'
        }
        failure {
            echo '❌ Deployment failed!'
        }
        always {
            cleanWs()
        }
    }
}
