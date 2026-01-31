def call(Map config) {
    withCredentials([
        usernamePassword(
            credentialsId: config.credentialsId,
            usernameVariable: 'DOCKER_USER',
            passwordVariable: 'DOCKER_PASS'
        )
    ]) {
        sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'

        config.images.each {
            sh """
                docker build -t ${it.name}:${config.tag} ${it.dir}
                docker push ${it.name}:${config.tag}
            """
        }
    }
}
