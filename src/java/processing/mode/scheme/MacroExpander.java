package processing.mode.scheme;

import java.util.*;

/**
 * Simple syntax-rules macro expander.
 * Runs before transpilation to expand user-defined macros.
 */
public class MacroExpander {
    private int gensym = 0;

    record Rule(SExpr pattern, List<SExpr> template) {}
    record Macro(String name, List<String> literals, List<Rule> rules) {}

    private final Map<String, Macro> macros = new LinkedHashMap<>();

    /** Collect macros and expand all forms. */
    // Built-in derived forms, written as ordinary syntax-rules. Collected before the
    // sketch's own macros, so a sketch can still define its own version.
    private static final String PRELUDE = """
        (define-syntax let-values
          (syntax-rules ()
            ((_ () body ...) ((lambda () body ...)))
            ((_ ((formals expr) rest ...) body ...)
             (call-with-values (lambda () expr) (lambda formals (let-values (rest ...) body ...))))))
        (define-syntax let*-values
          (syntax-rules ()
            ((_ clauses body ...) (let-values clauses body ...))))
        """;

    public List<SExpr> expand(List<SExpr> forms) throws SchemeException {
        for (SExpr f : Parser.read(PRELUDE)) collectMacro(f);
        // First pass: collect define-syntax
        for (SExpr f : forms) {
            if (f.isForm("define-syntax")) {
                collectMacro(f);
            }
        }
        // Second pass: expand
        List<SExpr> result = new ArrayList<>();
        for (SExpr f : forms) {
            if (f.isForm("define-syntax") || f.isForm("let-syntax") || f.isForm("letrec-syntax")) {
                // keep it for reference but don't emit
                continue;
            }
            result.add(expandForm(f));
        }
        return result;
    }

    private void collectMacro(SExpr f) throws SchemeException {
        List<SExpr> parts = toList(f);
        if (parts.size() < 3) return;
        String name = sym(parts.get(1));
        SExpr transformer = parts.get(2);
        if (!transformer.isForm("syntax-rules")) return;
        List<SExpr> srParts = toList(transformer);
        List<String> literals = new ArrayList<>();
        for (SExpr lit : toList(srParts.get(1))) {
            literals.add(sym(lit));
        }
        List<Rule> rules = new ArrayList<>();
        for (int i = 2; i < srParts.size(); i++) {
            List<SExpr> rule = toList(srParts.get(i));
            SExpr pat = ((SExpr.Pair) rule.get(0)).cdr(); // skip macro name, keep dotted tail
            List<SExpr> tmpl = rule.subList(1, rule.size());
            rules.add(new Rule(pat, tmpl));
        }
        macros.put(name, new Macro(name, literals, rules));
    }

    private SExpr expandForm(SExpr e) throws SchemeException {
        if (!(e instanceof SExpr.Pair)) return e;
        SExpr.Pair p = (SExpr.Pair) e;
        String head = p.headSym();
        // Check if head is a macro
        if (head != null && macros.containsKey(head)) {
            Macro m = macros.get(head);
            List<SExpr> args = toList(e).subList(1, toList(e).size());
            SExpr expanded = applyMacro(m, args, e.line());
            return expandForm(expanded);
        }
        // Quoted data is not code
        if ("quote".equals(head)) return e;
        return expandPair(e);
    }

    /** Rebuild a pair chain, expanding each element and keeping any dotted tail. */
    private SExpr expandPair(SExpr e) throws SchemeException {
        if (!(e instanceof SExpr.Pair p)) return e;
        return new SExpr.Pair(expandForm(p.car()), expandPair(p.cdr()), p.line());
    }

