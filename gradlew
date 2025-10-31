#!/usr/bin/env sh

# Create a simple gradlew script
echo "Downloading Gradle..."
wget -q https://services.gradle.org/distributions/gradle-8.0-bin.zip
unzip -q gradle-8.0-bin.zip
./gradle-8.0/bin/gradle assembleDebug
