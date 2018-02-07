/*
 * Copyright 2018 Google Inc.
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

package com.google.cloud.tools.jib.registry.credentials;

import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.registry.DockerCredentialRetriever;
import com.google.cloud.tools.jib.registry.NonexistentDockerCredentialHelperException;
import com.google.cloud.tools.jib.registry.NonexistentServerUrlDockerCredentialHelperException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Stores retrieved registry credentials.
 *
 * <p>The credentials are referred to by the registry they are used for.
 */
public class RegistryCredentials {

  /**
   * Retrieves credentials for {@code registries} using the credential helpers referred to by {@code
   * credentialHelperSuffixes}.
   *
   * <p>This obtains the registry credentials, not the <a
   * href="https://docs.docker.com/registry/spec/auth/token/">Docker authentication token</a>.
   */
  public static RegistryCredentials from(
      List<String> credentialHelperSuffixes, List<String> registries)
      throws IOException, NonexistentDockerCredentialHelperException {
    RegistryCredentials registryCredentials = new RegistryCredentials();

    // TODO: These can be done in parallel.
    for (String registry : registries) {
      for (String credentialHelperSuffix : credentialHelperSuffixes) {
        Authorization authorization = retrieveCredentials(registry, credentialHelperSuffix);
        if (authorization != null) {
          registryCredentials.store(registry, authorization);
          break;
        }
      }
    }
    return registryCredentials;
  }

  /**
   * Attempts to retrieve authorization for {@code registry} using docker-credential-{@code
   * credentialHelperSuffix}.
   *
   * @return the retrieved credentials, or {@code null} if not found
   */
  @Nullable
  private static Authorization retrieveCredentials(String registry, String credentialHelperSuffix)
      throws IOException, NonexistentDockerCredentialHelperException {
    try {
      DockerCredentialRetriever dockerCredentialRetriever =
          new DockerCredentialRetriever(registry, credentialHelperSuffix);

      return dockerCredentialRetriever.retrieve();

    } catch (NonexistentServerUrlDockerCredentialHelperException ex) {
      // Returns null if no authorization is found.
      return null;
    }
  }

  /** Maps from registry to the credentials for that registry. */
  private final Map<String, Authorization> credentials = new HashMap<>();

  /** Instantiate using {@link #from}. */
  private RegistryCredentials() {};

  private void store(String registry, Authorization authorization) {
    credentials.put(registry, authorization);
  }

  public Authorization get(String registry) throws NoRegistryCredentialsException {
    if (!credentials.containsKey(registry)) {
      throw new NoRegistryCredentialsException(registry);
    }
    return credentials.get(registry);
  }
}
