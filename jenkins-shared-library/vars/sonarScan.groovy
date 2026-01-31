def call(Map config) {
    def scannerHome = tool 'SonarScanner'

    withSonarQubeEnv(config.sonarEnv ?: 'SonarQube') {
        sh """
            ${scannerHome}/bin/sonar-scanner \
              -Dsonar.projectKey=${config.projectKey} \
              -Dsonar.sources=${config.sources} \
              -Dsonar.exclusions=${config.exclusions} \
              -Dsonar.host.url=${config.hostUrl} \
              -Dsonar.token=${config.token}
        """
    }
}
