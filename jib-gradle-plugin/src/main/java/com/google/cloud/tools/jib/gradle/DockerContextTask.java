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

import com.google.cloud.tools.jib.docker.DockerContextGenerator;
import com.google.common.base.Preconditions;
import com.google.common.io.InsecureRecursiveDeleteException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
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

  /**
   * The input files to this task are all the output files for all the dependencies of the {@code
   * classes} task.
   */
  @InputFiles
  public FileCollection getInputFiles() {
    Task classesTask = getProject().getTasks().getByPath("classes");
    Set<? extends Task> classesDependencies =
        classesTask.getTaskDependencies().getDependencies(classesTask);

    List<FileCollection> dependencyFileCollections = new ArrayList<>();
    for (Task task : classesDependencies) {
      dependencyFileCollections.add(task.getOutputs().getFiles());
    }
    return getProject().files(dependencyFileCollections);
  }

  /** The output directory for the Docker context is by default {@code build/jib-docker-context}. */
  @OutputDirectory
  public String getTargetDir() {
    if (targetDir == null) {
      return getProject().getBuildDir().toPath().resolve("jib-docker-context").toString();
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
    Preconditions.checkNotNull(jibExtension);

    GradleBuildLogger gradleBuildLogger = new GradleBuildLogger(getLogger());
    GradleProjectProperties gradleProjectProperties =
        GradleProjectProperties.getForProject(getProject(), gradleBuildLogger);
    String mainClass = gradleProjectProperties.getMainClass(jibExtension);

    String targetDir = getTargetDir();

    try {
      new DockerContextGenerator(gradleProjectProperties.getSourceFilesConfiguration())
          .setBaseImage(jibExtension.getBaseImage())
          .setJvmFlags(jibExtension.getJvmFlags())
          .setMainClass(mainClass)
          .setJavaArguments(jibExtension.getArgs())
          .generate(Paths.get(targetDir));

      gradleBuildLogger.info("Created Docker context at " + targetDir);

    } catch (InsecureRecursiveDeleteException ex) {
      throw new GradleException(
          HelpfulSuggestionsProvider.get(
                  "Export Docker context failed because cannot clear directory '"
                      + getTargetDir()
                      + "' safely")
              .forDockerContextInsecureRecursiveDelete(getTargetDir()),
          ex);

    } catch (IOException ex) {
      throw new GradleException(
          HelpfulSuggestionsProvider.get("Export Docker context failed")
              .suggest("check if the command-line option `--jib.dockerDir` is set correctly"),
          ex);
    }
  }

  DockerContextTask setJibExtension(JibExtension jibExtension) {
    this.jibExtension = jibExtension;
    return this;
  }
}
