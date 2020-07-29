/*
 * Copyright 2020 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.api.buildplan;

import java.nio.file.Path;

/** Serves as a base class for providing file permissions on a container. */
public interface FilePermissionsProvider {

  /**
   * Returns the file permissions that should be set for a path, given the source path and
   * destination path on a container.
   *
   * @param sourcePath the source file.
   * @param destinationPath the destination path in the container filesystem.
   * @return the permissions to be set for the file.
   */
  public FilePermissions retrieveFilePermissions(Path sourcePath, AbsoluteUnixPath destinationPath);
}