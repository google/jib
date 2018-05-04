/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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

package com.google.cloud.tools.jib.builder;

import com.google.cloud.tools.jib.Timer;
import com.google.cloud.tools.jib.blob.Blob;
import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.hash.CountingDigestOutputStream;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.RegistryException;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/** Pushes the container configuration. */
class PushContainerConfigurationStep implements Callable<ListenableFuture<BlobDescriptor>> {

  private static final String DESCRIPTION = "Pushing container configuration";

  private final BuildConfiguration buildConfiguration;
  private final ListenableFuture<Authorization> pushAuthorizationFuture;
  private final ListenableFuture<ListenableFuture<Blob>> buildContainerConfigurationFutureFuture;
  private final ListeningExecutorService listeningExecutorService;

  PushContainerConfigurationStep(
      BuildConfiguration buildConfiguration,
      ListenableFuture<Authorization> pushAuthorizationFuture,
      ListenableFuture<ListenableFuture<Blob>> buildContainerConfigurationFutureFuture,
      ListeningExecutorService listeningExecutorService) {
    this.buildConfiguration = buildConfiguration;
    this.pushAuthorizationFuture = pushAuthorizationFuture;
    this.buildContainerConfigurationFutureFuture = buildContainerConfigurationFutureFuture;
    this.listeningExecutorService = listeningExecutorService;
  }

  /** Depends on {@code blobFuturesFuture} and {@code pushAuthorizationFuture}. */
  @Override
  public ListenableFuture<BlobDescriptor> call() throws ExecutionException, InterruptedException {
    List<ListenableFuture<?>> blobFuturesFutureDependencies = new ArrayList<>();
    blobFuturesFutureDependencies.add(pushAuthorizationFuture);
    blobFuturesFutureDependencies.add(
        NonBlockingFutures.get(buildContainerConfigurationFutureFuture));
    return Futures.whenAllSucceed(blobFuturesFutureDependencies)
        .call(this::afterBlobFuturesFuture, listeningExecutorService);
  }

  /** Depends on {@code blobFuturesFuture.get()} and {@code pushAuthorizationFuture}. */
  private BlobDescriptor afterBlobFuturesFuture()
      throws ExecutionException, InterruptedException, IOException, RegistryException {
    try (Timer timer = new Timer(buildConfiguration.getBuildLogger(), DESCRIPTION)) {
      // TODO: Use PushBlobStep.
      // Pushes the container configuration.
      RegistryClient registryClient =
          new RegistryClient(
                  NonBlockingFutures.get(pushAuthorizationFuture),
                  buildConfiguration.getTargetRegistry(),
                  buildConfiguration.getTargetRepository())
              .setTimer(timer);

      CountingDigestOutputStream digestOutputStream =
          new CountingDigestOutputStream(ByteStreams.nullOutputStream());
      Blob blob =
          NonBlockingFutures.get(NonBlockingFutures.get(buildContainerConfigurationFutureFuture));
      blob.writeTo(digestOutputStream);

      BlobDescriptor descriptor = digestOutputStream.toBlobDescriptor();
      registryClient.pushBlob(descriptor.getDigest(), blob);
      return descriptor;
    }
  }
}