    private SExpr applyMacro(Macro m, List<SExpr> args, int line) throws SchemeException {
        SExpr argChain = new SExpr.Nil(line);
        for (int i = args.size() - 1; i >= 0; i--) argChain = new SExpr.Pair(args.get(i), argChain, line);
        for (Rule rule : m.rules) {
            Map<String, SExpr> bindings = new LinkedHashMap<>();
            Map<String, List<SExpr>> ellipsisBindings = new LinkedHashMap<>();
            if (matchPattern(rule.pattern, argChain, m.literals, bindings, ellipsisBindings)) {
                SExpr tmpl = rule.template.get(0);
                // Hygiene: names the template binds itself get fresh names so they can't capture user code.
                java.util.Set<String> introduced = new java.util.LinkedHashSet<>();
                collectBinders(tmpl, introduced);
                for (String n : introduced)
                    if (!bindings.containsKey(n) && !ellipsisBindings.containsKey(n) && !n.equals("...") && !n.equals("_"))
                        bindings.put(n, new SExpr.Sym(Transpiler.freshName(n, "__h" + (++gensym)), line));
                return expandTemplate(tmpl, bindings, ellipsisBindings, line);
            }
        }
        throw new SchemeException("No matching syntax-rules pattern for " + m.name, line);
    }

    // A pair chain split into its elements and its tail (Nil for a proper list).
    private static final class Spine { final List<SExpr> items = new ArrayList<>(); SExpr tail; }

    private Spine spine(SExpr e) {
        Spine s = new Spine();
        while (e instanceof SExpr.Pair p) { s.items.add(p.car()); e = p.cdr(); }
        s.tail = e;
        return s;
    }

    private SExpr chain(List<SExpr> items, int from, SExpr tail, int line) {
        SExpr r = tail;
        for (int i = items.size() - 1; i >= from; i--) r = new SExpr.Pair(items.get(i), r, line);
        return r;
    }

    private boolean matchPattern(SExpr pat, SExpr arg, List<String> literals,
                                 Map<String, SExpr> b, Map<String, List<SExpr>> eb) {
        if (pat instanceof SExpr.Sym s) {
            if (s.name().equals("_")) return true;
            if (literals.contains(s.name())) return arg instanceof SExpr.Sym as && as.name().equals(s.name());
            b.put(s.name(), arg);
            return true;
        }
        if (pat instanceof SExpr.Nil) return arg instanceof SExpr.Nil;
        if (!(pat instanceof SExpr.Pair)) return true; // literal data in patterns: not checked, as before
        if (!(arg instanceof SExpr.Pair) && !(arg instanceof SExpr.Nil)) return false;
        Spine ps = spine(pat), as = spine(arg);
        int e = -1;
        for (int i = 0; i + 1 < ps.items.size(); i++) if (ps.items.get(i + 1).isSym("...")) { e = i; break; }
        if (e < 0) {
            int n = ps.items.size();
            if (as.items.size() < n) return false;
            for (int i = 0; i < n; i++) if (!matchPattern(ps.items.get(i), as.items.get(i), literals, b, eb)) return false;
            if (ps.tail instanceof SExpr.Nil) return as.items.size() == n && as.tail instanceof SExpr.Nil;
            return matchPattern(ps.tail, chain(as.items, n, as.tail, arg.line()), literals, b, eb);
        }
        int after = ps.items.size() - e - 2;
        int count = as.items.size() - e - after;
        if (count < 0) return false;
        for (int i = 0; i < e; i++) if (!matchPattern(ps.items.get(i), as.items.get(i), literals, b, eb)) return false;
        SExpr sub = ps.items.get(e);
        List<String> vars = new ArrayList<>();
        patternVars(sub, literals, vars);
        for (String v : vars) eb.put(v, new ArrayList<>());
        for (int k = 0; k < count; k++) {
            Map<String, SExpr> b2 = new LinkedHashMap<>();
            Map<String, List<SExpr>> eb2 = new LinkedHashMap<>();
            if (!matchPattern(sub, as.items.get(e + k), literals, b2, eb2)) return false;
            for (String v : vars) eb.get(v).add(b2.containsKey(v) ? b2.get(v) : new SExpr.Nil(arg.line()));
        }
        for (int i = 0; i < after; i++)
            if (!matchPattern(ps.items.get(e + 2 + i), as.items.get(e + count + i), literals, b, eb)) return false;
        if (ps.tail instanceof SExpr.Nil) return as.tail instanceof SExpr.Nil;
        return matchPattern(ps.tail, as.tail, literals, b, eb);
    }

