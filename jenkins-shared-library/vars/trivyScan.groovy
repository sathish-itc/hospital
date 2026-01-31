def call(Map config) {
    config.images.each { img ->
        sh """
            docker run --rm \
              -v /var/run/docker.sock:/var/run/docker.sock \
              aquasec/trivy:latest image \
              --severity HIGH,CRITICAL \
              --ignore-unfixed \
              ${img}:${config.tag} || true
        """
    }
}
