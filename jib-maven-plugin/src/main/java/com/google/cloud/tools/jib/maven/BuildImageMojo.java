/*
 * Copyright 2018 Google LLC. All rights reserved.
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

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.frontend.BuildStepsExecutionException;
import com.google.cloud.tools.jib.frontend.BuildStepsRunner;
import com.google.cloud.tools.jib.frontend.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.frontend.HelpfulSuggestions;
import com.google.cloud.tools.jib.frontend.MainClassFinder;
import com.google.cloud.tools.jib.image.ImageReference;
import com.google.cloud.tools.jib.image.json.BuildableManifestTemplate;
import com.google.cloud.tools.jib.image.json.OCIManifestTemplate;
import com.google.cloud.tools.jib.image.json.V22ManifestTemplate;
import com.google.cloud.tools.jib.registry.RegistryClient;
import com.google.cloud.tools.jib.registry.credentials.RegistryCredentials;
import com.google.common.base.Preconditions;
import java.util.Arrays;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Builds a container image. */
@Mojo(name = "build", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM)
public class BuildImageMojo extends JibPluginConfiguration {

  /** Enumeration of {@link BuildableManifestTemplate}s. */
  public enum ImageFormat {
    Docker(V22ManifestTemplate.class),
    OCI(OCIManifestTemplate.class);

    private final Class<? extends BuildableManifestTemplate> manifestTemplateClass;

    ImageFormat(Class<? extends BuildableManifestTemplate> manifestTemplateClass) {
      this.manifestTemplateClass = manifestTemplateClass;
    }

    private Class<? extends BuildableManifestTemplate> getManifestTemplateClass() {
      return manifestTemplateClass;
    }
  }

  /** {@code User-Agent} header suffix to send to the registry. */
  private static final String USER_AGENT_SUFFIX = "jib-maven-plugin";

  private static final HelpfulSuggestions HELPFUL_SUGGESTIONS =
      HelpfulSuggestionsProvider.get("Build image failed");

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    // Validate 'imageFormat'.
    if (Arrays.stream(ImageFormat.values()).noneMatch(value -> value.name().equals(getFormat()))) {
      throw new MojoFailureException(
          "<imageFormat> parameter is configured with value '"
              + getFormat()
              + "', but the only valid configuration options are '"
              + ImageFormat.Docker
              + "' and '"
              + ImageFormat.OCI
              + "'.");
    }

    // Parses 'from' and 'to' into image reference.
    ImageReference baseImage = parseBaseImageReference(getBaseImage());
    ImageReference targetImage = parseTargetImageReference(getTargetImage());

    // Checks Maven settings for registry credentials.
    MavenSettingsServerCredentials mavenSettingsServerCredentials =
        new MavenSettingsServerCredentials(Preconditions.checkNotNull(session).getSettings());
    RegistryCredentials knownBaseRegistryCredentials =
        mavenSettingsServerCredentials.retrieve(baseImage.getRegistry());
    RegistryCredentials knownTargetRegistryCredentials =
        mavenSettingsServerCredentials.retrieve(targetImage.getRegistry());

    MavenBuildLogger mavenBuildLogger = new MavenBuildLogger(getLog());
    MavenProjectProperties mavenProjectProperties =
        MavenProjectProperties.getForProject(getProject(), mavenBuildLogger);
    BuildConfiguration buildConfiguration =
        BuildConfiguration.builder(mavenBuildLogger)
            .setBaseImage(baseImage)
            .setBaseImageCredentialHelperName(getBaseImageCredentialHelperName())
            .setKnownBaseRegistryCredentials(knownBaseRegistryCredentials)
            .setTargetImage(targetImage)
            .setTargetImageCredentialHelperName(getTargetImageCredentialHelperName())
            .setKnownTargetRegistryCredentials(knownTargetRegistryCredentials)
            .setMainClass(MainClassFinder.resolveMainClass(getMainClass(), mavenProjectProperties))
            .setJvmFlags(getJvmFlags())
            .setEnvironment(getEnvironment())
            .setTargetFormat(ImageFormat.valueOf(getFormat()).getManifestTemplateClass())
            .build();

    // TODO: Instead of disabling logging, have authentication credentials be provided
    // Disables annoying Apache HTTP client logging.
    System.setProperty(
        "org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
    System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "error");

    RegistryClient.setUserAgentSuffix(USER_AGENT_SUFFIX);

    try {
      BuildStepsRunner.forBuildImage(
              buildConfiguration,
              mavenProjectProperties.getSourceFilesConfiguration(),
              mavenProjectProperties.getCacheDirectory(),
              getUseOnlyProjectCache())
          .build(HELPFUL_SUGGESTIONS);
      getLog().info("");

    } catch (CacheDirectoryCreationException | BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());
    }
  }
}
