package processing.mode.scheme;

import java.util.*;

/**
 * Renames bindings that collide with names the Transpiler special-cases, so
 * (let ((rest f)) (rest x)) calls the user's function instead of the builtin.
 * Lexically scoped: only uses of that binding are renamed.
 */
final class AlphaRename {
    private final Set<String> special;
    private int counter = 0;

    AlphaRename(Set<String> special) { this.special = special; }

    List<SExpr> run(List<SExpr> forms) {
        Map<String, String> env = new HashMap<>();
        for (SExpr f : forms) { String d = definedName(f); if (d != null) bind(env, d); }
        List<SExpr> out = new ArrayList<>();
        for (SExpr f : forms) out.add(walk(f, env));
        return out;
    }

    private SExpr walk(SExpr e, Map<String, String> env) {
        if (e instanceof SExpr.Sym s) {
            String r = env.get(s.name());
            return r == null ? e : new SExpr.Sym(r, s.line());
        }
        if (!(e instanceof SExpr.Pair p)) return e;
        int ln = p.line();
        String h = (p.car() instanceof SExpr.Sym hs && !env.containsKey(hs.name())) ? hs.name() : null;
        if (h == null) return walkAll(e, env);
        switch (h) {
            case "quote": return e;
            case "quasiquote": return mk(p.car(), mk(walkQQ(nth(e, 1), env, 1), nil(ln), ln), ln);
            case "lambda": {
                Map<String, String> in = new HashMap<>(env);
                SExpr params = bindParams(nth(e, 1), in);
                return mk(p.car(), mk(params, walkBody(tail(e, 2), in), ln), ln);
            }
            case "define": {
                SExpr target = nth(e, 1);
                if (target instanceof SExpr.Pair tp) {
                    Map<String, String> in = new HashMap<>(env);
                    SExpr params = bindParams(tp.cdr(), in);
                    return mk(p.car(), mk(mk(walk(tp.car(), env), params, ln), walkBody(tail(e, 2), in), ln), ln);
                }
                return mk(p.car(), mk(walk(target, env), walkAll(tail(e, 2), env), ln), ln);
            }
            case "let": {
                SExpr second = nth(e, 1);
                Map<String, String> in = new HashMap<>(env);
                if (second instanceof SExpr.Sym loop) {
                    SExpr loopName = bindOne(loop, in);
                    SExpr nb = rebind(nth(e, 2), env, in, "let");
                    return mk(p.car(), mk(loopName, mk(nb, walkBody(tail(e, 3), in), ln), ln), ln);
                }
                SExpr nb = rebind(second, env, in, "let");
                return mk(p.car(), mk(nb, walkBody(tail(e, 2), in), ln), ln);
            }
            case "let*": case "letrec": case "letrec*": {
                Map<String, String> in = new HashMap<>(env);
                SExpr nb = rebind(nth(e, 1), env, in, h);
                return mk(p.car(), mk(nb, walkBody(tail(e, 2), in), ln), ln);
            }
            case "do": {
                Map<String, String> in = new HashMap<>(env);
                for (SExpr spec : items(nth(e, 1))) if (spec instanceof SExpr.Pair sp) bind(in, name(sp.car()));
                List<SExpr> specs = new ArrayList<>();
                for (SExpr spec : items(nth(e, 1))) {
                    List<SExpr> v = items(spec);
                    List<SExpr> nv = new ArrayList<>();
                    nv.add(walk(v.get(0), in));
                    if (v.size() > 1) nv.add(walk(v.get(1), env));
                    if (v.size() > 2) nv.add(walk(v.get(2), in));
                    specs.add(list(nv, spec.line()));
                }
                return mk(p.car(), mk(list(specs, ln), walkAll(tail(e, 2), in), ln), ln);
            }
            case "guard": {
                SExpr spec = nth(e, 1);
                Map<String, String> in = new HashMap<>(env);
                SExpr var = bindOne((SExpr.Sym) ((SExpr.Pair) spec).car(), in);
                SExpr nspec = mk(var, walkAll(((SExpr.Pair) spec).cdr(), in), spec.line());
                return mk(p.car(), mk(nspec, walkAll(tail(e, 2), env), ln), ln);
            }
            case "case": {
                List<SExpr> clauses = new ArrayList<>();
                for (SExpr c : items(tail(e, 2))) {
                    SExpr.Pair cp = (SExpr.Pair) c;
                    clauses.add(mk(cp.car(), walkAll(cp.cdr(), env), c.line()));
                }
                return mk(p.car(), mk(walk(nth(e, 1), env), list(clauses, ln), ln), ln);
            }
            default: return walkAll(e, env);
        }
    }

