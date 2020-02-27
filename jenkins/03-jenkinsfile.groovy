def artefactVersion;

def loadEnvironmentVariables(path){
    def props = readProperties  file: path
    keys= props.keySet()
    for(key in keys) {
        value = props["${key}"]
        env."${key}" = "${value}"
    }
}

podTemplate(label: "dotnet-31",
            cloud: "openshift",
            inheritFrom: "maven",
            containers: [
                containerTemplate(name: "jnlp",
                                image: "registry.redhat.io/dotnet/dotnet-31-jenkins-agent-rhel7:latest",
                                ttyEnabled: true,
                                envVars: [
                                    envVar(key: "CONTAINER_HEAP_PERCENT", value: "0.25")
                                ])
            ],
            volumes: [
            ]) {
    node("dotnet-31") {
        stage("checkout") {
          sh 'printenv'
          checkout scm
        }

        stage("gitversion") {
            sh 'dotnet tool install --global GitVersion.Tool --version 5.1.3'
            sh 'dotnet-gitversion /output buildserver'

            sh 'cat gitversion.properties'
            loadEnvironmentVariables 'gitversion.properties'
            artefactVersion = "${GitVersion_SemVer}-${GitVersion_ShortSha}";
        }
    }
}