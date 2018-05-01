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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.builder.BuildConfiguration;
import com.google.cloud.tools.jib.docker.DockerContextGenerator;
import com.google.common.base.Preconditions;
import com.google.common.io.InsecureRecursiveDeleteException;
import java.io.IOException;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class DockerContextTask extends DefaultTask {

  @Nullable private String targetDir;
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

  /** The output directory for the Docker context is by default {@code build/jib-dockercontext}. */
  @OutputDirectory
  public String getTargetDir() {
    if (targetDir == null) {
      return getProject().getBuildDir().toPath().resolve("jib-dockercontext").toString();
    }
    return targetDir;
  }

  /** The output directory can be overriden with the {@code --jib.dockerDir} command line option. */
  @Option(option = "jib.dockerDir", description = "Directory to output the Docker context to")
  public void setTargetDir(String targetDir) {
    this.targetDir = targetDir;
  }

  @TaskAction
  public void generateDockerContext() {
    // Asserts required parameters are not null.
    Preconditions.checkNotNull(jibExtension);
    Preconditions.checkNotNull(jibExtension.getFrom());
    Preconditions.checkNotNull(jibExtension.getFrom().getImage());
    Preconditions.checkNotNull(jibExtension.getJvmFlags());

    // TODO: Refactor with BuildImageTask.
    ProjectProperties projectProperties = new ProjectProperties(getProject(), getLogger());

    String mainClass = jibExtension.getMainClass();
    if (mainClass == null) {
      mainClass = projectProperties.getMainClassFromJarTask();
      if (mainClass == null) {
        throw new GradleException("Could not find main class specified in a 'jar' task");
      }
    }
    if (!BuildConfiguration.isValidJavaClass(mainClass)) {
      getLogger().warn("'mainClass' is not a valid Java class : " + mainClass);
    }

    String targetDir = getTargetDir();

    try {
      new DockerContextGenerator(projectProperties.getSourceFilesConfiguration())
          .setBaseImage(jibExtension.getFrom().getImage())
          .setJvmFlags(jibExtension.getJvmFlags())
          .setMainClass(mainClass)
          .generate(Paths.get(targetDir));

      getLogger().info("Created Docker context at " + targetDir);

    } catch (InsecureRecursiveDeleteException ex) {
      throwMojoExecutionExceptionWithHelpMessage(
          ex,
          "cannot clear directory '"
              + targetDir
              + "' safely - clear it manually before creating the Docker context");

    } catch (IOException ex) {
      throwMojoExecutionExceptionWithHelpMessage(
          ex, "check if the command-line option `--jib.dockerDir` is set correctly");
    }
  }

  void setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
  }

  private <T extends Throwable> void throwMojoExecutionExceptionWithHelpMessage(
      T ex, String suggestion) {
    StringBuilder message = new StringBuilder("Export Docker context failed");
    if (suggestion != null) {
      message.append(", perhaps you should ");
      message.append(suggestion);
    }
    throw new GradleException(message.toString(), ex);
  }
}
