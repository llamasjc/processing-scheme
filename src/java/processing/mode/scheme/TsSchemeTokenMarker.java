package processing.mode.scheme;

import processing.app.syntax.Token;
import processing.app.syntax.TokenMarker;
import processing.app.syntax.SyntaxDocument;

import javax.swing.text.Segment;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tree-sitter-backed syntax token marker for the Processing jEdit text area.
 *
 * <p>Processing4's {@link TokenMarker} uses an {@code addColoring(keyword, type)}
 * registration model for keyword-based syntax highlighting.  This subclass
 * bypasses that mechanism: instead of registering keyword lists it rebuilds
 * a per-character highlight array asynchronously via tree-sitter and overrides
 * {@link #markTokensImpl} to walk that array for each rendered line.
 *
 * <p>The abstract method {@code addColoring(String, String)} required by the
 * base class is implemented as a no-op — all colouring information comes
 * from the tree-sitter highlights query.
 */
public class TsSchemeTokenMarker extends TokenMarker {

  // ── Highlight map (one byte per char position in source) ─────────────────

  private static final byte[] EMPTY = new byte[0];

  /** Token-type per character offset in the current source string. */
  private final AtomicReference<byte[]> highlights = new AtomicReference<>(EMPTY);

  /** The source string matching the highlights array. */
  private volatile String highlightedSource = "";

  // ── Document reference ────────────────────────────────────────────────────

  private volatile SyntaxDocument syntaxDoc;

  public void setDoc(SyntaxDocument d) { this.syntaxDoc = d; }

  // ── Async updater ─────────────────────────────────────────────────────────

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CppTsHighlighter");
        t.setDaemon(true);
        return t;
      });

  private ScheduledFuture<?> pending;

  // ── Query (loaded lazily) ─────────────────────────────────────────────────

  private volatile ch.usi.si.seart.treesitter.Query query;
  private final Object queryLock = new Object();

  public TsSchemeTokenMarker() {
  }

  // ── TokenMarker contract ──────────────────────────────────────────────────

  /**
   * Required by the base class's keyword-registration model.
   * We don't use keyword lists — all highlighting comes from tree-sitter —
   * so this is intentionally a no-op.
   */
  @Override
  public void addColoring(String keyword, String type) {
    byte t = tokenId(type);
    if (t >= 0) keywords.put(keyword, t);
  }

  /** keywords.txt entries, word -> Token type, e.g. ellipse -> FUNCTION1. */
  private final java.util.Map<String, Byte> keywords = new java.util.concurrent.ConcurrentHashMap<>();

  private static byte tokenId(String type) {
    try { return Token.class.getField(type.trim()).getByte(null); }
    catch (Throwable e) { return -1; }
  }

  /** Fallback if Processing did not call addColoring: read the mode's keywords.txt ourselves. */
  private void loadKeywordsFile() {
    try {
      java.io.File jar = new java.io.File(TsSchemeTokenMarker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      java.io.File f = new java.io.File(jar.getParentFile().getParentFile(), "keywords.txt");
      for (String line : java.nio.file.Files.readAllLines(f.toPath())) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2 && !parts[0].startsWith("#")) addColoring(parts[0], parts[1]);
      }
    } catch (Throwable e) { hlog(e); }
  }

  private static boolean isDelim(char c) {
    return Character.isWhitespace(c) || "()[]{}\"';`,".indexOf(c) >= 0;
  }

  /**
   * Symbols take their color from keywords.txt, like Processing's Java and C++ modes.
   * Comments and strings from tree-sitter are left alone. #t, #f and #\\a keep their query color.
   * Anything not in keywords.txt (user names, numbers) is plain.
   */
  private void applyKeywords(String src, byte[] hl) {
    if (keywords.isEmpty()) loadKeywordsFile();
    int n = Math.min(src.length(), hl.length), i = 0;
    while (i < n) {
      char c = src.charAt(i);
      if (isDelim(c) || hl[i] == Token.COMMENT1 || hl[i] == Token.COMMENT2 || hl[i] == Token.LITERAL1) { i++; continue; }
      int j = i;
      while (j < n && !isDelim(src.charAt(j))) j++;
      String w = src.substring(i, j);
      if (w.equals("#t") || w.equals("#true"))  w = "true";
      if (w.equals("#f") || w.equals("#false")) w = "false";
      if (c != '#' || w.equals("true") || w.equals("false")) {
        Byte t = keywords.get(w);
        byte type = t != null ? t : Token.NULL;
        // Like Processing's Java/C++ modes: function-type words only color when called.
        // In Scheme a call is "right after (", the mirror of C++'s "followed by (".
        if (type == Token.FUNCTION1 || type == Token.FUNCTION2 || type == Token.FUNCTION3 || type == Token.FUNCTION4) {
          int k = i - 1;
          while (k >= 0 && Character.isWhitespace(src.charAt(k))) k--;
          if (k < 0 || src.charAt(k) != '(') type = Token.NULL;
        }
        Arrays.fill(hl, i, j, type);
      }
      i = j;
    }
  }

  /**
   * Called by the text-area renderer for each visible line.
   * We walk our per-character highlight array instead of the base-class
   * keyword table, so we override this directly.
   */
  @Override
  public byte markTokensImpl(byte token, Segment line, int lineIndex) {
    byte[] hl  = highlights.get();
    String src = highlightedSource;

    if (hl.length == 0) {
      addToken(line.count, Token.NULL);
      return 0;
    }

    int lineStart = offsetOfLine(src, lineIndex);
    if (lineStart < 0 || lineStart >= src.length()) {
      addToken(line.count, Token.NULL);
      return 0;
    }

    int  pos      = 0;
    byte lastType = (lineStart < hl.length) ? hl[lineStart] : Token.NULL;
    int  runLen   = 0;

    while (pos < line.count) {
      int  charPos = lineStart + pos;
      byte type    = (charPos < hl.length) ? hl[charPos] : Token.NULL;
      if (type != lastType) {
        if (runLen > 0) addToken(runLen, lastType);
        lastType = type;
        runLen   = 0;
      }
      runLen++;
      pos++;
    }
    if (runLen > 0) addToken(runLen, lastType);

    return 0;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  public synchronized void invalidate() {
    if (pending != null) pending.cancel(false);
    pending = scheduler.schedule(() -> { try { rebuildHighlights(); } catch (Throwable t) { hlog(t); } }, 150, TimeUnit.MILLISECONDS);
  }

  public void dispose() { scheduler.shutdownNow(); }

  // ── Highlight rebuilding ──────────────────────────────────────────────────

  private void rebuildHighlights() {
    try {
      TsSchemeEngine engine = TsSchemeEngine.get();
      if (engine == null) { hlog("engine unavailable"); return; }
      String src = currentDocSource();
      if (src == null || src.isEmpty()) return;

      try (TsSchemeEngine.ParseResult result = engine.parse(src)) {
        ch.usi.si.seart.treesitter.Query q = ensureQuery();
        if (q == null) { hlog("query did not compile"); return; }

        byte[] hl = new byte[src.length()];
        Arrays.fill(hl, Token.NULL);

        // node.walk(query) is the public factory for QueryCursor in 1.9.1
        ch.usi.si.seart.treesitter.QueryCursor cursor = result.root().walk(q);

        for (ch.usi.si.seart.treesitter.QueryMatch match : cursor) {
          Map<ch.usi.si.seart.treesitter.Capture,
              Collection<ch.usi.si.seart.treesitter.Node>> caps = match.getCaptures();
          for (Map.Entry<ch.usi.si.seart.treesitter.Capture,
                         Collection<ch.usi.si.seart.treesitter.Node>> entry
               : caps.entrySet()) {
            String capName = entry.getKey().getName();
            byte   tokType = highlightNameToToken(capName);
            if (tokType == Token.NULL) continue;
            for (ch.usi.si.seart.treesitter.Node node : entry.getValue()) {
              int start = node.getStartByte();
              int end   = node.getEndByte();
              for (int i = start; i < end && i < hl.length; i++) hl[i] = tokType;
            }
          }
        }

        highlightedSource = src;
        applyKeywords(src, hl);
        highlights.set(hl);
        Runnable u = onUpdate; if (u != null) javax.swing.SwingUtilities.invokeLater(u);
      }

      javax.swing.SwingUtilities.invokeLater(this::fireTokensChanged);

    } catch (Throwable ignored) { hlog(ignored);  }
  }

  // ── Source access ─────────────────────────────────────────────────────────

  private volatile java.util.function.Supplier<String> source;

  /** The editor tells the marker where its text lives. */
  private volatile Runnable onUpdate;
  public void setOnUpdate(Runnable r) { this.onUpdate = r; }

  public void setSource(java.util.function.Supplier<String> source) { this.source = source; }

  private String currentDocSource() {
    java.util.function.Supplier<String> s = source;
    return s == null ? null : s.get();
  }

  // ── Query management ──────────────────────────────────────────────────────

  private ch.usi.si.seart.treesitter.Query ensureQuery() {
    synchronized (queryLock) {
      if (query != null) return query;
      try {
        String qs = loadHighlightsScm();
        if (qs != null) {
          query = ch.usi.si.seart.treesitter.Query.getFor(
              ch.usi.si.seart.treesitter.Language.SCHEME, qs);
        }
      } catch (Throwable ignored) { hlog(ignored);  }
      return query;
    }
  }

  private static String loadHighlightsScm() {
    try (java.io.InputStream in =
             TsSchemeTokenMarker.class.getResourceAsStream("/queries/highlights.scm")) {
      if (in == null) return null;
      return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Throwable e) { hlog(e); return null; }
  }

  // ── Highlight name → Token type ──────────────────────────────────────────

  private static byte highlightNameToToken(String name) {
    return switch (name) {
      case "function.builtin", "variable.builtin" -> Token.KEYWORD2;
      case "type.builtin",     "constant.builtin",
           "type",             "keyword",
           "keyword.control",  "keyword.operator",
           "keyword.return",   "keyword.type",
           "storage.type",     "storage.modifier"  -> Token.KEYWORD1;
      case "function",         "method",
           "function.call",    "method.call"        -> Token.KEYWORD3;
      case "comment",          "comment.line",
           "comment.block"                          -> Token.COMMENT1;
      case "string",           "string.escape"      -> Token.LITERAL1;
      case "number",           "character",
           "number.float",     "number.integer"     -> Token.LITERAL2;
      case "operator"                               -> Token.OPERATOR;
      case "error"                                  -> Token.INVALID;
      default                                       -> Token.NULL;
    };
  }

  // ── Line-offset calculation ───────────────────────────────────────────────

  private static int offsetOfLine(String src, int lineIndex) {
    if (lineIndex == 0) return 0;
    int line = 0;
    for (int i = 0; i < src.length(); i++) {
      if (src.charAt(i) == '\n') {
        line++;
        if (line == lineIndex) return i + 1;
      }
    }
    return -1;
  }

  // ── Notification helper ───────────────────────────────────────────────────

  private void fireTokensChanged() {
    SyntaxDocument d = syntaxDoc;
    if (d != null) {
      try { d.tokenizeLines(); }
      catch (Throwable ignored) { hlog(ignored);  }
    }
  }

  private static final java.util.Set<String> HLOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private static void hlog(Object o) {
    String m = (o instanceof Throwable t) ? t.toString() : String.valueOf(o);
    if (HLOGGED.add(m)) System.err.println("[SchemeMode] highlight: " + m);
  }
}
