/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.image.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * JSON Template for Docker Manifest List Schema V2.2
 *
 * <p>Example manifest list JSON:
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 2,
 *   "mediaType": "application/vnd.docker.distribution.manifest.list.v2+json",
 *   "manifests": [
 *     {
 *       "mediaType": "application/vnd.docker.image.manifest.v2+json",
 *       "size": 7143,
 *       "digest": "sha256:e692418e4cbaf90ca69d05a66403747baa33ee08806650b51fab815ad7fc331f",
 *       "platform": {
 *         "architecture": "ppc64le",
 *         "os": "linux",
 *       }
 *     },
 *     {
 *       "mediaType": "application/vnd.docker.image.manifest.v2+json",
 *       "size": 7682,
 *       "digest": "sha256:5b0bcabd1ed22e9fb1310cf6c2dec7cdef19f0ad69efa1f392e94a4333501270",
 *       "platform": {
 *         "architecture": "amd64",
 *         "os": "linux",
 *         "features": [
 *           "sse4"
 *         ]
 *       }
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @see <a href="https://docs.docker.com/registry/spec/manifest-v2-2/#manifest-list">Image Manifest
 *     Version 2, Schema 2: Manifest List</a>
 */
public class V22ManifestListTemplate implements ManifestTemplate {

  public static final String MANIFEST_MEDIA_TYPE =
      "application/vnd.docker.distribution.manifest.list.v2+json";
  private static final int SCHEMA_VERSION = 2;

  private final int schemaVersion = SCHEMA_VERSION;
  private final String mediaType = MANIFEST_MEDIA_TYPE;

  @JsonCreator
  public V22ManifestListTemplate(@JsonProperty("mediaType") String mediaType, @JsonProperty("schemaVersion") Integer schemaVersion, @JsonProperty("manifests") List<ManifestDescriptorTemplate> manifests) {
    Preconditions.checkArgument(Objects.equals(mediaType, MANIFEST_MEDIA_TYPE), "mediaType " + mediaType + " does not equal " + MANIFEST_MEDIA_TYPE);
    Preconditions.checkArgument(Objects.equals(schemaVersion, SCHEMA_VERSION), "schemaVersion " + schemaVersion + " does not equal " + SCHEMA_VERSION);
    this.manifests = manifests;
  }

  public V22ManifestListTemplate() {
  }

  @Override
  public int getSchemaVersion() {
    return schemaVersion;
  }

  @Override
  public String getManifestMediaType() {
    return mediaType;
  }

  @Nullable private List<ManifestDescriptorTemplate> manifests;

  /**
   * Adds a manifest.
   *
   * @param manifest a manifest descriptor
   */
  public void addManifest(ManifestDescriptorTemplate manifest) {
    if (manifests == null) {
      manifests = new ArrayList<>();
    }
    manifests.add(manifest);
  }

  @VisibleForTesting
  public List<ManifestDescriptorTemplate> getManifests() {
    return Preconditions.checkNotNull(manifests);
  }

  /**
   * Returns a list of digests for a specific platform found in the manifest list. see
   * <a>https://docs.docker.com/registry/spec/manifest-v2-2/#manifest-list</a>
   *
   * @param architecture the architecture of the target platform
   * @param os the os of the target platform
   * @return a list of matching digests
   */
  public List<String> getDigestsForPlatform(String architecture, String os) {
    return getManifests()
        .stream()
        .filter(
            manifest ->
                manifest.platform != null
                    && os.equals(manifest.platform.os)
                    && architecture.equals(manifest.platform.architecture))
        .map(ManifestDescriptorTemplate::getDigest)
        .collect(Collectors.toList());
  }

  /** Template for inner JSON object representing a single platform specific manifest. */
  public static class ManifestDescriptorTemplate implements JsonTemplate {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Platform implements JsonTemplate {
      @Nullable private final String architecture;
      @Nullable private final String os;

      @JsonCreator
      public Platform(@JsonProperty("architecture") String architecture, @JsonProperty("os") String os) {
        this.architecture = architecture;
        this.os = os;
      }

      @Nullable
      public String getArchitecture() {
        return architecture;
      }

      @Nullable
      public String getOs() {
        return os;
      }
    }

    @Nullable private String mediaType;
    @Nullable private String digest;

    @SuppressWarnings("unused")
    private long size;

    @Nullable private Platform platform;

    public void setSize(long size) {
      this.size = size;
    }

    public void setDigest(String digest) {
      this.digest = digest;
    }

    @Nullable
    public String getDigest() {
      return digest;
    }

    public void setMediaType(String mediaType) {
      this.mediaType = mediaType;
    }

    @Nullable
    public String getMediaType() {
      return mediaType;
    }

    /**
     * Sets a platform.
     *
     * @param architecture the manifest architecture
     * @param os the manifest os
     */
    public void setPlatform(String architecture, String os) {
      platform = new Platform(architecture, os);
    }

    @Nullable
    public Platform getPlatform() {
      return platform;
    }
  }
}
