package processing.mode.scheme;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Export Application for Scheme sketches: the generated Python program, its
 * assets and the Python with mewnala that comes in the mode's zip
 * (python/&lt;platform&gt;/), so the export runs with nothing installed, on
 * every platform the zip has a Python for. Same dialog and extras as Lua and
 * Rust Mode: an icon, a .app on macOS, an AppImage on Linux, and on Windows
 * a &lt;Sketch&gt;.exe with the icon built in.
 */
final class SchemeExport {
  private SchemeExport() {}

  /** The Python folder for a platform in this copy of the mode, or null. */
  static File python(File modeFolder, ExportDialog.Target t) {
    File d = new File(modeFolder, "python/" + t.lua);
    File exe = new File(d, t.windows() ? "pythonw.exe" : "bin/python3");
    return exe.isFile() ? d : null;
  }

  /**
   * Writes one platform's export into outDir/&lt;platform folder&gt; and returns it.
   * @param script  the Python program SchemeBuild generated
   */
  static File write(File modeFolder, File sketchFolder, String sketchName, File script,
                    ExportDialog.Target t, File outDir, File icon, boolean appImage,
                    Consumer<String> status) throws IOException, InterruptedException {
    File py = python(modeFolder, t);
    if (py == null) throw new IOException("there's no Python for " + t.label + " in this copy of Scheme Mode");
    String safe = sketchName.replaceAll("[^A-Za-z0-9_.-]", "_");
    File dest = new File(outDir, t.folder);
    AppExport.deleteTree(dest.toPath());
    dest.mkdirs();
    status.accept("Exporting for " + t.label + " (copying Python)...");

    if (t.windows()) {
      // Python's files at the top, and pythonw.exe copied as <Sketch>.exe with
      // the icon. A .pth file in site-packages starts sketch.py when Python
      // runs under any other name with nothing to run, which is exactly the
      // double-clicked <Sketch>.exe.
      AppExport.copyTree(py.toPath(), dest.toPath());
      Files.copy(script.toPath(), new File(dest, "sketch.py").toPath());
      SchemeRunner.copyAssets(sketchFolder, new File(dest, "assets"));
      File exe = new File(dest, safe + ".exe");
      Files.copy(new File(dest, "pythonw.exe").toPath(), exe.toPath());
      Files.writeString(new File(dest, "Lib/site-packages/zz_sketch_start.pth").toPath(),
        "import os, sys; exec(\"if len(sys.argv) <= 1 and os.path.basename(sys.executable).lower() not in ('python.exe', 'pythonw.exe'):\\n"
        + "    os.chdir(os.path.dirname(sys.executable)); sys.argv = ['sketch.py']\\n"
        + "    import runpy; runpy.run_path('sketch.py', run_name='__main__'); sys.exit(0)\")\n",
        StandardCharsets.UTF_8);
      if (icon != null && icon.isFile()) {
        java.awt.image.BufferedImage img = AppIcons.read(icon);
        int[] sizes = { 16, 32, 48, 64, 128, 256 };
        java.util.List<byte[]> pngs = new java.util.ArrayList<>();
        for (int s : sizes) pngs.add(AppIcons.pngBytes(img, s));
        try { PeIcon.embed(exe, pngs, sizes); }
        catch (IOException e) { AppIcons.writeIco(img, new File(dest, safe + ".ico")); }
      }
      return dest;
    }

    // macOS and Linux: Python in lib/python, the program and assets at the top,
    // and a launcher named after the sketch
    AppExport.copyTree(py.toPath(), new File(dest, "lib/python").toPath());
    File[] bins = new File(dest, "lib/python/bin").listFiles();
    if (bins != null) for (File b : bins) b.setExecutable(true, false);
    Files.copy(script.toPath(), new File(dest, "lib/sketch.py").toPath());
    SchemeRunner.copyAssets(sketchFolder, new File(dest, "lib/assets"));
    StringBuilder sh = new StringBuilder("#!/bin/sh\n")
      .append("# ").append(sketchName).append(": a Processing sketch written in Scheme.\n")
      .append("APPDIR=$(cd \"$(dirname \"$0\")\" && pwd -P)\n")
      .append("cd \"$APPDIR/lib\" || exit 1\n")
      .append("export RUST_LOG=error BEVY_LOG=error WGPU_LOG=error RUST_LOG_STYLE=never\n");
    if (!t.mac()) sh.append("export MEWNALA_BACKEND=glfw-x11 GDK_BACKEND=x11\n");
    sh.append("exec \"$APPDIR/lib/python/bin/python3\" -u sketch.py \"$@\"\n");
    File launcher = new File(dest, safe);
    Files.writeString(launcher.toPath(), sh.toString(), StandardCharsets.UTF_8);
    launcher.setExecutable(true, false);

    if (t.mac()) {
      File prev = AppExport.icon;
      AppExport.icon = icon;
      try { AppExport.macApp(dest, sketchName, safe, new File(dest, "lib/python/bin/python3")); }
      finally { AppExport.icon = prev; }
      File[] inApp = new File(dest, safe + ".app/Contents/Resources/lib/python/bin").listFiles();
      if (inApp != null) for (File b : inApp) b.setExecutable(true, false);
      return dest;
    }

    if (icon != null && icon.isFile()) {
      AppIcons.writePng(AppIcons.read(icon), 256, new File(dest, safe + ".png"));
      File desktop = new File(dest, safe + ".desktop");
      Files.writeString(desktop.toPath(), "[Desktop Entry]\nType=Application\nName=" + sketchName
        + "\nExec=" + new File(dest, safe).getAbsolutePath() + "\nIcon=" + new File(dest, safe + ".png").getAbsolutePath()
        + "\nCategories=Graphics;\nTerminal=false\n", StandardCharsets.UTF_8);
      desktop.setExecutable(true, false);
    }
    if (appImage) {
      File img = AppImage.make(modeFolder, dest, sketchName, safe,
        t == ExportDialog.Target.LINUX_ARM64 ? "aarch64" : "x86_64", icon, status);
      status.accept("Made " + img.getName());
    }
    return dest;
  }
}
