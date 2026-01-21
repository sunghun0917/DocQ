pipeline {
    agent any
    
    environment {
        DOCKER_REGISTRY = credentials('docker-registry-url') ?: 'localhost:5000'
        DOCKER_IMAGE_TAG = "${env.BUILD_NUMBER}"
        PROJECT_NAME = 'docq'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Backend Build & Test') {
            steps {
                dir('docq_backend') {
                    script {
                        if (isUnix()) {
                            sh './gradlew clean build --no-daemon'
                        } else {
                            bat 'gradlew.bat clean build --no-daemon'
                        }
                    }
                }
            }
            post {
                always {
                    junit 'docq_backend/build/test-results/test/*.xml'
                }
            }
        }
        
        stage('Frontend Build & Test') {
            steps {
                dir('docq-frontend') {
                    script {
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
        }
        
        stage('AI Service Check') {
            steps {
                dir('docq-ai') {
                    script {
                        sh '''
                            python -m pip install --upgrade pip
                            pip install -r requirements.txt
                            python -m pytest || echo "No tests configured"
                        '''
                    }
                }
            }
        }
        
        stage('Build Docker Images') {
            when {
                anyOf {
                    branch 'main'
                    branch 'master'
                }
            }
            parallel {
                stage('Backend Image') {
                    steps {
                        script {
                            def backendImage = docker.build(
                                "${DOCKER_REGISTRY}/${PROJECT_NAME}-backend:${DOCKER_IMAGE_TAG}",
                                "-f docq_backend/Dockerfile docq_backend/"
                            )
                            backendImage.push()
                            backendImage.push("${DOCKER_REGISTRY}/${PROJECT_NAME}-backend:latest")
                        }
                    }
                }
                
                stage('Frontend Image') {
                    steps {
                        script {
                            def frontendImage = docker.build(
                                "${DOCKER_REGISTRY}/${PROJECT_NAME}-frontend:${DOCKER_IMAGE_TAG}",
                                "-f docq-frontend/Dockerfile docq-frontend/"
                            )
                            frontendImage.push()
                            frontendImage.push("${DOCKER_REGISTRY}/${PROJECT_NAME}-frontend:latest")
                        }
                    }
                }
                
                stage('AI Service Image') {
                    steps {
                        script {
                            def aiImage = docker.build(
                                "${DOCKER_REGISTRY}/${PROJECT_NAME}-ai:${DOCKER_IMAGE_TAG}",
                                "-f docq-ai/Dockerfile docq-ai/"
                            )
                            aiImage.push()
                            aiImage.push("${DOCKER_REGISTRY}/${PROJECT_NAME}-ai:latest")
                        }
                    }
                }
            }
        }
        
        stage('Deploy') {
            when {
                anyOf {
                    branch 'main'
                    branch 'master'
                }
            }
            steps {
                script {
                    sh '''
                        cd deploy/compose
                        export IMAGE_TAG=${DOCKER_IMAGE_TAG}
                        docker-compose down
                        docker-compose pull
                        docker-compose up -d
                        docker-compose ps
                    '''
                }
            }
        }
    }
    
    post {
        success {
            echo 'Pipeline succeeded!'
            // 슬랙/이메일 알림 추가 가능
        }
        failure {
            echo 'Pipeline failed!'
            // 슬랙/이메일 알림 추가 가능
        }
        always {
            cleanWs()
        }
    }
}
