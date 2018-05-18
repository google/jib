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

import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.frontend.BuildStepsExecutionException;
import com.google.cloud.tools.jib.frontend.BuildStepsRunner;
import com.google.cloud.tools.jib.frontend.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.http.Authorization;
import com.google.cloud.tools.jib.http.Authorizations;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.InvalidImageReferenceException;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

/** Builds a container image and exports to the default Docker daemon. */
public class BuildDockerTask extends DefaultTask {

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build to Docker daemon failed");

  @Nullable private JibExtension jibExtension;

  @TaskAction
  public void buildDocker() throws InvalidImageReferenceException {
    if (!BuildStepsRunner.isDockerInstalled()) {
      throw new GradleException(HELPFUL_SUGGESTIONS.forDockerNotInstalled());
    }

    // Asserts required @Input parameters are not null.
    Preconditions.checkNotNull(jibExtension);

    GradleBuildLogger gradleBuildLogger = new GradleBuildLogger(getLogger());

    ImageReference baseImageReference = ImageReference.parse(jibExtension.getBaseImage());
    ImageReference targetImageReference = ImageReference.parse(jibExtension.getTargetImage());
    if (baseImageReference.usesDefaultTag()) {
      gradleBuildLogger.warn(
          "Base image '"
              + baseImageReference
              + "' does not use a specific image digest - build may not be reproducible");
    }

    ProjectProperties projectProperties =
        ProjectProperties.getForProject(getProject(), gradleBuildLogger);
    String mainClass = projectProperties.getMainClass(jibExtension.getMainClass());

    RegistryCredentials knownBaseRegistryCredentials = null;
    Authorization fromAuthorization = jibExtension.getFrom().getImageAuthorization();
    if (fromAuthorization != null) {
      knownBaseRegistryCredentials = new RegistryCredentials("jib.from.auth", fromAuthorization);
    }

    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(gradleBuildLogger)
            .setBaseImage(baseImageReference)
            .setBaseImageCredentialHelperName(jibExtension.getFrom().getCredHelper())
            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
            .setTargetImage(targetImageReference)
            .setMainClass(mainClass)
            .setJvmFlags(jibExtension.getJvmFlags())
            .build();

    // Uses a directory in the Gradle build cache as the Jib cache.
    Path cacheDirectory = projectProperties.getCacheDirectory();
    try {
      BuildStepsRunner.forBuildToDockerDaemon(
              buildConfiguration,
              projectProperties.getSourceFilesConfiguration(),
              cacheDirectory,
              jibExtension.getUseOnlyProjectCache())
          .build(HELPFUL_SUGGESTIONS);

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new GradleException(ex.getMessage(), ex.getCause());
    }
  }

  void setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }
}
