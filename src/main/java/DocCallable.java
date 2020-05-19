import java.io.InputStream;
import java.util.concurrent.Callable;

public class DocCallable {
  private Callable<InputStream> inputStreamCallable;

  public DocCallable(Callable<InputStream> inputStream) {
    this.inputStreamCallable = inputStream;
  }

  public Callable<InputStream> getInputStreamCallable() {
    return inputStreamCallable;
  }

}
