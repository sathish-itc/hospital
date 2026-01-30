pipeline {
    agent any

    environment {
        GCP_PROJECT_ID   = 'hospital-project-485718'
        GKE_CLUSTER_NAME = 'cluster-1'
        GKE_REGION       = 'us-central1-a'
        DOCKER_REGISTRY  = 'sathish33'
        BUILD_TAG        = "${BUILD_ID}"
        USE_GKE_GCLOUD_AUTH_PLUGIN = 'True'
    }

    stages {

        stage('Checkout Code') {
            steps {
                checkout scm
            }
        }

        stage('SonarQube Analysis') {
            steps {
                script {
                    def scannerHome = tool 'SonarScanner'
                    withSonarQubeEnv('SonarQube') {
                        sh """
                            ${scannerHome}/bin/sonar-scanner \
                              -Dsonar.projectKey=hospital-project \
                              -Dsonar.sources=frontend-api,patient-api,appointment-api \
                              -Dsonar.exclusions=**/node_modules/**,**/dist/**,**/build/** \
                              -Dsonar.host.url=http://20.75.196.235:9000 \
                              -Dsonar.token=$SONAR_AUTH_TOKEN
                        """
                    }
                }
            }
        }

        stage('Build & Push Docker Images') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'sathish33',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                    '''

                    script {
                        def images = [
                            [dir: 'frontend-api',    name: "${DOCKER_REGISTRY}/frontend_api_image"],
                            [dir: 'patient-api',     name: "${DOCKER_REGISTRY}/patient_api_image"],
                            [dir: 'appointment-api', name: "${DOCKER_REGISTRY}/appointment_api_image"]
                        ]

                        images.each {
                            sh """
                                docker build -t ${it.name}:${BUILD_TAG} ${it.dir}
                                docker push ${it.name}:${BUILD_TAG}
                            """
                        }
                    }
                }
            }
        }

        stage('Container Security Scan (Trivy)') {
            steps {
                script {
                    def images = [
                        "${DOCKER_REGISTRY}/frontend_api_image",
                        "${DOCKER_REGISTRY}/patient_api_image",
                        "${DOCKER_REGISTRY}/appointment_api_image"
                    ]

                    images.each { img ->
                        sh """
                            docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:latest \
                              image --severity HIGH,CRITICAL --ignore-unfixed ${img}:${BUILD_TAG} || true
                        """
                    }
                }
            }
        }

        stage('GCP Login & Fetch GKE Credentials') {
            steps {
                withCredentials([
                    file(credentialsId: 'GCP_SERVICE_ACCOUNT_KEY', variable: 'GCP_KEY_FILE')
                ]) {
                    sh '''
                        gcloud version
                        gcloud auth activate-service-account --key-file="$GCP_KEY_FILE"
                        gcloud config set project $GCP_PROJECT_ID
                        gcloud container clusters get-credentials $GKE_CLUSTER_NAME --zone $GKE_REGION

                        kubectl get nodes
                    '''
                }
            }
        }

        stage('Deploy to GKE') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'sathish33',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    sh '''
                        kubectl create namespace hospital --dry-run=client -o yaml | kubectl apply -f -

                        kubectl create secret docker-registry dockerhub-secret \
                          --docker-server=index.docker.io \
                          --docker-username=$DOCKER_USER \
                          --docker-password=$DOCKER_PASS \
                          --namespace hospital \
                          --dry-run=client -o yaml | kubectl apply -f -

                        kubectl apply -f mysql/deployment.yaml

                        helm upgrade --install appointment appointment-api/helm \
                          --namespace hospital \
                          --set image.tag=$BUILD_TAG

                        helm upgrade --install patient patient-api/helm \
                          --namespace hospital \
                          --set image.tag=$BUILD_TAG

                        helm upgrade --install frontend frontend-api/helm \
                          --namespace hospital \
                          --set image.tag=$BUILD_TAG
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker system prune -f'
        }
    }
}
