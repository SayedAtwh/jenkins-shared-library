def call(Map config = [:]) {

    node(config.agent ?: 'agent-1') {

        // ==============================
        // Tools
        // ==============================

        def jdkHome = tool(config.jdk ?: 'jdk-11')
        def mavenHome = tool(config.maven ?: 'maven')

        env.JAVA_HOME = jdkHome
        env.PATH = "${mavenHome}/bin:${jdkHome}/bin:${env.PATH}"


        // ==============================
        // Configuration
        // ==============================

        def imageName = config.imageName
        def imageTag = config.imageTag
        def containerName = config.containerName

        def imageVersion = "${BUILD_NUMBER}"


        // ==============================
        // Checkout
        // ==============================

        stage("Checkout") {

            git branch: config.branch ?: 'main',
                url: config.gitUrl
        }


        // ==============================
        // Build Java Application
        // ==============================

        stage("Build Java Application") {

            sh "mvn clean package -DskipTests=true"
        }


        // ==============================
        // Test Java Application
        // ==============================

        stage("Test Java Application") {

            sh "mvn test"
        }


        // ==============================
        // Build Docker Image
        // ==============================

        stage("Build Docker Image") {

            sh "docker build -t ${imageName}:${imageVersion} ."
        }


        // ==============================
        // Docker Login
        // ==============================

        stage("Docker Login into DockerHub") {

            withCredentials([
                string(
                    credentialsId: 'DOCKER_USERNAME',
                    variable: 'DOCKER_USERNAME'
                ),
                string(
                    credentialsId: 'DOCKER_PASSWORD',
                    variable: 'DOCKER_PASSWORD'
                )
            ]) {

                sh '''
                    docker login \
                        -u "$DOCKER_USERNAME" \
                        -p "$DOCKER_PASSWORD"
                '''
            }
        }


        // ==============================
        // Push Docker Image
        // ==============================

        stage("Push Docker Image") {

            sh "docker tag ${imageName}:${imageVersion} ${imageTag}:${imageVersion}"

            sh "docker push ${imageTag}:${imageVersion}"
        }


        // ==============================
        // Deploy
        // ==============================

        stage("Deploy") {

            sh """
                docker rm -f ${containerName} || true

                docker run -d \
                    --name ${containerName} \
                    -p 8080:8080 \
                    ${imageTag}:${imageVersion}
            """
        }
    }
}