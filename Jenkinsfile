pipeline {
    agent any

    environment {
        // GCP variables
        GCP_PROJECT_ID = 'hospital-project'
        GKE_CLUSTER_NAME = 'cluster-1'
        GKE_REGION = 'us-central1-a'
    }

    stages {

        // ========================
        // 1. Checkout Code
        // ========================
        stage('Checkout Code') {
            steps {
                checkout scm
            }
        }

        // ========================
        // 2. Build & Push Docker Images
        // ========================
        stage('Build & Push Docker Images') {
            steps {
                // Use your working style credentials here
                withCredentials([usernamePassword(credentialsId: 'sathish33', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    script {
                        def images = [
                            [path: 'frontend-api', name: 'sathish33/frontend_api_image'],
                            [path: 'patient-api', name: 'sathish33/patient_api_image'],
                            [path: 'appointment-api', name: 'sathish33/appointment_api_image']
                        ]

                        // Login once
                        sh 'echo "${DOCKER_PASS}" | docker login -u "${DOCKER_USER}" --password-stdin'

                        images.each { img ->
                            sh """
                                docker build -t ${img.name}:${env.BUILD_ID} ${img.path}
                                docker push ${img.name}:${env.BUILD_ID}
                            """
                        }
                    }
                }
            }
        }

        // ========================
        // 3. GCP Login & GKE Config
        // ========================
        stage('GCP Login & GKE Config') {
            steps {
                withCredentials([file(credentialsId: 'GCP_SERVICE_ACCOUNT_KEY', variable: 'GCP_KEY_FILE')]) {
                    sh """
                        echo "Authenticating to GCP"
                        gcloud auth activate-service-account --key-file=$GCP_KEY_FILE
                        gcloud config set project $GCP_PROJECT_ID
                        gcloud container clusters get-credentials $GKE_CLUSTER_NAME --region $GKE_REGION
                    """
                }
            }
        }

        // ========================
        // 4. Deploy to GKE
        // ========================
        stage('Deploy to GKE') {
            steps {
                script {
                    // Create namespace & DockerHub secret
                    sh """
                        kubectl create namespace hospital --dry-run=client -o yaml | kubectl apply -f -
                        kubectl create secret docker-registry dockerhub-secret \
                          --docker-server=index.docker.io \
                          --docker-username=$DOCKER_USER \
                          --docker-password=$DOCKER_PASS \
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

    // ========================
    // Post-build cleanup
    // ========================
    post {
        always {
            echo "Cleaning up local Docker images"
            sh 'docker system prune -f'
        }
    }
}
