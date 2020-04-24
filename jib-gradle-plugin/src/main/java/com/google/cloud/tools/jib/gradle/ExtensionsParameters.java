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

import com.google.cloud.tools.jib.plugins.common.RawConfiguration.ExtensionConfiguration;
import java.util.Collections;
import java.util.Map;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/** Object in {@link JibExtension} that configures plugin extensions. */
public class ExtensionsParameters implements ExtensionConfiguration {

  private String implementation = "<extension implementation not configured>";
  private Map<String, String> properties = Collections.emptyMap();

  @Input
  public String getImplementation() {
    return getExtensionClass();
  }

  @Internal
  @Override
  public String getExtensionClass() {
    return implementation;
  }

  public void setImplementation(String implementation) {
    this.implementation = implementation;
  }

  @Input
  @Override
  public Map<String, String> getProperties() {
    return properties;
  }

  public void setProperties(Map<String, String> properties) {
    this.properties = properties;
  }
}
