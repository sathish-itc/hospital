pipeline {
    agent any

    environment {
        // GCP variables
        GCP_PROJECT_ID = 'hospital-project'
        GKE_CLUSTER_NAME = 'cluster-1'
        GKE_REGION = 'us-central1-a'

        // DockerHub credentials (stored in Jenkins credentials)
        DOCKER_USERNAME = credentials('dockerhub-username-id')
        DOCKER_PASSWORD = credentials('dockerhub-password-id')
        
        // GCP Service Account Key (stored in Jenkins as Secret File)
        GCP_SA_KEY = credentials('gcp-service-account-key-id')
    }

    stages {

        stage('Checkout Code') {
            steps {
                checkout scm
            }
        }

        // ======================================
        // 1. Build & Push Docker Images
        // ======================================
        stage('Build & Push Docker Images') {
            steps {
                script {
                    def images = [
                        [path: 'hospital-frontend', name: 'sathish33/frontend_api_image'],
                        [path: 'patient-api', name: 'sathish33/patient_api_image'],
                        [path: 'appointment-api', name: 'sathish33/appointment_api_image']
                    ]
                    images.each { img ->
                        sh """
                            docker login -u $DOCKER_USERNAME -p $DOCKER_PASSWORD
                            docker build -t ${img.name}:${env.BUILD_ID} ${img.path}
                            docker push ${img.name}:${env.BUILD_ID}
                        """
                    }
                }
            }
        }

        // ======================================
        // 2. GCP Login & GKE Config
        // ======================================
        stage('GCP Login') {
            steps {
                // The GCP key is stored as a secret file in Jenkins
                withCredentials([file(credentialsId: 'gcp-service-account-key-id', variable: 'GCP_KEY_FILE')]) {
                    sh """
                        echo "Authenticating to GCP"
                        gcloud auth activate-service-account --key-file=$GCP_KEY_FILE
                        gcloud config set project $GCP_PROJECT_ID
                        gcloud container clusters get-credentials $GKE_CLUSTER_NAME --region $GKE_REGION
                    """
                }
            }
        }

        // ======================================
        // 3. Deploy to GKE
        // ======================================
        stage('Deploy to GKE') {
            steps {
                script {
                    // Create namespace & DockerHub secret
                    sh """
                        kubectl create namespace hospital --dry-run=client -o yaml | kubectl apply -f -
                        kubectl create secret docker-registry dockerhub-secret \
                          --docker-server=index.docker.io \
                          --docker-username=$DOCKER_USERNAME \
                          --docker-password=$DOCKER_PASSWORD \
                          --namespace hospital \
                          --dry-run=client -o yaml | kubectl apply -f -
                    """

                    // Deploy MySQL
                    sh 'kubectl apply -f k8s/mysql/mysql-deployment.yaml'

                    // Deploy UI
                    sh """
                        helm upgrade --install ui ./hpm \
                          --namespace hospital \
                          -f hpm/values-ui.yaml \
                          --set image.tag=${env.BUILD_ID} \
                          --set imagePullSecrets[0].name=dockerhub-secret
                    """

                    // Deploy Patient API
                    sh """
                        helm upgrade --install patient-api ./hpm \
                          --namespace hospital \
                          -f hpm/values-patient_api.yaml \
                          --set image.tag=${env.BUILD_ID} \
                          --set imagePullSecrets[0].name=dockerhub-secret
                    """

                    // Deploy Appointment API
                    sh """
                        helm upgrade --install appointment-api ./hpm \
                          --namespace hospital \
                          -f hpm/values-appointment_api.yaml \
                          --set image.tag=${env.BUILD_ID} \
                          --set imagePullSecrets[0].name=dockerhub-secret
                    """
                }
            }
        }
    }

    post {
        always {
            echo "Cleaning up Docker images locally"
            sh 'docker system prune -f'
        }
    }
}
