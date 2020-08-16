# DevOps with OpenShift in practice

***Attention: We creted this branch to have a minimal version avoiding pod templates because of a recently appeared permission denied error on OpenShift Online. If you want to try it on a own cluster try the master branch instead.***

This workshop will guide you through the setup of a CI/CD pipeline on OpenShift Online.

***Prerequisites:***

* Make sure you have setup and an OpenShift Online Account with a free tier project.
* You entered all required fields inclusive password in your OpenShift account (required to create an image pull secret)
* You installed Jenkins through the OpenShift Catalog.
* You updated the Kubernetes plugin on Jenkins to the newest version (Not required, but triggering builds will only run properly through the Dashboard if you have not updated the plugin).
* You installed the `oc cli` and have logged into the OpenShift online cluster.

You can run following command `oc status`. You should see something like that:

```bash
In project DevopsFusionLeh (devopsfusionleh) on server https://api.us-west-1.starter.openshift-online.com:6443

https://demo-app-devopsfusionleh.apps.us-west-1.starter.openshift-online.com (redirects) to pod port 8080 (svc/demo-app)
  dc/demo-app deploys image-registry.openshift-image-registry.svc:5000/devopsfusionleh/demo-app:0.1.0-9ab70fd <-
    bc/demo-app-docker source builds uploaded code on registry.redhat.io/dotnet/dotnet-31-rhel7:latest 
    deployment #6 deployed 41 minutes ago - 1 pod
    deployment #5 failed 41 minutes ago: newer deployment was found running
    deployment #4 deployed 23 hours ago

svc/jenkins-jnlp - 172.30.255.244:50000
https://jenkins-devopsfusionleh.apps.us-west-1.starter.openshift-online.com (redirects) (svc/jenkins)
  dc/jenkins deploys openshift/jenkins:2 
    deployment #1 deployed 13 days ago - 1 pod

bc/devops-fusion-sample-pipeline is a Jenkins Pipeline
  build #91 succeeded 40 minutes ago
  build #90 failed about an hour ago


4 infos identified, use 'oc status --suggest' to see details.
```

## Fork the github repository

We prepared a git repository with simple demo app on github fro you guys. However, you will need to commit your changes into your own repository during the workshop.

Please create a fork of the following git repository:

`https://github.com/cicd-with-openshift-at-devopsfusion/workshop`

The repository contains following relevant files and folders:

