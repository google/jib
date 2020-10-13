/*
 * Copyright 2020 Google LLC.
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

package com.google.cloud.tools.jib.cli.jar;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.cli.jar.JarProcessor.JarType;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

public class JarProcessorTest {

  private static final String SPRING_BOOT_RESOURCE_DIR = "jar/springboot/springboot_sample.jar";
  private static final String STANDARD_RESOURCE_DIR_WITH_CP = "jar/standard/standardJarWithCp.jar";
  private static final String STANDARD_RESOURCE_DIR = "jar/standard/standardJar.jar";

  @Test
  public void testDetermineJarType_springBoot() throws IOException, URISyntaxException {
    Path springBootJar = Paths.get(Resources.getResource(SPRING_BOOT_RESOURCE_DIR).toURI());
    JarType jarType = JarProcessor.determineJarType(springBootJar);
    assertThat(jarType).isEqualTo(JarType.SPRING_BOOT);
  }

  @Test
  public void testDetermineJarType_standard() throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_RESOURCE_DIR).toURI());
    JarType jarType = JarProcessor.determineJarType(standardJar);
    assertThat(jarType).isEqualTo(JarType.STANDARD);
  }

  @Test
  public void testExplodeMode_standard() throws IOException, URISyntaxException {
    Path standardJar = Paths.get(Resources.getResource(STANDARD_RESOURCE_DIR_WITH_CP).toURI());
    List<FileEntriesLayer> layers = JarProcessor.explodeStandardJar(standardJar);
    FileEntriesLayer classesLayer = layers.get(0);
    FileEntriesLayer resourcesLayer = layers.get(1);
    FileEntriesLayer dependenciesLayer = layers.get(2);

    assertThat(layers.size()).isEqualTo(3);

    // Validate classes layer.
    //    assertThat(classesLayer.getEntries().size()).isEqualTo(10);
    assertThat(classesLayer.getName()).isEqualTo("Classes");
    List<AbsoluteUnixPath> actualClassesPaths =
        classesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/app/explodedJar/directory4",
            "/app/explodedJar/directory2",
            "/app/explodedJar/directory2/directory3",
            "/app/explodedJar/directory2/directory3/class3.class",
            "/app/explodedJar/directory2/class4.class",
            "/app/explodedJar/META-INF",
            "/app/explodedJar/directory1",
            "/app/explodedJar/directory1/class2.class",
            "/app/explodedJar/directory1/class1.class",
            "/app/explodedJar/class5.class"),
        actualClassesPaths);

    // Validate resources layer.
    assertThat(resourcesLayer.getEntries().size()).isEqualTo(9);
    assertThat(resourcesLayer.getName()).isEqualTo("Resources");
    List<AbsoluteUnixPath> actualResourcesPaths =
        resourcesLayer
            .getEntries()
            .stream()
            .map(FileEntry::getExtractionPath)
            .collect(Collectors.toList());
    assertExtractionPathsUnordered(
        Arrays.asList(
            "/app/explodedJar/directory4",
            "/app/explodedJar/directory4/resource3.txt",
            "/app/explodedJar/directory2",
            "/app/explodedJar/directory2/directory3",
            "/app/explodedJar/directory2/directory3/resource2.sql",
            "/app/explodedJar/META-INF",
            "/app/explodedJar/META-INF/MANIFEST.MF",
            "/app/explodedJar/directory1",
            "/app/explodedJar/directory1/resource1.txt"),
        actualResourcesPaths);

    // Validate dependencies layer.
    assertThat(dependenciesLayer.getEntries().size()).isEqualTo(3);
    assertThat(dependenciesLayer.getName()).isEqualTo("Dependencies");
    assertThat(
            dependenciesLayer
                .getEntries()
                .stream()
                .map(FileEntry::getExtractionPath)
                .collect(Collectors.toList()))
        .isEqualTo(
            ImmutableList.of(
                AbsoluteUnixPath.get("/app/dependencies/dependency1"),
                AbsoluteUnixPath.get("/app/dependencies/dependency2"),
                AbsoluteUnixPath.get("/app/dependencies/directory/dependency3")));
  }

  private static void assertExtractionPathsUnordered(
      List<String> expectedPaths, List<AbsoluteUnixPath> actualPaths) {
    List<String> expected = expectedPaths.stream().sorted().collect(Collectors.toList());
    List<String> actual =
        actualPaths.stream().map(path -> path.toString()).sorted().collect(Collectors.toList());
    Assert.assertEquals(expected, actual);
  }
}
