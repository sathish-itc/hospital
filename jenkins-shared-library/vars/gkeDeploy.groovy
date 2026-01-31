def call(Map config) {
    withCredentials([
        file(credentialsId: config.gcpCreds, variable: 'GCP_KEY_FILE'),
        usernamePassword(
            credentialsId: config.dockerCreds,
            usernameVariable: 'DOCKER_USER',
            passwordVariable: 'DOCKER_PASS'
        )
    ]) {
        sh """
            export PATH=${config.gcloudPath}:\$PATH
            export USE_GKE_GCLOUD_AUTH_PLUGIN=True

            gcloud auth activate-service-account --key-file="\$GCP_KEY_FILE"
            gcloud config set project ${config.projectId}
            gcloud container clusters get-credentials ${config.cluster} --zone ${config.zone}

            kubectl create namespace hospital --dry-run=client -o yaml | kubectl apply -f -

            kubectl create secret docker-registry dockerhub-secret \
              --docker-server=index.docker.io \
              --docker-username=\$DOCKER_USER \
              --docker-password=\$DOCKER_PASS \
              --namespace hospital \
              --dry-run=client -o yaml | kubectl apply -f -

            kubectl apply -f mysql/deployment.yaml

            helm upgrade --install appointment appointment-api/helm \
              --namespace hospital --set image.tag=${config.tag}

            helm upgrade --install patient patient-api/helm \
              --namespace hospital --set image.tag=${config.tag}

            helm upgrade --install frontend frontend-api/helm \
              --namespace hospital --set image.tag=${config.tag}
        """
    }
}