* ***src/*** The `src`folder contains a simple DotNetCore 3.1 Mvc App with the Serilog framework for semantic logging enabled.
* ***openshift/pipeline/pipeline.buildconfig.yml*** The OpenShift build config object, which sets up the Jenkins pipeline.
* ***jenkinsfile.groovy*** The jenkins file used for the Jenkins pipeline. You will work with that file to setup the final pipeline.
* ***jenkins/*** The `jenkins`folder contains the step by step templates you will need to setup the final jenkins pipeline.
* ***app-docker.template.yml*** An OpenShift template containing the build config for the S2I docker build with binary input. This is used to build the docker image from the pipeline.
* ***demo-app.template.yml*** An openShift template containing the deployment config, service and route which are use to deploy and serve our demo application.
* ***artifacts/app/publish*** The binaires used instead of building it because of the denied permission for the node selector used by the kubernetes plugin in OpenShift Online.

## 00 - First setup of the Jenkins Pipeline

First we are going to setup a build config in OpenShift with the `JenkinsPipelineStrategy`.

Jenkins runs without dependencies in OpenShift and we can setup our pipeline completely manually. But our goal is work with infrastructure as code and setup everything automatically.

Follow the steps in this chapter to setup a basic Jenkins Pipeline.

### Create a simple Jeninsfile

We created already a Jenkinsfile for you `jenkinsfile.groovie`. It should have following content. If not, you can copy the content from `00-jenkinsfile.groovy`. Don't forget to commit and push it to the remote repository.

```Groovy
node {
    stage("build") {
        echo 'dotnet build'
    }

    stage("test") {
        echo 'dotnet test'
    }
}
```

The pipeline runs on the master node (the Jenkins server) and contains two stages printing out something.

### Create a Jenkins Pipeline Build Configuration

We prepared the OpenShift build config file `openshift/pipeline/pipeline.buildconfig.yml` for you. It should have following content:

```yml
kind: BuildConfig
apiVersion: build.openshift.io/v1
metadata:
  name: devops-fusion-sample-pipeline
spec:
  source:
    git:
      uri: [THE_URI_OF_YOUR_GIT_REPO]
      ref: master
  strategy:
    jenkinsPipelineStrategy:
      jenkinsfilePath: jenkinsfile.groovy
      env:
      - name: OPENSHIFT_NAMESPACE
        value: [THE_NAME_OF_YOUR_OPENSHIFT_PROJECT]
    type: JenkinsPipeline
```

Please replace two values in this file:

* ***The git repo url*** Replace the value [THE_URI_OF_YOUR_GIT_REPO] with the url of you own git repositiory.
* ***The kubernetes namespace*** Replace the value [THE_NAME_OF_YOUR_OPENSHIFT_PROJECT] with the name of your OpenShift project.

Don't forget to commit and push it to the remote repository.

The build config uses a git repository as input and uses the `JeninsPipelineStrategy`to setup the Jenkins pipeline.

### Create the build config in OpenShift

Now you can run following command to create the build configuration in OpenShift.

```bash
oc create -f openshift/pipeline/pipeline.buildconfig.yml
```

Afterwards you can navigate to the OpenShift Dashboard => Builds => Build Configs where you should see an entry for the created build config.

### Start a build

We don't use a trigger for simplification (there are triggers for image streams and webhooks for repositories). So we will trigger the builds manually.

You can do this by navigating to the build config in your OpenShift Dashboard and click `Start Build`in the actions menu, or you can run following cli command:

```bash
oc start-build devops-fusion-sample-pipeline
```

You can now open and login to the Jenkins UI where you can see the triggered build.

## 01- Setup Pod Template

***As mentioned above, the demo does not work with the pod templates, Jenkins cannot provision pods due to a permission denied error (permission denied for node selector which does not work anymore in OpenShift Online). But we still do the step for explanation. The code has been adjusted so it still can run.***

OpenShift provides some PodTemplates (basic, maven and nodejs) out of the box. They can be addressed by using the node dsl syntax: `node('maven') {}`.

Most often this will not be enough. The OpenShift Sync and the Kubernetes Jenkins plugins provide various ways to define your own pod template:

* Directly in the Jenkins Settings UI
* By providing a specific ConfigMap labeled with role=jenkins-slave
* By providing an image stream labeled with role=jenkins-slave
* Jenkins pipeline DSL

As mentioned earlier, we aim to have everything in the code, so the Jenkins DSL fits the best since the pod template is directly described in the Jenkinsfile. The Kubernetes Jenkins Plugin DSL allows us to define a pod template in the jenkins file. A mentioned earlier, we provide a dotnet core 3.1 demo app, so we need a pod template with a dotnet core 3.1 Jenkins slave.

Specify the pod template in the `jenkinsfile.groovie`. _You can see (and copy) the code in the [01-jenkins.groovy](./jenkins/01-jenkinsfile.groovy) file._ Don't forget to commit and push it to the remote repository.

```Groovy
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
    // node("dotnet-31") {
    node() {  
        stage("build") {
            echo 'dotnet build'
        }

        stage("test") {
            echo 'dotnet test'
        }
    }
}
```

If you start the build now, you can switch over to the OpenShift dashboard and check the created pod template for the build. It will take a while, the OpenShift onlien free tier has not a lot of resources :-).

```bash
oc start-build devops-fusion-sample-pipeline
```

## 02 - Checkout the source code in the pipeline

Even though OpenShift will checkout the git repo for creating and starting the pipeline, the pipeline has no source code yet. We define a stage in the pipeline to checkout our demo app source.

You can remove the dummy steps in the `jenkinsfile.groovy` file and add the stage for the git checkout. _You can see (and copy) the code in the [02-jenkins.groovy](./jenkins/02-jenkinsfile.groovy) file._ Don't forget to commit and push it to the remote repository.

```groovy
stage("checkout") {
    sh 'printenv'
    checkout scm
}
```

The first line `sh 'printenv'` is a little helper, which we often use to debug our pipeline.

You can start the build and check in the Jenkins UI.

```bash
oc start-build devops-fusion-sample-pipeline
```

## 03 - Get the version number from the git history

We suggest to use a tool such as [https://gitversion.net/docs/](https://gitversion.net/docs/) to create a deterministic version number from the git history including the commit hash and branches, unfortunatelly we need a custom pod template to make this run, which does not work in openShift Online. We definitly recommend to have a versioning based on the git repository and history. We used this solution in a projects and rely basically on the git commit sha.

Add following stage to the `jenkins.groovy`file. _You can see (and copy) the code in the [03-jenkins.groovy](./jenkins/03-jenkinsfile.groovy) file._ Don't forget to commit and push it to the remote repository.

```groovy
// Add this line ath the beginning of the Jenkinsfile
def artefactVersion;

// Add this after the checkout stage in the Jenkinsfile
stage("gitversion") {
            // Ex.: ai_m_15-06-2019_08_22_9115204e

            String branchName = sh(returnStdout: true, script: "git rev-parse --abbrev-ref HEAD").trim()
            echo "Branch name: ${branchName}"

            String branchIndicator;
            if (branchName.equals("master")) {
                branchIndicator = 'm' // indicates master branch
            } else if (branchName.equals("develop")) {
                branchIndicator = 'd' // indicates master branch
            } else {
                branchIndicator = 'f' // indicates feature... actually just not master :-)
            }

            def today = new Date()
            String formattedDate = today.format('dd-MM-yyyy_HH_mm')
            String gitCommitSha = sh(returnStdout: true, script: "git rev-parse --short=7 HEAD").trim()
            echo "Git sha: ${gitCommitSha}"

            artefactVersion = "ai_${branchIndicator}_${formattedDate}_${gitCommitSha}" // adding date with time as 15-06-2019_08_22

            echo "Artifact identifier: ${artefactVersion}"
}
```

You can start the build and check in the Jenkins UI.

```bash
oc start-build devops-fusion-sample-pipeline
```

## 04 - Build and publish our demo app

Now we need to build and publish our demo app. We need to do following things:

* Restore the NuGet packages used for our demo app
* Compile the demo app (compile the binaries)
* Publish the demo app (dotnet will gather all required dlls and files and put it into a folder)

You can do more, e.g. in the real world we want to run our unit tests or build and Angular frontend etc.

***To make our demo run in OpenShift Online, we cannot really build our app since it requires a pod template, we commited the binary instead so we still can show the whole pipeline.***

Add the following build stages to the `jenkinsfile.groovy`. _You can see (and copy) the code in the [04-jenkins.groovy](./jenkins/04-jenkinsfile.groovy) file._ Don't forget to commit and push it to the remote repository.

```Groovy
    // stage("dotnet restore") {
    //     sh 'dotnet restore src/Zuehlke.OpenShiftDemo.sln'
    // }

    // stage("dotnet build") {
    //     sh 'dotnet build src/Zuehlke.OpenShiftDemo.sln -c Release --no-restore /p:AssemblyVersion=${GitVersion_AssemblySemVer} /p:FileVersion=${GitVersion_AssemblySemFileVer} /p:InformationalVersion=${GitVersion_InformationalVersion}'
    // }

    stage("dotnet publish") {
        // sh 'dotnet publish src/Zuehlke.OpenShiftDemo/Zuehlke.OpenShiftDemo.csproj -c Release -o ./artifacts/app/publish --no-restore --no-build /p:AssemblyVersion=${GitVersion_AssemblySemVer} /p:FileVersion=${GitVersion_AssemblySemFileVer} /p:InformationalVersion=${GitVersion_InformationalVersion}'
        zip zipFile: "demo-app-${artefactVersion}.zip", archive: true, dir: "./artifacts/app/publish", glob: "**/*.*"
    }
