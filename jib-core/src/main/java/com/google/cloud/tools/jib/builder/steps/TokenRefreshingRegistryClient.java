/*
 * Copyright 2018 Google LLC.
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

package com.google.cloud.tools.jib.builder.steps;

import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryAuthenticationFailedException;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryAuthenticator;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryCredentialsNotSentException;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.base.Verify;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** This class is stateful and thread-safe. */
public class TokenRefreshingRegistryClient {

  private static interface RegistryAction<T> {
    T run(RegistryClient registryClient) throws IOException, RegistryException;
  }

  public static TokenRefreshingRegistryClient create(BuildContext buildContext)
      throws CredentialRetrievalException, IOException, RegistryException {
    Credential credential =
        RegistryCredentialRetriever.getTargetImageCredential(buildContext).orElse(null);

    Optional<RegistryAuthenticator> authenticator =
        buildContext
            .newTargetImageRegistryClientFactory()
            .newRegistryClient()
            .getRegistryAuthenticator();

    Authorization authorization = null;
    if (authenticator.isPresent()) {
      authorization = authenticator.get().authenticatePush(credential);

    } else if (credential != null && !credential.isOAuth2RefreshToken()) {
      authorization =
          Authorization.fromBasicCredentials(credential.getUsername(), credential.getPassword());
    }

    return new TokenRefreshingRegistryClient(buildContext, credential, authorization);
  }

  private final BuildContext buildContext;
  private final Credential credential;
  private final AtomicReference<RegistryClient> registryClient = new AtomicReference<>();

  TokenRefreshingRegistryClient(
      BuildContext buildContext, Credential credential, @Nullable Authorization authorization) {
    this.buildContext = buildContext;
    this.credential = credential;
    registryClient.set(
        buildContext
            .newTargetImageRegistryClientFactory()
            .setAuthorization(authorization)
            .newRegistryClient());
  }

  Optional<BlobDescriptor> checkBlob(DescriptorDigest blobDigest)
      throws IOException, RegistryException {
    return execute(registryClient -> registryClient.checkBlob(blobDigest));
  }

  DescriptorDigest pushManifest(BuildableManifestTemplate manifestTemplate, String imageTag)
      throws IOException, RegistryException {
    return execute(registryClient -> registryClient.pushManifest(manifestTemplate, imageTag));
  }

  boolean pushBlob(
      DescriptorDigest blobDigest,
      Blob blob,
      @Nullable String sourceRepository,
      Consumer<Long> writtenByteCountListener)
      throws IOException, RegistryException {
    return execute(
        registryClient ->
            registryClient.pushBlob(blobDigest, blob, sourceRepository, writtenByteCountListener));
  }

  private <T> T execute(RegistryAction<T> action) throws IOException, RegistryException {
    int refreshCount = 0;
    while (true) {
      try {
        return action.run(Verify.verifyNotNull(registryClient.get()));

      } catch (RegistryUnauthorizedException ex) {
        int code = ex.getHttpResponseException().getStatusCode();
        if (code != HttpStatusCodes.STATUS_CODE_UNAUTHORIZED || refreshCount++ >= 5) {
          throw ex;
        }

        // Because we successfully authenticated with the registry initially, getting 401 here
        // probably means the token was expired.
        String wwwAuthenticate = ex.getHttpResponseException().getHeaders().getAuthenticate();
        refreshBearerToken(wwwAuthenticate);
      }
    }
  }

  private void refreshBearerToken(@Nullable String wwwAuthenticate)
      throws RegistryAuthenticationFailedException, RegistryCredentialsNotSentException {
    String registry = buildContext.getTargetImageConfiguration().getImageRegistry();
    String repository = buildContext.getTargetImageConfiguration().getImageRepository();
    String message = "refreshing Bearer auth token for " + registry + "/" + repository + "...";
    buildContext.getEventHandlers().dispatch(LogEvent.debug(message));

    if (wwwAuthenticate != null) {
      Optional<RegistryAuthenticator> authenticator =
          buildContext
              .newTargetImageRegistryClientFactory()
              .newRegistryClient()
              .getRegistryAuthenticator(wwwAuthenticate);
      if (authenticator.isPresent()) {
        Authorization authorization = authenticator.get().authenticatePush(credential);
        registryClient.set(
            buildContext
                .newTargetImageRegistryClientFactory()
                .setAuthorization(authorization)
                .newRegistryClient());
        return;
      }
    }

    throw new RegistryAuthenticationFailedException(
        registry,
        repository,
        "server did not return 'WWW-Authenticate: Bearer' header: " + wwwAuthenticate);
  }
}
