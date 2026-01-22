pipeline {
    agent any

    // Tools 섹션에 설정한 도구 이름을 넣으면
    // Jenkins가 자동으로 Docker 바이너리 경로를 PATH에 추가합니다.
    tools {
        dockerTool 'my-docker'
    }

    stages {
        stage('Deploy') {
            steps {
                script {
                    // 이제 docker 명령어를 사용할 수 있습니다.
                    dir('deploy/compose') {
                        sh 'docker compose down'
                        sh 'docker compose up -d'
                    }
                }
            }
        }
    }
}