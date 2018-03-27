#!/usr/bin/env groovy
package multi

def branch = 'master'
def repoUrl = 'https://github.com/jglick/simple-maven-project-with-tests.git'
def mavenHome

properties([
        parameters([
                choice(name: 'ENV', defaultValue: 'dev', choices: "dev\ntest\nstage\nproduction\nhotfix", description: ''),
                choice(name: 'SKIP_TEST', defaultValue: 'true', choices: "true\nfalse", description: ''),
                string(name: 'VERSION', defaultValue: '', description: ''),
                string(name: 'HOT_FIX_BRANCH', defaultValue: '', description: ''),
        ])
])

try {
    node('master') {
        mavenHome = tool 'M3'
        stage('Checking') {
            if (params.ENV == 'stage') {
                if (params.VERSION == '') {
                    error("stage环境下必须指定版本号")
                }
            }
            if (params.ENV == 'hotfix') {
                if (params.HOT_FIX_BRANCH == '') {
                    error("hotfix环境必须指定hotfix分支名")
                }
            }
            if (params.ENV == 'stage') {
                try {
                    timeout(time: 15, unit: 'SECONDS') {
                        input message: '确定要发布吗',
                                parameters: [[$class      : 'BooleanParameterDefinition',
                                              defaultValue: false,
                                              description : '点击将会发布Stage',
                                              name        : '发布Stage']]
                    }
                } catch (err) {
                    def user = err.getCauses()[0].getUser()
                    error "Aborted by:\n ${user}"
                }
            }
        }
        stage('Clean workspace') {
            deleteDir()
            sh 'ls -lah'
        }
        stage('Checkout source') {
            echo "About to clone code from url: ${repoUrl} branch: ${branch}"
            if (params.ENV != 'stage') {
                git branch: branch, url: repoUrl
            } else {
                git branch: 'master', url: repoUrl
            }
        }
        stage('Build') {
            echo 'About '
            if (params.EVN == 'stage') {
                echo 'About to replace SNAPSHOT version and set current version with ${version}'
                sh "${mavenHome}/bin/mvn versions:set -DnewVersion=${version}"
                sh "sed -i 's|-SNAPSHOT||g' ./**/pom.xml"
            }
            sh "${mavenHome}/bin/mvn clean package -DskipTests"
        }
        stage('Test') {
            parallel(
                    unittest: {
                        // node {
                        if (params.SKIP_TEST != 'true') {
                            echo 'About to run unit test'
                            sh "${mavenHome}/bin/mvn test"
                            junit './surefire-reports/*.xml'
                        } else {
                            echo 'Unit test skipped'
                        }
                        // }
                    },
                    autotest: {
                        // node {
                        echo 'About to run auto test'
                        //more auto test
                        // }
                    }
            )
        }
        stage('Sonar') {
            echo 'About to run sonar'
            //will use the sonar configured in settings.xml
            // sh "${mavenHome}/bin/mvn sonar:sonar"
        }
        stage('Deploy') {

        }
    }
} catch (exc) {
    echo "Caught: ${exc}"

    String recipient = 'infra@lists.jenkins-ci.org'

    mail subject: "${env.JOB_NAME} (${env.BUILD_NUMBER}) failed",
            body: "It appears that ${env.BUILD_URL} is failing, somebody should do something about that",
            to: recipient,
            replyTo: recipient,
            from: 'noreply@ci.jenkins.io'

    /* Rethrow to fail the Pipeline properly */
    throw exc
}
