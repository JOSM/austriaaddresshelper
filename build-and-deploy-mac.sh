#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/"
cd ${SCRIPT_DIR}
PLUGIN_NAME=${PWD##*/}

ant clean && \
ant dist && \
cp ../../dist/${PLUGIN_NAME}.jar ~/Library/JOSM/plugins/${PLUGIN_NAME}.jar
