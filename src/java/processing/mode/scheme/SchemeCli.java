package processing.mode.scheme;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Scheme sketch to Python, from the command line (no Processing IDE), for the
 * standalone package's run scripts:
 *
 *     java -cp SchemeMode.jar processing.mode.scheme.SchemeCli sketch.scm out.py
 *
 * Uses only the parser and the transpiler, so nothing from Processing is needed.
 */
public final class SchemeCli {
  private SchemeCli() {}

  /** The Python program for a Scheme sketch, as the IDE makes it. */
  static String toPython(String source, String sketchName) throws SchemeException {
    List<SExpr> forms = Parser.parse(source);
    String py = new Transpiler().transpile(forms, sketchName);
    // Processing Java's default camera: eye at (width/2, height/2,
    // (height/2)/tan(60*pi/360)) looking at (width/2, height/2, 0)
    return py.replace("    mode_3d()\n",
        "    mode_3d()\n" +
        "    _eye_z = (height/2) / _math.tan(60 * _math.pi / 360)\n" +
        "    camera_position(width/2, height/2, _eye_z)\n" +
        "    camera_look_at(width/2, height/2, 0)\n");
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("usage: SchemeCli sketch.scm out.py");
      System.exit(2);
    }
    Path in = Path.of(args[0]);
    String name = in.getFileName().toString().replaceAll("\\.scm$", "");
    try {
      String py = toPython(Files.readString(in), name);
      Path out = Path.of(args[1]);
      if (out.getParent() != null) Files.createDirectories(out.getParent());
      Files.writeString(out, py);
    } catch (SchemeException e) {
      System.err.println(in.getFileName() + ":" + e.line + ": " + e.getMessage());
      System.exit(1);
    }
  }
}
