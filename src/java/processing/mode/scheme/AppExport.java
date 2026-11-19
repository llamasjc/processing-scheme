package processing.mode.scheme;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * File &gt; Export Application, laid out like Processing's own export: a
 * folder named for this platform (linux-amd64, macos-aarch64,
 * windows-amd64...) inside the sketch folder, holding a launcher named after
 * the sketch, the program in lib/, and a copy of the sketch's data folder.
 * The launcher starts the program from its own folder, so loadImage("x.png")
 * and the data folder work wherever the export is moved.
 */
final class AppExport {
  private AppExport() {}

  static boolean windows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  /**
   * Processing's name for this platform, like the Java mode's export
   * folders: linux-amd64, linux-aarch64, macos-x86_64, macos-aarch64,
   * windows-amd64. Worked out here, since Processing's own helper isn't in
   * every Processing 4 release.
   */
  static String variant(String fallback) {
    String os = System.getProperty("os.name", "").toLowerCase();
    String arch = System.getProperty("os.arch", "").toLowerCase();
    boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
    boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
    if (os.contains("mac")) return "macos-" + (arm64 ? "aarch64" : "x86_64");
    String name = os.contains("win") ? "windows" : os.contains("linux") ? "linux" : null;
    if (name == null || !(arm64 || x64)) return fallback;
    return name + "-" + (arm64 ? "aarch64" : "amd64");
  }

  /** Shows a folder in the file manager; quietly does nothing where it can't. */
  static void show(File folder) {
    try {
      if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(folder);
    } catch (Throwable ignored) {}
  }

  /**
   * Writes the export and returns its folder.
   * @param program   the executable to ship (copied into lib/)
   * @param args      what the launcher passes it, relative to the export folder
   *                  (e.g. "lib/sketch.lua"); may be empty
   * @param renderer  "libprocessing" to start on that renderer, else null
   */
  static File write(File sketchFolder, String sketchName, File dataFolder, String variant,
                    File program, String[] args, String renderer) throws IOException {
    return write(sketchFolder, sketchName, dataFolder, variant, program, args, renderer, null);
  }

  /** GLFW the mode ships (libglfw.so.3, libglfw.3.dylib, glfw3.dll) in a folder. */
  static File[] glfwLibs(File dir) {
    if (dir == null) return new File[0];
    File[] fs = dir.listFiles((d, n) -> n.startsWith("libglfw.so.3") || n.matches("libglfw\\.3.*\\.dylib") || n.equalsIgnoreCase("glfw3.dll"));
    return fs == null ? new File[0] : fs;
  }

  /**
   * The same, also copying {@code libs} (the GLFW the mode ships) into lib/
   * next to the program, so the export runs where GLFW 3.4 isn't installed.
   */
  static File write(File sketchFolder, String sketchName, File dataFolder, String variant,
                    File program, String[] args, String renderer, File[] libs) throws IOException {
    return write(sketchFolder, sketchName, dataFolder, variant, windows(), mac(), program, args, renderer, libs);
  }

  /** For another platform: its folder name and launcher, whatever this computer is. */
  static File write(File sketchFolder, String sketchName, File dataFolder, ExportDialog.Target target,
                    File program, String[] args, String renderer, File[] libs) throws IOException {
    return write(sketchFolder, sketchName, dataFolder, target.folder, target.windows(), target.mac(),
                 program, args, renderer, libs);
  }

