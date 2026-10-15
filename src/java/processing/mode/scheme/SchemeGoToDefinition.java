package processing.mode.scheme;

import java.util.*;
import java.util.regex.*;

/**
 * Go-to-definition for SchemeMode. The index is built on demand from the current text
 * of every tab, so it is never stale. Each definition records where its name is written.
 */
public final class SchemeGoToDefinition {

    /** Where a name is defined: 0-based tab, 0-based row and column. */
    public record Definition(String name, int tabIndex, int row, int col) {}

    private SchemeGoToDefinition() {}

    /** Characters that end a Scheme identifier. */
    static boolean isDelimiter(char c) {
        return Character.isWhitespace(c) || "()[]{}\"';`,".indexOf(c) >= 0;
    }

    public static Map<String, Definition> build(List<String> tabTexts) {
        Map<String, Definition> index = new LinkedHashMap<>();
        for (int tab = 0; tab < tabTexts.size(); tab++) {
            String text = tabTexts.get(tab);
            String[] lines = text.split("\n", -1);
            try {
                for (SExpr f : Parser.read(text)) collect(f, tab, lines, index);
            } catch (Exception parseError) {
                scanLines(tab, lines, index); // half-typed code: still find the obvious defines
            }
        }
        return index;
    }

    public static Definition find(List<String> tabTexts, String name) {
        return build(tabTexts).get(name);
    }

    private static List<SExpr> items(SExpr e) {
        List<SExpr> out = new ArrayList<>();
        while (e instanceof SExpr.Pair p) { out.add(p.car()); e = p.cdr(); }
        return out;
    }

    private static void add(SExpr sym, int tab, String[] lines, Map<String, Definition> index) {
        if (!(sym instanceof SExpr.Sym s) || index.containsKey(s.name())) return;
        int row = Math.max(0, s.line() - 1);
        int col = 0;
        for (int r = row; r < Math.min(lines.length, row + 3); r++) {
            int c = columnOf(lines[r], s.name());
            if (c >= 0) { row = r; col = c; break; }
        }
        index.put(s.name(), new Definition(s.name(), tab, row, col));
    }

    /** Column of name as a whole Scheme token on the line, or -1. */
    static int columnOf(String line, String name) {
        int from = 0;
        while (true) {
            int i = line.indexOf(name, from);
            if (i < 0) return -1;
            boolean startOk = i == 0 || isDelimiter(line.charAt(i - 1));
            int end = i + name.length();
            boolean endOk = end >= line.length() || isDelimiter(line.charAt(end));
            if (startOk && endOk) return i;
            from = i + 1;
        }
    }

    private static void collect(SExpr e, int tab, String[] lines, Map<String, Definition> index) {
        if (!(e instanceof SExpr.Pair)) return;
        if (e.isForm("quote") || e.isForm("quasiquote")) return;
        List<SExpr> f = items(e);
        if (e.isForm("define") && f.size() > 1) {
            SExpr target = f.get(1);
            add(target instanceof SExpr.Pair p ? p.car() : target, tab, lines, index);
        } else if (e.isForm("define-syntax") && f.size() > 1) {
            add(f.get(1), tab, lines, index);
        } else if (e.isForm("define-values") && f.size() > 1) {
            SExpr formals = f.get(1);
            while (formals instanceof SExpr.Pair p) { add(p.car(), tab, lines, index); formals = p.cdr(); }
            add(formals, tab, lines, index);
        } else if (e.isForm("define-record-type") && f.size() > 3) {
            add(f.get(1), tab, lines, index);
            if (f.get(2) instanceof SExpr.Pair ctor) add(ctor.car(), tab, lines, index);
            add(f.get(3), tab, lines, index);
            for (SExpr field : f.subList(4, f.size())) {
                List<SExpr> fl = items(field);
                for (int i = 1; i < fl.size(); i++) add(fl.get(i), tab, lines, index);
            }
        }
        for (SExpr child : f) collect(child, tab, lines, index); // internal defines
    }

    private static final Pattern DEFINE_LINE =
        Pattern.compile("\\(\\s*(define|define-syntax)\\s+\\(?\\s*([^\\s()\\[\\]{}\"';`,]+)");

    private static void scanLines(int tab, String[] lines, Map<String, Definition> index) {
        for (int r = 0; r < lines.length; r++) {
            Matcher m = DEFINE_LINE.matcher(lines[r]);
            while (m.find()) index.putIfAbsent(m.group(2), new Definition(m.group(2), tab, r, m.start(2)));
        }
    }
}
