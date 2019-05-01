/*
 * Copyright 2017 Google LLC.
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

package com.google.cloud.tools.jib.hash;

import com.google.cloud.tools.jib.blob.BlobDescriptor;
import com.google.cloud.tools.jib.blob.WritableContents;
import com.google.cloud.tools.jib.image.DescriptorDigest;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Utility class for input/output streams. */
public class DigestUtil {

  public static DescriptorDigest computeJsonDigest(JsonTemplate template) throws IOException {
    return computeDigest(template, ByteStreams.nullOutputStream()).getDigest();
  }

  public static DescriptorDigest computeJsonDigest(List<? extends JsonTemplate> templates)
      throws IOException {
    WritableContents contents = contentsOut -> JsonTemplateMapper.writeTo(templates, contentsOut);
    return computeDigest(contents, ByteStreams.nullOutputStream()).getDigest();
  }

  public static BlobDescriptor computeDigest(JsonTemplate template, OutputStream outStream)
      throws IOException {
    WritableContents contents = contentsOut -> JsonTemplateMapper.writeTo(template, contentsOut);
    return computeDigest(contents, outStream);
  }

  public static BlobDescriptor computeDigest(InputStream inStream) throws IOException {
    return computeDigest(inStream, ByteStreams.nullOutputStream());
  }

  /**
   * Computes the digest by consuming the contents.
   *
   * @param contents the writable contents for which the digest is computed
   * @return computed digest and bytes consumed
   * @throws IOException if reading fails
   */
  public static BlobDescriptor computeDigest(WritableContents contents) throws IOException {
    return computeDigest(contents, ByteStreams.nullOutputStream());
  }

  /**
   * Computes the digest by consuming the contents of an {@link InputStream} and optionally copying
   * it to an {@link OutputStream}. Returns the computed digested along with the bytes consumed to
   * compute the digest. Does not close either stream.
   *
   * @param inStream the stream to read the contents from
   * @param outStream the stream to which the contents are copied
   * @return computed digest and bytes consumed
   * @throws IOException if reading from or writing fails
   */
  public static BlobDescriptor computeDigest(InputStream inStream, OutputStream outStream)
      throws IOException {
    WritableContents contents = contentsOut -> ByteStreams.copy(inStream, contentsOut);
    return computeDigest(contents, outStream);
  }

  /**
   * Computes the digest by consuming the contents and optionally copying it to an {@link
   * OutputStream}. Returns the computed digested along with the bytes consumed. Does not close the
   * stream.
   *
   * @param contents the writable contents for which the digest is computed
   * @param outStream the stream to which the contents are copied
   * @return computed digest and bytes consumed
   * @throws IOException if reading from or writing fails
   */
  public static BlobDescriptor computeDigest(WritableContents contents, OutputStream outStream)
      throws IOException {
    CountingDigestOutputStream digestOutStream = new CountingDigestOutputStream(outStream);
    contents.writeTo(digestOutStream);
    digestOutStream.flush();
    return new BlobDescriptor(digestOutStream.getBytesHahsed(), digestOutStream.getDigest());
  }
}