```

If you have a look at the build stages, you will recognize that we use the environment variables from the gitversion step and pass it into the dotnet commands.

We also create a ZIP file with the output of the publish command. We will need it later to build our docker image.

You can start the build and check in the Jenkins UI.

```bash
oc start-build devops-fusion-sample-pipeline
```

## 05 - Create an image pull secret

The docker image used to build our dotnet core source2image image is placed in the official RedHad image repository [registry.redhat.io](registry.redhat.io) and is a RHEL7 image. All RHEL7 image requires credentials to be used.

We need to create an image pull secret in our OpenShift project in order to access the images we require.

Execute following command:

```bash
oc create secret docker-registry image-pull-secret \
  --docker-server=registry.redhat.io \
  --docker-username=<user_name> \
  --docker-password=<password>
```

You can check in the OpenShift Dashboard if the secret is present.

## 06 -  Build the docker image

After building or demo app we need to build a docker image running our demo app. For this we have several options:

* The traditional way, run `docker build -t mydockerrep/image:latest .`. This works, but is a bit cumbersome to setup since the service account running the Jenkins slaves in OpenShift has not enough permissions to mount the docker socket.
* Use a build config with binary input and `DockerStrategy`. This would be our preferred solution because we define our own Dockerfile and use the build config to execute the docker build command. It works very fine with OpenShift on premise, but the docker strategy is not allowed to be used in openShift online.
* use a build config with binary input and the source2image strategy. The binary input is important because we don't want to build the demo app again (build once), but this approach works and we will use it to build the docker image for the demo app.

We prepared the build config for building the docker image in the `openshift/docker-build/app-docker.template.yml` file with the source2image strategy. It should have the following content:

```yml
apiVersion: v1
kind: Template
metadata:
  labels:
    app: demo-app-docker
  name: demo-app-docker
