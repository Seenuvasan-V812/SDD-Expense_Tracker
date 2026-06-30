@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM   http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET "BASE_DIR=%~dp0") ELSE (
  SET "BASE_DIR=%%ENV:%__MVNW_ARG0_NAME__%:~0,-1%%"
  @SET "__MVNW_ARG0_NAME__="
)
@SET MAVEN_PROJECTBASEDIR=%BASE_DIR%
@IF "%MAVEN_PROJECTBASEDIR:~-1%"=="\" SET "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"
@echo MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR%

@IF NOT EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties" (
  @echo Could not find %MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties
  @exit /B 1
)

@SET DOWNLOAD_URL=
@FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") DO (
  @IF "%%A"=="distributionUrl" SET "DISTRIBUTION_URL=%%B"
)

@IF "%JAVA_HOME%"=="" (
  SET JAVA_EXEC=java
) ELSE (
  SET JAVA_EXEC=%JAVA_HOME%\bin\java
)

@SET WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@IF EXIST %WRAPPER_JAR% (
  GOTO downloadFromJar
) ELSE (
  GOTO downloadMavenWrapper
)

:downloadMavenWrapper
@SET WRAPPER_URL=
@FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") DO (
  @IF "%%A"=="wrapperUrl" SET "WRAPPER_URL=%%B"
)
@IF "%WRAPPER_URL%"=="" (
  ECHO wrapperUrl not set in maven-wrapper.properties
  EXIT /B 1
)
@ECHO Downloading Maven Wrapper from %WRAPPER_URL%
powershell -Command "$webClient = New-Object System.Net.WebClient; $webClient.DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"
@IF NOT EXIST %WRAPPER_JAR% (
  ECHO Failed to download Maven Wrapper JAR
  EXIT /B 1
)

:downloadFromJar
@"%JAVA_EXEC%" -jar %WRAPPER_JAR% %*
