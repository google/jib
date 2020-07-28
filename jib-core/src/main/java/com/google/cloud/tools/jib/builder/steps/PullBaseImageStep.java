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

import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.DescriptorDigest;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryUnauthorizedException;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.blob.Blobs;
import com.google.cloud.tools.jib.builder.ProgressEventDispatcher;
import com.google.cloud.tools.jib.builder.TimerEventDispatcher;
import com.google.cloud.tools.jib.builder.steps.PullBaseImageStep.ImageAndRegistryClient;
import com.google.cloud.tools.jib.cache.CacheCorruptedException;
import com.google.cloud.tools.jib.configuration.BuildContext;
import com.google.cloud.tools.jib.event.EventHandlers;
import com.google.cloud.tools.jib.event.events.ProgressEvent;
import com.google.cloud.tools.jib.image.Image;
import com.google.cloud.tools.jib.image.LayerCountMismatchException;
import com.google.cloud.tools.jib.image.LayerPropertyNotFoundException;
import com.google.cloud.tools.jib.image.json.BadContainerConfigurationFormatException;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate;
import com.google.cloud.tools.jib.image.json.JsonToImageTranslator;
import com.google.cloud.tools.jib.image.json.ManifestAndConfig;
import com.google.cloud.tools.jib.image.json.ManifestTemplate;
import com.google.cloud.tools.jib.image.json.UnknownManifestFormatException;
import com.google.cloud.tools.jib.image.json.V21ManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestListTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.registry.ManifestAndDigest;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.CredentialRetrievalException;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;

/** Pulls the base image manifests for the specified platforms. */
class PullBaseImageStep implements Callable<List<ImageAndRegistryClient>> {

  private static final String DESCRIPTION = "Pulling base image manifest";

  /** Structure for the result returned by this step. */
  static class ImageAndRegistryClient {

    final Image image;
    @Nullable final RegistryClient registryClient;

    ImageAndRegistryClient(Image image, @Nullable RegistryClient registryClient) {
      this.image = image;
      this.registryClient = registryClient;
    }
  }

  private final BuildContext buildContext;
  private final ProgressEventDispatcher.Factory progressEventDispatcherFactory;

  PullBaseImageStep(
      BuildContext buildContext, ProgressEventDispatcher.Factory progressEventDispatcherFactory) {
    this.buildContext = buildContext;
    this.progressEventDispatcherFactory = progressEventDispatcherFactory;
  }

  @Override
  public List<ImageAndRegistryClient> call()
      throws IOException, RegistryException, LayerPropertyNotFoundException,
          LayerCountMismatchException, BadContainerConfigurationFormatException,
          CacheCorruptedException, CredentialRetrievalException {
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    // Skip this step if this is a scratch image
    ImageReference imageReference = buildContext.getBaseImageConfiguration().getImage();
    if (imageReference.isScratch()) {
      eventHandlers.dispatch(LogEvent.progress("Getting scratch base image..."));
      return Collections.singletonList(
          new ImageAndRegistryClient(Image.builder(buildContext.getTargetFormat()).build(), null));
    }

    eventHandlers.dispatch(
        LogEvent.progress("Getting manifest for base image " + imageReference + "..."));

    if (buildContext.isOffline()) {
      Optional<Image> image = getCachedBaseImage();
      if (image.isPresent()) {
        return Collections.singletonList(new ImageAndRegistryClient(image.get(), null));
      }
      throw new IOException(
          "Cannot run Jib in offline mode; " + imageReference + " not found in local Jib cache");

    } else if (imageReference.getDigest().isPresent()) {
      Optional<Image> image = getCachedBaseImage();
      if (image.isPresent()) {
        RegistryClient noAuthRegistryClient =
            buildContext.newBaseImageRegistryClientFactory().newRegistryClient();
        // TODO: passing noAuthRegistryClient may be problematic. It may return 401 unauthorized if
        // layers have to be downloaded. https://github.com/GoogleContainerTools/jib/issues/2220
        return Collections.singletonList(
            new ImageAndRegistryClient(image.get(), noAuthRegistryClient));
      }
    }

    try (ProgressEventDispatcher progressEventDispatcher =
            progressEventDispatcherFactory.create("pulling base image manifest", 2);
        TimerEventDispatcher ignored1 = new TimerEventDispatcher(eventHandlers, DESCRIPTION)) {

      // First, try with no credentials.
      RegistryClient noAuthRegistryClient =
          buildContext.newBaseImageRegistryClientFactory().newRegistryClient();
      try {
        return pullBaseImage(noAuthRegistryClient, progressEventDispatcher);

      } catch (RegistryUnauthorizedException ex) {
        eventHandlers.dispatch(
            LogEvent.lifecycle(
                "The base image requires auth. Trying again for " + imageReference + "..."));

        Credential registryCredential =
            RegistryCredentialRetriever.getBaseImageCredential(buildContext).orElse(null);

        RegistryClient registryClient =
            buildContext
                .newBaseImageRegistryClientFactory()
                .setCredential(registryCredential)
                .newRegistryClient();

        try {
          // TODO: refactor the code (https://github.com/GoogleContainerTools/jib/pull/2202)
          if (registryCredential == null || registryCredential.isOAuth2RefreshToken()) {
            throw ex;
          }

          eventHandlers.dispatch(LogEvent.debug("Trying basic auth for " + imageReference + "..."));
          registryClient.configureBasicAuth();
          return pullBaseImage(registryClient, progressEventDispatcher);

        } catch (RegistryUnauthorizedException registryUnauthorizedException) {
          // The registry requires us to authenticate using the Docker Token Authentication.
          // See https://docs.docker.com/registry/spec/auth/token
          eventHandlers.dispatch(
              LogEvent.debug("Trying bearer auth for " + imageReference + "..."));
          if (registryClient.doPullBearerAuth()) {
            return pullBaseImage(registryClient, progressEventDispatcher);
          }
          eventHandlers.dispatch(
              LogEvent.error(
                  "The registry asked for basic authentication, but the registry had refused basic "
                      + "authentication previously"));
          throw registryUnauthorizedException;
        }
      }
    }
  }

