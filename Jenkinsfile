pipeline {
    agent any

    environment {
        DEPLOY_DIR  = 'deploy/compose'
        UPSTAGE_KEY = 'up_q2sUP2hxDqM5cqdtqafwxUOixs1zU'
    }

    stages {

        stage('Checkout') {
            steps {
                // Checkout repository configured in Jenkins
                checkout scm
            }
        }

        stage('Prepare Env File') {
            steps {
                sh '''
                    set -e
                    mkdir -p docq-ai
                    echo "UPSTAGE_API_KEY=${UPSTAGE_KEY}" > docq-ai/.env
                '''
            }
        }

        stage('Deploy') {
            steps {
                dir("${DEPLOY_DIR}") {
                    sh '''
                        set -e
                        docker-compose pull --ignore-pull-failures || true
                        docker-compose down --remove-orphans || true
                        docker-compose up -d --build
                    '''
                }
            }
        }
    }

    post {
        success {
            echo 'Deploy succeeded'
        }
        failure {
            echo 'Deploy failed (check nginx.conf and network status)'
            sh '''
                echo "[DEBUG] docker-compose ps"
                cd deploy/compose && docker-compose ps
                echo "[DEBUG] backend logs (last 200 lines)"
                cd deploy/compose && docker-compose logs --tail=200 backend || true
                echo "[DEBUG] nginx error log tail"
                docker logs nginx --tail=100 || true
            '''
        }
        always {
            echo 'Pipeline finished'
        }
    }
}
