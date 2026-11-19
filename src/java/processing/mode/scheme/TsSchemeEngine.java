package processing.mode.scheme;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class TsSchemeEngine {

  // ── Singleton ────────────────────────────────────────────────────────────

  private static volatile TsSchemeEngine INSTANCE;
  private static volatile boolean  FAILED;

  public static TsSchemeEngine get() {
    if (FAILED) return null;
    TsSchemeEngine e = INSTANCE;
    if (e != null) return e;
    synchronized (TsSchemeEngine.class) {
      if (INSTANCE == null && !FAILED) {
        try {
          loadNative();
          INSTANCE = new TsSchemeEngine();
        } catch (Throwable t) {
          FAILED = true;
          System.err.println("[SchemeMode] tree-sitter unavailable: " + t);
        }
      }
      return INSTANCE;
    }
  }

  /**
   * The Scheme-only library built for this computer (native/&lt;platform&gt;/,
   * see native/build_ts.py) when the mode has one: java-tree-sitter's own
   * loader only knows Linux and macOS, so Windows needs this. Otherwise the
   * library inside the java-tree-sitter jar.
   */
  private static void loadNative() {
    String os = System.getProperty("os.name", "").toLowerCase();
    boolean arm = System.getProperty("os.arch", "").matches("aarch64|arm64");
    String rel = os.contains("win") ? "windows-x86_64/java-tree-sitter.dll"
               : os.contains("mac") ? "macos/libjava-tree-sitter.dylib"
               : (arm ? "linux-aarch64" : "linux-x86_64") + "/libjava-tree-sitter.so";
    java.io.File lib = null;
    try {
      java.io.File jar = new java.io.File(TsSchemeEngine.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      lib = new java.io.File(jar.getParentFile().getParentFile(), "native/" + rel);
    } catch (Exception ignored) {}
    if (lib != null && lib.isFile()) System.load(lib.getAbsolutePath());
    else ch.usi.si.seart.treesitter.LibraryLoader.load();
  }

  public static boolean isAvailable() { return !FAILED && INSTANCE != null; }

  public static synchronized void markFailed(String reason) {
    if (!FAILED) {
      FAILED = true;
      System.err.println(reason);
    }
  }

  public static synchronized void init(java.io.File modeFolder) {
    if (INSTANCE == null && !FAILED) get();
  }

  public static synchronized void initForCli(java.io.File nativeLib, java.io.File modeFolder) {
    if (INSTANCE == null && !FAILED) get();
  }

  // ── Construction ─────────────────────────────────────────────────────────

  private static final int POOL_SIZE = 4;

  private final BlockingQueue<ch.usi.si.seart.treesitter.Parser> pool =
      new ArrayBlockingQueue<>(POOL_SIZE);

  private TsSchemeEngine() {
    for (int i = 0; i < POOL_SIZE; i++) {
      pool.add(ch.usi.si.seart.treesitter.Parser.getFor(
          ch.usi.si.seart.treesitter.Language.SCHEME));
    }
  }

  // ── Public API ────────────────────────────────────────────────────────────

  public ch.usi.si.seart.treesitter.Language language() {
    return ch.usi.si.seart.treesitter.Language.SCHEME;
  }

  public ParseResult parse(String source) {
    ch.usi.si.seart.treesitter.Parser parser;
    try {
      parser = pool.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("interrupted waiting for parser", e);
    }
    ch.usi.si.seart.treesitter.Tree tree = parser.parse(source);
    return new ParseResult(source, tree, parser, pool);
  }

  public boolean hasErrors(String source) {
    try (ParseResult r = parse(source)) { return r.hasErrors(); }
  }

  // ── ParseResult ───────────────────────────────────────────────────────────

  public static final class ParseResult implements AutoCloseable {

    private final String source;
    private final ch.usi.si.seart.treesitter.Tree tree;
    private final ch.usi.si.seart.treesitter.Parser parser;
    private final BlockingQueue<ch.usi.si.seart.treesitter.Parser> pool;

    private ParseResult(String source,
                        ch.usi.si.seart.treesitter.Tree tree,
                        ch.usi.si.seart.treesitter.Parser parser,
                        BlockingQueue<ch.usi.si.seart.treesitter.Parser> pool) {
      this.source = source; this.tree = tree;
      this.parser = parser; this.pool  = pool;
    }

    public String source() { return source; }
    public ch.usi.si.seart.treesitter.Node root() { return tree.getRootNode(); }
    public boolean hasErrors() { return root().hasError(); }

    public String textOf(ch.usi.si.seart.treesitter.Node n) {
      int s = n.getStartByte(), e = n.getEndByte();
      if (s < 0 || e > source.length() || s > e) return "";
      return source.substring(s, e);
    }

    public String fieldText(ch.usi.si.seart.treesitter.Node n, String field) {
      ch.usi.si.seart.treesitter.Node child = n.getChildByFieldName(field);
      return child != null ? textOf(child) : "";
    }

    @Override public void close() {
      try { tree.close(); } catch (Exception ignored) {}
      pool.offer(parser);
    }
  }
}
