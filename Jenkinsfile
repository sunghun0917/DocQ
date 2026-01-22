pipeline {
    agent any

    environment {
        // 배포 관련 경로 설정 (필요에 따라 수정)
        DEPLOY_DIR = 'deploy/compose'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Deploy') {
            steps {
                script {
                    dir("${DEPLOY_DIR}") {
                        // 1. 기존 컨테이너 중지 및 삭제 (실패해도 진행하도록 || true 추가)
                        // 호스트 설정에 따라 'docker-compose' 또는 'docker compose' 중 맞는 것을 사용하세요.
                        sh 'docker compose down || true'

                        // 2. 새로운 컨테이너 빌드 및 실행
                        sh 'docker compose up -d --build'
                    }
                }
            }
        }
    }

    post {
        success {
            echo '✅ 배포에 성공했습니다!'
        }
        failure {
            echo '❌ 배포 중 오류가 발생했습니다. 로그를 확인하세요.'
        }
        always {
            // 워크스페이스 정리
            cleanWs()
        }
    }
}