    /** let: inits see the outer scope. let*: each init sees earlier names. letrec: all names. */
    private SExpr rebind(SExpr bindings, Map<String, String> outer, Map<String, String> in, String kind) {
        List<SExpr> bs = items(bindings);
        if (kind.startsWith("letrec")) for (SExpr b : bs) bind(in, name(((SExpr.Pair) b).car()));
        List<SExpr> out = new ArrayList<>();
        for (SExpr b : bs) {
            SExpr.Pair bp = (SExpr.Pair) b;
            Map<String, String> initEnv = kind.equals("let") ? outer : in;
            SExpr init = walkAll(bp.cdr(), initEnv);
            SExpr nm = kind.startsWith("letrec") ? walk(bp.car(), in) : bindOne((SExpr.Sym) bp.car(), in);
            out.add(mk(nm, init, b.line()));
        }
        return list(out, bindings.line());
    }

    private SExpr walkBody(SExpr body, Map<String, String> in) {
        for (SExpr f : items(body)) { String d = definedName(f); if (d != null) bind(in, d); }
        return walkAll(body, in);
    }

    private SExpr walkAll(SExpr e, Map<String, String> env) {
        if (!(e instanceof SExpr.Pair p)) return walk(e, env);
        return mk(walk(p.car(), env), walkAll(p.cdr(), env), p.line());
    }

    private SExpr walkQQ(SExpr e, Map<String, String> env, int depth) {
        if (!(e instanceof SExpr.Pair p)) return e;
        if (p.car() instanceof SExpr.Sym s) {
            if (s.name().equals("unquote") || s.name().equals("unquote-splicing")) {
                SExpr arg = nth(e, 1);
                SExpr na = depth == 1 ? walk(arg, env) : walkQQ(arg, env, depth - 1);
                return mk(p.car(), mk(na, nil(p.line()), p.line()), p.line());
            }
            if (s.name().equals("quasiquote"))
                return mk(p.car(), mk(walkQQ(nth(e, 1), env, depth + 1), nil(p.line()), p.line()), p.line());
        }
        return mk(walkQQ(p.car(), env, depth), walkQQ(p.cdr(), env, depth), p.line());
    }

    private SExpr bindParams(SExpr params, Map<String, String> env) {
        if (params instanceof SExpr.Sym s) return bindOne(s, env);
        if (params instanceof SExpr.Pair p) return mk(bindParams(p.car(), env), bindParams(p.cdr(), env), p.line());
        return params;
    }

    private SExpr bindOne(SExpr.Sym s, Map<String, String> env) {
        bind(env, s.name());
        String r = env.get(s.name());
        return r == null ? s : new SExpr.Sym(r, s.line());
    }

    private void bind(Map<String, String> env, String name) {
        if (name != null && special.contains(name)) env.put(name, fresh(name));
    }

    /** rest -> rest-v1, null? -> null-v1?, set! -> set-v1! (snake() turns - into _). */
    private String fresh(String name) {
        String tag = "-v" + (++counter);
        if (name.endsWith("?") || name.endsWith("!"))
            return name.substring(0, name.length() - 1) + tag + name.charAt(name.length() - 1);
        return name + tag;
    }

    private static String definedName(SExpr f) {
        if (!(f instanceof SExpr.Pair p) || !(p.car() instanceof SExpr.Sym s) || !s.name().equals("define")) return null;
        SExpr t = nth(f, 1);
        if (t instanceof SExpr.Pair tp) t = tp.car();
        return name(t);
    }

    private static String name(SExpr e) { return e instanceof SExpr.Sym s ? s.name() : null; }
    private static SExpr nth(SExpr e, int i) { return ((SExpr.Pair) tail(e, i)).car(); }
    private static SExpr tail(SExpr e, int i) { for (int k = 0; k < i; k++) e = ((SExpr.Pair) e).cdr(); return e; }
    private static SExpr mk(SExpr a, SExpr d, int line) { return new SExpr.Pair(a, d, line); }
    private static SExpr nil(int line) { return new SExpr.Nil(line); }

    private static List<SExpr> items(SExpr e) {
        List<SExpr> out = new ArrayList<>();
        while (e instanceof SExpr.Pair p) { out.add(p.car()); e = p.cdr(); }
        return out;
    }

    private static SExpr list(List<SExpr> xs, int line) {
        SExpr r = new SExpr.Nil(line);
        for (int i = xs.size() - 1; i >= 0; i--) r = new SExpr.Pair(xs.get(i), r, line);
        return r;
    }
}
