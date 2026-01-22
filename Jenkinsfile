pipeline {
    agent any

    environment {
        COMPOSE_DIR = 'deploy/compose'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
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
            echo '✅ 배포 성공'
        }
        failure {
            echo '❌ 배포 실패'
        }
        always {
            cleanWs()
        }
    }
}
