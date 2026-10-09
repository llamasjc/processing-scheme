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
          ch.usi.si.seart.treesitter.LibraryLoader.load();
          INSTANCE = new TsSchemeEngine();
        } catch (Throwable t) {
          FAILED = true;
          System.err.println("[SchemeMode] tree-sitter unavailable: " + t);
        }
      }
      return INSTANCE;
    }
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
