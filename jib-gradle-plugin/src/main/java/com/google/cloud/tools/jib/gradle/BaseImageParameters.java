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

package com.google.cloud.tools.jib.gradle;

import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;

/** Extension object that configures the base image. */
public class BaseImageParameters {

  private final AuthParameters auth;

  @Nullable private String image;
  @Nullable private String credHelper;

  @Inject
  public BaseImageParameters(ObjectFactory objectFactory, String imageDescriptor) {
    auth = objectFactory.newInstance(AuthParameters.class, imageDescriptor + ".auth");
  }

  @Input
  @Nullable
  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  @Input
  @Nullable
  public String getCredHelper() {
    return credHelper;
  }

  public void setCredHelper(String credHelper) {
    this.credHelper = credHelper;
  }

  @Nested
  public AuthParameters getAuth() {
    return auth;
  }

  public void auth(Action<? super AuthParameters> action) {
    action.execute(auth);
  }
}
