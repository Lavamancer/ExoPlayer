/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.google.android.exoplayer2.transformer.mh;

import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_PORTRAIT_ASSET_URI_STRING;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.extractBitmapsFromVideo;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.skipAndLogIfFormatsUnsupported;
import static com.google.android.exoplayer2.transformer.SequenceEffectTestUtil.SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS;
import static com.google.android.exoplayer2.transformer.SequenceEffectTestUtil.assertBitmapsMatchExpectedAndSave;
import static com.google.android.exoplayer2.transformer.SequenceEffectTestUtil.clippedVideo;
import static com.google.android.exoplayer2.transformer.SequenceEffectTestUtil.createComposition;
import static com.google.android.exoplayer2.transformer.mh.HdrCapabilitiesUtil.skipAndLogIfOpenGlToneMappingUnsupported;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_H265;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.effect.Presentation;
import com.google.android.exoplayer2.effect.RgbFilter;
import com.google.android.exoplayer2.effect.ScaleAndRotateTransformation;
import com.google.android.exoplayer2.transformer.Composition;
import com.google.android.exoplayer2.transformer.EditedMediaItemSequence;
import com.google.android.exoplayer2.transformer.EncoderUtil;
import com.google.android.exoplayer2.transformer.ExportException;
import com.google.android.exoplayer2.transformer.ExportTestResult;
import com.google.android.exoplayer2.transformer.Transformer;
import com.google.android.exoplayer2.transformer.TransformerAndroidTestRunner;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Tests for using different {@linkplain Effect effects} for {@link MediaItem MediaItems} in one
 * {@link EditedMediaItemSequence}, with HDR assets.
 */
@RunWith(AndroidJUnit4.class)
public final class TransformerSequenceEffectTestWithHdr {

  private static final int EXPORT_HEIGHT = 240;
  @Rule public final TestName testName = new TestName();

  private final Context context = ApplicationProvider.getApplicationContext();

  private @MonotonicNonNull String testId;

  @Before
  @EnsuresNonNull({"testId"})
  public void setUp() {
    testId = testName.getMethodName();
  }

  @Test
  @RequiresNonNull("testId")
  public void export_withSdrThenHdr() throws Exception {
    assumeFalse(
        skipAndLogIfOpenGlToneMappingUnsupported(
            testId, /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT));
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                MP4_PORTRAIT_ASSET_URI_STRING,
                ImmutableList.of(RgbFilter.createInvertedFilter()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    // Expected bitmaps were generated on the Pixel 7 Pro, because emulators don't
    // support decoding HDR.
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  /**
   * If the first asset in a sequence is HDR, then Transformer will output HDR. However, because SDR
   * to HDR tone-mapping is not implemented, VideoFrameProcessor cannot take a later SDR input asset
   * after already being configured for HDR output.
   */
  @Test
  @RequiresNonNull("testId")
  public void export_withHdrThenSdr_throws_whenHdrEditingSupported() throws Exception {
    assumeTrue(
        "Device does not support HDR10 editing.",
        deviceSupportsHdrEditing(
            VIDEO_H265, checkNotNull(MP4_ASSET_720P_4_SECOND_HDR10_FORMAT.colorInfo)));
    assumeFalse(
        skipAndLogIfFormatsUnsupported(
            context,
            testId,
            /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT,
            /* outputFormat= */ null));
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_PORTRAIT_ASSET_URI_STRING,
                ImmutableList.of(RgbFilter.createInvertedFilter()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    @Nullable ExportException expectedException = null;
    try {
      new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
          .build()
          .run(testId, composition);
    } catch (ExportException e) {
      expectedException = e;
    }
    assertThat(expectedException).isNotNull();
    assertThat(checkNotNull(checkNotNull(expectedException).getMessage()))
        .isEqualTo("Video frame processing error");
  }

  /**
   * If the first asset in a sequence is HDR, but HDR editing is not supported, then the first asset
   * will fallback to OpenGL tone-mapping, and configure VideoFrameProcessor for SDR output.
   */
  @Test
  @RequiresNonNull("testId")
  public void export_withHdrThenSdr_whenHdrEditingUnsupported() throws Exception {
    assumeFalse(
        "Device supports HDR10 editing.",
        deviceSupportsHdrEditing(
            VIDEO_H265, checkNotNull(MP4_ASSET_720P_4_SECOND_HDR10_FORMAT.colorInfo)));
    assumeFalse(
        skipAndLogIfOpenGlToneMappingUnsupported(
            testId, /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT));
    assumeFalse(
        skipAndLogIfFormatsUnsupported(
            context,
            testId,
            /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT,
            /* outputFormat= */ null));
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                MP4_ASSET_720P_4_SECOND_HDR10,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(45).build()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_PORTRAIT_ASSET_URI_STRING,
                ImmutableList.of(RgbFilter.createInvertedFilter()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    // Expected bitmaps were generated on the Samsung S22 Ultra (US), because emulators don't
    // support decoding HDR, and the Pixel 7 Pro does support HDR editing.
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  private static boolean deviceSupportsHdrEditing(String mimeType, ColorInfo colorInfo) {
    return !EncoderUtil.getSupportedEncodersForHdrEditing(mimeType, colorInfo).isEmpty();
  }
}
