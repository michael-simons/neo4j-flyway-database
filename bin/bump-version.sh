#!/usr/bin/env bash
#
# Copyright 2025 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


set -euo pipefail
DIR="$(dirname "$(realpath "$0")")"

NUM_DOTS=$(echo "${1}" | awk -F. '{ print NF - 1 }')

if [[ "${1}" == *"SNAPSHOT" ]]; then
  if [[ $NUM_DOTS -lt 2 ]]; then
    echo "Version must be either an plain version or a full x.y.z-SNAPSHOT version"
    exit
  fi
  VERSION="${1}"
else
  if [[ $NUM_DOTS -eq 0 ]]; then
    VERSION="${1}.0.0-SNAPSHOT"
  elif [[ $NUM_DOTS -eq 1 ]]; then
    VERSION="${1}.0-SNAPSHOT"
  else
    VERSION="${1}-SNAPSHOT"
  fi
fi

# Replace version in Maven build descriptor
./mvnw -f "$DIR"/../pom.xml versions:set -DgenerateBackupPoms=false -DnewVersion="${VERSION}"
