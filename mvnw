#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.2.0
# ----------------------------------------------------------------------------

set -e

# set -x # to debug the script

# Function to download a file using curl or wget
http_download() {
  # args: url dest
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$1" -o "$2"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$1" -O "$2"
  else
    echo "Error: Please install 'curl' or 'wget' to download the Maven Wrapper" >&2
    exit 1
  fi
}

# Resolve base directory
PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' >/dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")/"$link"
  fi
done
BASE_DIR=$(cd "$(dirname "$PRG")" >/dev/null; pwd -P)

# Load Maven wrapper properties
WRAPPER_PROPERTIES="${BASE_DIR}/.mvn/wrapper/maven-wrapper.properties"
if [ ! -f "$WRAPPER_PROPERTIES" ]; then
  echo "Error: Maven wrapper properties not found at $WRAPPER_PROPERTIES" >&2
  exit 1
fi

# Read properties
distributionUrl=$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPERTIES")
wrapperUrl=$(sed -n 's/^wrapperUrl=//p' "$WRAPPER_PROPERTIES")

# Prepare directories
MAVEN_USER_HOME=${MAVEN_USER_HOME:-"$HOME/.m2"}
WRAPPER_JAR="${BASE_DIR}/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER="org.apache.maven.wrapper.MavenWrapperMain"

# Download the Maven Wrapper JAR if missing
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Maven Wrapper JAR from $wrapperUrl" >&2
  http_download "$wrapperUrl" "$WRAPPER_JAR"
fi

# Verify the JAR exists now
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Error: Maven Wrapper JAR was not downloaded" >&2
  exit 1
fi

# Determine Java command
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

# Basic args
JAVA_OPTS=${JAVA_OPTS:-""}
MAVEN_OPTS=${MAVEN_OPTS:-""}

# Launch Maven Wrapper
exec "$JAVACMD" \
  $JAVA_OPTS $MAVEN_OPTS \
  -classpath "$WRAPPER_JAR" \
  -Dmaven.multiModuleProjectDirectory="$BASE_DIR" \
  -Dmaven.home="$BASE_DIR/.mvn" \
  $WRAPPER_LAUNCHER \
  "$@"
