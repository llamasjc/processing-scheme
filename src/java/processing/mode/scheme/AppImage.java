package processing.mode.scheme;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * A Linux export as one AppImage file, the way C++ Mode makes them: the
 * export folder becomes the AppDir (its launcher is AppRun), with a .desktop
 * file and the icon, packed by appimagetool. appimagetool is downloaded once
 * into the mode's tools/ folder. Only on Linux, where appimagetool runs.
 */
final class AppImage {
  private AppImage() {}

  static boolean possible() {
    return System.getProperty("os.name", "").toLowerCase().contains("linux");
  }

  private static String hostArch() {
    String a = System.getProperty("os.arch", "").toLowerCase();
    return a.contains("aarch64") || a.contains("arm64") ? "aarch64" : "x86_64";
  }

  static File tool(File modeFolder, Consumer<String> status) throws IOException, InterruptedException {
    File f = new File(modeFolder, "tools/appimagetool-" + hostArch() + ".AppImage");
    if (f.isFile()) { f.setExecutable(true, false); return f; }
    status.accept("Downloading appimagetool (once)...");
    f.getParentFile().mkdirs();
    String url = "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-" + hostArch() + ".AppImage";
    Http.download(url, f, null);
    f.setExecutable(true, false);
    return f;
  }

  /**
   * Packs the export folder {@code dest} (a Linux export with its launcher
   * {@code safe}) into dest/&lt;safe&gt;-&lt;arch&gt;.AppImage.
   * @param arch  x86_64 or aarch64, the platform the export is for
   */
  static File make(File modeFolder, File dest, String sketchName, String safe, String arch,
                   File iconPng, Consumer<String> status) throws IOException, InterruptedException {
    File tool = tool(modeFolder, status);
    File appDir = new File(dest.getParentFile(), "." + dest.getName() + ".AppDir");
    AppExport.deleteTree(appDir.toPath());
    AppExport.copyTree(dest.toPath(), appDir.toPath());
    File launcher = new File(appDir, safe);
    File appRun = new File(appDir, "AppRun");
    Files.copy(launcher.toPath(), appRun.toPath(), StandardCopyOption.REPLACE_EXISTING);
    appRun.setExecutable(true, false);
    for (File f : new File(appDir, "lib").listFiles()) if (!f.getName().contains(".")) f.setExecutable(true, false);
    if (iconPng != null) AppIcons.writePng(AppIcons.read(iconPng), 256, new File(appDir, safe + ".png"));
    else Files.write(new File(appDir, safe + ".png").toPath(), blankIcon());
    Files.writeString(new File(appDir, safe + ".desktop").toPath(),
      "[Desktop Entry]\nType=Application\nName=" + sketchName + "\nExec=AppRun\nIcon=" + safe
      + "\nCategories=Graphics;\nTerminal=false\n", StandardCharsets.UTF_8);
    File out = new File(dest, safe + "-" + arch + ".AppImage");
    status.accept("Packing " + out.getName() + "...");
    ProcessBuilder pb = new ProcessBuilder(tool.getAbsolutePath(), "--no-appstream", appDir.getAbsolutePath(), out.getAbsolutePath());
    pb.environment().put("ARCH", arch);
    // runs without FUSE, which many systems don't have
    pb.environment().put("APPIMAGE_EXTRACT_AND_RUN", "1");
    pb.redirectErrorStream(true);
    Process p = pb.start();
    StringBuilder log = new StringBuilder();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) log.append(line).append('\n');
    }
    int rc = p.waitFor();
    AppExport.deleteTree(appDir.toPath());
    if (rc != 0 || !out.isFile()) {
      System.err.println(log);
      throw new IOException("appimagetool failed (see the console)");
    }
    out.setExecutable(true, false);
    return out;
  }

  /** a 1x1 transparent PNG, when there's no icon (appimagetool needs one) */
  private static byte[] blankIcon() throws IOException {
    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", b);
    return b.toByteArray();
  }
}
