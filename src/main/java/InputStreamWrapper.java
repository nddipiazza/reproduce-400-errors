import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.io.InputStream;

public class InputStreamWrapper extends InputStream {

  private final HttpGet request;
  private final InputStream wrappedInputStream;

  public InputStreamWrapper(HttpGet request, InputStream wrappedInputStream) {
    this.request = request;
    this.wrappedInputStream = wrappedInputStream;
  }

  @Override
  public int read() throws IOException {
    return wrappedInputStream.read();
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return wrappedInputStream.read(b, off, len);
  }

  @Override
  public int read(byte[] b) throws IOException {
    return wrappedInputStream.read(b);
  }

  @Override
  public long skip(long n) throws IOException {
    return wrappedInputStream.skip(n);
  }

  @Override
  public synchronized void mark(int readlimit) {
    wrappedInputStream.mark(readlimit);
  }

  @Override
  public synchronized void reset() throws IOException {
    wrappedInputStream.reset();
  }

  @Override
  public boolean markSupported() {
    return wrappedInputStream.markSupported();
  }

  @Override
  public int available() throws IOException {
    return wrappedInputStream.available();
  }

  @Override
  public void close() throws IOException {
    try {
      request.abort();
    } finally {
      wrappedInputStream.close();
    }
  }
}
