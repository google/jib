/*
 * Copyright 2021 Google LLC.
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

package com.google.cloud.tools.jib.cli.war;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.cli.JarProcessor;
import com.google.cloud.tools.jib.cli.jar.JarLayers;
import com.google.cloud.tools.jib.plugins.common.ZipUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

class StandardWarExplodedProcessor implements JarProcessor {

  private final Path warPath;
  private final Path targetExplodedWarRoot;
  private final Integer warJavaVersion;
  private final AbsoluteUnixPath appRoot;

  /**
   * Constructor for {@link StandardWarExplodedProcessor}.
   *
   * @param warPath path to war file
   * @param targetExplodedWarRoot path to exploded-war root
   * @param warJavaVersion war java version
   * @param appRoot app root in container
   */
  StandardWarExplodedProcessor(
      Path warPath, Path targetExplodedWarRoot, Integer warJavaVersion, AbsoluteUnixPath appRoot) {
    this.warPath = warPath;
    this.targetExplodedWarRoot = targetExplodedWarRoot;
    this.warJavaVersion = warJavaVersion;
    this.appRoot = appRoot;
  }

  @Override
  public List<FileEntriesLayer> createLayers() throws IOException {
    // Clear the exploded-jar root first
    if (Files.exists(targetExplodedWarRoot)) {
      MoreFiles.deleteRecursively(targetExplodedWarRoot, RecursiveDeleteOption.ALLOW_INSECURE);
    }

    ZipUtil.unzip(warPath, targetExplodedWarRoot, true);
    Predicate<Path> isFile = Files::isRegularFile;
    Predicate<Path> isInWebInfLib =
        path -> path.startsWith(targetExplodedWarRoot.resolve("WEB-INF").resolve("lib"));
    Predicate<Path> isSnapshot = path -> path.getFileName().toString().contains("SNAPSHOT");

    // Non-snapshot layer
    Predicate<Path> isInWebInfLibAndIsNotSnapshot = isInWebInfLib.and(isSnapshot.negate());
    Predicate<Path> nonSnapshotPredicate = isFile.and(isInWebInfLibAndIsNotSnapshot);
    FileEntriesLayer nonSnapshotLayer =
        JarLayers.getDirectoryContentsAsLayer(
            "dependencies", targetExplodedWarRoot, nonSnapshotPredicate, appRoot);

    // Snapshot layer
    Predicate<Path> isInWebInfLibAndIsSnapshot = isInWebInfLib.and(isSnapshot);
    Predicate<Path> snapshotPredicate = isFile.and(isInWebInfLibAndIsSnapshot);
    FileEntriesLayer snapshotLayer =
        JarLayers.getDirectoryContentsAsLayer(
            "snapshot dependencies", targetExplodedWarRoot, snapshotPredicate, appRoot);

    // Classes layer.
    Predicate<Path> isClass = path -> path.getFileName().toString().endsWith(".class");
    Predicate<Path> isInWebInfClasses =
        path -> path.startsWith(targetExplodedWarRoot.resolve("WEB-INF").resolve("classes"));
    Predicate<Path> classesPredicate = isInWebInfClasses.and(isClass);
    FileEntriesLayer classesLayer =
        JarLayers.getDirectoryContentsAsLayer(
            "classes", targetExplodedWarRoot, classesPredicate, appRoot);

    // Resources layer.
    Predicate<Path> resourcesPredicate = isInWebInfLib.or(isClass).negate();
    FileEntriesLayer resourcesLayer =
        JarLayers.getDirectoryContentsAsLayer(
            "resources",
            targetExplodedWarRoot,
            resourcesPredicate.and(Files::isRegularFile),
            appRoot);

    ArrayList<FileEntriesLayer> layers = new ArrayList<>();
    if (!nonSnapshotLayer.getEntries().isEmpty()) {
      layers.add(nonSnapshotLayer);
    }
    if (!snapshotLayer.getEntries().isEmpty()) {
      layers.add(snapshotLayer);
    }
    if (!resourcesLayer.getEntries().isEmpty()) {
      layers.add(resourcesLayer);
    }
    if (!classesLayer.getEntries().isEmpty()) {
      layers.add(classesLayer);
    }

    return layers;
  }

  @Override
  public ImmutableList<String> computeEntrypoint(List<String> jvmFlags) {
    ImmutableList.Builder<String> entrypoint = ImmutableList.builder();
    entrypoint.add("java");
    entrypoint.add("-jar");
    entrypoint.add("/usr/local/jetty/start.jar");
    return entrypoint.build();
  }

  @Override
  public Integer getJarJavaVersion() {
    return warJavaVersion;
  }
}
