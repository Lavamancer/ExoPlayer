/*
 * Copyright 2022 The Android Open Source Project
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
 */
package androidx.media3.effect;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.media3.common.FrameProcessor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.Util;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ChainingGlTextureProcessorListener}. */
@RunWith(AndroidJUnit4.class)
public final class ChainingGlTextureProcessorListenerTest {
  private static final long EXECUTOR_WAIT_TIME_MS = 100;

  private final FrameProcessingTaskExecutor frameProcessingTaskExecutor =
      new FrameProcessingTaskExecutor(
          Util.newSingleThreadExecutor("Test"), mock(FrameProcessor.Listener.class));
  private final GlTextureProcessor mockProducingGlTextureProcessor = mock(GlTextureProcessor.class);
  private final FakeGlTextureProcessor fakeConsumingGlTextureProcessor =
      spy(new FakeGlTextureProcessor());
  private final ChainingGlTextureProcessorListener chainingGlTextureProcessorListener =
      new ChainingGlTextureProcessorListener(
          mockProducingGlTextureProcessor,
          fakeConsumingGlTextureProcessor,
          frameProcessingTaskExecutor);

  @After
  public void release() throws InterruptedException {
    frameProcessingTaskExecutor.release(/* releaseTask= */ () -> {}, EXECUTOR_WAIT_TIME_MS);
  }

  @Test
  public void onInputFrameProcessed_surrendersFrameToPreviousGlTextureProcessor()
      throws InterruptedException {
    TextureInfo texture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);

    chainingGlTextureProcessorListener.onInputFrameProcessed(texture);
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(mockProducingGlTextureProcessor).releaseOutputFrame(texture);
  }

  @Test
  public void onOutputFrameAvailable_passesFrameToNextGlTextureProcessor()
      throws InterruptedException {
    TextureInfo texture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);
    long presentationTimeUs = 123;

    chainingGlTextureProcessorListener.onOutputFrameAvailable(texture, presentationTimeUs);
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(fakeConsumingGlTextureProcessor).maybeQueueInputFrame(texture, presentationTimeUs);
  }

  @Test
  public void onOutputFrameAvailable_nextGlTextureProcessorRejectsFrame_triesAgain()
      throws InterruptedException {
    TextureInfo texture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);
    long presentationTimeUs = 123;
    fakeConsumingGlTextureProcessor.rejectNextFrame();

    chainingGlTextureProcessorListener.onOutputFrameAvailable(texture, presentationTimeUs);
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(fakeConsumingGlTextureProcessor, times(2))
        .maybeQueueInputFrame(texture, presentationTimeUs);
  }

  @Test
  public void onOutputFrameAvailable_twoFramesWithFirstRejected_retriesFirstBeforeSecond()
      throws InterruptedException {
    TextureInfo firstTexture =
        new TextureInfo(/* texId= */ 1, /* fboId= */ 1, /* width= */ 100, /* height= */ 100);
    long firstPresentationTimeUs = 123;
    TextureInfo secondTexture =
        new TextureInfo(/* texId= */ 2, /* fboId= */ 2, /* width= */ 100, /* height= */ 100);
    long secondPresentationTimeUs = 567;
    fakeConsumingGlTextureProcessor.rejectNextFrame();

    chainingGlTextureProcessorListener.onOutputFrameAvailable(
        firstTexture, firstPresentationTimeUs);
    chainingGlTextureProcessorListener.onOutputFrameAvailable(
        secondTexture, secondPresentationTimeUs);
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(fakeConsumingGlTextureProcessor, times(2))
        .maybeQueueInputFrame(firstTexture, firstPresentationTimeUs);
    verify(fakeConsumingGlTextureProcessor)
        .maybeQueueInputFrame(secondTexture, secondPresentationTimeUs);
  }

  @Test
  public void onOutputStreamEnded_signalsInputStreamEndedToNextGlTextureProcessor()
      throws InterruptedException {
    chainingGlTextureProcessorListener.onCurrentOutputStreamEnded();
    Thread.sleep(EXECUTOR_WAIT_TIME_MS);

    verify(fakeConsumingGlTextureProcessor).signalEndOfCurrentInputStream();
  }

  private static class FakeGlTextureProcessor implements GlTextureProcessor {

    private volatile boolean rejectNextFrame;

    public void rejectNextFrame() {
      rejectNextFrame = true;
    }

    @Override
    public void setInputListener(InputListener inputListener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setOutputListener(OutputListener outputListener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setErrorListener(ErrorListener errorListener) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean maybeQueueInputFrame(TextureInfo inputTexture, long presentationTimeUs) {
      boolean acceptFrame = !rejectNextFrame;
      rejectNextFrame = false;
      return acceptFrame;
    }

    @Override
    public void releaseOutputFrame(TextureInfo outputTexture) {}

    @Override
    public void signalEndOfCurrentInputStream() {}

    @Override
    public void release() {}
  }
}