pipeline {
    agent any

    environment {
        DEPLOY_DIR = 'deploy/compose'

        // Jenkins Credentials
        EC2_HOST_IP     = credentials('ec2-host-ip')
        DB_HOST         = credentials('db-host')
        DB_PORT         = credentials('db-port')
        DB_NAME         = credentials('db-name')
        DB_USER         = credentials('db-user')
        DB_PASS         = credentials('db-pass')
        UPSTAGE_API_KEY = credentials('upstage-api-key')
        OPENAI_API_KEY  = credentials('openai-api-key')

        // Non-sensitive
        OPENAI_BASE_URL = 'https://gms.ssafy.io/gmsapi/api.openai.com/v1'
        MINERU_API_URL  = 'http://localhost:18000/file_parse'
    }

    stages {

        stage('Checkout') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                checkout scm
            }
        }

        stage('Prepare Env Files') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                }
            }

            steps {
                sh '''
                    set -e

                    # AI service env
                    mkdir -p docq-ai
                    cat > docq-ai/.env << EOF
UPSTAGE_API_KEY=${UPSTAGE_API_KEY}
OPENAI_API_KEY=${OPENAI_API_KEY}
OPENAI_BASE_URL=${OPENAI_BASE_URL}
MINERU_API_URL=${MINERU_API_URL}
EOF

                    # Backend env (prod)
                    cat > ${DEPLOY_DIR}/.env << EOF
EC2_HOST_IP=${EC2_HOST_IP}
SPRING_PROFILES_ACTIVE=prod
DB_HOST=${DB_HOST}
DB_PORT=${DB_PORT}
DB_NAME=${DB_NAME}
DB_USER=${DB_USER}
DB_PASS=${DB_PASS}
EOF
                '''
            }
        }

        stage('Build') {
            when {
                anyOf {
                    branch 'master'
                    branch 'develop'
                }
            }

            steps {
                dir("${DEPLOY_DIR}") {
                    sh '''
                        set -e
                        docker-compose build
                    '''
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'master'
            }
            steps {
                dir("${DEPLOY_DIR}") {
                    sh '''
                        set -e
                        docker-compose down --remove-orphans || true
                        docker volume prune -f || true
                        docker-compose up -d --build
                    '''
                }
            }
        }


    post {
        success {
            script {
                if (env.BRANCH_NAME == 'master') {
                    echo 'CI/CD succeeded (production deployed)'
                } else if (env.BRANCH_NAME == 'develop') {
                    echo 'CI succeeded (build only)'
                } else {
                    echo 'Skipped branch'
                }
            }
        }

        failure {
            script {
                if (env.BRANCH_NAME == 'master') {
                    sh '''
                        echo "[DEBUG] docker-compose ps"
                        cd deploy/compose && docker-compose ps || true

                        echo "[DEBUG] backend logs"
                        cd deploy/compose && docker-compose logs --tail=200 backend || true

                        echo "[DEBUG] nginx logs"
                        docker logs nginx --tail=100 || true
                    '''
                }
            }
        }

        always {
            echo "Pipeline finished for branch: ${env.BRANCH_NAME}"
        }
    }
}