node {
    stage("build") {
        echo 'dotnet build'
    }

    stage("test") {
        echo 'dotnet test'
    }
}