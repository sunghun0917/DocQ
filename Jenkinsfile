pipeline {
    agent any

    environment {
        DEPLOY_DIR  = 'deploy/compose'
        UPSTAGE_KEY = 'up_q2sUP2hxDqM5cqdtqafwxUOixs1zU'
    }

    stages {

        stage('Checkout') {
            steps {
                // Jenkins 설정에서 지정한 브랜치를 자동으로 체크아웃
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
                        docker-compose down || true
                        docker-compose up -d --build
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
            echo '❌ 배포 실패 (nginx.conf 파일/디렉토리 상태 확인)'
        }
        always {
            echo '빌드 종료'
        }
    }
}