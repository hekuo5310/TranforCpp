#!/bin/bash
# Setup Gradle for the project

set -e

# Check if build.gradle exists
if [ ! -f "build.gradle" ] && [ ! -f "build.gradle.kts" ]; then
  echo "Creating build.gradle..."
  cat > build.gradle << 'EOF'
plugins {
    id 'java'
}

group = 'com.github.tranforcpp'
version = '1.1.0'

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = "https://repo.papermc.io/repository/maven-public/"
    }
    maven {
        name = "sonatype"
        url = "https://oss.sonatype.org/content/groups/public/"
    }
}

dependencies {
    compileOnly "io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT"
    compileOnly "org.jetbrains:annotations:24.1.0"
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

jar {
    archiveFileName = "${project.name}-${project.version}.jar"
    from sourceSets.main.output
}

processResources {
    inputs.property 'version', version
    filesMatching('paper-plugin.yml') {
        expand 'version': version
    }
}
EOF
fi

echo "Gradle setup completed!"