    private void patternVars(SExpr p, List<String> literals, List<String> out) {
        if (p instanceof SExpr.Sym s) {
            String n = s.name();
            if (!n.equals("_") && !n.equals("...") && !literals.contains(n)) out.add(n);
        } else if (p instanceof SExpr.Pair pr) {
            patternVars(pr.car(), literals, out);
            patternVars(pr.cdr(), literals, out);
        }
    }

    private void ellipsisVars(SExpr t, Map<String, SExpr> b, Map<String, List<SExpr>> eb, List<String> out) {
        if (t instanceof SExpr.Sym s) {
            if (eb.containsKey(s.name()) && !b.containsKey(s.name()) && !out.contains(s.name())) out.add(s.name());
        } else if (t instanceof SExpr.Pair p) {
            ellipsisVars(p.car(), b, eb, out);
            ellipsisVars(p.cdr(), b, eb, out);
        }
    }

    private SExpr expandTemplate(SExpr tmpl, Map<String, SExpr> b,
                                 Map<String, List<SExpr>> eb, int line) throws SchemeException {
        if (tmpl instanceof SExpr.Sym s) return b.getOrDefault(s.name(), tmpl);
        if (!(tmpl instanceof SExpr.Pair)) return tmpl;
        Spine ts = spine(tmpl);
        List<SExpr> out = new ArrayList<>();
        for (int i = 0; i < ts.items.size(); i++) {
            SExpr item = ts.items.get(i);
            if (i + 1 < ts.items.size() && ts.items.get(i + 1).isSym("...")) {
                List<String> vars = new ArrayList<>();
                ellipsisVars(item, b, eb, vars);
                int n = Integer.MAX_VALUE;
                for (String v : vars) n = Math.min(n, eb.get(v).size());
                if (vars.isEmpty()) n = 0;
                for (int k = 0; k < n; k++) {
                    Map<String, SExpr> b2 = new LinkedHashMap<>(b);
                    for (String v : vars) b2.put(v, eb.get(v).get(k));
                    out.add(expandTemplate(item, b2, eb, line));
                }
                i++;
                continue;
            }
            out.add(expandTemplate(item, b, eb, line));
        }
        SExpr tail = ts.tail instanceof SExpr.Nil ? new SExpr.Nil(line) : expandTemplate(ts.tail, b, eb, line);
        return chain(out, 0, tail, line);
    }


    private void collectBinders(SExpr t, java.util.Set<String> out) {
        if (!(t instanceof SExpr.Pair)) return;
        Spine s = spine(t);
        if (!s.items.isEmpty() && s.items.get(0) instanceof SExpr.Sym h) {
            String k = h.name();
            if (k.equals("let") || k.equals("let*") || k.equals("letrec") || k.equals("letrec*") || k.equals("do")) {
                int bi = 1;
                if (k.equals("let") && s.items.size() > 1 && s.items.get(1) instanceof SExpr.Sym nm) { out.add(nm.name()); bi = 2; }
                if (s.items.size() > bi)
                    for (SExpr b : spine(s.items.get(bi)).items) {
                        SExpr v = b instanceof SExpr.Pair bp ? bp.car() : b;
                        if (v instanceof SExpr.Sym vs) out.add(vs.name());
                    }
            } else if (k.equals("lambda") && s.items.size() > 1) {
                symsIn(s.items.get(1), out);
            }
        }
        for (SExpr x : s.items) collectBinders(x, out);
    }

    private void symsIn(SExpr e, java.util.Set<String> out) {
        if (e instanceof SExpr.Sym s) out.add(s.name());
        else if (e instanceof SExpr.Pair p) { symsIn(p.car(), out); symsIn(p.cdr(), out); }
    }

    private List<SExpr> toList(SExpr e) {
        if (e instanceof SExpr.Pair p) return p.toList();
        if (e instanceof SExpr.Nil) return List.of();
        return List.of(e);
    }

    private String sym(SExpr e) {
        if (e instanceof SExpr.Sym s) return s.name();
        return e.toString();
    }
}
