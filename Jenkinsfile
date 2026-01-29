pipeline {
    agent any

    environment {
        // Project / Cluster
        GCP_PROJECT        = 'hospital-project'
        GKE_CLUSTER_NAME   = 'cluster-1'
        GKE_ZONE           = 'us-central1-a'

        // Docker
        DOCKER_USER        = 'sathish33'
        IMAGE_TAG          = '17'
    }

    stages {

        stage('Checkout Code') {
            steps {
                checkout scm
            }
        }

        stage('Build & Push Docker Images') {
            steps {
                withCredentials([
                    string(credentialsId: 'sathish33', variable: 'DOCKER_PASS')
                ]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u $DOCKER_USER --password-stdin

                        docker build -t $DOCKER_USER/frontend_api_image:$IMAGE_TAG frontend-api
                        docker push $DOCKER_USER/frontend_api_image:$IMAGE_TAG

                        docker build -t $DOCKER_USER/patient_api_image:$IMAGE_TAG patient-api
                        docker push $DOCKER_USER/patient_api_image:$IMAGE_TAG

                        docker build -t $DOCKER_USER/appointment_api_image:$IMAGE_TAG appointment-api
                        docker push $DOCKER_USER/appointment_api_image:$IMAGE_TAG
                    '''
                }
            }
        }

        stage('Install gcloud CLI (if needed)') {
            steps {
                sh '''
                    if ! command -v gcloud &> /dev/null; then
                        echo "Installing gcloud..."
                        curl -sSL https://sdk.cloud.google.com | bash
                        source $HOME/google-cloud-sdk/path.bash.inc
                    else
                        echo "gcloud already installed"
                    fi

                    gcloud version
                '''
            }
        }

        stage('GCP Login & GKE Config') {
            steps {
                withCredentials([
                    file(credentialsId: 'GCP_SERVICE_ACCOUNT_KEY', variable: 'GCP_KEY_FILE')
                ]) {
                    sh '''
                        echo "Authenticating to GCP..."
                        gcloud auth revoke --all || true
                        gcloud auth activate-service-account --key-file="$GCP_KEY_FILE"

                        gcloud config set project $GCP_PROJECT

                        echo "Fetching GKE credentials..."
                        gcloud container clusters get-credentials $GKE_CLUSTER_NAME \
                          --zone $GKE_ZONE \
                          --project $GCP_PROJECT
                    '''
                }
            }
        }

        stage('Deploy to GKE') {
            steps {
                sh '''
                    kubectl version --client
                    kubectl get nodes

                    kubectl apply -f k8s/
                '''
            }
        }
    }

    post {
        always {
            echo "Cleaning up Docker cache"
            sh 'docker system prune -f || true'
        }
    }
}
