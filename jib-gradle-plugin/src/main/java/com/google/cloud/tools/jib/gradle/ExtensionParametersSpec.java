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

package com.google.cloud.tools.jib.gradle;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;

/** Allows to add {@link ExtensionParameters} objects to the list property of the same type. */
public class ExtensionParametersSpec {

  private final Project project;
  private final ListProperty<ExtensionParameters> pluginExtensions;

  @Inject
  public ExtensionParametersSpec(
      Project project, ListProperty<ExtensionParameters> pluginExtensions) {
    this.project = project;
    this.pluginExtensions = pluginExtensions;
  }

  /**
   * Adds a new plugin extension configuration to the extensions list.
   *
   * @param action closure representing an extension configuration
   */
  public void pluginExtension(Action<? super ExtensionParameters> action) {
    ExtensionParameters extension = project.getObjects().newInstance(ExtensionParameters.class);
    action.execute(extension);
    pluginExtensions.add(extension);
  }
}
