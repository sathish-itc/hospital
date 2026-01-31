def call(Map config = [:]) {
    // Simple Trivy wrapper for learning/demo purposes.
    // config: images (list) optional, buildTag optional
    List images = config.get('images', null)
    String buildTag = config.get('buildTag', env.BUILD_TAG ?: 'latest')

    // If no images provided, use simple defaults (helps when learning locally).
    def registry = env.DOCKER_REGISTRY ?: ''
    if (!images) {
        images = [
            "${registry}/frontend_api_image",
            "${registry}/patient_api_image",
            "${registry}/appointment_api_image"
        ]
    }

    echo "Starting simple Trivy scans for ${images.size()} image(s)"
    images.each { imageName ->
        sh """
            echo "--- Trivy: Scanning ${imageName}:${buildTag} ---"
            docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:latest \
                image --severity HIGH,CRITICAL ${imageName}:${buildTag} || true
        """
    }
    echo "Simple Trivy scans completed (learning mode)"
}
