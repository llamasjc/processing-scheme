package processing.mode.scheme;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Exporting for a platform whose Python isn't in this copy of Scheme Mode
 * (a copy built from the repository has none, the release zip has every
 * one): each release also has SchemeMode-python-&lt;platform&gt;.zip, and the
 * Export dialog's Install button downloads it into python/&lt;platform&gt;.
 */
final class PythonDownload {
  private PythonDownload() {}

  /** The platforms the engine (mewnala) is built for. */
  static boolean available(ExportDialog.Target t) {
    return t == ExportDialog.Target.LINUX_X64 || t == ExportDialog.Target.WINDOWS_X64
        || t == ExportDialog.Target.MACOS_ARM64;
  }

  /** The release this copy came from: url and prettyVersion in mode.properties. */
  static String zipUrl(File modeFolder, ExportDialog.Target t) {
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(new File(modeFolder, "mode.properties").toPath())) {
      p.load(in);
    } catch (IOException e) {
      return null;
    }
    String url = p.getProperty("url", "").trim();
    String version = p.getProperty("prettyVersion", "").trim();
    if (!url.startsWith("https://github.com/") || version.isEmpty()) return null;
    if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
    return url + "/releases/download/v" + version + "/SchemeMode-python-" + t.lua + ".zip";
  }

  /** Why t is greyed out, or null when it can be exported. */
  static String why(File modeFolder, ExportDialog.Target t) {
    if (SchemeExport.python(modeFolder, t) != null) return null;
    if (!available(t)) return "the drawing engine isn't built for it yet";
    return "needs its Python (about 40 MB, once)";
  }

  /** Downloads and unpacks it. Off the Swing thread. */
  static boolean install(File modeFolder, ExportDialog.Target t, Consumer<String> status) {
    String url = zipUrl(modeFolder, t);
    if (url == null) { status.accept("Can't tell which release this copy of Scheme Mode came from."); return false; }
    Path tmp = null;
    try {
      tmp = Files.createTempFile("schememode-python", ".zip");
      status.accept("Downloading Python for " + t.label + "...");
      Http.download(url, tmp.toFile(), done -> status.accept("Downloading Python for " + t.label + ": " + (done >> 20) + " MB"));
      status.accept("Unpacking Python for " + t.label + "...");
      Path root = new File(modeFolder, "python").toPath().toAbsolutePath().normalize();
      Path target = root.resolve(t.lua);
      Path part = root.resolve("." + t.lua + ".part");
      AppExport.deleteTree(part);
      try (ZipInputStream z = new ZipInputStream(Files.newInputStream(tmp))) {
        ZipEntry e;
        while ((e = z.getNextEntry()) != null) {
          // entries are <platform>/...
          String name = e.getName().replace('\\', '/');
          int slash = name.indexOf('/');
          if (slash < 0) continue;
          Path dest = part.resolve(name.substring(slash + 1)).normalize();
          if (!dest.startsWith(part)) throw new IOException("bad entry " + e.getName());
          if (e.isDirectory()) { Files.createDirectories(dest); continue; }
          Files.createDirectories(dest.getParent());
          Files.copy(z, dest, StandardCopyOption.REPLACE_EXISTING);
          if (name.contains("/bin/")) dest.toFile().setExecutable(true, false);
        }
      }
      AppExport.deleteTree(target);
      Files.move(part, target);
      boolean ok = SchemeExport.python(modeFolder, t) != null;
      status.accept(ok ? "Python for " + t.label + " is installed." : "The download didn't have Python in it.");
      return ok;
    } catch (Exception e) {
      status.accept("Couldn't get Python for " + t.label + ": " + e.getMessage());
      return false;
    } finally {
      if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
    }
  }
}