  /**
   * Pulls the base images specified in the platforms list.
   *
   * @param registryClient to communicate with remote registry
   * @param progressEventDispatcher the {@link ProgressEventDispatcher} for emitting {@link
   *     ProgressEvent}s
   * @return the list of pulled images
   * @throws IOException when an I/O exception occurs during the pulling
   * @throws RegistryException if communicating with the registry caused a known error
   * @throws LayerCountMismatchException if the manifest and configuration contain conflicting layer
   *     information
   * @throws LayerPropertyNotFoundException if adding image layers fails
   * @throws BadContainerConfigurationFormatException if the container configuration is in a bad
   *     format
   */
  private List<ImageAndRegistryClient> pullBaseImage(
      RegistryClient registryClient, ProgressEventDispatcher progressEventDispatcher)
      throws IOException, RegistryException, LayerPropertyNotFoundException,
          LayerCountMismatchException, BadContainerConfigurationFormatException {
    EventHandlers eventHandlers = buildContext.getEventHandlers();
    List<ManifestAndDigest<?>> manifestsAndDigests = new ArrayList<>();
    List<ImageAndRegistryClient> imagesAndRegistryClient = new ArrayList<>();

    ManifestAndDigest<?> manifestAndDigest =
        registryClient.pullManifest(buildContext.getBaseImageConfiguration().getImageQualifier());
    ManifestTemplate manifestTemplate = manifestAndDigest.getManifest();

    // special handling if we happen upon a manifest list, extract manifests handle them normally
    if (manifestTemplate instanceof V22ManifestListTemplate) {
      Set<Platform> platforms = buildContext.getContainerConfiguration().getPlatforms();
      Iterator<Platform> platformsIterator = platforms.iterator();
      while (platformsIterator.hasNext()) {
        manifestsAndDigests.add(
            obtainPlatformSpecificImageManifest(
                registryClient,
                (V22ManifestListTemplate) manifestTemplate,
                platformsIterator.next()));
      }
    } else {
      manifestsAndDigests.add(manifestAndDigest);
    }

    for (ManifestAndDigest<?> manifestAndDigestInstance : manifestsAndDigests) {
      manifestTemplate = manifestAndDigestInstance.getManifest();
      switch (manifestTemplate.getSchemaVersion()) {
        case 1:
          V21ManifestTemplate v21ManifestTemplate = (V21ManifestTemplate) manifestTemplate;
          buildContext
              .getBaseImageLayersCache()
              .writeMetadata(
                  buildContext.getBaseImageConfiguration().getImage(), v21ManifestTemplate);
          imagesAndRegistryClient.add(
              new ImageAndRegistryClient(
                  JsonToImageTranslator.toImage(v21ManifestTemplate), registryClient));
          break;
        case 2:
          eventHandlers.dispatch(
              LogEvent.lifecycle("Using base image with digest: " + manifestAndDigest.getDigest()));
          BuildableManifestTemplate buildableManifestTemplate =
              (BuildableManifestTemplate) manifestTemplate;
          if (buildableManifestTemplate.getContainerConfiguration() == null
              || buildableManifestTemplate.getContainerConfiguration().getDigest() == null) {
            throw new UnknownManifestFormatException(
                "Invalid container configuration in Docker V2.2/OCI manifest: \n"
                    + JsonTemplateMapper.toUtf8String(buildableManifestTemplate));
          }

          DescriptorDigest containerConfigurationDigest =
              buildableManifestTemplate.getContainerConfiguration().getDigest();

          try (ThrottledProgressEventDispatcherWrapper progressEventDispatcherWrapper =
              new ThrottledProgressEventDispatcherWrapper(
                  progressEventDispatcher.newChildProducer(),
                  "pull container configuration " + containerConfigurationDigest)) {
            String containerConfigurationString =
                Blobs.writeToString(
                    registryClient.pullBlob(
                        containerConfigurationDigest,
                        progressEventDispatcherWrapper::setProgressTarget,
                        progressEventDispatcherWrapper::dispatchProgress));

            ContainerConfigurationTemplate containerConfigurationTemplate =
                JsonTemplateMapper.readJson(
                    containerConfigurationString, ContainerConfigurationTemplate.class);
            buildContext
                .getBaseImageLayersCache()
                .writeMetadata(
                    buildContext.getBaseImageConfiguration().getImage(),
                    buildableManifestTemplate,
                    containerConfigurationTemplate);

            imagesAndRegistryClient.add(
                new ImageAndRegistryClient(
                    JsonToImageTranslator.toImage(
                        buildableManifestTemplate, containerConfigurationTemplate),
                    registryClient));
          }
          break;
        default:
          LogEvent.info("Unknown manifest schema version: " + manifestTemplate.getSchemaVersion());
      }
    }
    return imagesAndRegistryClient;
  }