parameters:
- name: DOCKER_IMAGE_TAG
  required: true
- name: DOCKER_IMAGE_REPOSITORY
  required: true

objects:

- apiVersion: build.openshift.io/v1
  kind: BuildConfig
  metadata:
    labels:
      app: demo-app-docker
    name: demo-app-docker
  spec:
    source:
      contextDir: .
      type: Binary
    output:
      to:
        kind: DockerImage
        name: image-registry.openshift-image-registry.svc:5000/${DOCKER_IMAGE_REPOSITORY}/demo-app:${DOCKER_IMAGE_TAG}
    runPolicy: Parallel
    resources:
      limits:
        cpu: 400m
        memory: 256Mi
    strategy:
      sourceStrategy:
        from:
          kind: DockerImage
          name: registry.redhat.io/dotnet/dotnet-31-rhel7
        pullSecret:
          name: image-pull-secret
        env:
        - name: DOTNET_STARTUP_ASSEMBLY
          value: Zuehlke.OpenShiftDemo.dll
      type: Source
```

We are going to add a pipeline stage which does following:

1. Process and the `app-docker.template.yml` to Kubernetes objects (build-config).
2. Replace the parameters in the `app-docker.template.yml`.
3. Apply the Kubernetes objects (build-config) to OpenShift.
4. Start the build-config build within the pipeline.

Add the following build stages to the `jenkinsfile.groovy`. _You can see (and copy) the code in the [06-jenkins.groovy](./jenkins/06-jenkinsfile.groovy) file._ Don't forget to commit and push it to the remote repository.

```Groovy
stage("docker build") {
    openshift.withCluster() {
        openshift.withProject() {
            def objects = openshift.process("-f", "openshift/docker-build/app-docker.template.yml", "-p", "DOCKER_IMAGE_REPOSITORY=${OPENSHIFT_NAMESPACE}", "-p", "DOCKER_IMAGE_TAG=${artefactVersion}")
            openshift.apply(objects, "--force")

            openshift.selector("bc", "demo-app-docker").startBuild("--from-archive=demo-app-${artefactVersion}.zip", "--wait")
        }
    }
}
```

As you can see, we use the OpenShift Jenkins DSL to select the OpenShift cluster and project. We don't need to specific anything here, because we are operating on the same cluster and project as Jenkins is running.

Afterwards we process and apply the template containing the build config. We pass in the openshift namespace and artefact version.

At the end we select our build config and start a build. The `--wait` flag will wait for the end of the build.

You can start the build and check in the Jenkins UI.

```bash
oc start-build devops-fusion-sample-pipeline
```

## 07 - The final step - deploy the demo app

Finally we need to deploy our demo app. To deploy our app in OpenShift we need three OpenShift objects.

1. ***DeploymentConfig*** Similar to the Kubernetes deployment, but was introduced by OpenShift before the deployment existed. It basically defines how the pods will look like and also covers the configuration for teh replication manager.
2. ***Service*** A standard Kubernetes service.
3. ***Route*** Creates a public route to the service. Similar to the ingress Kubernetes objects.

We prepared the `openshift/demo-app/demo-app.template.yml`file with a template containing a these objects. It should have following content:

```yml
apiVersion: v1
kind: Template
metadata:
  name: demo-app-template

