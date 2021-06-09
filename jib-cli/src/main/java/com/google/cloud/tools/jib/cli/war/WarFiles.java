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

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.cli.ArtifactProcessor;
import com.google.cloud.tools.jib.cli.CommonCliOptions;
import com.google.cloud.tools.jib.cli.CommonContainerConfigCliOptions;
import com.google.cloud.tools.jib.cli.ContainerBuilders;
import com.google.cloud.tools.jib.plugins.common.logging.ConsoleLogger;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class WarFiles {

  /**
   * Generates a {@link JibContainerBuilder} from contents of a WAR file.
   *
   * @param processor artifact processor
   * @param commonCliOptions common cli options
   * @param commonContainerConfigCliOptions common cli options shared between jar and war command
   * @param logger console logger
   * @return JibContainerBuilder
   * @throws IOException if I/O error occurs when opening the jar file or if temporary directory
   *     provided doesn't exist
   * @throws InvalidImageReferenceException if the base image reference is invalid
   */
  public static JibContainerBuilder toJibContainerBuilder(
      ArtifactProcessor processor,
      CommonCliOptions commonCliOptions,
      CommonContainerConfigCliOptions commonContainerConfigCliOptions,
      ConsoleLogger logger)
      throws IOException, InvalidImageReferenceException {
    JibContainerBuilder containerBuilder;
    List<FileEntriesLayer> layers;
    Optional<String> baseImage = commonContainerConfigCliOptions.getFrom();
    if (baseImage.isPresent()) {
      containerBuilder =
          ContainerBuilders.create(
              baseImage.get(),
              Collections.emptySet(),
              commonCliOptions,
              logger);
    } else {
      containerBuilder = ContainerBuilders.create("jetty",   Collections.emptySet(), commonCliOptions, logger);
    }

    if (commonContainerConfigCliOptions.getProgramArguments().isEmpty()){
      containerBuilder.setProgramArguments((List<String>) null);
    }
    else{
      containerBuilder.setProgramArguments(commonContainerConfigCliOptions.getProgramArguments());
    }
    layers = processor.createLayers();
    containerBuilder
        .setEntrypoint(computeEntrypoint(commonContainerConfigCliOptions, processor))
        .setFileEntriesLayers(layers)
        .setExposedPorts(commonContainerConfigCliOptions.getExposedPorts())
        .setVolumes(commonContainerConfigCliOptions.getVolumes())
        .setEnvironment(commonContainerConfigCliOptions.getEnvironment())
        .setLabels(commonContainerConfigCliOptions.getLabels());
    commonContainerConfigCliOptions.getUser().ifPresent(containerBuilder::setUser);
    commonContainerConfigCliOptions.getFormat().ifPresent(containerBuilder::setFormat);
    commonContainerConfigCliOptions.getCreationTime().ifPresent(containerBuilder::setCreationTime);

    return containerBuilder;
  }

  @Nullable
  public static List<String> computeEntrypoint(CommonContainerConfigCliOptions commonContainerConfigCliOptions, ArtifactProcessor processor) throws IOException {
    Optional<String> baseImage = commonContainerConfigCliOptions.getFrom();
    List<String> entrypoint = commonContainerConfigCliOptions.getEntrypoint();
    if (!entrypoint.isEmpty()){
      return entrypoint;
    }
    Boolean isJetty = baseImage.isPresent() || (baseImage.isPresent() && baseImage.get().startsWith("jetty"));
    if (isJetty){
       return processor.computeEntrypoint(ImmutableList.of());
    }
    return null;
  }


}
