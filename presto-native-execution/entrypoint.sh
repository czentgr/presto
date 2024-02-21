#!/bin/sh
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

echo "node.id=$HOSTNAME" >> /opt/presto-server/etc/node.properties

# Check for the reason of setting split_preload_per_driver:
# https://github.com/prestodb/presto/issues/20020#issuecomment-1785083459
MALLOC_CONF="prof:true,prof_active:true,prof_leak:true" \
GLOG_logtostderr=1 presto_server \
    --etc-dir=/opt/presto-server/etc \
    2>&1 | tee /var/log/presto-server/console.log
