Jenkins Shared Library: SonarQube + Trivy
======================================

This shared library provides two pipeline steps you can use from your Jenkins pipelines:

- `sonarScan` — runs SonarQube analysis using `sonar-scanner`.
- `trivyScan` — runs Trivy image scans for a list of images.

Installation (Jenkins)
----------------------
1. Add this repository as a *Global Pipeline Library* in Jenkins (Manage Jenkins → Configure System → Global Pipeline Libraries).
   - Name: `jenkins-shared-library` (or any name you prefer)
   - Default version: `master`
   - Retrieval: Modern SCM → point at this repo

Usage examples
--------------
In a pipeline that loads the library (configured globally):

Declarative example:

    @Library('jenkins-shared-library') _

    pipeline {
      agent any
      stages {
        stage('Run Sonar') {
          steps {
            script {
              sonarScan(
                projectKey: 'hospital-project',
                sources: 'frontend-api,patient-api,appointment-api',
                hostUrl: 'http://20.75.196.235:9000',
                token: env.SONAR_AUTH_TOKEN
              )
            }
          }
        }

        stage('Run Trivy') {
          steps {
            script {
              trivyScan(images: [
                'sathish33/frontend_api_image',
                'sathish33/patient_api_image'
              ], buildTag: env.BUILD_TAG)
            }
          }
        }
      }
    }

Notes
-----
- `sonarScan` will use the Jenkins `tool` named `SonarScanner`. Configure that under Manage Jenkins → Global Tool Configuration.
- `trivyScan` runs Trivy in Docker and needs Docker available on the agent with access to the images.
