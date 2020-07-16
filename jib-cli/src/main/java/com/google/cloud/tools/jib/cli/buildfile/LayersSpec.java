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

package com.google.cloud.tools.jib.cli.buildfile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A yaml block for specifying layers.
 *
 * <p>Example use of this yaml snippet.
 *
 * <pre>{@code
 * properties: see {@link FilePropertiesSpec}
 * entries:
 *   - see {@link LayerSpec}
 *   - see {@link LayerSpec}
 * }</pre>
 */
public class LayersSpec {
  private List<LayerSpec> entries;
  @Nullable private FilePropertiesSpec properties;

  /**
   * Constructor for use by jackson to populate this object.
   *
   * @param entries octal string for directory permissions
   * @param properties octal string for file permissions
   */
  @JsonCreator
  public LayersSpec(
      @JsonProperty(value = "entries", required = true) List<LayerSpec> entries,
      @JsonProperty("properties") FilePropertiesSpec properties) {
    this.properties = properties;
    this.entries = entries;
  }

  public Optional<FilePropertiesSpec> getProperties() {
    return Optional.ofNullable(properties);
  }

  public List<LayerSpec> getEntries() {
    return entries;
  }
}
