#!/bin/bash
set -eo pipefail

error_exit() {
    echo "$1" 1>&2
    exit 1
}

[[ -z "${FIREBASE_PROJECT}" ]] && error_exit "FIREBASE_PROJECT environment variable missing"

emulator_cmd="firebase emulators:start --only auth,firestore,functions,pubsub,storage --project=${FIREBASE_PROJECT}"
[[ -n "${DATA_DIRECTORY}" ]] && emulator_cmd+=" --import=./${DATA_DIRECTORY}"

exec $emulator_cmd
