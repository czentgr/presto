/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "presto_cpp/main/SignalHandler.h"
#include <folly/io/async/EventBaseManager.h>
#include <jemalloc/jemalloc.h>
#include <csignal>
#include "presto_cpp/main/PrestoServer.h"
#include "presto_cpp/main/common/Utils.h"

namespace facebook::presto {

SignalHandler::SignalHandler(PrestoServer* prestoServer)
    : folly::AsyncSignalHandler(folly::EventBaseManager::get()->getEventBase()),
      prestoServer_(prestoServer) {
  registerSignalHandler(SIGINT);
  registerSignalHandler(SIGTERM);
  registerSignalHandler(SIGABRT);
}

void SignalHandler::signalReceived(int signum) noexcept {
  PRESTO_SHUTDOWN_LOG(INFO) << "Received signal " << signum;
const char *fileName = "/var/log/presto-server/je_stopping_signal_heap.out";
#ifdef __linux__
  malloc_stats_print(NULL, NULL, NULL);
  mallctl("prof.dump", NULL, NULL, &fileName, sizeof(const char *));
#else
  je_malloc_stats_print(NULL, NULL, NULL);
  je_mallctl("prof.dump", NULL, NULL, &fileName, sizeof(const char *));
#endif
  prestoServer_->stop();
}


DumpSignalHandler::DumpSignalHandler()
    : folly::AsyncSignalHandler(folly::EventBaseManager::get()->getEventBase()) {
  registerSignalHandler(SIGUSR1);
  registerSignalHandler(SIGUSR2);
}

void DumpSignalHandler::signalReceived(int signum) noexcept {
  PRESTO_SHUTDOWN_LOG(INFO) << "Received signal " << signum;
  const char *fileName = "/var/log/presto-server/je_dump_signal_heap.out";
#ifdef __linux__
  malloc_stats_print(NULL, NULL, NULL);
  mallctl("prof.dump", NULL, NULL, &fileName, sizeof(const char *));
#else
  je_malloc_stats_print(NULL, NULL, NULL);
  je_mallctl("prof.dump", NULL, NULL, &fileName, sizeof(const char *));
#endif
}

} // namespace facebook::presto