  /**
   * Looks through a manifest list for the user specified arch/os manifest and downloads and returns
   * the first manifest it finds.
   */
  @VisibleForTesting
  ManifestAndDigest<?> obtainPlatformSpecificImageManifest(
      RegistryClient registryClient,
      V22ManifestListTemplate manifestListTemplate,
      Platform platform)
      throws IOException, RegistryException {

    String architecture = platform.getArchitecture();
    String os = platform.getOs();

    EventHandlers eventHandlers = buildContext.getEventHandlers();
    eventHandlers.dispatch(
        LogEvent.lifecycle(
            "The base image reference is a manifest list, searching for architecture="
                + architecture
                + ", os="
                + os));

    List<String> digests = manifestListTemplate.getDigestsForPlatform(architecture, os);
    if (digests.size() == 0) {
      String errorMessage =
          buildContext.getBaseImageConfiguration().getImage()
              + " is a manifest list, but the list does not contain an image manifest for the platform architecture="
              + architecture
              + ", os="
              + os
              + ". If your intention was to specify a platform for your image,"
              + " see https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#how-do-i-specify-a-platform-in-the-manifest-list-or-oci-index-of-a-base-image"
              + " to learn more about specifying a platform";

      eventHandlers.dispatch(LogEvent.error(errorMessage));
      throw new RegistryException(errorMessage);
    }
    return registryClient.pullManifest(digests.get(0));
  }

  /**
   * Retrieves the cached base image.
   *
   * @return the cached image, if found
   * @throws IOException when an I/O exception occurs
   * @throws CacheCorruptedException if the cache is corrupted
   * @throws LayerPropertyNotFoundException if adding image layers fails
   * @throws BadContainerConfigurationFormatException if the container configuration is in a bad
   *     format
   */
  private Optional<Image> getCachedBaseImage()
      throws IOException, CacheCorruptedException, BadContainerConfigurationFormatException,
          LayerCountMismatchException {
    ImageReference baseImage = buildContext.getBaseImageConfiguration().getImage();
    Optional<ManifestAndConfig> metadata =
        buildContext.getBaseImageLayersCache().retrieveMetadata(baseImage);
    if (!metadata.isPresent()) {
      return Optional.empty();
    }

    ManifestTemplate manifestTemplate = metadata.get().getManifest();
    if (manifestTemplate instanceof V21ManifestTemplate) {
      return Optional.of(JsonToImageTranslator.toImage((V21ManifestTemplate) manifestTemplate));
    }

    ContainerConfigurationTemplate configurationTemplate =
        metadata.get().getConfig().orElseThrow(IllegalStateException::new);
    return Optional.of(
        JsonToImageTranslator.toImage(
            (BuildableManifestTemplate) manifestTemplate, configurationTemplate));
  }
}
