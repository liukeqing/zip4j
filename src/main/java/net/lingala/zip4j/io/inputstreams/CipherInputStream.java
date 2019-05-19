package net.lingala.zip4j.io.inputstreams;

import net.lingala.zip4j.crypto.Decrypter;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.LocalFileHeader;
import net.lingala.zip4j.zip.CompressionMethod;

import java.io.IOException;
import java.io.InputStream;

abstract class CipherInputStream extends InputStream {

  private ZipEntryInputStream zipEntryInputStream;
  private Decrypter decrypter;
  private byte[] lastReadRawDataCache;
  private byte[] singleByteBuffer = new byte[1];

  public CipherInputStream(ZipEntryInputStream zipEntryInputStream, LocalFileHeader localFileHeader) throws IOException, ZipException {
    this.zipEntryInputStream = zipEntryInputStream;
    this.decrypter = initializeDecrypter(localFileHeader);

    if (getCompressionMethod(localFileHeader) == CompressionMethod.DEFLATE) {
      lastReadRawDataCache = new byte[512];
    }
  }

  @Override
  public int read() throws IOException {
    int readLen = read(singleByteBuffer);

    if (readLen == -1) {
      return -1;
    }

    return singleByteBuffer[0] & 0xff;
  }

  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int readLen = zipEntryInputStream.read(b, off, len);

    cacheRawData(b, readLen);

    try {
      decrypter.decryptData(b, off, readLen);
    } catch (ZipException e) {
      throw new IOException(e);
    }

    return readLen;
  }

  public byte[] getLastReadRawDataCache() {
    return lastReadRawDataCache;
  }

  protected int readRaw(byte[] b) throws IOException {
    return zipEntryInputStream.read(b);
  }

  private void cacheRawData(byte[] b, int len) {
    if (lastReadRawDataCache != null) {
      System.arraycopy(b, 0, lastReadRawDataCache, 0, len);
    }
  }

  private CompressionMethod getCompressionMethod(LocalFileHeader localFileHeader) throws ZipException {
    if (localFileHeader.getCompressionMethod() != CompressionMethod.AES_INTERNAL_ONLY) {
      return localFileHeader.getCompressionMethod();
    }

    if (localFileHeader.getAesExtraDataRecord() == null) {
      throw new ZipException("AesExtraDataRecord not present in localheader for aes encrypted data");
    }

    return localFileHeader.getAesExtraDataRecord().getCompressionMethod();
  }

  protected void endOfEntryReached(InputStream inputStream) throws IOException {
    // is optional but useful for AES
  }

  protected long getNumberOfBytesReadForThisEntry() {
    return zipEntryInputStream.getNumberOfBytesRead();
  }

  protected abstract Decrypter initializeDecrypter(LocalFileHeader localFileHeader) throws IOException, ZipException;
}
