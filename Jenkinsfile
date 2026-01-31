@Library('jenkins-shared-library') _

pipeline {
    agent any

    environment {
        GCP_PROJECT_ID   = 'hospital-project-485718'
        GKE_CLUSTER_NAME = 'cluster-1'
        GKE_REGION       = 'us-central1-a'
        DOCKER_REGISTRY  = 'sathish33'
        BUILD_TAG        = "${BUILD_ID}"
    }

    stages {

        stage('SonarQube Analysis') {
            steps {
                sonarScan(
                    projectKey: 'hospital-project',
                    sources: 'frontend-api,patient-api,appointment-api',
                    exclusions: '**/node_modules/**,**/dist/**,**/build/**',
                    hostUrl: 'http://20.75.196.235:9000',
                    token: env.SONAR_AUTH_TOKEN
                )
            }
        }

        stage('Docker Build & Push') {
            steps {
                dockerBuildPush(
                    credentialsId: 'sathish33',
                    tag: BUILD_TAG,
                    images: [
                        [dir: 'frontend-api',    name: "${DOCKER_REGISTRY}/frontend_api_image"],
                        [dir: 'patient-api',     name: "${DOCKER_REGISTRY}/patient_api_image"],
                        [dir: 'appointment-api', name: "${DOCKER_REGISTRY}/appointment_api_image"]
                    ]
                )
            }
        }

        stage('Trivy Scan') {
            steps {
                trivyScan(
                    tag: BUILD_TAG,
                    images: [
                        "${DOCKER_REGISTRY}/frontend_api_image",
                        "${DOCKER_REGISTRY}/patient_api_image",
                        "${DOCKER_REGISTRY}/appointment_api_image"
                    ]
                )
            }
        }

        stage('Deploy to GKE') {
            steps {
                gkeDeploy(
                    gcpCreds: 'GCP_SERVICE_ACCOUNT_KEY',
                    dockerCreds: 'sathish33',
                    projectId: GCP_PROJECT_ID,
                    cluster: GKE_CLUSTER_NAME,
                    zone: GKE_REGION,
                    tag: BUILD_TAG,
                    gcloudPath: '/home/swathireddy73/google-cloud-sdk/bin'
                )
            }
        }
    }
}
