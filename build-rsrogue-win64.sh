#!/bin/bash

# Builds rsrogueSetup.exe (64-bit Windows) from build/libs/RuneLite.jar. Run after
# ./gradlew build -PrsrogueServerHost=<address>. Needs MSVC (cmake) and Inno Setup (iscc).

set -e

echo Launcher sha256sum
sha256sum build/libs/RuneLite.jar

cmake -S liblauncher -B liblauncher/build64 -A x64
cmake --build liblauncher/build64 --config Release

pushd native
cmake -B build-x64 -A x64
cmake --build build-x64 --config Release
popd

source .jdk-versions.sh

rm -rf build/rsrogue-win-x64
mkdir -p build/rsrogue-win-x64

if ! [ -f win64_jre.zip ] ; then
    curl -Lo win64_jre.zip $WIN64_LINK
fi

echo "$WIN64_CHKSUM win64_jre.zip" | sha256sum -c

cp native/build-x64/src/Release/RuneLite.exe build/rsrogue-win-x64/rsrogue.exe
cp build/libs/RuneLite.jar build/rsrogue-win-x64/
cp packr/win-x64-config.json build/rsrogue-win-x64/config.json
cp liblauncher/build64/Release/launcher_amd64.dll build/rsrogue-win-x64/

unzip -q win64_jre.zip
mv jdk-$WIN64_VERSION-jre build/rsrogue-win-x64/jre

echo rsrogue.exe sha256sum
sha256sum build/rsrogue-win-x64/rsrogue.exe

# We use the filtered iss file
iscc build/filtered-resources/rsrogue.iss
