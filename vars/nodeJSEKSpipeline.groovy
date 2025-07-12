def call(Map configMap){
    pipeline {
        agent {
            label 'AGENT-1'
        }
    
        environment {
            appVersion = '' //global variable
            environment = 'dev'
            project = configMap.get("project")
            component = configMap.get("component")
            
            
        }

        stages {
            stage ('read package json') { //Pipeline Utility Steps plugin.
                steps {
                    script {
                        def packageJson = readJSON file: 'package.json'
                        appVersion = packageJson.version
                        echo "app version: ${appVersion}"
                    }
                }
            }
        /* stage ('sonar Analysis'){
                environment {
                    SCANNER_HOME = tool 'sonar-6.0' // the name used while setup of scanner 
                }
                steps{
                    withSonarQubeEnv('Sonar') {
                        sh '${SCANNER_HOME}/bin/sonar-scanner' 
                
                    }
                    // sonar-scanner check for files sonar-project.properties     
                }
            }
            stage("Quality Gate") {
                steps {
                    timeout(time: 5, unit: 'MINUTES') {
                        // Parameter indicates whether to set pipeline to UNSTABLE if Quality Gate fails
                        // true = set pipeline to UNSTABLE, false = don't
                        waitForQualityGate abortPipeline: true
                    }
                }
            } */

            stage ('docker image build') {
                steps {
                    withAWS(region: 'us-east-1', credentials: 'aws-cred') {
                        sh """
                        aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 905418383993.dkr.ecr.us-east-1.amazonaws.com
                        docker build -t ${project}/${component}:${appVersion} .
                        docker images
                        docker tag ${project}/${component}:${appVersion} 905418383993.dkr.ecr.us-east-1.amazonaws.com/${project}/${component}:${appVersion}
                        docker push 905418383993.dkr.ecr.us-east-1.amazonaws.com/${project}/${component}:${appVersion}
                        """
                    }
                }

            }
            /*
            stage ('deploy') {
                steps {
                    withAWS(region: 'us-east-1', credentials: 'aws-cred') {
                        sh """
                        aws eks update-kubeconfig --region us-east-1 --name ${project}-${environment}
                        cd helm
                        sed -i 's/IMAGE_VERSION/${appVersion}/g' values-${environment}.yaml
                        helm upgrade --install ${component} -n ${project} -f values-${environment}.yaml .
                        """
                    }
                    // i - replace in IMAGE_VERSION value with $appversion value , target file is values-dev.yaml          
                }
            }

            stage ('trigger frontend') {
                steps {
                    echo 'triggering frontend'
                    build job: "frontend", wait: true
                }
                
            }
            */
            stage ('deploy-cd') {
                steps {
                    echo 'triggering backend cd'
                    build job: "../${component}-cd",parameters: [
                        string(name: version, values: "$appVersion"),
                        string(name: ENV, values: "dev")], wait: true
                    // if the backend-cd is not in the same folder: ../backend
                    // if in same folder : backend-cd
                }
                
            }


        }
        
        post { 
            always { 
                echo 'Deleting workspace artifacts...!'  // this always executes if pipeline fail or success.
                deleteDir()

            }

            failure {
                echo 'pipeline is fail!'  // this will executes if pipeline fail
            }

            success {
                echo 'pipeline is success!'  // this will executes if pipeline success
            }

        }
    }

}
