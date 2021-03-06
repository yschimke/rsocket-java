package io.rsocket.frame;

import static io.rsocket.frame.FrameHeaderFlyweight.FRAME_HEADER_LENGTH;
import static org.junit.Assert.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.rsocket.FrameType;
import org.junit.Test;

public class FrameHeaderFlyweightTest {
  // Taken from spec
  private static final int FRAME_MAX_SIZE = 16_777_215;

  private final ByteBuf byteBuf = Unpooled.buffer(1024);

  @Test
  public void headerSize() {
    int frameLength = 123456;
    FrameHeaderFlyweight.encodeFrameHeader(byteBuf, frameLength, 0, FrameType.SETUP, 0);
    assertEquals(frameLength, FrameHeaderFlyweight.frameLength(byteBuf));
  }

  @Test
  public void headerSizeMax() {
    int frameLength = FRAME_MAX_SIZE;
    FrameHeaderFlyweight.encodeFrameHeader(byteBuf, frameLength, 0, FrameType.SETUP, 0);
    assertEquals(frameLength, FrameHeaderFlyweight.frameLength(byteBuf));
  }

  @Test(expected = IllegalArgumentException.class)
  public void headerSizeTooLarge() {
    FrameHeaderFlyweight.encodeFrameHeader(byteBuf, FRAME_MAX_SIZE + 1, 0, FrameType.SETUP, 0);
  }

  @Test
  public void frameLength() {
    int length =
        FrameHeaderFlyweight.encode(
            byteBuf, 0, 0, FrameType.SETUP, Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER);
    assertEquals(length, 9); // 72 bits
  }

  @Test
  public void metadataLength() {
    ByteBuf metadata = Unpooled.wrappedBuffer(new byte[] {1, 2, 3, 4});
    FrameHeaderFlyweight.encode(byteBuf, 0, 0, FrameType.SETUP, metadata, Unpooled.EMPTY_BUFFER);
    assertEquals(
        4,
        FrameHeaderFlyweight.decodeMetadataLength(
            byteBuf, FrameHeaderFlyweight.FRAME_HEADER_LENGTH));
  }

  @Test
  public void dataLength() {
    ByteBuf data = Unpooled.wrappedBuffer(new byte[] {1, 2, 3, 4, 5});
    int length =
        FrameHeaderFlyweight.encode(byteBuf, 0, 0, FrameType.SETUP, Unpooled.EMPTY_BUFFER, data);
    assertEquals(
        5,
        FrameHeaderFlyweight.dataLength(
            byteBuf, FrameType.SETUP, FrameHeaderFlyweight.FRAME_HEADER_LENGTH));
  }

  @Test
  public void metadataSlice() {
    ByteBuf metadata = Unpooled.wrappedBuffer(new byte[] {1, 2, 3, 4});
    FrameHeaderFlyweight.encode(
        byteBuf, 0, 0, FrameType.REQUEST_RESPONSE, metadata, Unpooled.EMPTY_BUFFER);
    metadata.resetReaderIndex();

    assertEquals(metadata, FrameHeaderFlyweight.sliceFrameMetadata(byteBuf));
  }

  @Test
  public void dataSlice() {
    ByteBuf data = Unpooled.wrappedBuffer(new byte[] {1, 2, 3, 4, 5});
    FrameHeaderFlyweight.encode(
        byteBuf, 0, 0, FrameType.REQUEST_RESPONSE, Unpooled.EMPTY_BUFFER, data);
    data.resetReaderIndex();

    assertEquals(data, FrameHeaderFlyweight.sliceFrameData(byteBuf));
  }

  @Test
  public void streamId() {
    int streamId = 1234;
    FrameHeaderFlyweight.encode(
        byteBuf, streamId, 0, FrameType.SETUP, Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER);
    assertEquals(streamId, FrameHeaderFlyweight.streamId(byteBuf));
  }

  @Test
  public void typeAndFlag() {
    FrameType frameType = FrameType.FIRE_AND_FORGET;
    int flags = 0b1110110111;
    FrameHeaderFlyweight.encode(
        byteBuf, 0, flags, frameType, Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER);

    assertEquals(flags, FrameHeaderFlyweight.flags(byteBuf));
    assertEquals(frameType, FrameHeaderFlyweight.frameType(byteBuf));
  }

  @Test
  public void typeAndFlagTruncated() {
    FrameType frameType = FrameType.SETUP;
    int flags = 0b11110110111; // 1 bit too many
    FrameHeaderFlyweight.encode(
        byteBuf, 0, flags, FrameType.SETUP, Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER);

    assertNotEquals(flags, FrameHeaderFlyweight.flags(byteBuf));
    assertEquals(flags & 0b0000_0011_1111_1111, FrameHeaderFlyweight.flags(byteBuf));
    assertEquals(frameType, FrameHeaderFlyweight.frameType(byteBuf));
  }

  @Test
  public void missingMetadataLength() {
    for (FrameType frameType : FrameType.values()) {
      switch (frameType) {
        case UNDEFINED:
          break;
        case CANCEL:
        case METADATA_PUSH:
        case LEASE:
          assertFalse(
              "!hasMetadataLengthField(): " + frameType,
              FrameHeaderFlyweight.hasMetadataLengthField(frameType));
          break;
        default:
          if (frameType.canHaveMetadata()) {
            assertTrue(
                "hasMetadataLengthField(): " + frameType,
                FrameHeaderFlyweight.hasMetadataLengthField(frameType));
          }
      }
    }
  }

  @Test
  public void wireFormat() {
    ByteBuf expectedBuffer = Unpooled.buffer(1024);
    int currentIndex = 0;
    // frame length
    int frameLength =
        FrameHeaderFlyweight.FRAME_HEADER_LENGTH - FrameHeaderFlyweight.FRAME_LENGTH_SIZE;
    expectedBuffer.setInt(currentIndex, frameLength << 8);
    currentIndex += 3;
    // stream id
    expectedBuffer.setInt(currentIndex, 5);
    currentIndex += Integer.BYTES;
    // flags and frame type
    expectedBuffer.setShort(currentIndex, (short) 0b001010_0001100000);
    currentIndex += Short.BYTES;

    FrameType frameType = FrameType.NEXT_COMPLETE;
    FrameHeaderFlyweight.encode(
        byteBuf, 5, 0, frameType, Unpooled.EMPTY_BUFFER, Unpooled.EMPTY_BUFFER);

    ByteBuf expected = expectedBuffer.slice(0, currentIndex);
    ByteBuf actual = byteBuf.slice(0, FRAME_HEADER_LENGTH);

    assertEquals(ByteBufUtil.hexDump(expected), ByteBufUtil.hexDump(actual));
  }
}
