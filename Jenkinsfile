@Library('hospital-Shard_library@main') _

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

        stage('Setup gcloud PATH (Bulletproof)') {
            steps {
                setupGcloud()
            }
        }

        stage('Checkout Code') {
            steps {
                checkout scm
            }
        }

        stage('SonarQube Analysis') {
            steps {
                sonarAnalysis()
            }
        }

        stage('SonarQube Quality Gate') {
    steps {
        timeout(time: 5, unit: 'MINUTES') {
            waitForQualityGate abortPipeline: true
        }
    }
}

        stage('Build & Push Docker Images') {
            steps {
                dockerBuildPush(DOCKER_REGISTRY, BUILD_TAG)
            }
        }

        stage('GCP Login & Fetch GKE Credentials') {
            steps {
                gcpLoginGke(GCP_PROJECT_ID, GKE_CLUSTER_NAME, GKE_REGION)
            }
        }

        stage('Terraform: Show Imported GKE Cluster') {
            steps {
                terraformShow()
            }
        }

        stage('Deploy to GKE') {
            steps {
                deployGke(BUILD_TAG)
            }
        }
    }

    post {
        always {
            sh 'docker system prune -f'
        }
    }
}
