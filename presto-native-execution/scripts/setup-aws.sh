#!/bin/bash
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Propagate errors and improve debugging.
set -eufx -o pipefail

SCRIPT_DIR=$(readlink -f "$(dirname "${BASH_SOURCE[0]}")")
if [ -f "${SCRIPT_DIR}/setup-common.sh" ]
then
  source "${SCRIPT_DIR}/setup-common.sh"
else
  source "${SCRIPT_DIR}/../velox/scripts/setup-common.sh"
fi
DEPENDENCY_DIR=${DEPENDENCY_DIR:-$(pwd)}

function install_aws_deps {
  local AWS_REPO_NAME="aws/aws-sdk-cpp"

  github_checkout $AWS_REPO_NAME "1.11.654" --recurse-submodules
  cmake_install -DCMAKE_BUILD_TYPE="${CMAKE_BUILD_TYPE}" -DBUILD_SHARED_LIBS:BOOL=OFF -DMINIMIZE_SIZE:BOOL=ON -DENABLE_TESTING:BOOL=OFF -DBUILD_ONLY:STRING="s3;identity-management"
}

install_aws_deps