parameters:
- name: DOCKER_IMAGE_TAG
  description: The docker image tag of the main container. Gets passed to every template automatically.
  required: true
- name: DOCKER_IMAGE_REPOSITORY
  required: true

objects:

- apiVersion: apps.openshift.io/v1
  kind: DeploymentConfig
  metadata:
    name: demo-app
  spec:
    selector:
      app: demo-app
    replicas: 1
    template:
      metadata:
        labels:
          app: demo-app
      spec:
        containers:
          - name: demo-app
            image: image-registry.openshift-image-registry.svc:5000/${DOCKER_IMAGE_REPOSITORY}/demo-app:${DOCKER_IMAGE_TAG}
            ports:
              - containerPort: 8080
            env:
              - name: ASPNETCORE_ENVIRONMENT
                value: Production
            resources:
              limits:
                cpu: 400m
                memory: 256Mi

- apiVersion: v1
  kind: Service
  metadata:
    name: demo-app
  spec:
    selector:
      app: demo-app
    ports:
      - protocol: TCP
        port: 8080
        targetPort: 8080
    type: ClusterIP

- kind: Route
  apiVersion: route.openshift.io/v1
  metadata:
    name: demo-app
    labels:
      app: demo-app
  spec:
    host: demo-app-[THE_NAME_OF_YOUR_OPENSHIFT_PROJECT].apps.[THE_NAME_OF_YUR_CLUSTER].starter.openshift-online.com
    to:
      kind: Service
      name: demo-app
    port:
      targetPort: 8080
    tls:
      termination: edge
      insecureEdgeTerminationPolicy: Redirect
```

Please replace two values in this file:

* ***The kubernetes namespace*** Replace the value [THE_NAME_OF_YOUR_OPENSHIFT_PROJECT] with the name of your OpenShift project.
   ***The name of your cluster*** Replace the value [THE_NAME_OF_YUR_CLUSTER] with the name of your OpenShift onlione cluster, e.g. `us-east-2`. The whole host should look like this: `demo-app-devopsfusionleh.apps.us-east-2.starter.openshift-online.com`. If you are yusing this in a different environment than OpenShift Online, please use the domain of your cluster.

Don't forget to commit and push it to the remote repository.

Now we need a pipelien stage which process this template, applies the objects to OpenShift and trigger a DeploymentConfig rollout. Add the following build stages to the `jenkinsfile.groovy`. _You can see (and copy) the code in the [07-jenkins.groovy](./jenkins/07-jenkinsfile.groovy) file._ Don't forget to commit and push it to the remote repository.

```Groovy
stage("deploy") {
    openshift.withCluster() {
        openshift.withProject() {
            def objects = openshift.process("-f", "openshift/demo-app/demo-app.template.yml", "-p", "DOCKER_IMAGE_REPOSITORY=${OPENSHIFT_NAMESPACE}", "-p", "DOCKER_IMAGE_TAG=${artefactVersion}")
            openshift.apply(objects, "--force")

            def rm = openshift.selector('dc', "demo-app").rollout()
            rm.latest()
            rm.status()
        }
    }
}
```

You can start the build and check in the Jenkins UI.

```bash
oc start-build devops-fusion-sample-pipeline
```

After the build succeeded, you can check the created OpenShift objects. The `demo-app` deployment config should have a running pod. You can find and open the application url in the created route.

Congratulation, you have setup your first Jenkins pipeline on openshift for building and deploying an application.

***done***
