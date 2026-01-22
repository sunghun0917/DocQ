pipeline {
    agent any

    environment {
        PROJECT_NAME   = 'docq'
        IMAGE_TAG      = "${env.BUILD_NUMBER}"
        COMPOSE_FILE   = 'deploy/compose/docker-compose.yml'
        NODE_IMAGE     = 'node:20-alpine'
        PY_IMAGE       = 'python:3.11-slim'
    }

    stages {
        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Backend: build & test') {
            steps {
                dir('docq_backend') {
                    sh './gradlew clean build --no-daemon'
                }
            }
            post { always { junit 'docq_backend/build/test-results/test/*.xml' } }
        }

        stage('Frontend: build') {
            steps {
                sh """
                  docker run --rm \
                    -v "\$PWD/docq-frontend:/app" \
                    -w /app ${NODE_IMAGE} \
                    sh -c "npm ci && npm run build"
                """
            }
        }

        stage('AI service: deps check') {
            steps {
                sh """
                  docker run --rm \
                    -v "\$PWD/docq-ai:/app" \
                    -w /app ${PY_IMAGE} \
                    sh -c "pip install -r requirements.txt && pytest || true"
                """
            }
        }

        stage('Build images') {
            steps {
                sh """
                  docker build -t ${PROJECT_NAME}-backend:${IMAGE_TAG} docq_backend
                  docker build -t ${PROJECT_NAME}-frontend:${IMAGE_TAG} docq-frontend
                  docker build -t ${PROJECT_NAME}-ai:${IMAGE_TAG} docq-ai
                """
            }
        }

        stage('Deploy (docker compose)') {
            when { anyOf { branch 'main'; branch 'master' } }
            steps {
                sh """
                  cd deploy/compose
                  docker compose down
                  docker compose up -d --build
                  docker compose ps
                """
            }
        }
    }

    post {
        always { cleanWs() }
    }
}
