package net.lingala.zip4j.io.outputstreams;

import net.lingala.zip4j.crypto.AESEncrpyter;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;

import java.io.IOException;
import java.io.OutputStream;

import static net.lingala.zip4j.util.InternalZipConstants.AES_BLOCK_SIZE;

class AesCipherOutputStream extends CipherOutputStream<AESEncrpyter> {

  private byte[] pendingBuffer = new byte[AES_BLOCK_SIZE];
  private int pendingBufferLength = 0;

  public AesCipherOutputStream(ZipEntryOutputStream outputStream, ZipParameters zipParameters) throws IOException, ZipException {
    super(outputStream, zipParameters);
  }

  @Override
  protected AESEncrpyter initializeEncrypter(OutputStream outputStream, ZipParameters zipParameters) throws IOException, ZipException {
    AESEncrpyter encrypter = new AESEncrpyter(zipParameters.getPassword(), zipParameters.getAesKeyStrength());
    writeAesEncryptionHeaderData(encrypter, outputStream);
    return encrypter;
  }

  private void writeAesEncryptionHeaderData(AESEncrpyter encrpyter, OutputStream outputStream) throws IOException {
    outputStream.write(encrpyter.getSaltBytes());
    outputStream.write(encrpyter.getDerivedPasswordVerifier());
  }

  @Override
  public void write(int b) throws IOException {
    write(new byte[] {(byte) b});
  }

  @Override
  public void write(byte[] b) throws IOException {
    write(b, 0, b.length);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (len >= (AES_BLOCK_SIZE - pendingBufferLength)) {
      System.arraycopy(b, off, pendingBuffer, pendingBufferLength, (AES_BLOCK_SIZE - pendingBufferLength));
      encryptAndWrite(pendingBuffer, 0, pendingBuffer.length);
      off = (AES_BLOCK_SIZE - pendingBufferLength);
      len = len - off;
      pendingBufferLength = 0;
    } else {
      System.arraycopy(b, off, pendingBuffer, pendingBufferLength, len);
      pendingBufferLength += len;
      return;
    }

    if (len != 0 && len % 16 != 0) {
      System.arraycopy(b, (len + off) - (len % 16), pendingBuffer, 0, len % 16);
      pendingBufferLength = len % 16;
      len = len - pendingBufferLength;
    }

    encryptAndWrite(b, off, len);
  }

  @Override
  public void closeEntry() throws IOException {
    if (this.pendingBufferLength != 0) {
      encryptAndWrite(pendingBuffer, 0, pendingBufferLength);
      pendingBufferLength = 0;
    }

    super.write(getEncrypter().getFinalMac());
    super.closeEntry();
  }
}
