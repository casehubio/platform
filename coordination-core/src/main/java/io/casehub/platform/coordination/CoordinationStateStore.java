/*
 * Copyright 2020-Present The Open Workflow Specification Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.platform.coordination;

import java.util.Optional;

public interface CoordinationStateStore extends AutoCloseable {
  void save(String instanceId, String json);

  Optional<String> load(String instanceId);

  @Override
  void close();

  static CoordinationStateStore noOp() {
    return new CoordinationStateStore() {
      @Override
      public void save(String instanceId, String json) {}

      @Override
      public Optional<String> load(String instanceId) {
        return Optional.empty();
      }

      @Override
      public void close() {}
    };
  }
}
