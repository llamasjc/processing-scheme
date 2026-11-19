package processing.mode.scheme;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.function.LongConsumer;

/**
 * Downloads with java.net.HttpURLConnection. Processing ships a trimmed Java
 * without the java.net.http module, so HttpClient isn't there at run time.
 */
final class Http {
  private Http() {}

  /** Downloads url to dest (through dest.part, so a broken download leaves nothing). */
  static void download(String url, File dest, LongConsumer progress) throws IOException {
    HttpURLConnection c = open(url);
    long total = c.getContentLengthLong();
    File part = new File(dest.getPath() + ".part");
    try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(part.toPath())) {
      byte[] buf = new byte[1 << 16];
      long done = 0, shown = 0;
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
        done += n;
        if (progress != null && done - shown > (8 << 20)) { shown = done; progress.accept(done); }
      }
      if (total > 0 && done != total) throw new IOException("download cut off");
    } catch (IOException e) {
      part.delete();
      throw e;
    }
    Files.move(part.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }

  /** Follows redirects itself: GitHub sends release files from another host. */
  private static HttpURLConnection open(String url) throws IOException {
    for (int hops = 0; hops < 8; hops++) {
      HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
      c.setInstanceFollowRedirects(false);
      c.setConnectTimeout(20000);
      c.setReadTimeout(60000);
      c.setRequestProperty("User-Agent", "Processing");
      int code = c.getResponseCode();
      if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
        url = new URL(new URL(url), c.getHeaderField("Location")).toString();
        c.disconnect();
        continue;
      }
      if (code != 200) throw new IOException("HTTP " + code);
      return c;
    }
    throw new IOException("too many redirects");
  }
}
