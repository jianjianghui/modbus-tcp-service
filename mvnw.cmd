@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup script, version 3.2.0
@REM ----------------------------------------------------------------------------

@echo off

setlocal ENABLEEXTENSIONS
set CMD_LINE_ARGS=%*

set BASE_DIR=%~dp0

set WRAPPER_PROPERTIES=%BASE_DIR%\.mvn\wrapper\maven-wrapper.properties
if not exist "%WRAPPER_PROPERTIES%" (
  echo Error: Maven wrapper properties not found at %WRAPPER_PROPERTIES%
  exit /B 1
)

for /F "tokens=1,2 delims==" %%A in (%WRAPPER_PROPERTIES%) do (
  if "%%A"=="distributionUrl" set DISTRIBUTION_URL=%%B
  if "%%A"=="wrapperUrl" set WRAPPER_URL=%%B
)

set WRAPPER_JAR=%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar
if not exist "%WRAPPER_JAR%" (
  echo Downloading Maven Wrapper JAR from %WRAPPER_URL%
  powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object System.Net.WebClient).DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"
  if not exist "%WRAPPER_JAR%" (
    echo Error: Maven Wrapper JAR was not downloaded
    exit /B 1
  )
)

set MAVEN_JAVA_EXE=java
if defined JAVA_HOME set MAVEN_JAVA_EXE=%JAVA_HOME%\bin\java.exe

set JVM_CONFIG_MAVEN_PROPS=

"%MAVEN_JAVA_EXE%" %JVM_CONFIG_MAVEN_PROPS% -classpath "%WRAPPER_JAR%" -Dmaven.multiModuleProjectDirectory="%BASE_DIR%" org.apache.maven.wrapper.MavenWrapperMain %CMD_LINE_ARGS%
