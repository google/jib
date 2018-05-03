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

package com.google.cloud.tools.jib.gradle;

import com.google.api.client.http.HttpTransport;
import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.builder.BuildImageSteps;
import com.google.cloud.tools.jib.cache.Caches;
import com.google.cloud.tools.jib.frontend.HelpfulMessageBuilder;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

/** Builds a container image. */
public class BuildImageTask extends DefaultTask {

  /** Directory name for the cache. The directory will be relative to the build output directory. */
  private static final String CACHE_DIRECTORY_NAME = "jib-cache";

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-gradle-plugin";

  private static final HelpfulMessageBuilder helpfulMessageBuilder =
      new HelpfulMessageBuilder("Build image failed");

  /** Converts an {@link ImageConfiguration} to an {@link Authorization}. */
  @Nullable
  private static Authorization getImageAuthorization(ImageConfiguration imageConfiguration) {
    if (imageConfiguration.getAuth().getUsername() == null
        || imageConfiguration.getAuth().getPassword() == null) {
      return null;
    }

    return Authorizations.withBasicCredentials(
        imageConfiguration.getAuth().getUsername(), imageConfiguration.getAuth().getPassword());
  }

  @Nullable private JibExtension jibExtension;

  /**
   * This will call the property {@code "jib"} so that it is the same name as the extension. This
   * way, the user would see error messages for missing configuration with the prefix {@code jib.}.
   */
  @Nested
  @Nullable
  public JibExtension getJib() {
    return jibExtension;
  }

  @TaskAction
  public void buildImage() throws InvalidImageReferenceException, IOException {
    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);
    Preconditions.checkNotNull(jibExtension.getFrom());
    Preconditions.checkNotNull(jibExtension.getFrom().getImage());
    Preconditions.checkNotNull(jibExtension.getTo());
    Preconditions.checkNotNull(jibExtension.getTo().getImage());
    Preconditions.checkNotNull(jibExtension.getJvmFlags());
    Preconditions.checkNotNull(jibExtension.getFormat());

    ImageReference baseImageReference = ImageReference.parse(jibExtension.getFrom().getImage());
    ImageReference targetImageReference = ImageReference.parse(jibExtension.getTo().getImage());

    ProjectProperties projectProperties = new ProjectProperties(getProject(), getLogger());
    String mainClass = projectProperties.getMainClass(jibExtension.getMainClass());

    RegistryCredentials knownBaseRegistryCredentials = null;
    RegistryCredentials knownTargetRegistryCredentials = null;
    Authorization fromAuthorization = getImageAuthorization(jibExtension.getFrom());
    if (fromAuthorization != null) {
      knownBaseRegistryCredentials = new RegistryCredentials("jib.from.auth", fromAuthorization);
    }
    Authorization toAuthorization = getImageAuthorization(jibExtension.getTo());
    if (toAuthorization != null) {
      knownTargetRegistryCredentials = new RegistryCredentials("jib.to.auth", toAuthorization);
    }

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(new GradleBuildLogger(getLogger()))
            .setBaseImage(baseImageReference)
            .setBaseImageCredentialHelperName(jibExtension.getFrom().getCredHelper())
            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
            .setTargetImage(targetImageReference)
            .setTargetImageCredentialHelperName(jibExtension.getTo().getCredHelper())
            .setKnownTargetRegistryCredentials(knownTargetRegistryCredentials)
            .setMainClass(mainClass)
            .setEnableReproducibleBuilds(jibExtension.getReproducible())
            .setJvmFlags(jibExtension.getJvmFlags())
            .setTargetFormat(jibExtension.getFormat())
            .build();

    // Uses a directory in the Gradle build cache as the Jib cache.
    Path cacheDirectory = getProject().getBuildDir().toPath().resolve(CACHE_DIRECTORY_NAME);
    if (!Files.exists(cacheDirectory)) {
      Files.createDirectory(cacheDirectory);
    }
    Caches.Initializer cachesInitializer = Caches.newInitializer(cacheDirectory);
    if (jibExtension.getUseOnlyProjectCache()) {
      cachesInitializer.setBaseCacheDirectory(cacheDirectory);
    }

    getLogger().lifecycle("Pushing image as " + targetImageReference);
    getLogger().lifecycle("");
    getLogger().lifecycle("");

    // TODO: Instead of disabling logging, have authentication credentials be provided
    // Disables annoying Apache HTTP client logging.
    System.setProperty(
        "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "error");
    // Disables Google HTTP client logging.
    Logger logger = Logger.getLogger(HttpTransport.class.getName());
    logger.setLevel(Level.OFF);

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    doBuildImage(
        new BuildImageSteps(
            buildConfiguration,
            projectProperties.getSourceFilesConfiguration(),
            cachesInitializer));

    getLogger().lifecycle("");
    getLogger().lifecycle("Built and pushed image as " + targetImageReference);
    getLogger().lifecycle("");
  }

  void setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }

  private void doBuildImage(BuildImageSteps buildImageSteps) {
    try {
      buildImageSteps.run();

    } catch (Throwable ex) {
      throw new GradleException(helpfulMessageBuilder.withNoHelp(), ex);
    }
    // TODO: Catch and handle exceptions.
  }
}
