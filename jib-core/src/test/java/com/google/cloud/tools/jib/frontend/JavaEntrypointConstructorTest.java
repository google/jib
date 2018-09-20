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

package com.google.cloud.tools.jib.frontend;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/** Tests for {@link JavaEntrypointConstructor}. */
public class JavaEntrypointConstructorTest {

  @Test
  public void testMakeEntrypoint() {
    String expectedResourcesPath = "/app/resources/";
    String expectedClassesPath = "/app/classes/";
    String expectedDependenciesPath = "/app/libs/*";
    List<String> expectedJvmFlags = Arrays.asList("-flag", "anotherFlag");
    String expectedMainClass = "SomeMainClass";

    List<String> entrypoint =
        JavaEntrypointConstructor.makeEntrypoint(
            Arrays.asList(expectedResourcesPath, expectedClassesPath, expectedDependenciesPath),
            expectedJvmFlags,
            expectedMainClass);
    Assert.assertEquals(
        Arrays.asList(
            "java",
            "-flag",
            "anotherFlag",
            "-cp",
            "/app/resources/:/app/classes/:/app/libs/*",
            "SomeMainClass"),
        entrypoint);

    // Checks that this is also the default entrypoint.
    Assert.assertEquals(
        JavaEntrypointConstructor.makeDefaultEntrypoint(
            "/app", expectedJvmFlags, expectedMainClass),
        entrypoint);
  }

  @Test
  public void testMakeDefaultEntrypoint_classpathString() {
    // Checks that this is also the default entrypoint.
    List<String> entrypoint =
        JavaEntrypointConstructor.makeDefaultEntrypoint("/app", Collections.emptyList(), "MyMain");
    Assert.assertEquals("/app/resources/:/app/classes/:/app/libs/*", entrypoint.get(2));
  }

  @Test
  public void testMakeDefaultEntrypoint_classpathStringWithNonDefaultAppRoot() {
    // Checks that this is also the default entrypoint.
    List<String> entrypoint =
        JavaEntrypointConstructor.makeDefaultEntrypoint("/my/app", Collections.emptyList(), "Main");
    Assert.assertEquals("/my/app/resources/:/my/app/classes/:/my/app/libs/*", entrypoint.get(2));
  }

  @Test
  public void testMakeDefaultEntrypoint_appRootWithTrailingSlash() {
    // Checks that this is also the default entrypoint.
    List<String> entrypoint =
        JavaEntrypointConstructor.makeDefaultEntrypoint(
            "/my/root/", Collections.emptyList(), "SomeMainClass");
    Assert.assertEquals("/my/root/resources/:/my/root/classes/:/my/root/libs/*", entrypoint.get(2));
  }

  @Test
  public void testMakeDefaultEntrypoint_nonAbsoluteAppRoot() {
    try {
      JavaEntrypointConstructor.makeDefaultEntrypoint(
          "relative/path", Collections.emptyList(), "MainClass");
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "appRoot should be an absolute path in Unix-style: relative/path", ex.getMessage());
    }
  }

  @Test
  public void testMakeDefaultEntrypoint_windowsAppRootPath() {
    try {
      JavaEntrypointConstructor.makeDefaultEntrypoint(
          "\\windows\\path", Collections.emptyList(), "MyMain");
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "appRoot should be an absolute path in Unix-style: \\windows\\path", ex.getMessage());
    }
  }

  @Test
  public void testMakeDefaultEntrypoint_windowsPathWithDriveLetter() {
    try {
      JavaEntrypointConstructor.makeDefaultEntrypoint(
          "D:\\windows\\path", Collections.emptyList(), "MyMain");
      Assert.fail();
    } catch (IllegalArgumentException ex) {
      Assert.assertEquals(
          "appRoot should be an absolute path in Unix-style: D:\\windows\\path", ex.getMessage());
    }
  }

  @Test
  public void testMakeDistrolessJettyEntrypoint() {
    List<String> expected = Arrays.asList("java", "-jar", "/jetty/start.jar");
    Assert.assertEquals(expected, JavaEntrypointConstructor.makeDistrolessJettyEntrypoint());
  }
}
