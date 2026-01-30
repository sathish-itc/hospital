pipeline {
    agent any

    environment {
        GCP_PROJECT_ID   = 'hospital-project-485718'
        GKE_CLUSTER_NAME = 'cluster-1'
        GKE_REGION       = 'us-central1-a'
        GCLOUD_HOME      = "${WORKSPACE}/gcloud"
        PATH             = "${WORKSPACE}/gcloud/google-cloud-sdk/bin:${env.PATH}"
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
                    // Use the SonarScanner installed in Jenkins
                    def scannerHome = tool 'SonarScanner'

                    // Inject SonarQube environment variables
                    withSonarQubeEnv('SonarQube') {
                        sh """
                            echo "Running SonarQube analysis..."
                            ${scannerHome}/bin/sonar-scanner \
                                -Dsonar.projectKey=hospital-project \
                                -Dsonar.sources=. \
                                -Dsonar.host.url=http://20.75.196.235:9000 \
                                -Dsonar.login=$SONAR_AUTH_TOKEN
                        """
                    }
                }
            }
        }

        stage('SonarQube Quality Gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
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
                            [dir: 'frontend-api',    name: 'sathish33/frontend_api_image'],
                            [dir: 'patient-api',     name: 'sathish33/patient_api_image'],
                            [dir: 'appointment-api', name: 'sathish33/appointment_api_image']
                        ]

                        images.each {
                            sh """
                                env -u DOCKER_API_VERSION docker build -t ${it.name}:${BUILD_ID} ${it.dir}
                                env -u DOCKER_API_VERSION docker push ${it.name}:${BUILD_ID}
                            """
                        }
                    }
                }
            }
        }

        stage('Install gcloud CLI (no sudo)') {
            steps {
                sh '''
                    if [ ! -d "$GCLOUD_HOME/google-cloud-sdk" ]; then
                        echo "Installing gcloud locally..."
                        mkdir -p $GCLOUD_HOME
                        curl -sSL https://sdk.cloud.google.com | bash -s -- --disable-prompts --install-dir=$GCLOUD_HOME
                    else
                        echo "gcloud already installed"
                    fi

                    gcloud version
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

                        echo "Installing GKE auth plugin..."
                        gcloud components install gke-gcloud-auth-plugin --quiet

                        echo "Enabling GKE auth plugin..."
                        export USE_GKE_GCLOUD_AUTH_PLUGIN=True

                        echo "Fetching GKE cluster credentials..."
                        gcloud container clusters get-credentials $GKE_CLUSTER_NAME --zone $GKE_REGION

                        echo "Verifying access..."
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

                        echo "Deploying Backend..."
                        helm upgrade --install appointment appointment-api/helm \
                            --namespace hospital \
                            -f appointment-api/helm/values.yaml \
                            --set image.tag=$BUILD_ID \
                            --set imagePullSecrets[0].name=dockerhub-secret

                        echo "Deploying Backend..."
                        helm upgrade --install patient patient-api/helm \
                            --namespace hospital \
                            -f patient-api/helm/values.yaml \
                            --set image.tag=$BUILD_ID \
                            --set imagePullSecrets[0].name=dockerhub-secret

                        echo "Deploying UI..."
                        helm upgrade --install frontend frontend-api/helm \
                            --namespace hospital \
                            -f frontend-api/helm/values.yaml \
                            --set image.tag=$BUILD_ID \
                            --set imagePullSecrets[0].name=dockerhub-secret
                    '''
                }
            }
        }
    }

    post {
        always {
            echo 'Cleaning up Docker cache'
            sh 'env -u DOCKER_API_VERSION docker system prune -f'
        }
    }
}
