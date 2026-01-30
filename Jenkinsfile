pipeline {
    agent any

    environment {
        GCP_PROJECT_ID   = 'hospital-project-485718'
        GKE_CLUSTER_NAME = 'cluster-1'
        GKE_REGION       = 'us-central1-a'
        GCLOUD_HOME      = "${WORKSPACE}/gcloud"
        PATH             = "${WORKSPACE}/gcloud/google-cloud-sdk/bin:${env.PATH}"
        DOCKER_REGISTRY  = 'sathish33'
        BUILD_TAG        = "${BUILD_ID}"
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
                            echo "Running SonarQube analysis..."
                            ${scannerHome}/bin/sonar-scanner \
                                -Dsonar.projectKey=hospital-project \
                                -Dsonar.sources=frontend-api,patient-api,appointment-api \
                                -Dsonar.exclusions=**/node_modules/**,**/dist/**,**/build/**,**/*.min.js,**/*.spec.ts,**/e2e/**,**/.scannerwork/** \
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
                    script {
                        sh 'echo "$DOCKER_PASS" | env -u DOCKER_API_VERSION docker login -u "$DOCKER_USER" --password-stdin'

                        def images = [
                            [dir: 'frontend-api',    name: "${DOCKER_REGISTRY}/frontend_api_image"],
                            [dir: 'patient-api',     name: "${DOCKER_REGISTRY}/patient_api_image"],
                            [dir: 'appointment-api', name: "${DOCKER_REGISTRY}/appointment_api_image"]
                        ]

                        images.each {
                            sh """
                                env -u DOCKER_API_VERSION docker build -t ${it.name}:${BUILD_TAG} ${it.dir}
                                env -u DOCKER_API_VERSION docker push ${it.name}:${BUILD_TAG}
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

                    images.each { imageName ->
                        sh """
                            echo "🔍 Scanning ${imageName}:${BUILD_TAG} with Trivy..."
                            docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:latest \
                                image --ignore-unfixed --severity HIGH,CRITICAL --format table ${imageName}:${BUILD_TAG} || true
                            docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:latest \
                                image --ignore-unfixed --severity HIGH,CRITICAL --format json --output ${imageName.replace('/', '_')}-trivy.json ${imageName}:${BUILD_TAG} || true
                        """
                    }
                }
            }
        }

        stage('Install gcloud CLI & GKE Plugin') {
            steps {
                sh '''
                    if [ ! -d "$GCLOUD_HOME/google-cloud-sdk" ]; then
                        echo "Installing gcloud locally..."
                        mkdir -p $GCLOUD_HOME
                        curl -sSL https://sdk.cloud.google.com | bash -s -- --disable-prompts --install-dir=$GCLOUD_HOME
                    else
                        echo "gcloud already installed"
                    fi

                    # Add gcloud to PATH and enable GKE auth plugin
                    echo "export PATH=$GCLOUD_HOME/google-cloud-sdk/bin:\$PATH" >> $BASH_ENV
                    echo "export USE_GKE_GCLOUD_AUTH_PLUGIN=True" >> $BASH_ENV
                    export PATH=$GCLOUD_HOME/google-cloud-sdk/bin:$PATH
                    export USE_GKE_GCLOUD_AUTH_PLUGIN=True

                    gcloud version
                    gcloud components install gke-gcloud-auth-plugin --quiet || true

                    # Verify installation
                    command -v gke-gcloud-auth-plugin
                '''
            }
        }

        stage('GCP Login & Fetch GKE Credentials') {
            steps {
                withCredentials([
                    file(credentialsId: 'GCP_SERVICE_ACCOUNT_KEY', variable: 'GCP_KEY_FILE')
                ]) {
                    sh '''
                        echo "Activating GCP service account..."
                        gcloud auth activate-service-account --key-file="$GCP_KEY_FILE"
                        gcloud config set project $GCP_PROJECT_ID

                        echo "Fetching GKE cluster credentials..."
                        gcloud container clusters get-credentials $GKE_CLUSTER_NAME --zone $GKE_REGION

                        echo "Verifying kubectl access..."
                        kubectl version --client
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
                        echo "Creating namespace..."
                        kubectl create namespace hospital --dry-run=client -o yaml | kubectl apply -f -

                        echo "Creating Docker registry secret..."
                        kubectl create secret docker-registry dockerhub-secret \
                          --docker-server=index.docker.io \
                          --docker-username=$DOCKER_USER \
                          --docker-password=$DOCKER_PASS \
                          --namespace hospital \
                          --dry-run=client -o yaml | kubectl apply -f -

                        echo "Deploying MySQL..."
                        kubectl apply -f mysql/deployment.yaml

                        echo "Deploying Appointment API..."
                        helm upgrade --install appointment appointment-api/helm \
                            --namespace hospital \
                            -f appointment-api/helm/values.yaml \
                            --set image.tag=$BUILD_TAG \
                            --set imagePullSecrets[0].name=dockerhub-secret

                        echo "Deploying Patient API..."
                        helm upgrade --install patient patient-api/helm \
                            --namespace hospital \
                            -f patient-api/helm/values.yaml \
                            --set image.tag=$BUILD_TAG \
                            --set imagePullSecrets[0].name=dockerhub-secret

                        echo "Deploying Frontend UI..."
                        helm upgrade --install frontend frontend-api/helm \
                            --namespace hospital \
                            -f frontend-api/helm/values.yaml \
                            --set image.tag=$BUILD_TAG \
                            --set imagePullSecrets[0].name=dockerhub-secret
                    '''
                }
            }
        }
    }

    post {
        always {
            echo '🧹 Cleaning up Docker cache'
            sh 'env -u DOCKER_API_VERSION docker system prune -f'
        }
    }
}
