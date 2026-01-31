def call(Map config = [:]) {
    // config: projectKey, sources, exclusions, hostUrl, token, scannerTool (optional)
    String projectKey = config.get('projectKey', 'hospital-project')
    String sources = config.get('sources', 'frontend-api,patient-api,appointment-api')
    String exclusions = config.get('exclusions', "**/node_modules/**,**/dist/**,**/build/**,**/*.min.js,**/*.spec.ts,**/e2e/**,**/.scannerwork/**")
    String hostUrl = config.get('hostUrl', 'http://localhost:9000')
    String token = config.get('token', env.SONAR_AUTH_TOKEN)
    String scannerTool = config.get('scannerTool', 'SonarScanner')

    // This function is intended to be called from a declarative pipeline inside a `script {}` block.
    // Do not create nested `node` or `stage` blocks here.
    def scannerHome = tool scannerTool
    withSonarQubeEnv('SonarQube') {
        sh """
            echo "Running SonarQube analysis..."
            ${scannerHome}/bin/sonar-scanner \
                -Dsonar.projectKey=${projectKey} \
                -Dsonar.sources=${sources} \
                -Dsonar.exclusions=${exclusions} \
                -Dsonar.host.url=${hostUrl} \
                -Dsonar.token=${token}
        """
    }
}
