pipeline {
    agent any

    stages {
        stage('Deploy') {
            steps {
                sshagent(credentials: ['jenkins-ssh']) {
                    sh '''
                        ssh -o StrictHostKeyChecking=no ubuntu@localhost "
                            cd /home/ubuntu/S14P11D209/deploy/compose &&
                            docker compose down &&
                            docker compose up -d --build
                        "
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
    }
}
