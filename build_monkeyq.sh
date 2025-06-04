#!/bin/zsh


./gradlew clean makeJar
$ANDROID_HOME/build-tools/28.0.3/dx --dex --min-sdk-version=26 --output=monkeyq.jar $(pwd)/monkey/build/libs/monkey.jar