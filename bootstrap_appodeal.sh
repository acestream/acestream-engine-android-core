#!/bin/sh
set -e

ROOT=$(readlink -f $(dirname $0))
SDK_URL="http://bit.ly/appodeal-android-sdk-2-4-10-nodex"
TEMP_DIR=${ROOT}/tmp
LIBS_DIR=${ROOT}/libs
FLAG=${LIBS_DIR}/.appodeal_installed

if [ -f $FLAG ]; then
    echo "Appodeal is already installed"
    exit
fi

# Download and unzip Appodeal SDK
mkdir -p $TEMP_DIR
cd $TEMP_DIR
wget $SDK_URL -O appodeal.zip
unzip -u appodeal.zip

# Remove unnecessary file (these are already included in project)
rm converter-gson-2.2.0.jar
rm gson-2.7.jar
rm okhttp-3.7.0.jar
rm okio-1.12.0.jar
rm retrofit-2.2.0.jar

# Remove SDKs for which we use newew versions
rm adcolony-3.3.4.aar
rm inmobi-7.1.1.jar

# Copy to libs folder
mkdir -p $LIBS_DIR
cp ./*.jar $LIBS_DIR
cp ./aar/*.aar $LIBS_DIR

# Cleanup
cd $ROOT
rm -rf $TEMP_DIR

# Finish
touch $FLAG
echo "Appodeal bootstrap done"
