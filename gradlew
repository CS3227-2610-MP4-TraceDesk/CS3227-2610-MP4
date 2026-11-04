#!/bin/sh

# Gradle Wrapper bootstrap script.

app_path=$0

while [ -h "$app_path" ]; do
  ls=$(ls -ld "$app_path")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  case $link in
    /*) app_path=$link ;;
    *) app_path=$(dirname "$app_path")/$link ;;
  esac
done

app_home=$(cd "${app_path%/*}" && pwd -P)

CLASSPATH=$app_home/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
  JAVACMD=$JAVA_HOME/bin/java
else
  JAVACMD=java
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
  echo "ERROR: JAVA_HOME is not set and no java command was found." >&2
  exit 1
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