  private static File write(File sketchFolder, String sketchName, File dataFolder, String variant,
                            boolean forWindows, boolean forMac,
                            File program, String[] args, String renderer, File[] libs) throws IOException {
    File dest = new File(sketchFolder, variant);
    deleteTree(dest.toPath());
    String safe = sketchName.replaceAll("[^A-Za-z0-9_.-]", "_");
    if (forWindows) return writeWindows(dest, sketchName, safe, dataFolder, program, renderer, libs);
    File lib = new File(dest, "lib");
    if (!lib.mkdirs()) throw new IOException("can't make " + lib);

    File prog = new File(lib, program.getName());
    Files.copy(program.toPath(), prog.toPath(), StandardCopyOption.REPLACE_EXISTING);
    prog.setExecutable(true, false);
    if (libs != null) {
      for (File l : libs) Files.copy(l.toPath(), new File(lib, l.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    if (dataFolder != null && dataFolder.isDirectory()) copyTree(dataFolder.toPath(), new File(dest, "data").toPath());

    if (forWindows) {
      StringBuilder b = new StringBuilder("@echo off\r\ncd /d \"%~dp0\"\r\n");
      if (renderer != null) b.append("set PROCESSING_RENDERER=").append(renderer).append("\r\n");
      // hour(), minute()... in local time, as when run from the IDE
      b.append("for /f %%o in ('powershell -NoProfile -Command \"[int][TimeZoneInfo]::Local.GetUtcOffset([DateTime]::Now).TotalSeconds\"') do set PROCESSING_UTC_OFFSET=%%o\r\n");
      b.append("\"%~dp0lib\\").append(prog.getName()).append("\"");
      for (String a : args) b.append(" \"%~dp0").append(a.replace('/', '\\')).append("\"");
      b.append(" %*\r\n");
      Files.writeString(new File(dest, safe + ".bat").toPath(), b.toString(), StandardCharsets.UTF_8);
    } else {
      StringBuilder b = new StringBuilder("#!/bin/sh\n");
      b.append("# Starts ").append(sketchName).append(" from this folder, so its data/ files are found.\n");
      b.append("APPDIR=$(cd \"$(dirname \"$0\")\" && pwd -P)\n");
      b.append("cd \"$APPDIR\" || exit 1\n");
      if (renderer != null) b.append("export PROCESSING_RENDERER=").append(renderer).append("\n");
      // the GLFW shipped in lib/ is found before (or instead of) the system's
      b.append("export LD_LIBRARY_PATH=\"$APPDIR/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}\"\n");
      b.append("export DYLD_LIBRARY_PATH=\"$APPDIR/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}\"\n");
      // hour(), minute()... in local time, as when run from the IDE
      b.append("z=$(date +%z 2>/dev/null)\n");
      b.append("case \"$z\" in [+-][0-9][0-9][0-9][0-9])\n");
      b.append("  h=${z#?}; m=${h#??}; h=${h%??}; h=${h#0}; m=${m#0}\n");
      b.append("  o=$(( ${h:-0} * 3600 + ${m:-0} * 60 )); [ \"${z%????}\" = \"-\" ] && o=$(( -o ))\n");
      b.append("  export PROCESSING_UTC_OFFSET=$o;;\n");
      b.append("esac\n");
      b.append("exec \"$APPDIR/lib/").append(prog.getName()).append("\"");
      for (String a : args) b.append(" \"$APPDIR/").append(a).append("\"");
      b.append(" \"$@\"\n");
      File launcher = new File(dest, safe);
      Files.writeString(launcher.toPath(), b.toString(), StandardCharsets.UTF_8);
      launcher.setExecutable(true, false);
      if (forMac) macApp(dest, sketchName, safe, prog);
      else iconFiles(dest, sketchName, safe, false);
    }
    return dest;
  }

  /**
   * Windows: the program itself is &lt;Sketch&gt;.exe, at the top of the export
   * with its icon built in, so it's what you double-click (a .bat can't have
   * an icon). The DLLs sit next to it, where Windows looks; launch.txt has
   * what a launcher would set (the renderer), and the program makes its own
   * folder the working folder and asks Windows for the time zone.
   */
  private static File writeWindows(File dest, String sketchName, String safe, File dataFolder,
                                   File program, String renderer, File[] libs) throws IOException {
    if (!dest.mkdirs()) throw new IOException("can't make " + dest);
    File exe = new File(dest, safe + ".exe");
    Files.copy(program.toPath(), exe.toPath(), StandardCopyOption.REPLACE_EXISTING);
    if (libs != null)
      for (File l : libs) Files.copy(l.toPath(), new File(dest, l.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
    if (dataFolder != null && dataFolder.isDirectory()) copyTree(dataFolder.toPath(), new File(dest, "data").toPath());
    if (renderer != null)
      Files.writeString(new File(dest, "launch.txt").toPath(), "PROCESSING_RENDERER=" + renderer + "\r\n", StandardCharsets.UTF_8);
    File png = icon;
    if (png != null && png.isFile()) {
      try {
        java.awt.image.BufferedImage img = AppIcons.read(png);
        int[] sizes = { 16, 32, 48, 64, 128, 256 };
        java.util.List<byte[]> pngs = new java.util.ArrayList<>();
        for (int s : sizes) pngs.add(AppIcons.pngBytes(img, s));
        PeIcon.embed(exe, pngs, sizes);
      } catch (IOException e) {
        // the program still works; the icon goes next to it instead
        System.err.println("icon: couldn't put it into the .exe (" + e.getMessage() + "); left " + safe + ".ico next to it");
        AppIcons.writeIco(AppIcons.read(png), new File(dest, safe + ".ico"));
      }
    }
    return dest;
  }

  static boolean mac() {
    return System.getProperty("os.name", "").toLowerCase().contains("mac");
  }

  /** The PNG picked in the Export Application dialog for the exports being made, or null. */
  static volatile File icon;

  /** Windows and Linux: the icon next to the launcher (.ico; .png plus a .desktop file). */
  private static void iconFiles(File dest, String sketchName, String safe, boolean forWindows) {
    File png = icon;
    if (png == null || !png.isFile()) return;
    try {
      java.awt.image.BufferedImage img = AppIcons.read(png);
      if (forWindows) {
        AppIcons.writeIco(img, new File(dest, safe + ".ico"));
      } else {
        AppIcons.writePng(img, 256, new File(dest, safe + ".png"));
        File desktop = new File(dest, safe + ".desktop");
        Files.writeString(desktop.toPath(), "[Desktop Entry]\nType=Application\nName=" + sketchName
          + "\nExec=sh -c 'cd \"$(dirname \"%k\")\" && ./" + safe + "'\nIcon=" + new File(dest, safe + ".png").getAbsolutePath()
          + "\nCategories=Graphics;\nTerminal=false\n", StandardCharsets.UTF_8);
        desktop.setExecutable(true, false);
      }
    } catch (IOException e) {
      System.err.println("icon: " + e.getMessage());
    }
  }

  /**
   * macOS: wrap the export in a real .app (as C++ Mode does) and sign it
   * ad hoc. Without a signature Apple Silicon calls a downloaded app
   * "damaged" and won't open it at all; with one it's only an app from an
   * unidentified developer, which opens with right-click, Open. Notarizing
   * would remove that last step but needs a paid Apple developer account.
   */
  static void macApp(File dest, String sketchName, String safe, File prog) throws IOException {
    File app = new File(dest, safe + ".app");
    File contents = new File(app, "Contents");
    File res = new File(contents, "Resources");
    File macos = new File(contents, "MacOS");
    if (!macos.mkdirs() || !res.mkdirs()) throw new IOException("can't make " + app);
    File[] top = dest.listFiles();
    if (top != null) for (File f : top) {
      if (f.equals(app)) continue;
      if (f.isDirectory()) copyTree(f.toPath(), new File(res, f.getName()).toPath());
      else Files.copy(f.toPath(), new File(res, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    new File(res, safe).setExecutable(true, false);
    new File(new File(res, "lib"), prog.getName()).setExecutable(true, false);
    File exe = new File(macos, safe);
    Files.writeString(exe.toPath(), "#!/bin/sh\nexec \"$(dirname \"$0\")/../Resources/" + safe + "\" \"$@\"\n", StandardCharsets.UTF_8);
    exe.setExecutable(true, false);
    String id = safe.replaceAll("[^A-Za-z0-9.-]", "-");
    Files.writeString(new File(contents, "Info.plist").toPath(),
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
      + "<plist version=\"1.0\"><dict>\n"
      + "<key>CFBundleName</key><string>" + sketchName.replace("&", "&amp;").replace("<", "&lt;") + "</string>\n"
      + "<key>CFBundleExecutable</key><string>" + safe + "</string>\n"
      + "<key>CFBundleIdentifier</key><string>org.processing.sketch." + id + "</string>\n"
      + "<key>CFBundlePackageType</key><string>APPL</string>\n"
      + "<key>CFBundleShortVersionString</key><string>1.0</string>\n"
      + (icon != null && icon.isFile() ? "<key>CFBundleIconFile</key><string>" + safe + ".icns</string>\n" : "")
      + "<key>NSHighResolutionCapable</key><true/>\n"
      + "<key>NSCameraUsageDescription</key><string>The sketch uses the camera.</string>\n"
      + "<key>NSMicrophoneUsageDescription</key><string>The sketch uses the microphone.</string>\n"
      + "</dict></plist>\n", StandardCharsets.UTF_8);
    if (icon != null && icon.isFile()) {
      try { AppIcons.writeIcns(AppIcons.read(icon), new File(res, safe + ".icns")); }
      catch (IOException e) { System.err.println("icon: " + e.getMessage()); }
    }
    // sign the libraries and the program first, then the bundle
    File[] libs = new File(res, "lib").listFiles();
    if (libs != null) for (File l : libs) sign(l);
    sign(app);
    // our own copy came from the IDE, so it isn't quarantined; a downloaded one is
    Files.writeString(new File(dest, "If macOS won't open it.txt").toPath(),
      "macOS only opens apps from the App Store or from paid Apple developers without asking.\n\n"
      + "The first time: right-click " + safe + ".app and choose Open, then Open again.\n"
      + "On macOS 15 and newer: try to open it once, then go to System Settings > Privacy & Security\n"
      + "and click Open Anyway.\n\n"
      + "Or in Terminal:  xattr -dr com.apple.quarantine " + safe + ".app\n", StandardCharsets.UTF_8);
  }

  private static void sign(File f) {
    try {
      Process p = new ProcessBuilder("codesign", "--force", "--sign", "-", f.getAbsolutePath())
        .redirectErrorStream(true).start();
      p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
      p.waitFor();
    } catch (Exception ignored) {}
  }

  /**
   * File &gt; Export as PDEZ: the sketch folder zipped next to it, as
   * <sketch>.pdez, the way Processing's Java mode makes one (entries start
   * with the sketch's folder name, so opening the file in Processing installs
   * the sketch). The platform folders Export Application makes are left out:
   * they're programs, big, and only run on one platform.
   */
  static File pdez(File sketchFolder) throws IOException {
    Path folder = sketchFolder.toPath();
    Path target = new File(sketchFolder.getPath() + ".pdez").toPath();
    Files.deleteIfExists(target);
    java.util.Set<String> skip = java.util.Set.of("linux-amd64", "linux-aarch64", "linux-arm",
        "macos-x86_64", "macos-aarch64", "windows-amd64", "windows-aarch64");
    try (java.util.zip.ZipOutputStream zs = new java.util.zip.ZipOutputStream(Files.newOutputStream(target));
         Stream<Path> all = Files.walk(folder)) {
      for (Path p : (Iterable<Path>) all::iterator) {
        if (Files.isDirectory(p)) continue;
        Path rel = folder.relativize(p);
        if (rel.getNameCount() > 1 && skip.contains(rel.getName(0).toString())) continue;
        // forward slashes on every platform, like any zip
        String name = sketchFolder.getName() + "/" + rel.toString().replace(File.separatorChar, '/');
        zs.putNextEntry(new java.util.zip.ZipEntry(name));
        Files.copy(p, zs);
        zs.closeEntry();
      }
    }
    return target.toFile();
  }

  static void copyTree(Path from, Path to) throws IOException {
    try (Stream<Path> all = Files.walk(from)) {
      for (Path p : (Iterable<Path>) all::iterator) {
        Path t = to.resolve(from.relativize(p).toString());
        if (Files.isDirectory(p)) Files.createDirectories(t);
        else {
          Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
          // programs stay programs (Python's bin/, launchers)
          if (!windows() && Files.isExecutable(p)) t.toFile().setExecutable(true, false);
        }
      }
    }
  }

  static void deleteTree(Path p) throws IOException {
    if (!Files.exists(p)) return;
    try (Stream<Path> all = Files.walk(p)) {
      for (Path q : (Iterable<Path>) all.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(q);
    }
  }
}
