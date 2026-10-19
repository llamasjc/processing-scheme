package processing.mode.scheme;

import java.util.*;

/**
 * Transpiler — Scheme AST → Python mewnala script.
 *
 * No Kawa. No JNA. Pure Java 17.
 * Pipeline: .scm source → Parser → SExpr tree → this class → .py file → python3 + mewnala
 *
 * mewnala facts (0.0.8):
 *   - Colors 0-255 integers
 *   - run() discovers setup/draw by name in caller globals
 *   - mouse_x, width, etc. are dynamic module attributes (not functions)
 *   - Full API in mewnala/__init__.pyi
 */
public class Transpiler {
    private java.util.Map<SExpr, String> hoisted = new java.util.IdentityHashMap<>();
    private int lamN = 0;
    private int condN = 0;
    private int letN = 0;
    private String tcoName = null;
    private List<String> tcoParams = null;
    private java.util.Map<SExpr, String> hoistedLets = new java.util.IdentityHashMap<>();
    private java.util.Set<SExpr> tailLets = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    // mewnala functions: scheme-name → python_name (simple snake_case mapping)
    private static final Set<String> MEWNALA_FNS = Set.of(
        "ellipse","circle","rect","square","line","point","triangle","quad","arc",
        "bezier","curve","begin-shape","end-shape","begin-contour","end-contour",
        "vertex","bezier-vertex","curve-vertex","quadratic-vertex",
        "fill","no-fill","stroke","no-stroke","stroke-weight","stroke-cap","stroke-join",
        "tint","no-tint","background",
        "push-matrix","pop-matrix","reset-matrix","translate","rotate",
        "rotate-x","rotate-y","rotate-z","scale","shear-x","shear-y",
        "color-mode","color-hex","hsla","hsva","hwba","lab","lch","oklab","oklch",
        "srgb","linear-rgb","xyz",
        "text","text-size","text-align","text-leading","text-style","text-weight",
        "text-font","text-width","text-ascent","text-descent","text-bounds","text-wrap",
        "text-feature","no-text-feature","text-variation","text-to-paths","text-to-contours",
        "text-to-model","text-to-points","text-glyph-colors","text-glyph-rects",
        "text-lines","text-line-count",
        "image","image-mode","load-image","create-image",
        "size","full-screen","window-title","window-resize","window-move",
        "window-resizable","window-decorated","window-opacity","window-visible",
        "window-maximize","window-iconify","window-restore","window-always-on-top",
        "window-center-on","window-position-on",
        "mode-2d","mode-3d",
        "sphere","box","cylinder","cone","torus",
        "plane","capsule","draw-tetrahedron","draw-conical-frustum","draw-geometry",
        "camera-position","camera-look-at","camera-center","camera-distance","camera-speed",
        "camera-min-distance","camera-max-distance","camera-reset",
        "orbit-camera","pan-camera","free-camera","disable-camera",
        "directional-light","point-light","spot-light",
        "emissive","metallic","roughness","unlit","use-material","blend-mode",
        "ellipse-mode","rect-mode","no-loop","redraw","flush",
        "key-is-down","key-just-pressed","pixel-density","monitors","primary-monitor",
        "display-density","load-gltf","particles","kernel-noise","kernel-transform",
        "midi-connect","midi-disconnect","midi-list-ports","midi-note-on","midi-note-off",
        "midi-play-notes","midi-refresh-ports","create-color","vec2","vec3","vec4","quat"
    );

    // Dynamic attributes: accessed as plain variables in Python (no call)
    private static final Map<String,String> DYNATTR = new LinkedHashMap<>();
    static {
        DYNATTR.put("mouse-x","mouse_x"); DYNATTR.put("mouse-y","mouse_y");
        DYNATTR.put("pmouse-x","pmouse_x"); DYNATTR.put("pmouse-y","pmouse_y");
        DYNATTR.put("mouse-is-pressed","mouse_is_pressed");
        DYNATTR.put("mouse-button","mouse_button"); DYNATTR.put("mouse-wheel","mouse_wheel");
        DYNATTR.put("moved-x","moved_x"); DYNATTR.put("moved-y","moved_y");
        DYNATTR.put("key","key"); DYNATTR.put("key-code","key_code");
        DYNATTR.put("key-is-pressed","key_is_pressed");
        DYNATTR.put("width","width"); DYNATTR.put("height","height");
        DYNATTR.put("focused","focused"); DYNATTR.put("pixel-width","pixel_width");
        DYNATTR.put("pixel-height","pixel_height"); DYNATTR.put("frame-count","frame_count");
        DYNATTR.put("delta-time","delta_time"); DYNATTR.put("elapsed-time","elapsed_time");
        DYNATTR.put("display-width","display_width"); DYNATTR.put("display-height","display_height");
        DYNATTR.put("window-x","window_x"); DYNATTR.put("window-y","window_y");
    }

    // Constants
    private static final Map<String,String> CONST = new LinkedHashMap<>();
    static {
        CONST.put("PI","PI"); CONST.put("TWO-PI","TWO_PI"); CONST.put("HALF-PI","HALF_PI");
        CONST.put("QUARTER-PI","QUARTER_PI"); CONST.put("TAU","TAU");
        CONST.put("DEG-TO-RAD","DEG_TO_RAD"); CONST.put("RAD-TO-DEG","RAD_TO_DEG");
        CONST.put("CENTER","CENTER"); CONST.put("CORNER","CORNER"); CONST.put("CORNERS","CORNERS");
        CONST.put("RADIUS","RADIUS"); CONST.put("LEFT","LEFT"); CONST.put("RIGHT","RIGHT");
        CONST.put("OPEN","OPEN"); CONST.put("CHORD","CHORD"); CONST.put("PIE","PIE");
        CONST.put("CLOSE","CLOSE");
        CONST.put("POINTS","POINTS"); CONST.put("LINES","LINES");
        CONST.put("TRIANGLES","TRIANGLES"); CONST.put("TRIANGLE-FAN","TRIANGLE_FAN");
        CONST.put("TRIANGLE-STRIP","TRIANGLE_STRIP"); CONST.put("QUADS","QUADS");
        CONST.put("QUAD-STRIP","QUAD_STRIP"); CONST.put("POLYGON","POLYGON");
        CONST.put("ROUND","ROUND"); CONST.put("SQUARE","SQUARE"); CONST.put("PROJECT","PROJECT");
        CONST.put("MITER","MITER"); CONST.put("BEVEL","BEVEL");
        CONST.put("HSL","HSL"); CONST.put("HSV","HSV"); CONST.put("HWB","HWB");
        CONST.put("LAB","LAB"); CONST.put("LCH","LCH"); CONST.put("OKLAB","OKLAB");
        CONST.put("OKLCH","OKLCH"); CONST.put("SRGB","SRGB"); CONST.put("XYZ","XYZ");
        CONST.put("LINEAR","LINEAR");
        CONST.put("ENTER","ENTER"); CONST.put("BACKSPACE","BACKSPACE"); CONST.put("TAB","TAB");
        CONST.put("DELETE","DELETE"); CONST.put("ESCAPE","ESCAPE"); CONST.put("HOME","HOME");
        CONST.put("END","END"); CONST.put("INSERT","INSERT"); CONST.put("UP","UP");
        CONST.put("DOWN","DOWN"); CONST.put("LEFT-ARROW","LEFT_ARROW");
        CONST.put("RIGHT-ARROW","RIGHT_ARROW"); CONST.put("PAGE-UP","PAGE_UP");
        CONST.put("PAGE-DOWN","PAGE_DOWN"); CONST.put("SHIFT","SHIFT");
        CONST.put("CONTROL","CONTROL"); CONST.put("ALT","ALT"); CONST.put("SUPER","SUPER");
        for (int i = 1; i <= 12; i++) CONST.put("F"+i, "F"+i);
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final StringBuilder out = new StringBuilder();
    private int lastScmLine = -1;
    private boolean inFunction = false;
    private final Set<String> globalVars = new LinkedHashSet<>();
    private final Set<String> localVars  = new LinkedHashSet<>();

    // ── Entry point ───────────────────────────────────────────────────────────

    public String transpile(List<SExpr> forms, String sketchName) throws SchemeException {
        // collect top-level define names
        for (SExpr f : forms)
            if (f.isForm("define")) { String n = f.defineName(); if (n != null) globalVars.add(snake(n)); }

        emit("# Generated by SchemeMode — " + sketchName + "\n");
        emit("from mewnala import *\n");
        emit("import sys; sys.setrecursionlimit(100000)\n");
        emit("import operator as _op\n");
        emit("_add = _op.add; _sub = _op.sub; _mul = _op.mul\n");
        emit("_div = _op.truediv; _mod = _op.mod; _pow = _op.pow\n");
        emit("_lt = _op.lt; _gt = _op.gt; _le = _op.le; _ge = _op.ge\n");
        emit("_eq = _op.eq; _ne = _op.ne\n");
        emit("import functools as _ft\n");
        emit("_add = lambda *a: sum(a) if len(a)>2 else _op.add(*a)\n");
        emit("_mul = lambda *a: _ft.reduce(_op.mul, a) if len(a)>2 else _op.mul(*a)\n");
        emit("_sub = lambda *a: _ft.reduce(_op.sub, a) if len(a)>2 else _op.sub(*a)\n");
        emit("_min = lambda *a: min(a)\n");
        emit("_max = lambda *a: max(a)\n");
        emit("import functools as _ft\n");
        emit("def _apply_op(op, args): return _ft.reduce(op, args)\n");
        emit("def even_p(n): return n % 2 == 0\n");
        emit("def odd_p(n): return n % 2 != 0\n");
        emit("def zero_p(n): return n == 0\n");
        emit("def positive_p(n): return n > 0\n");
        emit("def negative_p(n): return n < 0\n");
        emit("def number_p(n): return isinstance(n, (int, float))\n");
        emit("def string_p(n): return isinstance(n, str) and not isinstance(n, _Sym)\n");
        emit("def symbol_p(n): return isinstance(n, _Sym)\n");
        emit("def procedure_p(n): return callable(n)\n");
        emit("def char_gt_eq__p(a, b): return a >= b\n");
        emit("def char_lt_eq__p(a, b): return a <= b\n");
        emit("def char_gt__p(a, b): return a > b\n");
        emit("def char_lt__p(a, b): return a < b\n");
        emit("def char_eq__p(a, b): return a == b\n");
        emit("def char_to_integer(c): return ord(c)\n");
        emit("def integer_to_char(n): return chr(n)\n");
        emit("def char_alphabetic_p(c): return c.isalpha()\n");
        emit("def char_numeric_p(c): return c.isdigit()\n");
        emit("def char_whitespace_p(c): return c.isspace()\n");
        emit("def char_upper_case_p(c): return c.isupper()\n");
        emit("def char_lower_case_p(c): return c.islower()\n");
        emit("def list_to_string(lst): return ''.join(lst)\n");
        emit("def string_to_list(s): return list(s)\n\n");
        emit("from mewnala.math import *\n");
        emit("import math as _math\n\n");
        emit("def _err(*a): raise Exception(' '.join(str(x) for x in a))\n\n");
        emit("import random as _random\n");
        emit("def random(*args):\n");
        emit("    if len(args)==1: return _random.uniform(0, args[0])\n");
        emit("    return _random.uniform(args[0], args[1])\n\n");
        emit("def random_gaussian(): return _random.gauss(0, 1)\n\n");
        emit("def noise(*args): return _random.random()\n\n");
        emit("def text(*args): pass  # not yet in mewnala\n");
        emit("def text_size(*args): pass  # not yet in mewnala\n");
        emit("def text_align(*args): pass  # not yet in mewnala\n");
        emit("def text_leading(*args): pass  # not yet in mewnala\n");
        emit("def text_width(*args): return 0  # not yet in mewnala\n");
        emit("def text_ascent(*args): return 0  # not yet in mewnala\n");
        emit("def text_descent(*args): return 0  # not yet in mewnala\n\n");
        emit("# Scheme list primitives\n");
        emit("class _Pair:\n    __slots__ = ('car', 'cdr')\n    def __init__(s, a, d): s.car = a; s.cdr = d\n    def __getitem__(s, i): return (s.car, s.cdr)[i]\n    def __repr__(s): return f'({s.car!r} . {s.cdr!r})'\ndef car(p): return p[0]\n");
        emit("def cdr(p): return p.cdr if isinstance(p, _Pair) else list(p[1:])\n");
        emit("def cons(h, t): return [h] + list(t) if isinstance(t, _SEQ) else _Pair(h, t)\n");
        emit("def is_null(p): return p is None or (isinstance(p, _SEQ) and len(p) == 0)\n");
        emit("def is_pair(p): return isinstance(p, _Pair) or (isinstance(p, _SEQ) and len(p) > 0)\n");
        emit("def is_list(p): return isinstance(p, _SEQ)\n");
        emit("def append(*args): return [x for lst in args for x in (lst if isinstance(lst, _SEQ) else [lst])]\n");
        emit("def cadr(p): return p[1]\n");
        emit("def caddr(p): return p[2]\n");
        emit("def cadddr(p): return p[3]\n");
        emit("def caaar(p): return p[0][0][0]\n");
        emit("def caar(p): return p[0][0]\n");
        emit("def cadar(p): return p[0][1]\n");
        emit("def cddr(p): return p[2:]\n");
        emit("def cdar(p): return p[0][1:]\n");
        emit("def vector(*args): return list(args)\n");
        emit("def vector_ref(v, i): return v[i]\n");
        emit("def vector_set_x(v, i, x): v[i] = x\n");
        emit("def vector_length(v): return len(v)\n");
        emit("def make_vector(n, *args): return [args[0] if args else 0]*n\n");
        emit("def string_length(s): return len(s)\n");
        emit("def string_ref(s, i): return s[i]\n");
        emit("def substring(s, a, b): return s[a:b]\n");
        emit("def string_append(*args): return ''.join(args)\n");
        emit("def number_to_string(n, radix=10): return str(n) if radix == 10 else format(n, {2:'b', 8:'o', 16:'x'}[radix])\n");
        emit("def string_to_number(s, radix=10):\n"
             + "    try: return int(s, radix)\n"
             + "    except (ValueError, TypeError): pass\n"
             + "    if radix == 10:\n"
             + "        try: return float(s)\n"
             + "        except ValueError: pass\n"
             + "    return False\n\n");
        emit("def _quot(a, b):\n"
             + "    q = abs(a) // abs(b)\n"
             + "    return q if (a < 0) == (b < 0) else -q\n"
             + "def _rem(a, b): return a - b * _quot(a, b)\n"
             + "def _eqv(a, b):\n"
             + "    if a is b: return True\n"
             + "    if isinstance(a, _SEQ) and isinstance(b, _SEQ): return len(a) == 0 and len(b) == 0\n"
             + "    if type(a) is not type(b): return False\n"
             + "    if isinstance(a, (str, int, float, bool)): return a == b\n"
             + "    return False\n"
             + "def _member(x, lst, test=lambda a, b: a == b):\n"
             + "    for i, y in enumerate(lst):\n"
             + "        if test(x, y): return lst[i:]\n"
             + "    return False\n\n");

        emit("def string_to_symbol(s): return _Sym(s)\n"
             + "def boolean_eq__p(a, *r): return all(x is a for x in r)\n"
             + "def vector_fill(v, x, start=0, end=None):\n"
             + "    for i in range(start, len(v) if end is None else end): v[i] = x\n\n");
        emit("def _for_each(f, *ls):\n"
             + "    for a in zip(*ls): f(*a)\n"
             + "def string_index(s, c):\n"
             + "    i = s.find(c)\n"
             + "    return i if i >= 0 else False\n"
             + "def vector_to_list(v, start=0, end=None): return list(v[start:end])\n"
             + "def list_to_vector(l): return list(l)\n\n");
        emit("def _show(x, w=False):\n"
             + "    if x is True: return '#t'\n"
             + "    if x is False: return '#f'\n"
             + "    if isinstance(x, _Sym): return str.__str__(x)\n"
             + "    if isinstance(x, str):\n"
             + "        return '\"' + x.replace('\\\\', '\\\\\\\\').replace('\"', '\\\\\"') + '\"' if w else x\n"
             + "    if isinstance(x, _SEQ + (_Pair,)):\n"
             + "        items = []\n"
             + "        while isinstance(x, _Pair): items.append(_show(x.car, w)); x = x.cdr\n"
             + "        if isinstance(x, _SEQ): return '(' + ' '.join(items + [_show(y, w) for y in x]) + ')'\n"
             + "        return '(' + ' '.join(items) + ' . ' + _show(x, w) + ')'\n"
             + "    return str(x)\n"
             + "def _display(x, port=None): print(_show(x), end='')\n"
             + "def write(x, port=None): print(_show(x, True), end='')\n"
             + "def _print(*a): print(*(_show(x) for x in a), end='')\n\n");
        emit("def exact_p(n): return isinstance(n, int) and not isinstance(n, bool)\n"
             + "def inexact_p(n): return isinstance(n, float)\n\n");
        emit("class _Raised(Exception):\n"
             + "    def __init__(s, obj): Exception.__init__(s, obj); s.obj = obj\n"
             + "def _raise(obj): raise _Raised(obj)\n"
             + "def _unwrap(ex): return ex.obj if isinstance(ex, _Raised) else ex\n\n");
        emit("class _Sym(str):\n"
             + "    _tab = {}\n"
             + "    def __new__(c, n):\n"
             + "        s = c._tab.get(n)\n"
             + "        if s is None: s = c._tab[n] = str.__new__(c, n)\n"
             + "        return s\n"
             + "    def __eq__(s, o): return s is o or (isinstance(o, _Sym) and str.__eq__(s, o))\n"
             + "    def __ne__(s, o): return not s.__eq__(o)\n"
             + "    __hash__ = str.__hash__\n"
             + "    def __repr__(s): return str.__str__(s)\n\n");
        emit("import sys as _sys, traceback as _tb, functools as _ft\n"
             + "_scm_src = {}\n"
             + "def _scm_line(fname, lineno):\n"
             + "    lines = _scm_src.get(fname)\n"
             + "    if lines is None:\n"
             + "        try:\n"
             + "            with open(fname) as fh: lines = fh.read().splitlines()\n"
             + "        except Exception: lines = []\n"
             + "        _scm_src[fname] = lines\n"
             + "    for i in range(min(lineno, len(lines)) - 1, -1, -1):\n"
             + "        t = lines[i].strip()\n"
             + "        if t.startswith('# scm:'):\n"
             + "            try: return int(t[6:])\n"
             + "            except ValueError: return None\n"
             + "    return None\n"
             + "def _scm_where(tb):\n"
             + "    for f in reversed(_tb.extract_tb(tb)):\n"
             + "        n = _scm_line(f.filename, f.lineno)\n"
             + "        if n is not None: return n\n"
             + "    return None\n"
             + "def _scm_report(e):\n"
             + "    if getattr(e, '_scm_reported', False): return\n"
             + "    try: e._scm_reported = True\n"
             + "    except Exception: pass\n"
             + "    _sys.stdout.flush()\n"
             + "    n = _scm_where(e.__traceback__)\n"
             + "    what = _show(e.obj, True) if isinstance(e, _Raised) else str(e) if isinstance(e, _SchemeError) else type(e).__name__ + ': ' + str(e)\n"
             + "    print(('Error on line %d: ' % n if n else 'Error: ') + what, file=_sys.stderr)\n"
             + "def _scm_hook(t, v, tb):\n"
             + "    _scm_report(v)\n"
             + "    import os as _os\n"
             + "    if _os.environ.get('SCHEME_TRACEBACK'): _sys.__excepthook__(t, v, tb)\n"
             + "_sys.excepthook = _scm_hook\n"
             + "def _scm_guard(f):\n"
             + "    @_ft.wraps(f)\n"
             + "    def w(*a, **k):\n"
             + "        try: return f(*a, **k)\n"
             + "        except Exception as e:\n"
             + "            _scm_report(e)\n"
             + "            raise\n"
             + "    return w\n"
             + "def _scm_wrap(g):\n"
             + "    for k, f in list(g.items()):\n"
             + "        if callable(f) and not isinstance(f, type) and getattr(f, '__module__', None) == g.get('__name__') \\\n"
             + "           and (k in ('setup', 'draw', 'settings') or k.startswith(('mouse_', 'key_', 'window_'))):\n"
             + "            g[k] = _scm_guard(f)\n"
             + "\n");
        emit("class _SchemeError(Exception): pass\n"
             + "def _bad(fn, want, got): raise _SchemeError(fn + ': expected ' + want + ', got ' + _show(got, True))\n"
             + "def car(p):\n"
             + "    if isinstance(p, _Pair): return p.car\n"
             + "    if isinstance(p, _SEQ) and len(p): return p[0]\n"
             + "    _bad('car', 'a pair', p)\n"
             + "def cdr(p):\n"
             + "    if isinstance(p, _Pair): return p.cdr\n"
             + "    if isinstance(p, _SEQ) and len(p): return _tail(p, 1)\n"
             + "    pass\n"
             + "    _bad('cdr', 'a pair', p)\n"
             + "def cadr(p):\n"
             + "    if isinstance(p, _SEQ) and len(p) > 1: return p[1]\n"
             + "    return car(cdr(p))\n"
             + "def vector_ref(v, i):\n"
             + "    if not isinstance(v, _SEQ): _bad('vector-ref', 'a vector', v)\n"
             + "    if not (isinstance(i, int) and 0 <= i < len(v)):\n"
             + "        raise _SchemeError('vector-ref: index ' + _show(i) + ' out of range for a vector of length ' + str(len(v)))\n"
             + "    return v[i]\n"
             + "\n");
        emit("class _Tail:\n"
             + "    # cdr of a list: shares the list, remembers a start offset. O(1) instead of a copy.\n"
             + "    __slots__ = ('b', 's')\n"
             + "    def __init__(t, b, s): t.b = b; t.s = s\n"
             + "    def __len__(t): return len(t.b) - t.s\n"
             + "    def __bool__(t): return len(t.b) > t.s\n"
             + "    def __iter__(t):\n"
             + "        b = t.b\n"
             + "        for i in range(t.s, len(b)): yield b[i]\n"
             + "    def __reversed__(t):\n"
             + "        b = t.b\n"
             + "        for i in range(len(b) - 1, t.s - 1, -1): yield b[i]\n"
             + "    def __getitem__(t, i):\n"
             + "        n = len(t.b) - t.s\n"
             + "        if isinstance(i, slice):\n"
             + "            a, e, st = i.indices(n)\n"
             + "            if st == 1 and e == n: return _Tail(t.b, t.s + a)\n"
             + "            return list(t)[i]\n"
             + "        if i < 0: i += n\n"
             + "        if not 0 <= i < n: raise IndexError('list index out of range')\n"
             + "        return t.b[t.s + i]\n"
             + "    def __setitem__(t, i, v):\n"
             + "        n = len(t.b) - t.s\n"
             + "        if i < 0: i += n\n"
             + "        if not 0 <= i < n: raise IndexError('list assignment index out of range')\n"
             + "        t.b[t.s + i] = v\n"
             + "    def __eq__(t, o):\n"
             + "        if isinstance(o, (list, tuple, _Tail)): return len(t) == len(o) and all(x == y for x, y in zip(t, o))\n"
             + "        return NotImplemented\n"
             + "    def __ne__(t, o):\n"
             + "        r = t.__eq__(o)\n"
             + "        return r if r is NotImplemented else not r\n"
             + "    __hash__ = None\n"
             + "    def __add__(t, o): return list(t) + list(o)\n"
             + "    def __radd__(t, o): return list(o) + list(t)\n"
             + "    def __contains__(t, x): return any(y == x for y in t)\n"
             + "    def __repr__(t): return repr(list(t))\n"
             + "_SEQ = (list, tuple, _Tail)\n"
             + "def _tail(p, k): return _Tail(p.b, p.s + k) if isinstance(p, _Tail) else _Tail(p, k)\n"
             + "\n");
        emit("def caar(p): return car(car(p))\n"
             + "def cdar(p): return cdr(car(p))\n"
             + "def cddr(p): return cdr(cdr(p))\n"
             + "def caddr(p): return car(cdr(cdr(p)))\n"
             + "def cdddr(p): return cdr(cdr(cdr(p)))\n"
             + "def cadddr(p): return car(cdr(cdr(cdr(p))))\n"
             + "def cadar(p): return car(cdr(car(p)))\n"
             + "def caaar(p): return car(car(car(p)))\n"
             + "def string_ref(s, i):\n"
             + "    if not isinstance(s, str): _bad('string-ref', 'a string', s)\n"
             + "    if not (isinstance(i, int) and 0 <= i < len(s)):\n"
             + "        raise _SchemeError('string-ref: index ' + _show(i) + ' out of range for a string of length ' + str(len(s)))\n"
             + "    return s[i]\n"
             + "\n");
        emit("_add = lambda *a: _ft.reduce(_op.add, a, 0)\n"
             + "_mul = lambda *a: _ft.reduce(_op.mul, a, 1)\n"
             + "def char_upcase(c): return c.upper()\n"
             + "def char_downcase(c): return c.lower()\n"
             + "def set_car(p, v):\n"
             + "    if isinstance(p, _Pair): p.car = v\n"
             + "    elif isinstance(p, _SEQ) and len(p): p[0] = v\n"
             + "    else: _bad('set-car!', 'a pair', p)\n"
             + "def set_cdr(p, v):\n"
             + "    if isinstance(p, _Pair): p.cdr = v\n"
             + "    elif isinstance(p, list) and p: p[1:] = list(v)\n"
             + "    elif isinstance(p, _Tail) and len(p): p.b[p.s + 1:] = list(v)\n"
             + "    else: _bad('set-cdr!', 'a pair', p)\n"
             + "def vector_map(f, *vs): return [f(*a) for a in zip(*vs)]\n"
             + "def vector_for_each(f, *vs):\n"
             + "    for a in zip(*vs): f(*a)\n"
             + "def values(*a): return a[0] if len(a) == 1 else a\n"
             + "def call_with_values(producer, consumer):\n"
             + "    r = producer()\n"
             + "    return consumer(*r) if isinstance(r, tuple) else consumer(r)\n"
             + "\n");
        emit("def _div(a, *r):\n"
             + "    if not r: a, r = 1, (a,)\n"
             + "    for b in r:\n"
             + "        if type(a) is int and type(b) is int and b != 0 and a % b == 0: a = a // b\n"
             + "        else: a = a / b\n"
             + "    return a\n"
             + "def _chain(op): return lambda *a: all(op(x, y) for x, y in zip(a, a[1:]))\n"
             + "_lt = _chain(_op.lt); _gt = _chain(_op.gt); _le = _chain(_op.le); _ge = _chain(_op.ge); _eq = _chain(_op.eq)\n"
             + "\n");
        emit("_mw_square = globals().get('square')\n"
             + "def square(*a):\n"
             + "    if len(a) == 1: return a[0] * a[0]\n"
             + "    return _mw_square(*a)\n"
             + "class _Escape(BaseException): pass\n"
             + "def call_with_current_continuation(f):\n"
             + "    e = _Escape()\n"
             + "    def k(*v):\n"
             + "        e.v = v[0] if len(v) == 1 else v\n"
             + "        raise e\n"
             + "    try: return f(k)\n"
             + "    except _Escape as x:\n"
             + "        if x is e: return x.v\n"
             + "        raise\n"
             + "def dynamic_wind(before, thunk, after):\n"
             + "    before()\n"
             + "    try: return thunk()\n"
             + "    finally: after()\n"
             + "class _ErrorObject(_SchemeError):\n"
             + "    def __init__(s, msg, irritants):\n"
             + "        _SchemeError.__init__(s, ' '.join([str(msg)] + [_show(x, True) for x in irritants]))\n"
             + "        s.message = msg; s.irritants = list(irritants)\n"
             + "def _err(msg='', *irritants): raise _ErrorObject(msg, irritants)\n"
             + "def error_object_p(x): return isinstance(x, _ErrorObject)\n"
             + "def error_object_message(e): return e.message\n"
             + "def error_object_irritants(e): return e.irritants\n"
             + "_handlers = []\n"
             + "def raise_continuable(obj):\n"
             + "    if not _handlers: raise _Raised(obj)\n"
             + "    h = _handlers.pop()\n"
             + "    try: return h(obj)\n"
             + "    finally: _handlers.append(h)\n"
             + "def with_exception_handler(handler, thunk):\n"
             + "    _handlers.append(handler)\n"
             + "    n = len(_handlers)\n"
             + "    try:\n"
             + "        return thunk()\n"
             + "    except Exception as ex:\n"
             + "        del _handlers[n - 1:]\n"
             + "        handler(_unwrap(ex))\n"
             + "        raise\n"
             + "    finally:\n"
             + "        del _handlers[n - 1:]\n"
             + "def fold(f, init, *ls):\n"
             + "    acc = init\n"
             + "    for a in zip(*ls): acc = f(*(a + (acc,)))\n"
             + "    return acc\n"
             + "def fold_left(f, init, *ls):\n"
             + "    acc = init\n"
             + "    for a in zip(*ls): acc = f(acc, *a)\n"
             + "    return acc\n"
             + "def fold_right(f, init, *ls):\n"
             + "    acc = init\n"
             + "    for a in reversed(list(zip(*ls))): acc = f(*(a + (acc,)))\n"
             + "    return acc\n"
             + "def reduce(f, ridentity, lst):\n"
             + "    lst = list(lst)\n"
             + "    if not lst: return ridentity\n"
             + "    acc = lst[0]\n"
             + "    for x in lst[1:]: acc = f(x, acc)\n"
             + "    return acc\n"
             + "def string_map(f, *ss): return ''.join(f(*a) for a in zip(*ss))\n"
             + "def string_for_each(f, *ss):\n"
             + "    for a in zip(*ss): f(*a)\n"
             + "def string_copy(s, start=0, end=None): return s[start:end]\n"
             + "def exact_integer_p(n): return type(n) is int\n"
             + "def digit_value(c): return int(c) if len(c) == 1 and c.isdigit() else False\n"
             + "def _sort_by(lst, less):\n"
             + "    return sorted(lst, key=_ft.cmp_to_key(lambda x, y: -1 if less(x, y) else (1 if less(y, x) else 0)))\n"
             + "def list_sort(a, b): return _sort_by(b, a) if callable(a) else _sort_by(a, b)\n"
             + "\n");
        emit("def _vals(r): return r if isinstance(r, tuple) else (r,)\n"
             + "_np = list(range(256)); _random.Random(0).shuffle(_np); _np = _np + _np\n"
             + "_noise_oct = 4; _noise_fall = 0.5\n"
             + "_noise_off = (0.31, 0.57, 0.83, 0.19, 0.71, 0.43, 0.97, 0.13)\n"
             + "def _fade(t): return t * t * t * (t * (t * 6 - 15) + 10)\n"
             + "def _grad(h, x, y, z):\n"
             + "    h &= 15\n"
             + "    u = x if h < 8 else y\n"
             + "    v = y if h < 4 else (x if h in (12, 14) else z)\n"
             + "    return (u if (h & 1) == 0 else -u) + (v if (h & 2) == 0 else -v)\n"
             + "def _perlin(x, y, z):\n"
             + "    fx, fy, fz = _math.floor(x), _math.floor(y), _math.floor(z)\n"
             + "    X, Y, Z = int(fx) & 255, int(fy) & 255, int(fz) & 255\n"
             + "    x, y, z = x - fx, y - fy, z - fz\n"
             + "    u, v, w = _fade(x), _fade(y), _fade(z)\n"
             + "    p = _np\n"
             + "    A = p[X] + Y; AA = p[A] + Z; AB = p[A + 1] + Z\n"
             + "    B = p[X + 1] + Y; BA = p[B] + Z; BB = p[B + 1] + Z\n"
             + "    def L(t, a, b): return a + t * (b - a)\n"
             + "    return L(w, L(v, L(u, _grad(p[AA], x, y, z), _grad(p[BA], x - 1, y, z)),\n"
             + "                     L(u, _grad(p[AB], x, y - 1, z), _grad(p[BB], x - 1, y - 1, z))),\n"
             + "                L(v, L(u, _grad(p[AA + 1], x, y, z - 1), _grad(p[BA + 1], x - 1, y, z - 1)),\n"
             + "                     L(u, _grad(p[AB + 1], x, y - 1, z - 1), _grad(p[BB + 1], x - 1, y - 1, z - 1))))\n"
             + "def noise(x, y=0.0, z=0.0):\n"
             + "    tot = 0.0; amp = 0.5; f = 1.0; norm = 0.0\n"
             + "    for k in range(_noise_oct):\n"
             + "        o = _noise_off[k % 8]\n"
             + "        tot += amp * (_perlin(x * f + o, y * f + o, z * f + o) * 0.5 + 0.5)\n"
             + "        norm += amp; amp *= _noise_fall; f *= 2\n"
             + "    return min(1.0, max(0.0, tot / norm))\n"
             + "def noise_detail(lod, falloff=None):\n"
             + "    global _noise_oct, _noise_fall\n"
             + "    _noise_oct = max(1, int(lod))\n"
             + "    if falloff is not None: _noise_fall = falloff\n"
             + "def noise_seed(seed):\n"
             + "    global _np\n"
             + "    q = list(range(256)); _random.Random(seed).shuffle(q); _np = q + q\n"
             + "class _OutPort:\n"
             + "    def __init__(s): s.buf = []\n"
             + "    def write(s, t): s.buf.append(t)\n"
             + "class _InPort:\n"
             + "    def __init__(s, t): s.t = t; s.i = 0\n"
             + "class _EofObject:\n"
             + "    def __repr__(s): return '#<eof>'\n"
             + "_eof = _EofObject()\n"
             + "def eof_object(): return _eof\n"
             + "def eof_object_p(x): return x is _eof\n"
             + "def open_output_string(): return _OutPort()\n"
             + "def get_output_string(p): return ''.join(p.buf)\n"
             + "def open_input_string(t): return _InPort(t)\n"
             + "def current_output_port(): return None\n"
             + "def _emit_to(s, port):\n"
             + "    if port is None: print(s, end='')\n"
             + "    else: port.write(s)\n"
             + "def _display(x, port=None): _emit_to(_show(x), port)\n"
             + "def write(x, port=None): _emit_to(_show(x, True), port)\n"
             + "def _newline(port=None): _emit_to(chr(10), port)\n"
             + "def write_string(s, port=None): _emit_to(s, port)\n"
             + "def write_char(c, port=None): _emit_to(c, port)\n"
             + "def flush_output_port(port=None): _sys.stdout.flush()\n"
             + "def read_char(p):\n"
             + "    if p.i >= len(p.t): return _eof\n"
             + "    c = p.t[p.i]; p.i += 1; return c\n"
             + "def peek_char(p): return _eof if p.i >= len(p.t) else p.t[p.i]\n"
             + "def read_line(p):\n"
             + "    if p.i >= len(p.t): return _eof\n"
             + "    j = p.t.find(chr(10), p.i)\n"
             + "    if j < 0: j = len(p.t)\n"
             + "    line = p.t[p.i:j]; p.i = j + 1; return line\n"
             + "def read_string(k, p):\n"
             + "    if p.i >= len(p.t): return _eof\n"
             + "    r = p.t[p.i:p.i + k]; p.i += len(r); return r\n"
             + "def with_output_to_string(thunk):\n"
             + "    import io, contextlib\n"
             + "    buf = io.StringIO()\n"
             + "    with contextlib.redirect_stdout(buf): thunk()\n"
             + "    return buf.getvalue()\n"
             + "def call_with_output_string(proc):\n"
             + "    p = _OutPort(); proc(p); return ''.join(p.buf)\n"
             + "def _hkey(k): return tuple(_hkey(x) for x in k) if isinstance(k, _SEQ) else k\n"
             + "def make_hash_table(*a): return {}\n"
             + "make_equal_hash_table = make_hash_table; make_strong_eqv_hash_table = make_hash_table; make_eqv_hash_table = make_hash_table\n"
             + "def hash_table_p(h): return isinstance(h, dict)\n"
             + "def hash_table_set(h, k, v): h[_hkey(k)] = v\n"
             + "def hash_table_ref(h, k, fail=None, succeed=None):\n"
             + "    kk = _hkey(k)\n"
             + "    if kk in h: return succeed(h[kk]) if succeed else h[kk]\n"
             + "    if fail: return fail()\n"
             + "    raise _SchemeError('hash-table-ref: no value for key ' + _show(k, True))\n"
             + "def hash_table_ref_default(h, k, d): return h.get(_hkey(k), d)\n"
             + "def hash_table_delete(h, k): h.pop(_hkey(k), None)\n"
             + "def hash_table_contains_p(h, k): return _hkey(k) in h\n"
             + "hash_table_exists_p = hash_table_contains_p\n"
             + "def hash_table_count(h): return len(h)\n"
             + "hash_table_size = hash_table_count\n"
             + "def hash_table_keys(h): return list(h.keys())\n"
             + "def hash_table_values(h): return list(h.values())\n"
             + "def hash_table_to_alist(h): return [[k, v] for k, v in h.items()]\n"
             + "def hash_table_update(h, k, f, fail=None):\n"
             + "    kk = _hkey(k)\n"
             + "    if kk in h: h[kk] = f(h[kk])\n"
             + "    elif fail: h[kk] = f(fail())\n"
             + "    else: raise _SchemeError('hash-table-update!: no value for key ' + _show(k, True))\n"
             + "def hash_table_update_default(h, k, f, d):\n"
             + "    kk = _hkey(k); h[kk] = f(h.get(kk, d))\n"
             + "def hash_table_walk(h, f):\n"
             + "    for k, v in list(h.items()): f(k, v)\n"
             + "def hash_table_copy(h, *a): return dict(h)\n"
             + "def hash_table_clear(h): h.clear()\n"
             + "\n");
        emit("class _Char(str):\n"
             + "    __slots__ = ()\n"
             + "    def __repr__(s): return _show(s, True)\n"
             + "_CHAR_NAMES = {' ': 'space', chr(10): 'newline', chr(9): 'tab', chr(13): 'return', chr(0): 'null'}\n"
             + "def char_p(x): return isinstance(x, _Char)\n"
             + "def string_p(n): return isinstance(n, str) and not isinstance(n, (_Sym, _Char))\n"
             + "_show_base = _show\n"
             + "def _show(x, w=False):\n"
             + "    if isinstance(x, _Char):\n"
             + "        return '#' + chr(92) + _CHAR_NAMES.get(str.__str__(x), str.__str__(x)) if w else str.__str__(x)\n"
             + "    if isinstance(x, _SEQ + (_Pair,)) and not isinstance(x, str):\n"
             + "        items = []\n"
             + "        while isinstance(x, _Pair): items.append(_show(x.car, w)); x = x.cdr\n"
             + "        if isinstance(x, _SEQ): return '(' + ' '.join(items + [_show(y, w) for y in x]) + ')'\n"
             + "        return '(' + ' '.join(items) + ' . ' + _show(x, w) + ')'\n"
             + "    return _show_base(x, w)\n"
             + "def string_ref(s, i):\n"
             + "    if not isinstance(s, str): _bad('string-ref', 'a string', s)\n"
             + "    if not (isinstance(i, int) and 0 <= i < len(s)):\n"
             + "        raise _SchemeError('string-ref: index ' + _show(i) + ' out of range for a string of length ' + str(len(s)))\n"
             + "    return _Char(s[i])\n"
             + "def string_to_list(s, start=0, end=None): return [_Char(c) for c in s[start:end]]\n"
             + "def integer_to_char(n): return _Char(chr(n))\n"
             + "def char_upcase(c): return _Char(c.upper())\n"
             + "def char_downcase(c): return _Char(c.lower())\n"
             + "def string_map(f, *ss): return ''.join(f(*[_Char(c) for c in a]) for a in zip(*ss))\n"
             + "def string_for_each(f, *ss):\n"
             + "    for a in zip(*ss): f(*[_Char(c) for c in a])\n"
             + "_read_char_base = read_char\n"
             + "_peek_char_base = peek_char\n"
             + "def read_char(p):\n"
             + "    c = _read_char_base(p)\n"
             + "    return _Char(c) if isinstance(c, str) else c\n"
             + "def peek_char(p):\n"
             + "    c = _peek_char_base(p)\n"
             + "    return _Char(c) if isinstance(c, str) else c\n"
             + "\n");
        emit("_scm_wrap_base = _scm_wrap\n"
             + "def _scm_wrap(g):\n"
             + "    _scm_wrap_base(g)\n"
             + "    user = {k: g.get(k) for k in ('mouse_released', 'mouse_clicked', 'key_pressed', 'key_released', 'key_typed', 'draw')}\n"
             + "    down = __import__('builtins').set()\n"
             + "    def ident():\n"
             + "        k = g.get('key')\n"
             + "        return k if k is not None else g.get('key_code')\n"
             + "    def kp():\n"
             + "        down.add(ident())\n"
             + "        if user['key_pressed']: user['key_pressed']()\n"
             + "        k = g.get('key')\n"
             + "        if user['key_typed'] and isinstance(k, str) and len(k) == 1 and k.isprintable(): user['key_typed']()\n"
             + "    def kr():\n"
             + "        i = ident()\n"
             + "        if i in down: down.discard(i)\n"
             + "        else: down.clear()\n"
             + "        if user['key_released']: user['key_released']()\n"
             + "    def mr():\n"
             + "        if user['mouse_released']: user['mouse_released']()\n"
             + "        if user['mouse_clicked']: user['mouse_clicked']()\n"
             + "    g['key_pressed'] = _scm_guard(kp)\n"
             + "    g['key_released'] = _scm_guard(kr)\n"
             + "    if user['mouse_released'] or user['mouse_clicked']: g['mouse_released'] = _scm_guard(mr)\n"
             + "    if user['draw']:\n"
             + "        d = user['draw']\n"
             + "        def dw():\n"
             + "            g['key_is_pressed'] = bool(down)\n"
             + "            return d()\n"
             + "        g['draw'] = dw\n"
             + "\n");
        emit("_scm_wrap_base2 = _scm_wrap\n"
             + "def _scm_wrap(g):\n"
             + "    _scm_wrap_base2(g)\n"
             + "    def charify():\n"
             + "        k = g.get('key')\n"
             + "        if isinstance(k, str) and len(k) == 1 and not isinstance(k, _Char): g['key'] = _Char(k)\n"
             + "    for name in ('key_pressed', 'key_released', 'draw'):\n"
             + "        f = g.get(name)\n"
             + "        if f:\n"
             + "            def w(f=f):\n"
             + "                charify()\n"
             + "                return f()\n"
             + "            g[name] = w\n"
             + "\n");
        emit("import time as _time, colorsys as _colorsys, datetime as _dt\n"
             + "_builtin_map = __builtins__['map'] if isinstance(__builtins__, dict) else __builtins__.map\n"
             + "_t0 = _time.perf_counter()\n"
             + "def _map(f, *a):\n"
             + "    if callable(f): return list(_builtin_map(f, *a))\n"
             + "    v, a1, b1, a2, b2 = (f,) + a\n"
             + "    return a2 + (b2 - a2) * ((v - a1) / (b1 - a1))\n"
             + "def lerp(a, b, t): return a + (b - a) * t\n"
             + "def constrain(v, lo, hi): return lo if v < lo else hi if v > hi else v\n"
             + "def norm(v, lo, hi): return (v - lo) / (hi - lo)\n"
             + "def mag(*a): return _math.sqrt(sum(x * x for x in a))\n"
             + "def dist(*a):\n"
             + "    h = len(a) // 2\n"
             + "    return _math.sqrt(sum((a[i] - a[i + h]) ** 2 for i in range(h)))\n"
             + "def radians(d): return d * _math.pi / 180.0\n"
             + "def degrees(r): return r * 180.0 / _math.pi\n"
             + "def millis(): return int((_time.perf_counter() - _t0) * 1000)\n"
             + "def second(): return _dt.datetime.now().second\n"
             + "def minute(): return _dt.datetime.now().minute\n"
             + "def hour(): return _dt.datetime.now().hour\n"
             + "def _rgba(c):\n"
             + "    if isinstance(c, (int, float)): v = c / 255.0; return v, v, v, 1.0\n"
             + "    return c.r, c.g, c.b, c.a\n"
             + "def red(c): return _rgba(c)[0] * 255.0\n"
             + "def green(c): return _rgba(c)[1] * 255.0\n"
             + "def blue(c): return _rgba(c)[2] * 255.0\n"
             + "def alpha(c): return _rgba(c)[3] * 255.0\n"
             + "def _hsv(c): r, g, b, _a = _rgba(c); return _colorsys.rgb_to_hsv(r, g, b)\n"
             + "def hue(c): return _hsv(c)[0] * 255.0\n"
             + "def saturation(c): return _hsv(c)[1] * 255.0\n"
             + "def brightness(c): return _hsv(c)[2] * 255.0\n"
             + "def lerp_color(c1, c2, t):\n"
             + "    a, b = _rgba(c1), _rgba(c2)\n"
             + "    return color(*[(a[i] + (b[i] - a[i]) * t) * 255.0 for i in range(4)])\n"
             + "_warned = __import__('builtins').set()\n"
             + "def _unsupported(name):\n"
             + "    def f(*a, **k):\n"
             + "        if name not in _warned:\n"
             + "            _warned.add(name)\n"
             + "            _sys.stdout.flush()\n"
             + "            print(name + ' is not supported yet in SchemeMode', file=_sys.stderr)\n"
             + "        return 0\n"
             + "    return f\n"
             + "for _n in ('text', 'text_size', 'text_align', 'text_font', 'text_leading', 'load_font', 'create_font', 'text_width',\n"
             + "           'text_ascent', 'text_descent', 'cursor', 'no_cursor', 'smooth', 'no_smooth', 'save_frame', 'save',\n"
             + "           'create_graphics', 'perspective', 'ortho'):\n"
             + "    globals()[_n] = _unsupported(_n.replace('_', '-'))\n"
             + "_fps_target = [None]; _fps_last = [None]; _fps_measured = [60.0]\n"
             + "def frame_rate(n=None):\n"
             + "    if n is None: return _fps_measured[0]\n"
             + "    _fps_target[0] = n\n"
             + "_scm_wrap_base3 = _scm_wrap\n"
             + "def _scm_wrap(g):\n"
             + "    _scm_wrap_base3(g)\n"
             + "    d = g.get('draw')\n"
             + "    if d:\n"
             + "        def dw():\n"
             + "            now = _time.perf_counter()\n"
             + "            if _fps_last[0] is not None:\n"
             + "                dt = now - _fps_last[0]\n"
             + "                if _fps_target[0]:\n"
             + "                    wait = 1.0 / _fps_target[0] - dt\n"
             + "                    if wait > 0:\n"
             + "                        _time.sleep(wait); now = _time.perf_counter(); dt = now - _fps_last[0]\n"
             + "                if dt > 0: _fps_measured[0] = 0.9 * _fps_measured[0] + 0.1 / dt\n"
             + "            _fps_last[0] = now\n"
             + "            return d()\n"
             + "        g['draw'] = dw\n"
             + "\n");
        emit("_mw_color = color; _mw_fill = fill; _mw_stroke = stroke; _mw_background = background\n"
             + "_mw_tint = globals().get('tint'); _mw_color_mode = color_mode\n"
             + "_RGB_MODE = globals().get('RGB')\n"
             + "_cm = {'rgb255': True}\n"
             + "def color_mode(mode, *mx):\n"
             + "    _cm['rgb255'] = (mode == _RGB_MODE) and all(m == 255 for m in mx)\n"
             + "    return _mw_color_mode(mode, *mx)\n"
             + "def _cn(a):\n"
             + "    if not _cm['rgb255']: return a\n"
             + "    return tuple(float(x) / 255.0 if isinstance(x, (int, float)) and not isinstance(x, bool) else x for x in a)\n"
             + "def color(*a): return _mw_color(*_cn(a))\n"
             + "def fill(*a): return _mw_fill(*_cn(a))\n"
             + "def stroke(*a): return _mw_stroke(*_cn(a))\n"
             + "def background(*a): return _mw_background(*_cn(a))\n"
             + "if _mw_tint:\n"
             + "    def tint(*a): return _mw_tint(*_cn(a))\n"
             + "def lerp_color(c1, c2, t):\n"
             + "    a, b = _rgba(c1), _rgba(c2)\n"
             + "    return _mw_color(*[float(a[i] + (b[i] - a[i]) * t) for i in range(4)])\n"
             + "for _n in ('camera', 'lights', 'ambient_light', 'begin_camera', 'end_camera'):\n"
             + "    globals()[_n] = _unsupported(_n.replace('_', '-'))\n"
             + "\n");
        emit("import mewnala as _mewnala\n"
             + "def _gfx():\n"
             + "    g = _mewnala._get_graphics()\n"
             + "    if g is None: raise _SchemeError('text drawing needs a window: call size first')\n"
             + "    return g\n"
             + "def _txt(x): return x if isinstance(x, str) else _show(x)\n"
             + "_TA_H = {'left': 0, 'center': 1, 'right': 2}\n"
             + "_TA_V = {'baseline': 0, 'top': 1, 'center': 2, 'bottom': 3}\n"
             + "def text(c, x, y, *r): return _gfx().text(_txt(c), x, y, *r)\n"
             + "def text_size(n): return _gfx().text_size(n)\n"
             + "def text_align(h, v=None):\n"
             + "    hv = _TA_H.get(str(h).lower(), h) if isinstance(h, str) else h\n"
             + "    vv = None if v is None else (_TA_V.get(str(v).lower(), v) if isinstance(v, str) else v)\n"
             + "    return _gfx().text_align(hv, vv)\n"
             + "def text_leading(n): return _gfx().text_leading(n)\n"
             + "def text_width(c): return _gfx().text_width(_txt(c))\n"
             + "def text_ascent(): return _gfx().text_ascent()\n"
             + "def text_descent(): return _gfx().text_descent()\n"
             + "def text_style(n): return _gfx().text_style(n)\n"
             + "def text_weight(n): return _gfx().text_weight(n)\n"
             + "def text_font(f=None): return _gfx().text_font(f)\n"
             + "for _k, _v in (('LEFT', 'left'), ('CENTER', 'center'), ('RIGHT', 'right'),\n"
             + "               ('TOP', 'top'), ('BOTTOM', 'bottom'), ('BASELINE', 'baseline')):\n"
             + "    globals().setdefault(_k, _v)\n"
             + "\n");
        emit("_mw_dir_light = globals().get('directional_light'); _mw_point_light = globals().get('point_light')\n"
             + "_mw_spot_light = globals().get('spot_light')\n"
             + "_mw_cam_pos = globals().get('camera_position'); _mw_cam_look = globals().get('camera_look_at')\n"
             + "_mw_cam_reset = globals().get('camera_reset')\n"
             + "_LIGHT_POINT_INTENSITY = 2000000.0; _LIGHT_SPOT_INTENSITY = 15000000.0\n"
             + "_LIGHT_DIR_ILLUMINANCE = 3000.0; _LIGHT_RANGE = 2000.0; _LIGHT_RADIUS = 0.25\n"
             + "_lights = {'dir': [], 'point': [], 'spot': []}\n"
             + "_light_slot = {'dir': 0, 'point': 0, 'spot': 0}\n"
             + "def _native(a): return bool(a) and not isinstance(a[0], (int, float))\n"
             + "def _rgb01(r, g, b): return (r / 255.0, g / 255.0, b / 255.0)\n"
             + "def _light(kind, make, pos, look):\n"
             + "    i = _light_slot[kind]; _light_slot[kind] += 1\n"
             + "    lst = _lights[kind]\n"
             + "    if i < len(lst): L = lst[i]\n"
             + "    else:\n"
             + "        L = make(); lst.append(L)\n"
             + "    if pos is not None: L.position(*pos)\n"
             + "    if look is not None: L.look_at(*look)\n"
             + "    return L\n"
             + "def directional_light(*a, **k):\n"
             + "    if _native(a) or k: return _mw_dir_light(*a, **k)\n"
             + "    r, g, b, nx, ny, nz = a\n"
             + "    return _light('dir', lambda: _mw_dir_light(_rgb01(r, g, b), _LIGHT_DIR_ILLUMINANCE), (0.0, 0.0, 0.0), (nx, ny, nz))\n"
             + "def point_light(*a, **k):\n"
             + "    if _native(a) or k: return _mw_point_light(*a, **k)\n"
             + "    r, g, b, x, y, z = a\n"
             + "    return _light('point', lambda: _mw_point_light(_rgb01(r, g, b), _LIGHT_POINT_INTENSITY, _LIGHT_RANGE, _LIGHT_RADIUS), (x, y, z), None)\n"
             + "def spot_light(*a, **k):\n"
             + "    if _native(a) or k: return _mw_spot_light(*a, **k)\n"
             + "    r, g, b, x, y, z, nx, ny, nz = a[:9]\n"
             + "    angle = a[9] if len(a) > 9 else _math.pi / 3\n"
             + "    return _light('spot', lambda: _mw_spot_light(_rgb01(r, g, b), _LIGHT_SPOT_INTENSITY, _LIGHT_RANGE, _LIGHT_RADIUS, 0.0, angle),\n"
             + "                  (x, y, z), (x + nx, y + ny, z + nz))\n"
             + "def lights():\n"
             + "    directional_light(255, 255, 255, 0.0, 0.0, -1.0)\n"
             + "def camera(*a):\n"
             + "    if not a: return _mw_cam_reset()\n"
             + "    _mw_cam_pos(a[0], a[1], a[2])\n"
             + "    if len(a) >= 6: _mw_cam_look(a[3], a[4], a[5])\n"
             + "for _n in ('ambient_light', 'no_lights', 'light_falloff', 'light_specular'):\n"
             + "    globals()[_n] = _unsupported(_n.replace('_', '-'))\n"
             + "_scm_wrap_base4 = _scm_wrap\n"
             + "def _scm_wrap(g):\n"
             + "    _scm_wrap_base4(g)\n"
             + "    d = g.get('draw')\n"
             + "    if d:\n"
             + "        def dw():\n"
             + "            for _k in _light_slot: _light_slot[_k] = 0\n"
             + "            return d()\n"
             + "        g['draw'] = dw\n"
             + "\n");
        emit("_mw_mode_3d = globals().get('mode_3d'); _mw_mode_2d = globals().get('mode_2d')\n"
             + "_mw_size = size; _mw_translate = translate; _mw_scale = scale\n"
             + "_mode3d = [False]\n"
             + "for _k, _v in (('P3D', 'P3D'), ('P2D', 'P2D')): globals().setdefault(_k, _v)\n"
             + "def mode_3d():\n"
             + "    _mode3d[0] = True\n"
             + "    return _mw_mode_3d()\n"
             + "def mode_2d():\n"
             + "    _mode3d[0] = False\n"
             + "    return _mw_mode_2d()\n"
             + "def size(w, h, *r):\n"
             + "    out = _mw_size(w, h)\n"
             + "    if r and r[0] == 'P3D': mode_3d()\n"
             + "    return out\n"
             + "_tz_notice = _unsupported('translate-with-z')\n"
             + "def translate(x, y, z=0):\n"
             + "    if z: _tz_notice()\n"
             + "    return _mw_translate(x, y)\n"
             + "def _fy(y): return globals().get('height', 0) - y if _mode3d[0] else y\n"
             + "def _conv(pt): return None if pt is None else (pt[0], _fy(pt[1]), pt[2])\n"
             + "def _light(kind, make, pos, look):\n"
             + "    i = _light_slot[kind]; _light_slot[kind] += 1\n"
             + "    lst = _lights[kind]\n"
             + "    if i < len(lst): L = lst[i]\n"
             + "    else:\n"
             + "        L = make(); lst.append(L)\n"
             + "    if pos is not None: L.position(*_conv(pos))\n"
             + "    if look is not None: L.look_at(*_conv(look))\n"
             + "    return L\n"
             + "def camera(*a):\n"
             + "    if not a: return _mw_cam_reset()\n"
             + "    _mw_cam_pos(*_conv(a[0:3]))\n"
             + "    if len(a) >= 6: _mw_cam_look(*_conv(a[3:6]))\n"
             + "_scm_wrap_base5 = _scm_wrap\n"
             + "def _scm_wrap(g):\n"
             + "    _scm_wrap_base5(g)\n"
             + "    d = g.get('draw')\n"
             + "    if d:\n"
             + "        def dw():\n"
             + "            if _mode3d[0]:\n"
             + "                _mw_translate(0, g.get('height', 0))\n"
             + "                _mw_scale(1, -1)\n"
             + "            return d()\n"
             + "        g['draw'] = dw\n"
             + "\n");
        emit("_scm_wrap_base6 = _scm_wrap\n"
             + "def _scm_wrap(g):\n"
             + "    _scm_wrap_base6(g)\n"
             + "    d = g.get('draw')\n"
             + "    if d:\n"
             + "        def dw():\n"
             + "            if _mode3d[0]:\n"
             + "                w = g.get('width', 0); h = g.get('height', 0)\n"
             + "                dist = (h / 2.0) / _math.tan(_math.pi / 6)\n"
             + "                _mw_cam_pos(w / 2.0, h / 2.0, dist)\n"
             + "                _mw_cam_look(w / 2.0, h / 2.0, 0.0)\n"
             + "            return d()\n"
             + "        g['draw'] = dw\n"
             + "\n");
        emit("_mw_roughness = globals().get('roughness')\n"
             + "_lit_frame = [False]\n"
             + "def _enable_lit():\n"
             + "    if not _lit_frame[0] and _mw_roughness:\n"
             + "        _mw_roughness(0.9)\n"
             + "        _lit_frame[0] = True\n"
             + "_light_base = _light\n"
             + "def _light(kind, make, pos, look):\n"
             + "    _enable_lit()\n"
             + "    return _light_base(kind, make, pos, look)\n"
             + "_scm_wrap_base7 = _scm_wrap\n"
             + "def _scm_wrap(g):\n"
             + "    _scm_wrap_base7(g)\n"
             + "    d = g.get('draw')\n"
             + "    if d:\n"
             + "        def dw():\n"
             + "            _lit_frame[0] = False\n"
             + "            return d()\n"
             + "        g['draw'] = dw\n"
             + "\n");
        emit("import os as _os, re as _re, json as _json, csv as _csv\n"
             + "_EXPORT = ('push pop push_style pop_style shape_mode create_shape shape load_shape arc_mode '\n"
             + "           'get set load_pixels update_pixels pixels filter blend copy mask exit is_looping '\n"
             + "           'random_seed PVector').split()\n"
             + "for _n in _EXPORT:\n"
             + "    if _n not in globals() and hasattr(_mewnala, _n): globals()[_n] = getattr(_mewnala, _n)\n"
             + "for _a, _b in (('push', 'push_matrix'), ('pop', 'pop_matrix')):\n"
             + "    if _a not in globals() and _b in globals(): globals()[_a] = globals()[_b]\n"
             + "for _n in ('push_style', 'pop_style', 'shape_mode', 'create_shape', 'shape', 'load_shape', 'arc_mode',\n"
             + "           'get', 'set', 'load_pixels', 'update_pixels', 'filter', 'blend', 'copy', 'mask'):\n"
             + "    if _n not in globals(): globals()[_n] = _unsupported(_n.replace('_', '-'))\n"
             + "def sq(x): return x * x\n"
             + "def atan2(y, x): return _math.atan2(y, x)\n"
             + "def ceil(x): return _math.ceil(x)\n"
             + "for _n in ('sqrt', 'exp', 'log', 'floor', 'sin', 'cos', 'tan', 'asin', 'acos', 'atan'):\n"
             + "    globals().setdefault(_n, getattr(_math, _n))\n"
             + "def random_seed(n): _random.seed(n)\n"
             + "def _nf1(x, left=0, right=None):\n"
             + "    if right is None and isinstance(x, int) and not isinstance(x, bool):\n"
             + "        return str(abs(x)).rjust(left, '0')\n"
             + "    r = 0 if right is None else right\n"
             + "    s = ('%.' + str(r) + 'f') % abs(x)\n"
             + "    i, _, f = s.partition('.')\n"
             + "    return i.rjust(left, '0') + ('.' + f if f else '')\n"
             + "def _nfmap(fn, x, *a): return [fn(v, *a) for v in x] if isinstance(x, _SEQ) else fn(x, *a)\n"
             + "def nf(x, left=0, right=None): return _nfmap(lambda v, l, r: ('-' if v < 0 else '') + _nf1(v, l, r), x, left, right)\n"
             + "def nfs(x, left=0, right=None): return _nfmap(lambda v, l, r: ('-' if v < 0 else ' ') + _nf1(v, l, r), x, left, right)\n"
             + "def nfp(x, left=0, right=None): return _nfmap(lambda v, l, r: ('-' if v < 0 else '+') + _nf1(v, l, r), x, left, right)\n"
             + "def nfc(x, right=None):\n"
             + "    def one(v, r):\n"
             + "        s = ('{:,.' + str(r) + 'f}').format(v) if r is not None else '{:,}'.format(v)\n"
             + "        return s\n"
             + "    return _nfmap(one, x, right)\n"
             + "def split(s, delim): return s.split(delim)\n"
             + "def split_tokens(s, delims=None):\n"
             + "    if delims is None: return s.split()\n"
             + "    return [t for t in _re.split('[' + _re.escape(delims) + ']', s) if t]\n"
             + "def join(lst, sep): return sep.join(_txt(x) for x in lst)\n"
             + "def trim(x): return [v.strip() for v in x] if isinstance(x, _SEQ) else x.strip()\n"
             + "def match(s, pattern):\n"
             + "    m = _re.search(pattern, s)\n"
             + "    return False if m is None else [m.group(0)] + list(m.groups())\n"
             + "def match_all(s, pattern):\n"
             + "    ms = list(_re.finditer(pattern, s))\n"
             + "    return False if not ms else [[m.group(0)] + list(m.groups()) for m in ms]\n"
             + "def hex(v, digits=None):\n"
             + "    s = format(v & 0xFFFFFFFF if v < 0 else v, 'X')\n"
             + "    return s.rjust(digits, '0')[-digits:] if digits else s.rjust(8, '0') if isinstance(v, int) and v > 0xFFFF else s\n"
             + "def unhex(s): return int(s, 16)\n"
             + "def binary(v, digits=None):\n"
             + "    s = format(v, 'b')\n"
             + "    return s.rjust(digits, '0')[-digits:] if digits else s\n"
             + "def unbinary(s): return int(s, 2)\n"
             + "def day(): return _dt.datetime.now().day\n"
             + "def month(): return _dt.datetime.now().month\n"
             + "def year(): return _dt.datetime.now().year\n"
             + "_looping = [True]\n"
             + "_mw_loop = globals().get('loop'); _mw_no_loop = globals().get('no_loop')\n"
             + "if _mw_loop:\n"
             + "    def loop(): _looping[0] = True; return _mw_loop()\n"
             + "if _mw_no_loop:\n"
             + "    def no_loop(): _looping[0] = False; return _mw_no_loop()\n"
             + "if 'is_looping' not in globals():\n"
             + "    def is_looping(): return _looping[0]\n"
             + "if 'exit' not in globals():\n"
             + "    def exit(): _sys.stdout.flush(); _os._exit(0)\n"
             + "def _find_data(name):\n"
             + "    for c in (name, _os.path.join('assets', name), _os.path.join('..', 'data', name), _os.path.join('..', name)):\n"
             + "        if _os.path.exists(c): return c\n"
             + "    raise _SchemeError('file not found: ' + name + ' (put it in the data folder of the sketch)')\n"
             + "def _out_path(name): return name if _os.path.isabs(name) else _os.path.join('..', name)\n"
             + "def load_strings(name):\n"
             + "    with open(_find_data(name), encoding='utf-8') as fh: return fh.read().splitlines()\n"
             + "def save_strings(name, lines):\n"
             + "    with open(_out_path(name), 'w', encoding='utf-8') as fh: fh.write(chr(10).join(_txt(x) for x in lines) + chr(10))\n"
             + "def load_bytes(name):\n"
             + "    with open(_find_data(name), 'rb') as fh: return list(fh.read())\n"
             + "def save_bytes(name, data):\n"
             + "    with open(_out_path(name), 'wb') as fh: fh.write(bytes(int(b) & 255 for b in data))\n"
             + "def _from_json(v):\n"
             + "    if isinstance(v, dict): return {k: _from_json(x) for k, x in v.items()}\n"
             + "    if isinstance(v, list): return [_from_json(x) for x in v]\n"
             + "    return v\n"
             + "def _to_json(v):\n"
             + "    if isinstance(v, dict): return {str(k): _to_json(x) for k, x in v.items()}\n"
             + "    if isinstance(v, (_SEQ, _Pair)) and not isinstance(v, str): return [_to_json(x) for x in v]\n"
             + "    if v is None: return None\n"
             + "    return v\n"
             + "def load_json(name):\n"
             + "    with open(_find_data(name), encoding='utf-8') as fh: return _from_json(_json.load(fh))\n"
             + "def save_json(name, value):\n"
             + "    with open(_out_path(name), 'w', encoding='utf-8') as fh: _json.dump(_to_json(value), fh, indent=2)\n"
             + "def load_table(name, options=''):\n"
             + "    with open(_find_data(name), encoding='utf-8', newline='') as fh:\n"
             + "        delim = chr(9) if ('tsv' in options or name.endswith('.tsv')) else ','\n"
             + "        return [row for row in _csv.reader(fh, delimiter=delim)]\n"
             + "for _k, _v in (('RETURN', chr(13)), ('ESC', chr(27)), ('CODED', 65535)): globals().setdefault(_k, _v)\n"
             + "if 'PVector' not in globals() and 'vec3' in globals():\n"
             + "    _mw_vec3 = vec3\n"
             + "    def PVector(x=0.0, y=0.0, z=0.0): return _mw_vec3(x, y, z)\n"
             + "\n");
        emit("_builtin_filter = __import__('builtins').filter\n"
             + "_filter_notice = _unsupported('filter')\n"
             + "def filter(*a):\n"
             + "    if a and callable(a[0]): return list(_builtin_filter(a[0], *a[1:]))\n"
             + "    return _filter_notice(*a)\n"
             + "\n");
        emit("_builtins = __import__('builtins')\n"
             + "def round(x, *n): return _builtins.round(x, *n)\n"
             + "_TA_HN = {0: 'left', 1: 'center', 2: 'right'}\n"
             + "_TA_VN = {0: 'baseline', 1: 'top', 2: 'center', 3: 'bottom'}\n"
             + "def text_align(h, v=None):\n"
             + "    hs = h.lower() if isinstance(h, str) else _TA_HN.get(h, h)\n"
             + "    vs = None if v is None else (v.lower() if isinstance(v, str) else _TA_VN.get(v, v))\n"
             + "    g = _gfx()\n"
             + "    try:\n"
             + "        return g.text_align(hs) if vs is None else g.text_align(hs, vs)\n"
             + "    except TypeError:\n"
             + "        return g.text_align(_TA_H.get(hs, hs), None if vs is None else _TA_V.get(vs, vs))\n"
             + "\n");
        emit("# mewnala reads whole numbers as 0-255 but decimals as 0-1, so colors go through Color objects\n"
             + "_NATIVE255 = False\n"
             + "_cn_base = _cn\n"
             + "def _cn(a): return a if _NATIVE255 else _cn_base(a)\n"
             + "def lerp_color(c1, c2, t):\n"
             + "    a, b = _rgba(c1), _rgba(c2)\n"
             + "    k = 255.0 if _NATIVE255 else 1.0\n"
             + "    return _mw_color(*[float(a[i] + (b[i] - a[i]) * t) * k for i in range(4)])\n"
             + "\n");
        emit("def _c255(v): return _builtins.round(v * 255.0, 3)\n"
             + "def red(c): return _c255(_rgba(c)[0])\n"
             + "def green(c): return _c255(_rgba(c)[1])\n"
             + "def blue(c): return _c255(_rgba(c)[2])\n"
             + "def alpha(c): return _c255(_rgba(c)[3])\n"
             + "def hue(c): return _c255(_hsv(c)[0])\n"
             + "def saturation(c): return _c255(_hsv(c)[1])\n"
             + "def brightness(c): return _c255(_hsv(c)[2])\n"
             + "\n");
        emit("def _as_color(a):\n"
             + "    if not a or isinstance(a[0], bool) or not isinstance(a[0], (int, float)): return a\n"
             + "    return (_mw_color(*_cn_base(a)),)\n"
             + "def fill(*a): return _mw_fill(*_as_color(a))\n"
             + "def stroke(*a): return _mw_stroke(*_as_color(a))\n"
             + "def background(*a): return _mw_background(*_as_color(a))\n"
             + "if _mw_tint:\n"
             + "    def tint(*a): return _mw_tint(*_as_color(a))\n"
             + "def color(*a): return _as_color(a)[0] if a and isinstance(a[0], (int, float)) and not isinstance(a[0], bool) else _mw_color(*a)\n"
             + "\n");
        emit("_PROVIDED = ('load_font create_font cursor no_cursor smooth no_smooth save_frame save create_graphics '\n"
             + "             'push_style pop_style get set load_pixels update_pixels blend copy mask shape_mode create_shape '\n"
             + "             'shape load_shape arc_mode ambient_light no_lights light_falloff light_specular').split()\n"
             + "for _n in _PROVIDED:\n"
             + "    if hasattr(_mewnala, _n): globals()[_n] = getattr(_mewnala, _n)\n"
             + "if hasattr(_mewnala, 'filter'): _filter_notice = _mewnala.filter\n"
             + "for _n in ('perspective', 'ortho'):\n"
             + "    if hasattr(_mewnala, _n): globals()[_n] = getattr(_mewnala, _n)\n"
             + "    elif hasattr(_mewnala.Graphics, _n):\n"
             + "        globals()[_n] = (lambda name: lambda *a: getattr(_gfx(), name)(*a))(_n)\n"
             + "\n");
        emit("_mw_ambient = getattr(_mewnala, 'ambient_light', None)\n"
             + "_AMBIENT_BRIGHTNESS = 400.0\n"
             + "_LIGHT_DIR_ILLUMINANCE = 6000.0\n"
             + "_amb = {'now': False, 'before': False}\n"
             + "_amb_notice = _unsupported('ambient-light')\n"
             + "def ambient_light(*a, **k):\n"
             + "    if not _mw_ambient: return _amb_notice()\n"
             + "    if _native(a) or k: return _mw_ambient(*a, **k)\n"
             + "    r, g, b = (a[0], a[0], a[0]) if len(a) == 1 else a[:3]\n"
             + "    _enable_lit()\n"
             + "    _amb['now'] = True\n"
             + "    return _mw_ambient(_rgb01(r, g, b), _AMBIENT_BRIGHTNESS)\n"
             + "def lights():\n"
             + "    ambient_light(128, 128, 128)\n"
             + "    directional_light(128, 128, 128, 0.0, 0.0, -1.0)\n"
             + "_scm_wrap_base8 = _scm_wrap\n"
             + "def _scm_wrap(g):\n"
             + "    _scm_wrap_base8(g)\n"
             + "    d = g.get('draw')\n"
             + "    if d:\n"
             + "        def dw():\n"
             + "            _amb['now'] = False\n"
             + "            r = d()\n"
             + "            if _mw_ambient and _amb['before'] and not _amb['now']:\n"
             + "                _mw_ambient((0.0, 0.0, 0.0), 0.0)\n"
             + "            _amb['before'] = _amb['now']\n"
             + "            return r\n"
             + "        g['draw'] = dw\n"
             + "\n");
        emit("_mw_cursor = getattr(_mewnala, 'cursor', None)\n"
             + "_mw_no_cursor = getattr(_mewnala, 'no_cursor', None)\n"
             + "_cursor_shape_notice = _unsupported('cursor shapes')\n"
             + "for _k in ('ARROW', 'CROSS', 'HAND', 'MOVE', 'TEXT', 'WAIT'): globals().setdefault(_k, _k.lower())\n"
             + "if _mw_cursor:\n"
             + "    def cursor(*a):\n"
             + "        if a:\n"
             + "            try: return _mw_cursor(a[0])\n"
             + "            except TypeError: _cursor_shape_notice()\n"
             + "        return _mw_cursor()\n"
             + "if _mw_no_cursor:\n"
             + "    def no_cursor(): return _mw_no_cursor()\n"
             + "\n");
        emit("class _PShape:\n"
             + "    __slots__ = ('kind', 'prim', 'args', 'verts', 'skind', 'close', 'fill', 'stroke', 'sweight',\n"
             + "                 'children', 'visible', 'ops')\n"
             + "    def __init__(s, kind, prim=None, args=()):\n"
             + "        s.kind = kind; s.prim = prim; s.args = tuple(args); s.verts = []; s.skind = None; s.close = False\n"
             + "        s.fill = None; s.stroke = None; s.sweight = None; s.children = []; s.visible = True; s.ops = []\n"
             + "    def __repr__(s): return '#<shape ' + s.kind + '>'\n"
             + "_PRIM_NAMES = {'RECT': 'rect', 'ELLIPSE': 'ellipse', 'ARC': 'arc', 'TRIANGLE': 'triangle', 'QUAD': 'quad',\n"
             + "               'LINE': 'line', 'POINT': 'point', 'BOX': 'box', 'SPHERE': 'sphere'}\n"
             + "for _k in list(_PRIM_NAMES) + ['GROUP', 'PATH']: globals().setdefault(_k, _k.lower())\n"
             + "GROUP = globals().get('GROUP', 'group'); PATH = globals().get('PATH', 'path')\n"
             + "_PRIM_BY = {globals()[_k]: _v for _k, _v in _PRIM_NAMES.items()}\n"
             + "def _is_shape(a): return bool(a) and isinstance(a[0], _PShape)\n"
             + "def create_shape(*a):\n"
             + "    if not a: return _PShape('path')\n"
             + "    k = a[0]\n"
             + "    if k == GROUP: return _PShape('group')\n"
             + "    if k == PATH: return _PShape('path')\n"
             + "    fn = _PRIM_BY.get(k)\n"
             + "    if fn is None: raise _SchemeError('create-shape: unknown kind ' + _show(k, True))\n"
             + "    return _PShape('prim', fn, a[1:])\n"
             + "_mw_begin_shape = begin_shape; _mw_end_shape = end_shape; _mw_vertex = vertex\n"
             + "_mw_bezier_vertex = globals().get('bezier_vertex'); _mw_curve_vertex = globals().get('curve_vertex')\n"
             + "_mw_quadratic_vertex = globals().get('quadratic_vertex')\n"
             + "def begin_shape(*a):\n"
             + "    if _is_shape(a):\n"
             + "        sh = a[0]; sh.verts = []; sh.skind = a[1] if len(a) > 1 else None\n"
             + "        return None\n"
             + "    return _mw_begin_shape(*a)\n"
             + "def end_shape(*a):\n"
             + "    if _is_shape(a):\n"
             + "        a[0].close = len(a) > 1 and a[1] == globals().get('CLOSE')\n"
             + "        return None\n"
             + "    return _mw_end_shape(*a)\n"
             + "def _rec(tag, base):\n"
             + "    def f(*a):\n"
             + "        if _is_shape(a):\n"
             + "            a[0].verts.append((tag,) + tuple(a[1:]))\n"
             + "            return None\n"
             + "        return base(*a)\n"
             + "    return f\n"
             + "vertex = _rec('v', _mw_vertex)\n"
             + "if _mw_bezier_vertex: bezier_vertex = _rec('b', _mw_bezier_vertex)\n"
             + "if _mw_curve_vertex: curve_vertex = _rec('c', _mw_curve_vertex)\n"
             + "if _mw_quadratic_vertex: quadratic_vertex = _rec('q', _mw_quadratic_vertex)\n"
             + "_fill0 = fill; _stroke0 = stroke; _nofill0 = no_fill; _nostroke0 = no_stroke; _sw0 = stroke_weight\n"
             + "def _styler(attr, base, off=False):\n"
             + "    def f(*a):\n"
             + "        if _is_shape(a):\n"
             + "            setattr(a[0], attr, False if off else tuple(a[1:]))\n"
             + "            return None\n"
             + "        return base(*a)\n"
             + "    return f\n"
             + "fill = _styler('fill', _fill0); stroke = _styler('stroke', _stroke0)\n"
             + "no_fill = _styler('fill', _nofill0, True); no_stroke = _styler('stroke', _nostroke0, True)\n"
             + "stroke_weight = _styler('sweight', _sw0)\n"
             + "def set_fill(sh, *c): sh.fill = False if c == (False,) else tuple(c)\n"
             + "def set_stroke(sh, *c): sh.stroke = False if c == (False,) else tuple(c)\n"
             + "def set_stroke_weight(sh, w): sh.sweight = (w,)\n"
             + "def _xform(op, base):\n"
             + "    def f(*a):\n"
             + "        if not _is_shape(a): return base(*a)\n"
             + "        sh = a[0]; args = list(a[1:])\n"
             + "        if sh.ops and sh.ops[-1][0] == op:\n"
             + "            last = list(sh.ops[-1][1])\n"
             + "            if op == 'scale':\n"
             + "                if len(args) == 1: args = [args[0], args[0]]\n"
             + "                if len(last) == 1: last = [last[0], last[0]]\n"
             + "                if len(last) == len(args):\n"
             + "                    sh.ops[-1] = (op, [x * y for x, y in zip(last, args)])\n"
             + "                    return None\n"
             + "            elif len(last) == len(args):\n"
             + "                sh.ops[-1] = (op, [x + y for x, y in zip(last, args)])\n"
             + "                return None\n"
             + "        sh.ops.append((op, args))\n"
             + "        return None\n"
             + "    return f\n"
             + "_XF = {'translate': translate, 'rotate': rotate, 'scale': scale}\n"
             + "translate = _xform('translate', translate); rotate = _xform('rotate', rotate); scale = _xform('scale', scale)\n"
             + "for _n in ('rotate_x', 'rotate_y', 'rotate_z'):\n"
             + "    if _n in globals():\n"
             + "        _XF[_n] = globals()[_n]\n"
             + "        globals()[_n] = _xform(_n, globals()[_n])\n"
             + "_mw_reset_matrix = globals().get('reset_matrix')\n"
             + "def reset_matrix(*a):\n"
             + "    if _is_shape(a):\n"
             + "        a[0].ops = []\n"
             + "        return None\n"
             + "    return _mw_reset_matrix(*a)\n"
             + "def _replay(sh):\n"
             + "    if not sh.visible: return\n"
             + "    push_matrix()\n"
             + "    for op, args in sh.ops: _XF[op](*args)\n"
             + "    styled = sh.fill is not None or sh.stroke is not None or sh.sweight is not None\n"
             + "    if styled: push_style()\n"
             + "    if sh.fill is False: _nofill0()\n"
             + "    elif sh.fill is not None: _fill0(*sh.fill)\n"
             + "    if sh.stroke is False: _nostroke0()\n"
             + "    elif sh.stroke is not None: _stroke0(*sh.stroke)\n"
             + "    if sh.sweight is not None: _sw0(*sh.sweight)\n"
             + "    if sh.kind == 'group':\n"
             + "        for c in sh.children: _replay(c)\n"
             + "    elif sh.kind == 'prim':\n"
             + "        globals()[sh.prim](*sh.args)\n"
             + "    else:\n"
             + "        if sh.skind is not None: _mw_begin_shape(sh.skind)\n"
             + "        else: _mw_begin_shape()\n"
             + "        for v in sh.verts:\n"
             + "            t = v[0]\n"
             + "            if t == 'v': _mw_vertex(*v[1:])\n"
             + "            elif t == 'b': _mw_bezier_vertex(*v[1:])\n"
             + "            elif t == 'c': _mw_curve_vertex(*v[1:])\n"
             + "            elif t == 'q': _mw_quadratic_vertex(*v[1:])\n"
             + "        if sh.close: _mw_end_shape(CLOSE)\n"
             + "        else: _mw_end_shape()\n"
             + "    if styled: pop_style()\n"
             + "    pop_matrix()\n"
             + "_shape_mode = [globals().get('CORNER')]\n"
             + "def shape_mode(m): _shape_mode[0] = m\n"
             + "def _bounds(sh):\n"
             + "    pts = [v[1:3] for v in sh.verts if v[0] == 'v']\n"
             + "    if not pts: return None\n"
             + "    xs = [q[0] for q in pts]; ys = [q[1] for q in pts]\n"
             + "    return min(xs), min(ys), max(xs), max(ys)\n"
             + "def shape(sh, x=0, y=0, *wh):\n"
             + "    push_matrix()\n"
             + "    _XF['translate'](x, y)\n"
             + "    b = _bounds(sh) if sh.kind == 'path' else None\n"
             + "    if b and wh and len(wh) >= 2 and b[2] > b[0] and b[3] > b[1]:\n"
             + "        _XF['scale'](wh[0] / (b[2] - b[0]), wh[1] / (b[3] - b[1]))\n"
             + "        _XF['translate'](-b[0], -b[1])\n"
             + "    elif b and _shape_mode[0] == globals().get('CENTER'):\n"
             + "        _XF['translate'](-(b[0] + b[2]) / 2.0, -(b[1] + b[3]) / 2.0)\n"
             + "    _replay(sh)\n"
             + "    pop_matrix()\n"
             + "def add_child(g, c): g.children.append(c)\n"
             + "def get_child(g, i): return g.children[i]\n"
             + "def get_child_count(g): return len(g.children)\n"
             + "def _vidx(sh): return [i for i, v in enumerate(sh.verts) if v[0] == 'v']\n"
             + "def get_vertex_count(sh): return len(_vidx(sh))\n"
             + "def get_vertex(sh, i): return list(sh.verts[_vidx(sh)[i]][1:])\n"
             + "def set_vertex(sh, i, *xy): sh.verts[_vidx(sh)[i]] = ('v',) + tuple(xy)\n"
             + "def set_visible(sh, b): sh.visible = b is not False\n"
             + "def is_visible(sh): return sh.visible\n"
             + "\n");
        emit("_SVG_NAMED = {'black': (0, 0, 0), 'white': (255, 255, 255), 'red': (255, 0, 0), 'green': (0, 128, 0),\n"
             + "              'lime': (0, 255, 0), 'blue': (0, 0, 255), 'yellow': (255, 255, 0), 'gray': (128, 128, 128),\n"
             + "              'grey': (128, 128, 128), 'orange': (255, 165, 0), 'purple': (128, 0, 128), 'cyan': (0, 255, 255),\n"
             + "              'magenta': (255, 0, 255), 'brown': (165, 42, 42), 'pink': (255, 192, 203), 'navy': (0, 0, 128)}\n"
             + "def _svg_color(v):\n"
             + "    if v is None: return None\n"
             + "    v = v.strip().lower()\n"
             + "    if v in ('none', 'transparent'): return False\n"
             + "    if v.startswith('#'):\n"
             + "        h = v[1:]\n"
             + "        if len(h) == 3: h = ''.join(c * 2 for c in h)\n"
             + "        try: return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))\n"
             + "        except ValueError: return None\n"
             + "    if v.startswith('rgb'):\n"
             + "        parts = [q.strip() for q in v[v.find('(') + 1:v.rfind(')')].split(',')]\n"
             + "        try: return tuple(int(float(q[:-1]) * 2.55) if q.endswith('%') else int(float(q)) for q in parts[:3])\n"
             + "        except ValueError: return None\n"
             + "    return _SVG_NAMED.get(v)\n"
             + "def _svg_style(el, parent):\n"
             + "    st = dict(parent)\n"
             + "    for k in ('fill', 'stroke', 'stroke-width', 'fill-opacity', 'stroke-opacity', 'opacity'):\n"
             + "        if el.get(k) is not None: st[k] = el.get(k)\n"
             + "    for part in (el.get('style') or '').split(';'):\n"
             + "        if ':' in part:\n"
             + "            k, v = part.split(':', 1)\n"
             + "            st[k.strip()] = v.strip()\n"
             + "    return st\n"
             + "def _svg_float(v, d=1.0):\n"
             + "    try: return float(str(v).replace('px', ''))\n"
             + "    except (TypeError, ValueError): return d\n"
             + "def _svg_apply_style(sh, st):\n"
             + "    op = _svg_float(st.get('opacity', 1))\n"
             + "    f = _svg_color(st.get('fill', 'black'))\n"
             + "    if f is False: sh.fill = False\n"
             + "    elif f is not None:\n"
             + "        fo = _svg_float(st.get('fill-opacity', 1)) * op\n"
             + "        sh.fill = f + ((int(fo * 255),) if fo < 1 else ())\n"
             + "    s = _svg_color(st.get('stroke', 'none'))\n"
             + "    if s is False: sh.stroke = False\n"
             + "    elif s is not None:\n"
             + "        so = _svg_float(st.get('stroke-opacity', 1)) * op\n"
             + "        sh.stroke = s + ((int(so * 255),) if so < 1 else ())\n"
             + "        sh.sweight = (_svg_float(st.get('stroke-width', 1)),)\n"
             + "def _svg_transform(t):\n"
             + "    ops = []\n"
             + "    t = t or ''\n"
             + "    while '(' in t and ')' in t:\n"
             + "        name, rest = t.split('(', 1)\n"
             + "        args_s, t = rest.split(')', 1)\n"
             + "        name = name.strip().strip(',').strip()\n"
             + "        try: nums = [float(q) for q in args_s.replace(',', ' ').split()]\n"
             + "        except ValueError: continue\n"
             + "        if not nums: continue\n"
             + "        if name == 'translate': ops.append(('translate', [nums[0], nums[1] if len(nums) > 1 else 0.0]))\n"
             + "        elif name == 'scale': ops.append(('scale', [nums[0], nums[1] if len(nums) > 1 else nums[0]]))\n"
             + "        elif name == 'rotate':\n"
             + "            a = _math.radians(nums[0])\n"
             + "            if len(nums) == 3: ops += [('translate', [nums[1], nums[2]]), ('rotate', [a]), ('translate', [-nums[1], -nums[2]])]\n"
             + "            else: ops.append(('rotate', [a]))\n"
             + "        elif name == 'matrix' and len(nums) == 6:\n"
             + "            a, b, c, d, e, f = nums\n"
             + "            sx = _math.hypot(a, b)\n"
             + "            sy = (a * d - b * c) / sx if sx else 0.0\n"
             + "            ops += [('translate', [e, f]), ('rotate', [_math.atan2(b, a)]), ('scale', [sx, sy])]\n"
             + "    return ops\n"
             + "def _svg_tokens(d):\n"
             + "    out = []; i = 0; n = len(d)\n"
             + "    while i < n:\n"
             + "        c = d[i]\n"
             + "        if c.isalpha() and c not in 'eE':\n"
             + "            out.append(c); i += 1\n"
             + "        elif c in '+-.0123456789':\n"
             + "            j = i\n"
             + "            if d[j] in '+-': j += 1\n"
             + "            dot = False; exp = False\n"
             + "            while j < n:\n"
             + "                ch = d[j]\n"
             + "                if ch.isdigit(): j += 1\n"
             + "                elif ch == '.' and not dot and not exp:\n"
             + "                    dot = True; j += 1\n"
             + "                elif ch in 'eE' and not exp:\n"
             + "                    exp = True; j += 1\n"
             + "                    if j < n and d[j] in '+-': j += 1\n"
             + "                else: break\n"
             + "            try: out.append(float(d[i:j]))\n"
             + "            except ValueError: pass\n"
             + "            i = max(j, i + 1)\n"
             + "        else: i += 1\n"
             + "    return out\n"
             + "def _svg_arc(x1, y1, rx, ry, phi, fa, fs, x2, y2):\n"
             + "    if rx == 0 or ry == 0: return [(x2, y2)]\n"
             + "    phi = _math.radians(phi); cp = _math.cos(phi); sp = _math.sin(phi)\n"
             + "    dx2 = (x1 - x2) / 2.0; dy2 = (y1 - y2) / 2.0\n"
             + "    x1p = cp * dx2 + sp * dy2; y1p = -sp * dx2 + cp * dy2\n"
             + "    rx = abs(rx); ry = abs(ry)\n"
             + "    lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)\n"
             + "    if lam > 1:\n"
             + "        k = _math.sqrt(lam); rx *= k; ry *= k\n"
             + "    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p\n"
             + "    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p\n"
             + "    co = _math.sqrt(max(0.0, num / den)) if den else 0.0\n"
             + "    if bool(fa) == bool(fs): co = -co\n"
             + "    cxp = co * rx * y1p / ry; cyp = -co * ry * x1p / rx\n"
             + "    cx = cp * cxp - sp * cyp + (x1 + x2) / 2.0; cy = sp * cxp + cp * cyp + (y1 + y2) / 2.0\n"
             + "    def ang(ux, uy, vx, vy): return _math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)\n"
             + "    t1 = ang(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)\n"
             + "    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)\n"
             + "    if not fs and dt > 0: dt -= 2 * _math.pi\n"
             + "    elif fs and dt < 0: dt += 2 * _math.pi\n"
             + "    n = max(4, int(abs(dt) / (_math.pi / 16)) + 1)\n"
             + "    pts = []\n"
             + "    for k in range(1, n + 1):\n"
             + "        t = t1 + dt * k / n\n"
             + "        pts.append((cx + rx * _math.cos(t) * cp - ry * _math.sin(t) * sp,\n"
             + "                    cy + rx * _math.cos(t) * sp + ry * _math.sin(t) * cp))\n"
             + "    return pts\n"
             + "def _svg_path(d):\n"
             + "    toks = _svg_tokens(d)\n"
             + "    subs = []; st = {'i': 0, 'cur': None, 'x': 0.0, 'y': 0.0, 'sx': 0.0, 'sy': 0.0, 'lc': None, 'lq': None}\n"
             + "    def num():\n"
             + "        v = toks[st['i']]; st['i'] += 1\n"
             + "        if isinstance(v, str): raise IndexError\n"
             + "        return v\n"
             + "    def add(v):\n"
             + "        if st['cur'] is None:\n"
             + "            st['cur'] = [[('v', st['x'], st['y'])], False]; subs.append(st['cur'])\n"
             + "        st['cur'][0].append(v)\n"
             + "    cmd = None\n"
             + "    while st['i'] < len(toks):\n"
             + "        t = toks[st['i']]\n"
             + "        if isinstance(t, str):\n"
             + "            cmd = t; st['i'] += 1\n"
             + "            if cmd.upper() != 'Z' and st['i'] < len(toks) and isinstance(toks[st['i']], str): continue\n"
             + "        elif cmd is None:\n"
             + "            st['i'] += 1; continue\n"
             + "        C = cmd.upper(); rel = cmd.islower(); x = st['x']; y = st['y']\n"
             + "        ox = x if rel else 0.0; oy = y if rel else 0.0\n"
             + "        try:\n"
             + "            if C == 'Z':\n"
             + "                if st['cur'] is not None: st['cur'][1] = True\n"
             + "                st['x'], st['y'] = st['sx'], st['sy']; st['cur'] = None\n"
             + "                if not isinstance(t, str): st['i'] += 1\n"
             + "            elif C == 'M':\n"
             + "                nx = num() + ox; ny = num() + oy\n"
             + "                st['cur'] = [[('v', nx, ny)], False]; subs.append(st['cur'])\n"
             + "                st['x'], st['y'], st['sx'], st['sy'] = nx, ny, nx, ny\n"
             + "                cmd = 'l' if rel else 'L'\n"
             + "            elif C == 'L':\n"
             + "                nx = num() + ox; ny = num() + oy\n"
             + "                add(('v', nx, ny)); st['x'], st['y'] = nx, ny\n"
             + "            elif C == 'H':\n"
             + "                nx = num() + ox; add(('v', nx, y)); st['x'] = nx\n"
             + "            elif C == 'V':\n"
             + "                ny = num() + oy; add(('v', x, ny)); st['y'] = ny\n"
             + "            elif C in 'CS':\n"
             + "                if C == 'C':\n"
             + "                    x1 = num() + ox; y1 = num() + oy\n"
             + "                else:\n"
             + "                    x1, y1 = (2 * x - st['lc'][0], 2 * y - st['lc'][1]) if st['lc'] else (x, y)\n"
             + "                x2 = num() + ox; y2 = num() + oy; nx = num() + ox; ny = num() + oy\n"
             + "                add(('b', x1, y1, x2, y2, nx, ny)); st['lc'] = (x2, y2); st['x'], st['y'] = nx, ny\n"
             + "            elif C in 'QT':\n"
             + "                if C == 'Q':\n"
             + "                    qx = num() + ox; qy = num() + oy\n"
             + "                else:\n"
             + "                    qx, qy = (2 * x - st['lq'][0], 2 * y - st['lq'][1]) if st['lq'] else (x, y)\n"
             + "                nx = num() + ox; ny = num() + oy\n"
             + "                add(('b', x + 2.0 / 3 * (qx - x), y + 2.0 / 3 * (qy - y),\n"
             + "                     nx + 2.0 / 3 * (qx - nx), ny + 2.0 / 3 * (qy - ny), nx, ny))\n"
             + "                st['lq'] = (qx, qy); st['x'], st['y'] = nx, ny\n"
             + "            elif C == 'A':\n"
             + "                rx = num(); ry = num(); rot = num(); fa = num(); fs = num()\n"
             + "                nx = num() + ox; ny = num() + oy\n"
             + "                for q in _svg_arc(x, y, rx, ry, rot, fa, fs, nx, ny): add(('v',) + q)\n"
             + "                st['x'], st['y'] = nx, ny\n"
             + "            else:\n"
             + "                st['i'] += 1\n"
             + "        except IndexError:\n"
             + "            break\n"
             + "        if C not in 'CS': st['lc'] = None\n"
             + "        if C not in 'QT': st['lq'] = None\n"
             + "    return [(v, c) for v, c in subs if len(v) > 1]\n"
             + "def _svg_build(el, st):\n"
             + "    tag = el.tag.split('}')[-1]\n"
             + "    st = _svg_style(el, st)\n"
             + "    def fnum(k): return _svg_float(el.get(k, 0), 0.0)\n"
             + "    sh = None\n"
             + "    if tag in ('svg', 'g'):\n"
             + "        sh = _PShape('group')\n"
             + "        for ch in el:\n"
             + "            c = _svg_build(ch, st)\n"
             + "            if c is not None: sh.children.append(c)\n"
             + "    elif tag == 'path':\n"
             + "        sh = _PShape('group')\n"
             + "        for verts, closed in _svg_path(el.get('d', '')):\n"
             + "            q = _PShape('path'); q.verts = verts; q.close = closed\n"
             + "            _svg_apply_style(q, st); sh.children.append(q)\n"
             + "    elif tag == 'rect':\n"
             + "        x, y, w, h = fnum('x'), fnum('y'), fnum('width'), fnum('height')\n"
             + "        sh = _PShape('path'); sh.verts = [('v', x, y), ('v', x + w, y), ('v', x + w, y + h), ('v', x, y + h)]\n"
             + "        sh.close = True; _svg_apply_style(sh, st)\n"
             + "    elif tag == 'circle':\n"
             + "        r = fnum('r'); sh = _PShape('prim', 'ellipse', (fnum('cx'), fnum('cy'), 2 * r, 2 * r)); _svg_apply_style(sh, st)\n"
             + "    elif tag == 'ellipse':\n"
             + "        sh = _PShape('prim', 'ellipse', (fnum('cx'), fnum('cy'), 2 * fnum('rx'), 2 * fnum('ry'))); _svg_apply_style(sh, st)\n"
             + "    elif tag == 'line':\n"
             + "        sh = _PShape('path'); sh.verts = [('v', fnum('x1'), fnum('y1')), ('v', fnum('x2'), fnum('y2'))]\n"
             + "        _svg_apply_style(sh, st); sh.fill = False\n"
             + "    elif tag in ('polyline', 'polygon'):\n"
             + "        try: nums = [float(q) for q in (el.get('points') or '').replace(',', ' ').split()]\n"
             + "        except ValueError: nums = []\n"
             + "        sh = _PShape('path'); sh.verts = [('v', nums[k], nums[k + 1]) for k in range(0, len(nums) - 1, 2)]\n"
             + "        sh.close = tag == 'polygon'; _svg_apply_style(sh, st)\n"
             + "        if tag == 'polyline': sh.fill = False\n"
             + "    if sh is not None: sh.ops = _svg_transform(el.get('transform'))\n"
             + "    return sh\n"
             + "def _load_svg(path):\n"
             + "    import xml.etree.ElementTree as _ET\n"
             + "    root = _ET.parse(path).getroot()\n"
             + "    sh = _svg_build(root, {})\n"
             + "    w = _svg_float(root.get('width'), 0.0); h = _svg_float(root.get('height'), 0.0)\n"
             + "    if (not w or not h) and root.get('viewBox'):\n"
             + "        try: vb = [float(q) for q in root.get('viewBox').replace(',', ' ').split()]\n"
             + "        except ValueError: vb = []\n"
             + "        if len(vb) == 4: w, h = vb[2], vb[3]\n"
             + "    if w and h: sh.args = (w, h)\n"
             + "    return sh\n"
             + "def _load_obj(path):\n"
             + "    mk = globals().get('create_geometry')\n"
             + "    if mk is None: raise _SchemeError('load-shape: OBJ files need a mewnala build with create-geometry')\n"
             + "    vs = []; tris = []\n"
             + "    with open(path) as fh:\n"
             + "        for line in fh:\n"
             + "            q = line.split()\n"
             + "            if not q: continue\n"
             + "            if q[0] == 'v' and len(q) >= 4: vs.append((float(q[1]), float(q[2]), float(q[3])))\n"
             + "            elif q[0] == 'f' and len(q) >= 4:\n"
             + "                idx = []\n"
             + "                for item in q[1:]:\n"
             + "                    k = int(item.split('/')[0]); idx.append(k - 1 if k > 0 else len(vs) + k)\n"
             + "                for k in range(1, len(idx) - 1): tris.append((idx[0], idx[k], idx[k + 1]))\n"
             + "    g = mk(); n = 0\n"
             + "    for a, b, c in tris:\n"
             + "        A, B, C = vs[a], vs[b], vs[c]\n"
             + "        ux, uy, uz = B[0] - A[0], B[1] - A[1], B[2] - A[2]\n"
             + "        wx, wy, wz = C[0] - A[0], C[1] - A[1], C[2] - A[2]\n"
             + "        nx, ny, nz = uy * wz - uz * wy, uz * wx - ux * wz, ux * wy - uy * wx\n"
             + "        ln = _math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0\n"
             + "        for P in (A, B, C):\n"
             + "            g.normal(nx / ln, ny / ln, nz / ln); g.vertex(*P); g.index(n); n += 1\n"
             + "    return _PShape('geom', g)\n"
             + "def load_shape(name, *a):\n"
             + "    path = _find_data(name)\n"
             + "    low = name.lower()\n"
             + "    if low.endswith('.svg'): return _load_svg(path)\n"
             + "    if low.endswith('.obj'): return _load_obj(path)\n"
             + "    raise _SchemeError('load-shape: only .svg and .obj files are supported, got ' + name)\n"
             + "_replay_base = _replay\n"
             + "def _replay(sh):\n"
             + "    if sh.kind != 'geom': return _replay_base(sh)\n"
             + "    if not sh.visible: return\n"
             + "    push_matrix()\n"
             + "    for op, args in sh.ops: _XF[op](*args)\n"
             + "    styled = sh.fill is not None or sh.stroke is not None\n"
             + "    if styled: push_style()\n"
             + "    if sh.fill is False: _nofill0()\n"
             + "    elif sh.fill is not None: _fill0(*sh.fill)\n"
             + "    globals()['draw_geometry'](sh.prim)\n"
             + "    if styled: pop_style()\n"
             + "    pop_matrix()\n"
             + "_shape_base = shape\n"
             + "def shape(sh, x=0, y=0, *wh):\n"
             + "    if sh.kind == 'group' and len(sh.args) == 2 and len(wh) >= 2 and sh.args[0] and sh.args[1]:\n"
             + "        push_matrix()\n"
             + "        _XF['translate'](x, y)\n"
             + "        _XF['scale'](wh[0] / sh.args[0], wh[1] / sh.args[1])\n"
             + "        _replay(sh)\n"
             + "        pop_matrix()\n"
             + "        return None\n"
             + "    return _shape_base(sh, x, y, *wh)\n"
             + "\n");
        emit("def _mt3(a, b, c, d, t): return a, b, c, d, t, 1.0 - t\n"
             + "if 'bezier_point' not in globals():\n"
             + "    def bezier_point(a, b, c, d, t):\n"
             + "        mt = 1.0 - t\n"
             + "        return mt * mt * mt * a + 3 * mt * mt * t * b + 3 * mt * t * t * c + t * t * t * d\n"
             + "if 'bezier_tangent' not in globals():\n"
             + "    def bezier_tangent(a, b, c, d, t):\n"
             + "        return 3 * t * t * (-a + 3 * b - 3 * c + d) + 6 * t * (a - 2 * b + c) + 3 * (-a + b)\n"
             + "if 'curve_point' not in globals():\n"
             + "    def curve_point(a, b, c, d, t):\n"
             + "        return 0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (-a + 3 * b - 3 * c + d) * t * t * t)\n"
             + "if 'curve_tangent' not in globals():\n"
             + "    def curve_tangent(a, b, c, d, t):\n"
             + "        return 0.5 * ((-a + c) + 2 * (2 * a - 5 * b + 4 * c - d) * t + 3 * (-a + 3 * b - 3 * c + d) * t * t)\n"
             + "if 'println' not in globals():\n"
             + "    def println(*a):\n"
             + "        _sys.stdout.write(' '.join(_show(x) for x in a) + chr(10)); _sys.stdout.flush()\n"
             + "_mw_emissive = getattr(_mewnala, 'emissive', None)\n"
             + "if _mw_emissive:\n"
             + "    def emissive(*a): return _mw_emissive(*_as_color(a))\n"
             + "if _mw_roughness and 'shininess' not in dir(_mewnala):\n"
             + "    def shininess(s): return _mw_roughness(max(0.0, min(1.0, 1.0 - float(s) / 128.0)))\n"
             + "for _n in ('specular', 'ambient'):\n"
             + "    if _n not in dir(_mewnala): globals()[_n] = _unsupported(_n)\n"
             + "_OBJ_TYPES = tuple(t for t in (getattr(_mewnala, 'Graphics', None), getattr(_mewnala, 'Image', None),\n"
             + "                               getattr(_mewnala, 'Geometry', None)) if isinstance(t, type))\n"
             + "_COLOR_FNS = ('fill', 'stroke', 'background', 'tint')\n"
             + "def _objfirst(name, base):\n"
             + "    def f(*a, **k):\n"
             + "        if a and isinstance(a[0], _OBJ_TYPES) and hasattr(a[0], name):\n"
             + "            rest = a[1:]\n"
             + "            if name in _COLOR_FNS: rest = _as_color(rest)\n"
             + "            return getattr(a[0], name)(*rest, **k)\n"
             + "        if base is None:\n"
             + "            raise _SchemeError(name.replace('_', '-') + ': needs an image, graphics or geometry as its first argument')\n"
             + "        return base(*a, **k)\n"
             + "    return f\n"
             + "_obj_methods = _builtins.set()\n"
             + "for _t in _OBJ_TYPES: _obj_methods |= {m for m in dir(_t) if not m.startswith('_') and callable(getattr(_t, m, None))}\n"
             + "for _n in sorted(_obj_methods):\n"
             + "    _cur = globals().get(_n)\n"
             + "    if _cur is None: globals()[_n] = _objfirst(_n, None)\n"
             + "    elif callable(_cur) and not isinstance(_cur, type): globals()[_n] = _objfirst(_n, _cur)\n"
             + "\n");
        emit("# -- sketch --\n");
        for (SExpr f : forms) {
            if (f.isForm("import")) continue;
            emitTopLevel(f);
            emit("\n");
        }

        if (!out.toString().contains("\nrun()")) emit("\n_scm_wrap(globals())\nrun()\n");
        return out.toString();
    }

    // ── Top-level ─────────────────────────────────────────────────────────────

    private void emitTopLevel(SExpr f) throws SchemeException {
        if (f.isForm("define")) { hoistLambdas(f, 0); emitDefine(f, 0); return; }
        if (f.isForm("define-record-type")) { emitRecordType(f); return; }
        if (f.isForm("define-syntax") || f.isForm("let-syntax")) {
            marker(f.line(), 0); emit("# define-syntax (skipped)\n"); return;
        }
        marker(f.line(), 0);
        emitStmt(f, 0);
    }

    // ── define ────────────────────────────────────────────────────────────────

    private void emitDefine0(SExpr f, int ind) throws SchemeException {
        List<SExpr> parts = list(f).subList(1, list(f).size());
        if (parts.isEmpty()) throw new SchemeException("empty define", f.line());
        SExpr head = parts.get(0);
        if (head instanceof SExpr.Pair hdr) {
            emitProcDef(hdr, parts.subList(1, parts.size()), ind, f.line());
        } else if (head instanceof SExpr.Sym s) {
            String py = snake(s.name());
            if (ind == 0) globalVars.add(py);
            marker(f.line(), ind); indent(ind);
            emit(py + " = ");
            if (parts.size() > 1) emitExpr(parts.get(1), ind); else emit("None");
            emit("\n");
        }
    }

    private static final java.util.Set<String> LIFECYCLE_FNS = java.util.Set.of(
        "setup", "draw", "mouse_pressed", "mouse_released",
        "mouse_moved", "mouse_dragged", "mouse_wheel_event",
        "key_pressed", "key_released"
    );

    

    private void emitProcDef(SExpr.Pair hdr, List<SExpr> body, int ind, int srcLine)
            throws SchemeException {
        List<SExpr> hl = hdr.toList();
        String name = snake(((SExpr.Sym) hl.get(0)).name());
        marker(srcLine, ind); indent(ind);
        emit("def " + name + "(");

        List<String> params = new ArrayList<>();
        boolean rest = false; String restParam = null;
        SExpr cur = hdr.cdr();
        while (cur instanceof SExpr.Pair p) {
            if (p.car() instanceof SExpr.Sym s) params.add(snake(s.name()));
            cur = p.cdr();
        }
        if (cur instanceof SExpr.Sym s) { rest = true; restParam = snake(s.name()); }

        emit(String.join(", ", params));
        if (rest) { if (!params.isEmpty()) emit(", "); emit("*" + restParam); }
        emit("):\n");

        boolean wasIn = inFunction;
        Set<String> wasLocal = new LinkedHashSet<>(localVars);
        inFunction = true; localVars.clear(); localVars.addAll(params);
        if (rest) localVars.add(restParam);

        // Hoist global declarations to top of function
        Set<String> setTargets = findSetTargets(body);
        for (String g : setTargets) {
            if (globalVars.contains(g) && !localVars.contains(g)) {
                indent(ind + 1); emit("global " + g + "\n");
                localVars.add(g);
            }
        }

        // A function calling itself in tail position loops instead of recursing.
        String selfName = ((SExpr.Sym) hl.get(0)).name();
        if (!rest && !LIFECYCLE_FNS.contains(name) && !params.contains(name))
            emitLoopBody(body, selfName, params, ind + 1);
        else
            emitBody(body, ind + 1, LIFECYCLE_FNS.contains(name));

        inFunction = wasIn; localVars.clear(); localVars.addAll(wasLocal);
    }

    private void emitBody(List<SExpr> body, int ind) throws SchemeException {
        emitBody(body, ind, false);
    }

    private void emitBody(List<SExpr> body, int ind, boolean noReturn) throws SchemeException {
        if (body.isEmpty()) { indent(ind); emit("pass\n"); return; }
        for (int i = 0; i < body.size() - 1; i++) {
            marker(body.get(i).line(), ind);
            emitStmt(body.get(i), ind);
        }
        SExpr last = body.get(body.size() - 1);
        marker(last.line(), ind);
        if (tcoName != null && !noReturn && emitTailLoop(last, ind, noReturn)) return;
        boolean isNamedLet = last.isForm("let") && last instanceof SExpr.Pair lp && lp.toList().size() > 1 && lp.toList().get(1) instanceof SExpr.Sym;
        if (isNamedLet && !noReturn) tailLets.add(last);
        boolean isPlainLet = !isNamedLet && (last.isForm("let") || last.isForm("let*") || last.isForm("letrec") || last.isForm("letrec*"));
        if (isPlainLet && !noReturn) { tailLets.add(last); emitStmt(last, ind); return; }
        if ((last.isForm("when") || last.isForm("unless")) && !noReturn) {
            List<SExpr> wf = list(last);
            hoistLambdas(wf.get(1), ind);
            indent(ind); emit(last.isForm("unless") ? "if not " : "if "); emitTest(wf.get(1), ind); emit(":\n");
            emitBody(wf.subList(2, wf.size()), ind+1, false);
            return;
        }
        boolean hasNamedLetBranch = last.isForm("if") && containsNamedLet(last);
        if (hasNamedLetBranch && !noReturn) {
            List<SExpr> f = ((SExpr.Pair) last).toList();
            indent(ind); emit("if "); emitTest(f.get(1), ind); emit(":\n");
            emitBody(List.of(f.get(2)), ind+1, false);
            if (f.size() > 3) { indent(ind); emit("else:\n"); emitBody(List.of(f.get(3)), ind+1, false); }
            return;
        }
        if (!noReturn && !last.isForm("guard") && !hasNamedLetBranch && !isNamedLet && (isSimple(last) || last.isForm("if") || last.isForm("cond") ||
            last.isForm("let") || last.isForm("let*") || last.isForm("letrec") ||
            last.isForm("and") || last.isForm("or") || last.isForm("begin"))) {
            hoistLambdas(last, ind);
            indent(ind); emit("return "); emitExpr(last, ind); emit("\n");
        } else {
            emitStmt(last, ind);
        }
    }

    

    // ── define-record-type ────────────────────────────────────────────────────

    private void emitRecordType(SExpr f) throws SchemeException {
        List<SExpr> p = list(f);
        String cls = pascal(sym(p.get(1)));
        List<SExpr> ctor = list(p.get(2));
        String ctorName = snake(sym(ctor.get(0)));
        List<String> fields = new ArrayList<>();
        for (int i = 1; i < ctor.size(); i++) fields.add(snake(sym(ctor.get(i))));
        String pred = snake(sym(p.get(3)));

        marker(f.line(), 0);
        emit("class " + cls + ":\n");
        emit("    __slots__ = " + pyStrList(fields) + "\n");
        emit("    def __init__(self, " + String.join(", ", fields) + "):\n");
        for (String fld : fields) emit("        self." + fld + " = " + fld + "\n");
        emit(ctorName + " = " + cls + "\n");
        emit(pred + " = lambda x: isinstance(x, " + cls + ")\n");

        for (int i = 4; i < p.size(); i++) {
            List<SExpr> fd = list(p.get(i));
            String fld = snake(sym(fd.get(0)));
            String acc = snake(sym(fd.get(1)));
            emit(acc + " = lambda r: r." + fld + "\n");
            if (fd.size() > 2) {
                String mod = snake(sym(fd.get(2)));
                emit(mod + " = lambda r, v: setattr(r, '" + fld + "', v)\n");
            }
        }
        emit("\n");
    }

    // ── Statements ────────────────────────────────────────────────────────────

    private void emitStmt(SExpr e, int ind) throws SchemeException {
        hoistLambdas(e, ind);
        if (e.isForm("define-values")) {
            List<SExpr> dv = list(e);
            List<String> names = new ArrayList<>();
            for (SExpr v : list(dv.get(1))) names.add(snakeBind(sym(v)));
            for (String nm : names) { if (inFunction) localVars.add(nm); else globalVars.add(nm); }
            indent(ind); emit(String.join(", ", names) + (names.size() == 1 ? "," : "") + " = _vals(");
            emitExpr(dv.get(2), ind); emit(")\n");
            return;
        }
        if (e instanceof SExpr.Nil) { indent(ind); emit("pass\n"); return; }
        if (e.isForm("define"))           { emitDefine(e, ind); return; }
        if (e.isForm("define-record-type")){ emitRecordType(e); return; }
        if (e.isForm("set!"))             { emitSet(e, ind); return; }
        if (e.isForm("begin"))            { emitBegin(e, ind); return; }
        if (e.isForm("if"))               { emitIf(e, ind, true); return; }
        if (e.isForm("cond"))             { emitCond(e, ind); return; }
        if (e.isForm("case"))             { emitCase(e, ind); return; }
        if (e.isForm("guard"))            { emitGuard(e, ind); return; }
        if (e.isForm("when"))             { emitWhen(e, ind, false); return; }
        if (e.isForm("unless"))           { emitWhen(e, ind, true); return; }
        if (e.isForm("let") || e.isForm("let*") || e.isForm("letrec") || e.isForm("letrec*"))
                                          { emitLet(e, ind); return; }
        if (e.isForm("do"))               { emitDo(e, ind); return; }
        if (e.isForm("for-each"))         { emitForEach(e, ind); return; }
        if (e.isForm("with-matrix"))      { emitWithMatrix(e, ind); return; }
        if (e.isForm("display"))          { indent(ind); emit("_display("); emitArgs(list(e).subList(1, list(e).size()), ind); emit(")\n"); return; }
        if (e.isForm("newline"))          { indent(ind); if (list(e).size() > 1) { emit("_newline("); emitExpr(nth(e,1), ind); emit(")\n"); } else emit("print()\n"); return; }
        if (e.isForm("write"))            { indent(ind); emit("write("); emitArgs(list(e).subList(1, list(e).size()), ind); emit(")\n"); return; }
        if (e.isForm("define-syntax") || e.isForm("let-syntax")) { indent(ind); emit("pass  # syntax\n"); return; }
        indent(ind); emitExpr(e, ind); emit("\n");
    }

    private void emitSet(SExpr e, int ind) throws SchemeException {
        String name = snake(sym(nth(e,1)));
        indent(ind);

        emit(name + " = "); emitExpr(nth(e,2), ind); emit("\n");
    }

    private void emitBegin(SExpr e, int ind) throws SchemeException {
        List<SExpr> stmts = list(e).subList(1, list(e).size());
        for (SExpr s : stmts) { marker(s.line(), ind); emitStmt(s, ind); }
    }

    private void emitIf(SExpr e, int ind, boolean asStmt) throws SchemeException {
        SExpr test = nth(e,1), then = nth(e,2);
        SExpr els = list(e).size() > 3 ? nth(e,3) : null;
        if (asStmt) {
            indent(ind); emit("if "); emitTest(test, ind); emit(":\n");
            marker(then.line(), ind+1); emitStmt(then, ind+1);
            if (els != null) { indent(ind); emit("else:\n"); marker(els.line(), ind+1); emitStmt(els, ind+1); }
        } else {
            emit("("); emitExpr(then, ind); emit(" if "); emitTest(test, ind); emit(" else ");
            if (els != null) emitExpr(els, ind); else emit("None");
            emit(")");
        }
    }

    private void emitCondExpr(SExpr.Pair form, int ind) throws SchemeException {
        // cond in expression position -> nested ternary: (v if test else ...)
        List<SExpr> clauses = list(form).subList(1, list(form).size());
        emitCondClauses(clauses, 0, ind);
    }

    private void emitCondClauses(List<SExpr> clauses, int i, int ind) throws SchemeException {
        if (i >= clauses.size()) { emit("None"); return; }
        List<SExpr> clause = list(clauses.get(i));
        SExpr test = clause.get(0);
        if (test.isSym("else")) {
            if (clause.size() > 1) emitSeqExpr(clause.subList(1, clause.size()), ind); else emit("None");
            return;
        }
        boolean arrow = clause.size() == 3 && clause.get(1).isSym("=>");
        emit("(");
        if (arrow || clause.size() == 1) {
            // Capture the test once: (f(_c) if (_c := test) else ...) / (_c if (_c := test) else ...)
            String t = "_c" + (++condN);
            if (arrow) { emitExpr(clause.get(2), ind); emit("(" + t + ")"); } else emit(t);
            emit(" if (" + t + " := "); emitExpr(test, ind); emit(")"); if (!isBoolExpr(test)) emit(" is not False");
        } else {
            emitSeqExpr(clause.subList(1, clause.size()), ind);
            emit(" if "); emitTest(test, ind);
        }
        emit(" else ");
        emitCondClauses(clauses, i + 1, ind);
        emit(")");
    }

    // Several forms in expression position: evaluate all in order, value of the last.
    private void emitSeqExpr(List<SExpr> forms, int ind) throws SchemeException {
        if (forms.size() == 1) { emitExpr(forms.get(0), ind); return; }
        emit("(");
        for (int k = 0; k < forms.size(); k++) { if (k > 0) emit(", "); emitExpr(forms.get(k), ind); }
        emit(")[-1]");
    }

    private void emitCond(SExpr e, int ind) throws SchemeException {
        List<SExpr> clauses = list(e).subList(1, list(e).size());
        boolean first = true;
        for (SExpr clause : clauses) {
            List<SExpr> c = list(clause);
            boolean isElse = c.get(0).isSym("else");
            boolean arrow = !isElse && c.size() == 3 && c.get(1).isSym("=>");
            indent(ind);
            if (isElse) emit("else:\n");
            else if (arrow) {
                String t = "_c" + (++condN);
                emit((first ? "if (" : "elif (") + t + " := "); emitExpr(c.get(0), ind); emit(")"); if (!isBoolExpr(c.get(0))) emit(" is not False"); emit(":\n");
                indent(ind+1); emitExpr(c.get(2), ind+1); emit("(" + t + ")\n");
                first = false;
                continue;
            }
            else { emit(first ? "if " : "elif "); emitTest(c.get(0), ind); emit(":\n"); }
            List<SExpr> body = c.subList(1, c.size());
            if (body.isEmpty()) { indent(ind+1); emit("pass\n"); }
            else for (SExpr b : body) emitStmt(b, ind+1);
            first = false;
        }
    }

    private void emitCaseExpr(SExpr.Pair form, int ind) throws SchemeException {
        List<SExpr> parts = list(form);
        SExpr key = parts.get(1);
        List<SExpr> clauses = parts.subList(2, parts.size());
        boolean hasElse = clauses.stream().anyMatch(c -> list(c).get(0).isSym("else"));
        String caseKey = "_c" + (++condN);
        emit("(");
        boolean first2 = true;
        for (int i = 0; i < clauses.size(); i++) {
            List<SExpr> clause = list(clauses.get(i));
            SExpr datums = clause.get(0);
            SExpr val = clause.get(clause.size()-1);
            List<SExpr> vals = clause.subList(1, clause.size());
            if (datums.isSym("else")) {
                if (!first2) emit(" else ");
                emitSeqExpr(vals, ind);
            } else {
                if (!first2) emit(" else ");
                emitSeqExpr(vals, ind);
                emit(" if ");
                // Evaluate the key once: the first test captures it, later tests reuse it.
                if (first2) { emit("(" + caseKey + " := "); emitExpr(key, ind); emit(")"); }
                else emit(caseKey);
                emit(" in ");
                List<SExpr> ds = list(datums);
                emit("(");
                for (int j = 0; j < ds.size(); j++) {
                    if (j > 0) emit(", ");
                    emitQuote(ds.get(j));
                }
                emit(",)");
                first2 = false;
            }
        }
        if (!hasElse) emit(" else None");
        emit(")");
    }

    private void emitGuard(SExpr e, int ind) throws SchemeException {
        // (guard (var clause...) body...)
        List<SExpr> parts = list(e);
        String var = snake(sym(list(parts.get(1)).get(0)));
        List<SExpr> clauses = list(parts.get(1)).subList(1, list(parts.get(1)).size());
        List<SExpr> body = parts.subList(2, parts.size());
        indent(ind); emit("try:\n");
        // emit body - last expression should be returned
        for (int i = 0; i < body.size() - 1; i++) emitStmt(body.get(i), ind+1);
        indent(ind+1); emit("return "); emitExpr(body.get(body.size()-1), ind+1); emit("\n");
        indent(ind); emit("except Exception as " + var + ":\n");
        indent(ind+1); emit(var + " = _unwrap(" + var + ")\n");
        boolean handled = false;
        for (SExpr clause : clauses) {
            List<SExpr> c = list(clause);
            SExpr test = c.get(0);
            if (test.isSym("else") || (test instanceof SExpr.Bool b && b.value())) {
                indent(ind+1);
                if (c.size() > 1) { emit("return "); emitExpr(c.get(c.size()-1), ind+1); emit("\n"); }
                else emit("pass\n");
                handled = true;
            } else {
                indent(ind+1); emit("if "); emitTest(test, ind+1); emit(":\n");
                indent(ind+2);
                if (c.size() > 1) { emit("return "); emitExpr(c.get(c.size()-1), ind+2); emit("\n"); }
                else emit("pass\n");
            }
        }
        if (!handled) { indent(ind+1); emit("raise\n"); }
    }

    private int guardCount = 0;

    private void emitGuardExpr(SExpr.Pair form, int ind) throws SchemeException {
        // guard in expression pos: emit a local def and call it
        List<SExpr> parts = list(form);
        String var = snake(sym(list(parts.get(1)).get(0)));
        List<SExpr> clauses = list(parts.get(1)).subList(1, list(parts.get(1)).size());
        List<SExpr> body = parts.subList(2, parts.size());
        String fn = "_guard_" + (guardCount++);
        // We can't emit a def inside an expression, so we put it before
        // by using the outer emitter's position — emit as call to inline lambda
        // Workaround: use exec-style helper via (lambda: ...) ()
        emit("(lambda: ");
        // try block inline not possible in Python — use nested function trick
        // emit: next(iter([body_result]), handler_result) won't work either
        // Best approach: just emit try/except as statement before, store in temp var
        // For now emit None and rely on statement form
        emit("None)()");
    }

    private void emitCase(SExpr e, int ind) throws SchemeException {
        indent(ind); emit("_cv = "); emitExpr(nth(e,1), ind); emit("\n");
        List<SExpr> clauses = list(e).subList(2, list(e).size());
        boolean first = true;
        for (SExpr clause : clauses) {
            List<SExpr> c = list(clause);
            boolean isElse = c.get(0).isSym("else");
            indent(ind);
            if (isElse) emit("else:\n");
            else { emit(first ? "if " : "elif "); emit("_cv in "); emitQuote(c.get(0)); emit(":\n"); }
            for (int i = 1; i < c.size(); i++) emitStmt(c.get(i), ind+1);
            first = false;
        }
    }

    private void emitWhen(SExpr e, int ind, boolean negate) throws SchemeException {
        indent(ind); emit(negate ? "if not " : "if "); emitTest(nth(e,1), ind); emit(":\n");
        List<SExpr> body = list(e).subList(2, list(e).size());
        if (body.isEmpty()) { indent(ind+1); emit("pass\n"); }
        else for (SExpr b : body) emitStmt(b, ind+1);
    }

    private void emitLet(SExpr e, int ind) throws SchemeException {
        boolean tailPos = tailLets.remove(e);
        String hlet = hoistedLets.get(e);
        if (hlet != null) { indent(ind); if (tailPos) emit("return "); emitHoistedLetCall(e, hlet, ind); emit("\n"); return; }
        List<SExpr> parts = list(e);
        SExpr second = parts.get(1);
        // Named let
        if (second instanceof SExpr.Sym loopSym) {
            String loopName = snake(loopSym.name());
            List<SExpr> bindings = list(parts.get(2));
            List<SExpr> body = parts.subList(3, parts.size());
            List<String> params = new ArrayList<>();
            List<SExpr> inits  = new ArrayList<>();
            for (SExpr b : bindings) {
                List<SExpr> bl = list(b);
                params.add(snakeBind(sym(bl.get(0))));
                inits.add(bl.size() > 1 ? bl.get(1) : new SExpr.Nil(b.line()));
            }
            marker(e.line(), ind); indent(ind);
            emit("def " + loopName + "(" + String.join(", ", params) + "):\n");
            boolean wi = inFunction; Set<String> wl = new LinkedHashSet<>(localVars);
            inFunction = true; localVars.clear(); localVars.addAll(params);
            // Hoist globals used in named let body
            Set<String> loopGlobals = findSetTargets(body);
            for (String g : loopGlobals) {
                if (globalVars.contains(g) && !params.contains(g)) {
                    indent(ind+1); emit("global " + g + "\n");
                    localVars.add(g);
                }
            }
            emitLoopBody(body, loopSym.name(), params, ind+1);
            inFunction = wi; localVars.clear(); localVars.addAll(wl);
            indent(ind); emit((tailPos ? "return " : "") + loopName + "("); emitArgs(inits, ind); emit(")\n");
            return;
        }
        // Regular let as statements. Statement lets share the function's Python scope, so each
        // binding gets a fresh name; otherwise it would clobber (or make local) an outer variable.
        List<SExpr> bindings = list(second);
        List<SExpr> body = new ArrayList<>(parts.subList(2, parts.size()));
        boolean star = e.isForm("let*"), rec = e.isForm("letrec") || e.isForm("letrec*");
        List<String> olds = new ArrayList<>(), news = new ArrayList<>();
        for (SExpr b : bindings) { String n = sym(list(b).get(0)); olds.add(n); news.add(freshName(n, "__l" + (++letN))); }
        java.util.Map<String, String> full = new java.util.HashMap<>();
        for (int k = 0; k < olds.size(); k++) full.put(olds.get(k), news.get(k));
        for (int i = 0; i < bindings.size(); i++) {
            List<SExpr> bl = list(bindings.get(i));
            SExpr init = bl.size() > 1 ? bl.get(1) : null;
            if (init != null && (star || rec)) {
                java.util.Map<String, String> seen = new java.util.HashMap<>();
                int upto = rec ? bindings.size() : i;
                for (int k = 0; k < upto; k++) seen.put(olds.get(k), news.get(k));
                init = subst(init, seen);
            }
            String varName = snakeBind(news.get(i));
            localVars.add(varName);
            marker(bindings.get(i).line(), ind); indent(ind);
            emit(varName + " = ");
            if (init != null) emitExpr(init, ind); else emit("None");
            emit("\n");
        }
        for (int j = 0; j < body.size(); j++) body.set(j, subst(body.get(j), full));
        emitBody(body, ind, !tailPos);
    }

    private void emitDo(SExpr e, int ind) throws SchemeException {
        List<SExpr> parts = list(e);
        List<SExpr> varSpecs = list(parts.get(1));
        List<SExpr> term = list(parts.get(2));
        List<SExpr> body = parts.subList(3, parts.size());
        for (SExpr vs : varSpecs) {
            List<SExpr> v = list(vs);
            indent(ind); emit(snake(sym(v.get(0))) + " = "); emitExpr(v.get(1), ind); emit("\n");
        }
        indent(ind); emit("while True:\n");
        indent(ind+1); emit("if "); emitTest(term.get(0), ind+1); emit(":\n");
        if (term.size() > 1) {
            SExpr lastTerm = term.get(term.size()-1);
            for (int i = 1; i < term.size() - 1; i++) emitStmt(term.get(i), ind+2);
            // Only return if last term produces a value (not a mutation)
            boolean isMutation = lastTerm.isForm("set!") || lastTerm.isForm("vector-set!") ||
                lastTerm.isForm("set-car!") || lastTerm.isForm("set-cdr!") ||
                lastTerm.isForm("define") || lastTerm.isForm("for-each") ||
                lastTerm.isForm("display") || lastTerm.isForm("newline");
            if (!isMutation && (isSimple(lastTerm) || lastTerm.isForm("if") || lastTerm.isForm("cond") || lastTerm.isForm("let") || lastTerm.isForm("let*"))) {
                indent(ind+2); emit("return "); emitExpr(lastTerm, ind+2); emit("\n");
            } else {
                emitStmt(lastTerm, ind+2);
                indent(ind+2); emit("break\n");
            }
        } else { indent(ind+2); emit("break\n"); }
        for (SExpr b : body) emitStmt(b, ind+1);
        // steps
        List<String> stepVars = new ArrayList<>();
        List<SExpr> stepExprs = new ArrayList<>();
        for (SExpr vs : varSpecs) {
            List<SExpr> v = list(vs);
            if (v.size() > 2) { stepVars.add(snake(sym(v.get(0)))); stepExprs.add(v.get(2)); }
        }
        if (!stepVars.isEmpty()) {
            if (stepVars.size() == 1) {
                // Single step — assign directly
                indent(ind+1); emit(stepVars.get(0) + " = "); emitExpr(stepExprs.get(0), ind+1); emit("\n");
            } else {
                indent(ind+1); emit("_ds = ("); emitArgs(stepExprs, ind+1); emit(",)\n");
                for (int i = 0; i < stepVars.size(); i++) {
                    indent(ind+1); emit(stepVars.get(i) + " = _ds[" + i + "]\n");
                }
            }
        }
    }

    private void emitForEach(SExpr e, int ind) throws SchemeException {
        List<SExpr> args = list(e).subList(1, list(e).size());
        indent(ind);
        if (args.size() == 2) {
            emit("for _fi in "); emitExpr(args.get(1), ind); emit(":\n");
            indent(ind+1); emitExpr(args.get(0), ind+1); emit("(_fi)\n");
        } else {
            emit("for _fi in zip("); emitArgs(args.subList(1, args.size()), ind); emit("):\n");
            indent(ind+1); emitExpr(args.get(0), ind+1); emit("(*_fi)\n");
        }
    }

    private void emitWithMatrix(SExpr e, int ind) throws SchemeException {
        indent(ind); emit("push_matrix()\n");
        for (SExpr b : list(e).subList(1, list(e).size())) emitStmt(b, ind);
        indent(ind); emit("pop_matrix()\n");
    }

    // ── Expressions ───────────────────────────────────────────────────────────

    private void emitExpr(SExpr e, int ind) throws SchemeException {
        if (e instanceof SExpr.Bool b)   { emit(b.value() ? "True" : "False"); }
        else if (e instanceof SExpr.Num n)    { emit(n.raw()); }
        else if (e instanceof SExpr.Str s)    { emitStr(s.value()); }
        else if (e instanceof SExpr.Char ch)  { emit("_Char('" + esc(ch.toJavaChar()) + "')"); }
        else if (e instanceof SExpr.Nil)      { emit("[]"); }
        else if (e instanceof SExpr.Vec v)    {
            emit("["); for (int i = 0; i < v.elements().size(); i++) { if (i>0) emit(", "); emitExpr(v.elements().get(i), ind); } emit("]");
        }
        else if (e instanceof SExpr.ByteVec bv) {
            emit("bytearray(["); for (int i = 0; i < bv.bytes().size(); i++) { if (i>0) emit(", "); emit(String.valueOf(bv.bytes().get(i))); } emit("])");
        }
        else if (e instanceof SExpr.Sym sym)  { emitSym(sym); }
        else if (e instanceof SExpr.Pair pair){ emitApp(pair, ind); }
    }

    private void emitSym(SExpr.Sym sym) {
        String n = sym.name();
        // Only use CONST for uppercase identifiers (Processing constants)
        if (n.equals(n.toUpperCase()) && CONST.containsKey(n)) { emit(CONST.get(n)); return; }
        if (DYNATTR.containsKey(n)) { emit(DYNATTR.get(n)); return; }
        emit(snake(n));
    }

    private void emitApp(SExpr.Pair pair, int ind) throws SchemeException {
        SExpr head = pair.car();
        List<SExpr> args = pair.cdr() instanceof SExpr.Nil ? List.of() : list(pair).subList(1, list(pair).size());

        if (!(head instanceof SExpr.Sym sym)) {
            emit("("); emitExpr(head, ind); emit(")("); emitArgs(args, ind); emit(")"); return;
        }

        String name = sym.name();
        switch (name) {
            case "quote"      -> { emitQuote(args.isEmpty() ? new SExpr.Nil(pair.line()) : args.get(0)); return; }
            case "quasiquote" -> { emitQQ(args.get(0), ind); return; }
            case "lambda"     -> { emitLambda(pair, ind); return; }
            case "if"         -> { emitIf(pair, ind, false); return; }
            case "cond"       -> { emitCondExpr(pair, ind); return; }
            case "case"       -> { emitCaseExpr(pair, ind); return; }
            case "guard"      -> { emitGuardExpr(pair, ind); return; }
            case "and"        -> { emitAndOr(args, ind, "and", "True"); return; }
            case "or"         -> { emitAndOr(args, ind, "or",  "False"); return; }
            case "not"        -> { if (isBoolExpr(args.get(0))) { emit("(not "); emitExpr(args.get(0), ind); emit(")"); } else { emit("(("); emitExpr(args.get(0), ind); emit(") is False)"); } return; }
            case "begin"      -> { emitBeginExpr(pair, ind); return; }
            case "let","let*","letrec","letrec*" -> { emitLetExpr(pair, ind); return; }
            case "values"     -> { if (args.size()==1) emitExpr(args.get(0),ind); else { emit("("); emitArgs(args,ind); emit(")"); } return; }
            case "set!"       -> { emitSetExpr(pair, ind); return; }
            case "when","unless" -> { emit("None"); return; }
            case "vector"     -> { emit("["); emitArgs(args, ind); emit("]"); return; }
            case "vector-ref" -> { emit("vector_ref("); emitExpr(args.get(0),ind); emit(", "); emitExpr(args.get(1),ind); emit(")"); return; }
            case "vector-set!" -> { emitExpr(args.get(0),ind); emit(".__setitem__("); emitExpr(args.get(1),ind); emit(", "); emitExpr(args.get(2),ind); emit(")"); return; }
            case "vector-length","string-length","length" -> { emit("len("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "apply"      -> { emitApply(args, ind); return; }
            case "map"        -> { emit("_map("); emitArgs(args,ind); emit(")"); return; }
            case "filter"     -> { emit("list(filter("); emitExpr(args.get(0),ind); emit(", "); emitExpr(args.get(1),ind); emit("))"); return; }
            case "for-each"   -> { emit("_for_each("); for (int k = 0; k < args.size(); k++) { if (k > 0) emit(", "); emitExpr(args.get(k),ind); } emit(")"); return; }
            case "fold-left","foldl" -> { emit("__import__('functools').reduce("); emitExpr(args.get(0),ind); emit(", "); emitExpr(args.get(2),ind); emit(", "); emitExpr(args.get(1),ind); emit(")"); return; }
            case "error"      -> { emit("_err("); emitArgs(args,ind); emit(")"); return; }
            case "void"       -> { emit("None"); return; }
            case "display"    -> { emit("_display("); emitArgs(args,ind); emit(")"); return; }
            case "newline"    -> { if (!args.isEmpty()) { emit("_newline("); emitExpr(args.get(0),ind); emit(")"); } else emit("print()"); return; }
            case "symbol->string" -> { emit("str("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "exact->inexact","inexact" -> { emit("float("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "inexact->exact","exact","floor->exact","truncate->exact" -> { emit("int("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "char->integer" -> { emit("ord("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "integer->char" -> { emit("chr("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "string->list"  -> { emit("list("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "list->string"  -> { emit("''.join("); emitExpr(args.get(0),ind); emit(")"); return; }
            case "string-append" -> { if (args.isEmpty()) emit("\"\""); else emitBinOp(args, " + ", ind); return; }
            case "substring"  -> { emitExpr(args.get(0),ind); emit("["); emitExpr(args.get(1),ind); emit(":"); if(args.size()>2) emitExpr(args.get(2),ind); emit("]"); return; }
            case "string-ref" -> { emit("string_ref("); emitExpr(args.get(0),ind); emit(", "); emitExpr(args.get(1),ind); emit(")"); return; }
            case "string-upcase"  -> { emitExpr(args.get(0),ind); emit(".upper()"); return; }
            case "string-downcase" -> { emitExpr(args.get(0),ind); emit(".lower()"); return; }
            case "string=?"  -> { emitExpr(args.get(0),ind); emit(" == "); emitExpr(args.get(1),ind); return; }
            case "string<?"  -> { emitExpr(args.get(0),ind); emit(" < ");  emitExpr(args.get(1),ind); return; }
            case "string>?"  -> { emitExpr(args.get(0),ind); emit(" > ");  emitExpr(args.get(1),ind); return; }
            case "iota"       -> {
                if (args.size()==1) { emit("list(range("); emitExpr(args.get(0),ind); emit("))"); }
                else { emit("list(range("); emitExpr(args.get(1),ind); emit(", "); emitExpr(args.get(1),ind); emit(" + "); emitExpr(args.get(0),ind); emit("))"); }
                return;
            }
        }

        // arithmetic
        String arith = arithOp(name, args, ind);
        if (arith != null) { emit(arith); return; }

        // comparison / predicate
        String cmp = cmpOp(name, args, ind);
        if (cmp != null) { emit(cmp); return; }

        // list ops
        String lop = listOp(name, args, ind);
        if (lop != null) { emit(lop); return; }

        // mewnala functions
        if (MEWNALA_FNS.contains(name)) {
            emit(snake(name) + "("); emitArgs(args, ind); emit(")"); return;
        }

        // general call
        emit(snake(name) + "("); emitArgs(args, ind); emit(")");
    }

    private void emitLambda(SExpr.Pair form, int ind) throws SchemeException {
        String hz = hoisted.get(form);
        if (hz != null) { emit(hz); return; }
        List<SExpr> parts = list(form);
        SExpr paramForm = parts.get(1);
        List<SExpr> body = parts.subList(2, parts.size());
        List<String> params = new ArrayList<>();
        boolean rest = false; String restP = null;
        if (paramForm instanceof SExpr.Pair pp) {
            SExpr cur = pp;
            while (cur instanceof SExpr.Pair p) { if (p.car() instanceof SExpr.Sym s) params.add(snake(s.name())); cur = p.cdr(); }
            if (cur instanceof SExpr.Sym s) { rest = true; restP = snake(s.name()); }
        } else if (paramForm instanceof SExpr.Sym s) { rest = true; restP = snake(s.name()); }
        String ps = String.join(", ", params) + (rest ? (params.isEmpty()?"":", ") + "*" + restP : "");
        if (body.size() == 1 && isSimple(body.get(0))) {
            emit("(lambda " + ps + ": "); emitExpr(body.get(0), ind); emit(")");
        } else {
            // Every body form runs in order; the lambda returns the last one's value.
            emit("(lambda " + ps + ": ");
            if (body.size() == 1) emitExpr(body.get(0), ind);
            else {
                emit("(");
                for (int i = 0; i < body.size(); i++) { if (i > 0) emit(", "); emitExpr(body.get(i), ind); }
                emit(")[-1]");
            }
            emit(")");
        }
    }


    // Lambdas that set! an outer variable can't be Python lambdas: a walrus would bind a
    // lambda-local. Emit each as a def (with nonlocal/global) just above the statement using it.
    private boolean isNamedLetForm(SExpr e) {
        return e.isForm("let") && list(e).size() > 2 && list(e).get(1) instanceof SExpr.Sym;
    }

    // Before a statement: hoist lambdas that set! outer variables, and named lets used as
    // values, into defs above it. A named let that IS the statement is emitted by emitLet.
    private void hoistLambdas(SExpr e, int ind) throws SchemeException {
        if (isNamedLetForm(e)) return;
        if (e.isForm("define") && list(e).size() > 2 && list(e).get(1) instanceof SExpr.Sym) {
            for (SExpr v : list(e).subList(2, list(e).size())) hoistInner(v, ind);
            return;
        }
        hoistInner(e, ind);
    }

    private void hoistInner(SExpr e, int ind) throws SchemeException {
        if (!(e instanceof SExpr.Pair)) return;
        if (e.isForm("quote") || e.isForm("quasiquote") || e.isForm("define")) return;
        if (isNamedLetForm(e)) { hoistNamedLet(e, ind); return; }
        if (e.isForm("lambda")) { hoistLambda((SExpr.Pair) e, ind); return; }
        SExpr cur = e;
        while (cur instanceof SExpr.Pair c) { hoistInner(c.car(), ind); cur = c.cdr(); }
    }


    private boolean hasNamedLet(SExpr e) {
        if (!(e instanceof SExpr.Pair)) return false;
        if (e.isForm("quote") || e.isForm("quasiquote")) return false;
        if (isNamedLetForm(e)) return true;
        SExpr cur = e;
        while (cur instanceof SExpr.Pair c) { if (hasNamedLet(c.car())) return true; cur = c.cdr(); }
        return false;
    }

    private void hoistNamedLet(SExpr form, int ind) throws SchemeException {
        if (hoistedLets.containsKey(form)) return;
        List<SExpr> ps = list(form);
        String orig = sym(ps.get(1));
        String fresh = freshName(orig, "__n" + (++letN));
        List<SExpr> bindings = list(ps.get(2));
        List<String> pnames = new ArrayList<>();
        java.util.Set<String> praw = new java.util.HashSet<>();
        for (SExpr b : bindings) { String n = sym(list(b).get(0)); praw.add(n); pnames.add(snakeBind(n)); }
        java.util.Map<String, String> m = new java.util.HashMap<>();
        if (!praw.contains(orig)) m.put(orig, fresh);
        List<SExpr> body = new ArrayList<>(ps.subList(3, ps.size()));
        for (int j = 0; j < body.size(); j++) body.set(j, subst(body.get(j), m));
        String pyName = snakeBind(fresh);
        hoistedLets.put(form, pyName);
        marker(form.line(), ind); indent(ind);
        emit("def " + pyName + "(" + String.join(", ", pnames) + "):\n");
        boolean wi = inFunction;
        Set<String> wl = new LinkedHashSet<>(localVars);
        List<String> outerLocal = new ArrayList<>(), outerGlobal = new ArrayList<>();
        for (String t : findSetTargets(body)) {
            if (pnames.contains(t)) continue;
            if (globalVars.contains(t)) outerGlobal.add(t);
            else if (wi && wl.contains(t)) outerLocal.add(t);
        }
        if (!outerLocal.isEmpty())  { indent(ind+1); emit("nonlocal " + String.join(", ", outerLocal) + "\n"); }
        if (!outerGlobal.isEmpty()) { indent(ind+1); emit("global " + String.join(", ", outerGlobal) + "\n"); }
        inFunction = true;
        localVars.clear(); localVars.addAll(pnames); localVars.addAll(outerLocal); localVars.addAll(outerGlobal);
        emitLoopBody(body, fresh, pnames, ind+1);
        inFunction = wi;
        localVars.clear(); localVars.addAll(wl);
    }

    private void emitHoistedLetCall(SExpr form, String name, int ind) throws SchemeException {
        List<SExpr> inits = new ArrayList<>();
        for (SExpr b : list(list(form).get(2))) { List<SExpr> bl = list(b); inits.add(bl.size() > 1 ? bl.get(1) : new SExpr.Nil(b.line())); }
        emit(name + "("); emitArgs(inits, ind); emit(")");
    }

    private void hoistLambda(SExpr.Pair form, int ind) throws SchemeException {
        if (hoisted.containsKey(form)) return;
        List<SExpr> parts = list(form);
        if (parts.size() < 3) return;
        List<SExpr> body = parts.subList(2, parts.size());
        List<String> params = new ArrayList<>();
        String restP = null;
        SExpr pf = parts.get(1);
        while (pf instanceof SExpr.Pair pp) { if (pp.car() instanceof SExpr.Sym ps) params.add(snake(ps.name())); pf = pp.cdr(); }
        if (pf instanceof SExpr.Sym rs) restP = snake(rs.name());
        List<String> own = new ArrayList<>(params);
        if (restP != null) own.add(restP);
        List<String> outerLocal = new ArrayList<>(), outerGlobal = new ArrayList<>();
        for (String t : findSetTargets(body)) {
            if (own.contains(t)) continue;
            if (globalVars.contains(t)) outerGlobal.add(t);
            else if (inFunction && localVars.contains(t)) outerLocal.add(t);
            else return; // not reachable from a def here; leave the lambda as it is
        }
        boolean loopInside = false;
        for (SExpr b : body) if (hasNamedLet(b)) loopInside = true;
        // A named let needs statement context, so a lambda containing one becomes a def too.
        if (outerLocal.isEmpty() && outerGlobal.isEmpty() && !loopInside) return;
        String name = "_lam" + (++lamN);
        hoisted.put(form, name);
        indent(ind);
        emit("def " + name + "(" + String.join(", ", params)
             + (restP != null ? (params.isEmpty() ? "" : ", ") + "*" + restP : "") + "):\n");
        if (!outerLocal.isEmpty())  { indent(ind+1); emit("nonlocal " + String.join(", ", outerLocal) + "\n"); }
        if (!outerGlobal.isEmpty()) { indent(ind+1); emit("global " + String.join(", ", outerGlobal) + "\n"); }
        boolean wi = inFunction;
        Set<String> wl = new LinkedHashSet<>(localVars);
        inFunction = true;
        localVars.clear(); localVars.addAll(own); localVars.addAll(outerLocal); localVars.addAll(outerGlobal);
        emitLoopBody(body, null, own, ind+1);
        inFunction = wi;
        localVars.clear(); localVars.addAll(wl);
    }


    // Scheme: only #f is false. Python: 0, [], "" and None are false too.
    // Tests that already yield a bool stay as they are; anything else is compared against False.
    private boolean isBoolExpr(SExpr e) {
        if (!(e instanceof SExpr.Pair p) || !(p.car() instanceof SExpr.Sym h)) return false;
        String n = h.name();
        if (n.equals("and") || n.equals("or")) {
            for (SExpr a : list(e).subList(1, list(e).size())) if (!isBoolExpr(a)) return false;
            return true;
        }
        return n.endsWith("?") || n.equals("<") || n.equals(">") || n.equals("=")
            || n.equals("<=") || n.equals(">=") || n.equals("not");
    }

    private void emitTest(SExpr t, int ind) throws SchemeException {
        if (isBoolExpr(t)) { emitExpr(t, ind); return; }
        emit("(("); emitExpr(t, ind); emit(") is not False)");
    }


    // A fresh variant of a Scheme name: the tag goes before trailing punctuation, so names
    // like ev? or set-x! still map to valid Python after snake-casing (ev__l3? -> ev_l3_p).
    static String freshName(String n, String tag) {
        int e = n.length();
        while (e > 0 && !Character.isLetterOrDigit(n.charAt(e - 1))) e--;
        if (e == 0) return n + tag;
        return n.substring(0, e) + tag + n.substring(e);
    }

    private static SExpr chainOf(List<SExpr> xs, SExpr tail, int line) {
        SExpr r = tail;
        for (int i = xs.size() - 1; i >= 0; i--) r = new SExpr.Pair(xs.get(i), r, line);
        return r;
    }

    private static java.util.Map<String, String> without(java.util.Map<String, String> m, java.util.Collection<String> names) {
        boolean hit = false;
        for (String n : names) if (m.containsKey(n)) { hit = true; break; }
        if (!hit) return m;
        java.util.Map<String, String> r = new java.util.HashMap<>(m);
        r.keySet().removeAll(names);
        return r;
    }

    private static java.util.Set<String> binderNames(SExpr params) {
        java.util.Set<String> out = new java.util.HashSet<>();
        while (params instanceof SExpr.Pair pp) { if (pp.car() instanceof SExpr.Sym s) out.add(s.name()); params = pp.cdr(); }
        if (params instanceof SExpr.Sym s) out.add(s.name());
        return out;
    }

    // Rename free occurrences of variables (all at once), leaving quoted data alone and
    // stopping where an inner lambda/let/do rebinds the same name.
    private SExpr subst(SExpr e, java.util.Map<String, String> m) {
        if (m.isEmpty()) return e;
        if (e instanceof SExpr.Sym s) { String t = m.get(s.name()); return t == null ? e : new SExpr.Sym(t, s.line()); }
        if (!(e instanceof SExpr.Pair p)) return e;
        if (e.isForm("quote")) return e;
        if (e.isForm("quasiquote")) return substQQ(e, m);
        if (e.isForm("lambda")) {
            List<SExpr> ps = list(e);
            if (ps.size() > 2) {
                java.util.Map<String, String> in = without(m, binderNames(ps.get(1)));
                List<SExpr> out = new ArrayList<>(ps.subList(0, 2));
                for (SExpr b : ps.subList(2, ps.size())) out.add(subst(b, in));
                return chainOf(out, new SExpr.Nil(p.line()), p.line());
            }
        }
        if (e.isForm("let") || e.isForm("let*") || e.isForm("letrec") || e.isForm("letrec*") || e.isForm("do")) {
            List<SExpr> ps = list(e);
            boolean named = e.isForm("let") && ps.size() > 1 && ps.get(1) instanceof SExpr.Sym;
            int bi = named ? 2 : 1;
            if (ps.size() > bi && (ps.get(bi) instanceof SExpr.Pair || ps.get(bi) instanceof SExpr.Nil)) {
                java.util.Set<String> bound = new java.util.HashSet<>();
                if (named) bound.add(((SExpr.Sym) ps.get(1)).name());
                for (SExpr b : list(ps.get(bi))) if (b instanceof SExpr.Pair bp && bp.car() instanceof SExpr.Sym bs) bound.add(bs.name());
                java.util.Map<String, String> in = without(m, bound);
                boolean recInits = e.isForm("letrec") || e.isForm("letrec*");
                List<SExpr> nb = new ArrayList<>();
                for (SExpr b : list(ps.get(bi))) {
                    if (!(b instanceof SExpr.Pair)) { nb.add(b); continue; }
                    List<SExpr> bl = list(b);
                    List<SExpr> r = new ArrayList<>();
                    r.add(bl.get(0));
                    if (bl.size() > 1) r.add(subst(bl.get(1), recInits ? in : m));
                    for (int k = 2; k < bl.size(); k++) r.add(subst(bl.get(k), in)); // do steps
                    nb.add(chainOf(r, new SExpr.Nil(b.line()), b.line()));
                }
                List<SExpr> out = new ArrayList<>(ps.subList(0, bi));
                out.add(chainOf(nb, new SExpr.Nil(p.line()), p.line()));
                for (SExpr x : ps.subList(bi + 1, ps.size())) out.add(subst(x, in));
                return chainOf(out, new SExpr.Nil(p.line()), p.line());
            }
        }
        return new SExpr.Pair(subst(p.car(), m), subst(p.cdr(), m), p.line());
    }

    private SExpr substQQ(SExpr e, java.util.Map<String, String> m) {
        if (e.isForm("unquote") || e.isForm("unquote-splicing")) {
            SExpr.Pair p = (SExpr.Pair) e;
            return new SExpr.Pair(p.car(), subst(p.cdr(), m), p.line());
        }
        if (e instanceof SExpr.Pair p) return new SExpr.Pair(substQQ(p.car(), m), substQQ(p.cdr(), m), p.line());
        return e;
    }

    // A define'd function starts a new Python function: no enclosing loop's tail calls apply.
    private void emitDefine(SExpr f, int ind) throws SchemeException {
        String tn = tcoName; List<String> tp = tcoParams;
        tcoName = null; tcoParams = null;
        try { emitDefine0(f, ind); } finally { tcoName = tn; tcoParams = tp; }
    }

    // Named-let body. If its last form calls the loop in tail position, the body becomes
    // while True: ... and those calls become reassignment + continue (no Python recursion).
    private void emitLoopBody(List<SExpr> body, String name, List<String> params, int ind) throws SchemeException {
        String tn = tcoName; List<String> tp = tcoParams;
        boolean loop = name != null && !body.isEmpty() && tailCalls(body.get(body.size() - 1), name);
        tcoName = loop ? name : null; tcoParams = loop ? params : null;
        try {
            if (loop) {
                indent(ind); emit("while True:\n");
                emitBody(body, ind + 1);
                indent(ind + 1); emit("return None\n");
            } else emitBody(body, ind);
        } finally { tcoName = tn; tcoParams = tp; }
    }

    private boolean tailCalls(SExpr e, String name) {
        if (!(e instanceof SExpr.Pair p)) return false;
        if (p.car() instanceof SExpr.Sym h && h.name().equals(name)) return true;
        if (e.isForm("quote") || e.isForm("quasiquote")) return false;
        List<SExpr> f = list(e);
        if (e.isForm("if"))
            return (f.size() > 2 && tailCalls(f.get(2), name)) || (f.size() > 3 && tailCalls(f.get(3), name));
        if (e.isForm("cond")) {
            for (SExpr c : f.subList(1, f.size())) {
                List<SExpr> cc = list(c);
                if (cc.size() >= 2 && !(cc.size() == 3 && cc.get(1).isSym("=>")) && tailCalls(cc.get(cc.size() - 1), name)) return true;
            }
            return false;
        }
        if (e.isForm("when") || e.isForm("unless") || e.isForm("begin"))
            return f.size() > 1 && tailCalls(f.get(f.size() - 1), name);
        if ((e.isForm("let") || e.isForm("let*") || e.isForm("letrec") || e.isForm("letrec*")) && !isNamedLetForm(e) && f.size() > 2) {
            for (SExpr b : list(f.get(1))) if (b instanceof SExpr.Pair bp && bp.car().isSym(name)) return false;
            return tailCalls(f.get(f.size() - 1), name);
        }
        return false;
    }

    // In a loop compiled to while True. Returns false to let emitBody's own tail paths
    // (when/unless/let) handle the form; they recurse into emitBody and reach this again.
    private boolean emitTailLoop(SExpr last, int ind, boolean noReturn) throws SchemeException {
        if (last instanceof SExpr.Pair p && p.car() instanceof SExpr.Sym h && h.name().equals(tcoName)) {
            List<SExpr> args = list(last).subList(1, list(last).size());
            if (args.size() != tcoParams.size()) return false;
            hoistLambdas(last, ind);
            if (!args.isEmpty()) {
                indent(ind); emit(String.join(", ", tcoParams) + " = ");
                for (int k = 0; k < args.size(); k++) { if (k > 0) emit(", "); emitExpr(args.get(k), ind); }
                emit("\n");
            }
            indent(ind); emit("continue\n");
            return true;
        }
        if (!tailCalls(last, tcoName)) return false;
        if (last.isForm("if")) {
            List<SExpr> f = list(last);
            hoistLambdas(f.get(1), ind);
            indent(ind); emit("if "); emitTest(f.get(1), ind); emit(":\n");
            emitBody(List.of(f.get(2)), ind + 1, noReturn);
            if (f.size() > 3) { indent(ind); emit("else:\n"); emitBody(List.of(f.get(3)), ind + 1, noReturn); }
            return true;
        }
        if (last.isForm("begin")) {
            List<SExpr> f = list(last);
            emitBody(f.subList(1, f.size()), ind, noReturn);
            return true;
        }
        if (last.isForm("cond")) {
            List<SExpr> cl = list(last).subList(1, list(last).size());
            for (SExpr c : cl) { List<SExpr> cc = list(c); if (cc.size() < 2 || (cc.size() == 3 && cc.get(1).isSym("=>"))) return false; }
            for (SExpr c : cl) { SExpr t = list(c).get(0); if (!t.isSym("else")) hoistLambdas(t, ind); }
            boolean first = true;
            for (SExpr c : cl) {
                List<SExpr> cc = list(c);
                indent(ind);
                if (cc.get(0).isSym("else")) emit("else:\n");
                else { emit(first ? "if " : "elif "); emitTest(cc.get(0), ind); emit(":\n"); }
                emitBody(cc.subList(1, cc.size()), ind + 1, noReturn);
                first = false;
            }
            return true;
        }
        return false;
    }

    private void emitBeginExpr(SExpr.Pair form, int ind) throws SchemeException {
        List<SExpr> items = list(form).subList(1, list(form).size());
        if (items.isEmpty()) { emit("None"); return; }
        if (items.size() == 1) { emitExpr(items.get(0), ind); return; }
        emit("(");
        for (int i = 0; i < items.size(); i++) { if (i>0) emit(", "); emitExpr(items.get(i), ind); }
        emit(")[-1]");
    }

    private void emitLetExpr(SExpr.Pair form, int ind) throws SchemeException {
        String hl = hoistedLets.get(form);
        if (hl != null) { emitHoistedLetCall(form, hl, ind); return; }
        List<SExpr> parts = list(form);
        String head = sym(parts.get(0));
        if (parts.get(1) instanceof SExpr.Sym loopSym2) {
            // named let in expression position — not easily expressible inline
            // emit None as placeholder (named let in expr pos should be hoisted to stmt)
            emit("None"); return;
        }
        List<SExpr> bindings = list(parts.get(1));
        List<SExpr> body = parts.subList(2, parts.size());

        boolean isStar = head.equals("let*") || head.equals("letrec*");

        if (isStar && bindings.size() > 1) {
            // let* in expr pos: chain lambdas so each binding sees previous
            emitLetStarExpr(bindings, body, 0, ind);
            return;
        }

        List<String> ps = new ArrayList<>();
        List<String> is = new ArrayList<>();
        for (SExpr b : bindings) {
            List<SExpr> bl = list(b);
            ps.add(snakeBind(sym(bl.get(0))));
            Transpiler sub = subTx(); sub.emitExpr(bl.size()>1 ? bl.get(1) : new SExpr.Nil(0), ind);
            is.add(sub.out.toString());
        }
        if (body.size() == 1) {
            emit("(lambda " + String.join(", ", ps) + ": ");
            emitExpr(body.get(0), ind);
            emit(")(" + String.join(", ", is) + ")");
        } else {
            emit("(lambda " + String.join(", ", ps) + ": [");
            for (int bi = 0; bi < body.size() - 1; bi++) {
                emitExpr(body.get(bi), ind); emit(", ");
            }
            emitExpr(body.get(body.size()-1), ind);
            emit("][-1])(" + String.join(", ", is) + ")");
        }
    }

    private void emitLetStarExpr(List<SExpr> bindings, List<SExpr> body, int i, int ind) throws SchemeException {
        if (i >= bindings.size()) {
            // emit body
            if (body.size() == 1) { emitExpr(body.get(0), ind); }
            else {
                emit("[");
                for (int bi = 0; bi < body.size() - 1; bi++) { emitExpr(body.get(bi), ind); emit(", "); }
                emitExpr(body.get(body.size()-1), ind);
                emit("][-1]");
            }
            return;
        }
        List<SExpr> bl = list(bindings.get(i));
        String param = snakeBind(sym(bl.get(0)));
        Transpiler sub = subTx(); sub.emitExpr(bl.size()>1 ? bl.get(1) : new SExpr.Nil(0), ind);
        emit("(lambda " + param + ": ");
        emitLetStarExpr(bindings, body, i + 1, ind);
        emit(")(" + sub.out.toString() + ")");
    }

    private void emitSetExpr(SExpr.Pair form, int ind) throws SchemeException {
        List<SExpr> p = list(form);
        emit("("); emitExpr(p.get(1), ind); emit(" := "); emitExpr(p.get(2), ind); emit(")");
    }

    private void emitApply(List<SExpr> args, int ind) throws SchemeException {
        emitExpr(args.get(0), ind); emit("(*(");
        if (args.size() == 2) emitExpr(args.get(1), ind);
        else { emit("["); emitArgs(args.subList(1, args.size()-1), ind); emit("] + list("); emitExpr(args.get(args.size()-1), ind); emit(")"); }
        emit("))");
    }

    private void emitAndOr(List<SExpr> args, int ind, String op, String empty) throws SchemeException {
        if (args.isEmpty()) { emit(empty); return; }
        if (args.size() == 1) { emitExpr(args.get(0), ind); return; }
        if (args.stream().allMatch(this::isBoolExpr)) {
            emit("(");
            for (int i = 0; i < args.size(); i++) { if (i > 0) emit(" " + op + " "); emitExpr(args.get(i), ind); }
            emit(")");
            return;
        }
        emitAndOrChain(args, 0, ind, op);
    }

    // Scheme and/or: only #f is false, and the result is an operand's value, not a bool.
    private void emitAndOrChain(List<SExpr> args, int i, int ind, String op) throws SchemeException {
        if (i == args.size() - 1) { emitExpr(args.get(i), ind); return; }
        emit("(");
        if (op.equals("and")) {
            emit("False if ("); emitExpr(args.get(i), ind); emit(") is False else ");
        } else {
            String t = "_c" + (++condN);
            emit(t + " if (" + t + " := "); emitExpr(args.get(i), ind); emit(") is not False else ");
        }
        emitAndOrChain(args, i + 1, ind, op);
        emit(")");
    }

    private void emitBinOp(List<SExpr> args, String op, int ind) throws SchemeException {
        emit("("); for (int i=0;i<args.size();i++){if(i>0)emit(op);emitExpr(args.get(i),ind);} emit(")");
    }

    // ── Arithmetic ────────────────────────────────────────────────────────────

    private String arithOp(String n, List<SExpr> args, int ind) throws SchemeException {
        if (args.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        switch (n) {
            case "+" -> { if(args.size()==1){sb.append("(+");apE(sb,args.get(0),ind);sb.append(")");} else { sb.append("("); apE(sb,args.get(0),ind); for(int i=1;i<args.size();i++){sb.append(" + ");apE(sb,args.get(i),ind);} sb.append(")"); } return sb.toString(); }
            case "-" -> { if(args.size()==1){sb.append("(-");apE(sb,args.get(0),ind);sb.append(")");} else {sb.append("(");apE(sb,args.get(0),ind);for(int i=1;i<args.size();i++){sb.append(" - ");apE(sb,args.get(i),ind);}sb.append(")");} return sb.toString(); }
            case "*" -> { sb.append("("); apE(sb,args.get(0),ind); for(int i=1;i<args.size();i++){sb.append(" * ");apE(sb,args.get(i),ind);} sb.append(")"); return sb.toString(); }
            case "/" -> { sb.append("_div("); apAll(sb, args, ind); sb.append(")"); return sb.toString(); }
            case "quotient","truncate-quotient" -> { sb.append("_quot("); apE(sb,args.get(0),ind); sb.append(", "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "floor-quotient" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" // "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "remainder","truncate-remainder" -> { sb.append("_rem("); apE(sb,args.get(0),ind); sb.append(", "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "modulo","floor-remainder" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" % "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "expt"   -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" ** "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "abs"    -> { sb.append("abs("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "max"    -> { sb.append("max("); apAll(sb,args,ind); sb.append(")"); return sb.toString(); }
            case "min"    -> { sb.append("min("); apAll(sb,args,ind); sb.append(")"); return sb.toString(); }
            case "floor"  -> { sb.append("_math.floor("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "ceiling" -> { sb.append("_math.ceil("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "truncate" -> { sb.append("int("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "round"  -> { sb.append("round("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "sqrt"   -> { sb.append("_math.sqrt("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "sin"    -> { sb.append("_math.sin("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "cos"    -> { sb.append("_math.cos("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "tan"    -> { sb.append("_math.tan("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "asin"   -> { sb.append("_math.asin("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "acos"   -> { sb.append("_math.acos("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "atan"   -> { if(args.size()==1){sb.append("_math.atan(");apE(sb,args.get(0),ind);}else{sb.append("_math.atan2(");apE(sb,args.get(0),ind);sb.append(", ");apE(sb,args.get(1),ind);} sb.append(")"); return sb.toString(); }
            case "log"    -> { sb.append("_math.log("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "exp"    -> { sb.append("_math.exp("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "gcd"    -> { sb.append("_math.gcd("); apAll(sb,args,ind); sb.append(")"); return sb.toString(); }
            case "bitwise-and","logand","bit-and" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" & "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "bitwise-or","logior","bit-or"   -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" | "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "bitwise-xor","logxor","bit-xor" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" ^ "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "bitwise-not","lognot","bit-not" -> { sb.append("(~"); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "arithmetic-shift","ash" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" << "); apE(sb,args.get(1),ind); sb.append(" if ("); apE(sb,args.get(1),ind); sb.append(") >= 0 else "); apE(sb,args.get(0),ind); sb.append(" >> -("); apE(sb,args.get(1),ind); sb.append("))"); return sb.toString(); }
        }
        return null;
    }

    // ── Comparison / predicates ───────────────────────────────────────────────

    private String cmpOp(String n, List<SExpr> args, int ind) throws SchemeException {
        StringBuilder sb = new StringBuilder();
        switch (n) {
            case "=","equal-number?" -> { sb.append("("); for (int k = 0; k < args.size(); k++) { if (k > 0) sb.append(" == "); apE(sb,args.get(k),ind); } sb.append(")"); return sb.toString(); }
            case "<"  -> { return chain(args, " < ",  ind); }
            case ">"  -> { return chain(args, " > ",  ind); }
            case "<=" -> { return chain(args, " <= ", ind); }
            case ">=" -> { return chain(args, " >= ", ind); }
            case "equal?" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" == "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "eqv?" -> { sb.append("_eqv("); apE(sb,args.get(0),ind); sb.append(", "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "eq?" -> { sb.append("_eqv("); apE(sb,args.get(0),ind); sb.append(", "); apE(sb,args.get(1),ind); sb.append(")"); return sb.toString(); }
            case "zero?"    -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" == 0)"); return sb.toString(); }
            case "positive?"-> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" > 0)"); return sb.toString(); }
            case "negative?"-> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" < 0)"); return sb.toString(); }
            case "even?"    -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" % 2 == 0)"); return sb.toString(); }
            case "odd?"     -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(" % 2 != 0)"); return sb.toString(); }
            case "null?","nil?" -> { sb.append("is_null("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "pair?"    -> { sb.append("is_pair("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "list?"    -> { sb.append("is_list("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "number?"  -> { sb.append("isinstance("); apE(sb,args.get(0),ind); sb.append(", (int, float))"); return sb.toString(); }
            case "string?"  -> { sb.append("string_p("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "boolean?" -> { sb.append("isinstance("); apE(sb,args.get(0),ind); sb.append(", bool)"); return sb.toString(); }
            case "procedure?","callable?" -> { sb.append("callable("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "integer?" -> { sb.append("isinstance("); apE(sb,args.get(0),ind); sb.append(", int)"); return sb.toString(); }
            case "char?"    -> { sb.append("char_p("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "vector?"  -> { sb.append("isinstance("); apE(sb,args.get(0),ind); sb.append(", list)"); return sb.toString(); }
        }
        return null;
    }

    private String chain(List<SExpr> args, String op, int ind) throws SchemeException {
        StringBuilder sb = new StringBuilder("("); apE(sb,args.get(0),ind);
        for (int i=1;i<args.size();i++){sb.append(op);apE(sb,args.get(i),ind);}
        sb.append(")"); return sb.toString();
    }

    // ── List ops ──────────────────────────────────────────────────────────────

    private String listOp(String n, List<SExpr> args, int ind) throws SchemeException {
        StringBuilder sb = new StringBuilder();
        switch (n) {
            case "car","first"   -> { sb.append("car("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "cdr","rest"    -> { sb.append("cdr("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "cadr","second" -> { sb.append("cadr("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "caddr","third" -> { sb.append("caddr("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "cadddr","fourth" -> { sb.append("cadddr("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "cddr"   -> { sb.append("cddr("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "list"   -> { sb.append("["); apAll(sb,args,ind); sb.append("]"); return sb.toString(); }
            case "list-ref" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(")["); apE(sb,args.get(1),ind); sb.append("]"); return sb.toString(); }
            case "list-tail" -> { sb.append("("); apE(sb,args.get(0),ind); sb.append(")["); apE(sb,args.get(1),ind); sb.append(":]"); return sb.toString(); }
            case "length" -> { sb.append("len("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "append" -> {
                if (args.isEmpty()) return "[]";
                sb.append("("); apE(sb,args.get(0),ind);
                for (int i=1;i<args.size();i++){sb.append(" + ");apE(sb,args.get(i),ind);}
                sb.append(")"); return sb.toString();
            }
            case "reverse" -> { sb.append("list(reversed("); apE(sb,args.get(0),ind); sb.append("))"); return sb.toString(); }
            case "list-copy" -> { sb.append("list("); apE(sb,args.get(0),ind); sb.append(")"); return sb.toString(); }
            case "assoc","assq","assv" -> { sb.append("next((_p for _p in "); apE(sb,args.get(1),ind); sb.append(" if _p[0] == "); apE(sb,args.get(0),ind); sb.append("), False)"); return sb.toString(); }
            case "member" -> { sb.append("_member("); apE(sb,args.get(0),ind); sb.append(", "); apE(sb,args.get(1),ind); if (args.size() > 2) { sb.append(", "); apE(sb,args.get(2),ind); } sb.append(")"); return sb.toString(); }
            case "memq","memv" -> { sb.append("_member("); apE(sb,args.get(0),ind); sb.append(", "); apE(sb,args.get(1),ind); sb.append(", _eqv)"); return sb.toString(); }
            case "sort","list-sort" -> { sb.append("list_sort("); apAll(sb, args, ind); sb.append(")"); return sb.toString(); }
        }
        return null;
    }

    // ── Quote / quasiquote ────────────────────────────────────────────────────

        private static boolean isImproper(SExpr e) {
        while (e instanceof SExpr.Pair p) e = p.cdr();
        return !(e instanceof SExpr.Nil);
    }

    /** (a b . c) becomes ("a", ("b", "c")), the same shape cons builds at runtime. */
    private void emitQuotedImproper(SExpr.Pair p) throws SchemeException {
        emit("_Pair(");
        emitQuote(p.car());
        emit(", ");
        if (p.cdr() instanceof SExpr.Pair next) emitQuotedImproper(next);
        else emitQuote(p.cdr());
        emit(")");
    }

    private void emitQuote(SExpr d) throws SchemeException {
        if (d instanceof SExpr.Pair && isImproper(d)) { emitQuotedImproper((SExpr.Pair) d); return; }
        if (d instanceof SExpr.Nil)     emit("[]");
        else if (d instanceof SExpr.Bool b)  emit(b.value() ? "True" : "False");
        else if (d instanceof SExpr.Num n)   emit(n.raw());
        else if (d instanceof SExpr.Str s)   emitStr(s.value());
        else if (d instanceof SExpr.Sym s)   { emit("_Sym("); emitStr(s.name()); emit(")"); }
        else if (d instanceof SExpr.Char c)  emit("_Char('" + esc(c.toJavaChar()) + "')");
        else if (d instanceof SExpr.Vec v)   { emit("["); for(int i=0;i<v.elements().size();i++){if(i>0)emit(", ");emitQuote(v.elements().get(i));}emit("]"); }
        else if (d instanceof SExpr.Pair p)  { emit("["); List<SExpr> items=p.toList(); for(int i=0;i<items.size();i++){if(i>0)emit(", ");emitQuote(items.get(i));}emit("]"); }
    }

    private void emitQQ(SExpr d, int ind) throws SchemeException {
        if (d.isForm("unquote")) { emitExpr(nth(d,1), ind); return; }
        if (d instanceof SExpr.Pair p) {
            List<SExpr> items = p.toList();
            boolean hasSplice = items.stream().anyMatch(e -> e.isForm("unquote-splicing"));
            if (hasSplice) {
                emit("(");
                for (SExpr item : items) {
                    if (item.isForm("unquote-splicing")) { emit("list("); emitExpr(nth(item,1), ind); emit(") + "); }
                    else { emit("["); emitQQ(item, ind); emit("] + "); }
                }
                emit("[])");
            } else {
                emit("["); for(int i=0;i<items.size();i++){if(i>0)emit(", ");emitQQ(items.get(i),ind);} emit("]");
            }
            return;
        }
        emitQuote(d);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void emitArgs(List<SExpr> args, int ind) throws SchemeException {
        for (int i=0;i<args.size();i++){if(i>0)emit(", ");emitExpr(args.get(i),ind);}
    }

    private void emitStr(String s) {
        emit("\"");
        for (char c : s.toCharArray()) {
            if (c=='"') emit("\\\""); else if (c=='\\') emit("\\\\");
            else if (c=='\n') emit("\\n"); else if (c=='\t') emit("\\t");
            else emit(String.valueOf(c));
        }
        emit("\"");
    }

    private String esc(char c) {
        return switch (c) {
            case '\'' -> "\\'"; case '\\' -> "\\\\";
            case '\n' -> "\\n"; case '\t' -> "\\t"; default -> String.valueOf(c);
        };
    }

    private void emit(String s)    { out.append(s); }
    private void indent(int n)     { out.append("    ".repeat(n)); }

    private void marker(int scmLine, int ind) {
        if (scmLine > 0 && scmLine != lastScmLine) {
            out.append("    ".repeat(ind)).append("# scm:").append(scmLine).append("\n");
            lastScmLine = scmLine;
        }
    }

    private Transpiler subTx() {
        Transpiler t = new Transpiler();
        t.inFunction = this.inFunction;
        t.globalVars.addAll(this.globalVars);
        t.localVars.addAll(this.localVars);
        return t;
    }

    private void apE(StringBuilder sb, SExpr e, int ind) throws SchemeException {
        Transpiler sub = subTx();
        // The sub-transpiler shares hoisting state and name counters with this one.
        sub.hoisted = hoisted; sub.hoistedLets = hoistedLets; sub.tailLets = tailLets;
        sub.lamN = lamN; sub.condN = condN; sub.letN = letN;
        sub.emitExpr(e, ind);
        lamN = sub.lamN; condN = sub.condN; letN = sub.letN;
        sb.append(sub.out);
    }

    private void apAll(StringBuilder sb, List<SExpr> args, int ind) throws SchemeException {
        for (int i=0;i<args.size();i++){if(i>0)sb.append(", ");apE(sb,args.get(i),ind);}
    }

    private boolean containsNamedLet(SExpr e) {
        if (e.isForm("let") && e instanceof SExpr.Pair p) {
            List<SExpr> parts = p.toList();
            return parts.size() > 1 && parts.get(1) instanceof SExpr.Sym;
        }
        if (e instanceof SExpr.Pair p) {
            for (SExpr child : p.toList()) if (containsNamedLet(child)) return true;
        }
        return false;
    }

    

    private boolean isSimple(SExpr e) {
        if (!(e instanceof SExpr.Pair)) return true;
        String h = ((SExpr.Pair)e).headSym();
        return h == null || !Set.of("define","set!","begin","if","cond","when","unless","let","let*","letrec","do","for-each","with-matrix").contains(h);
    }

    /** Collect all set! target names in a list of forms (recursive). */
    private Set<String> findSetTargets(List<SExpr> forms) {
        Set<String> targets = new LinkedHashSet<>();
        for (SExpr f : forms) collectSetTargets(f, targets);
        return targets;
    }

    private void collectSetTargets(SExpr expr, Set<String> targets) {
        if (!(expr instanceof SExpr.Pair p)) return;
        if (p.car().isSym("set!")) {
            List<SExpr> l = p.toList();
            if (l.size() >= 2 && l.get(1) instanceof SExpr.Sym s) targets.add(snake(s.name()));
        }
        // Recurse into all children
        SExpr cur = p;
        while (cur instanceof SExpr.Pair pp) { collectSetTargets(pp.car(), targets); cur = pp.cdr(); }
    }

    /** Convert camelCase to kebab-case: mouseX -> mouse-x, noFill -> no-fill */
    static String camelToKebab(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final java.util.Set<String> PY_RESERVED = java.util.Set.of(
        "False","None","True","and","as","assert","async","await",
        "break","class","continue","def","del","elif","else","except",
        "finally","for","from","global","if","import","in","is",
        "lambda","nonlocal","not","or","pass","raise","return",
        "try","while","with","yield"
    );

    static String snake(String name) {
        if (name.matches("[A-Z][A-Z0-9_]*")) return name; // all-caps constant like P3D or KEY_A: keep as written
        if (name == null) return "_";
        // Normalize camelCase to kebab-case first (mouseX -> mouse-x)
        // Only if the name contains uppercase letters (is camelCase)
        if (name.chars().anyMatch(Character::isUpperCase)) {
            name = camelToKebab(name);
        }
        return switch (name) {
            case "display" -> "_display"; case "length" -> "len";
            case "+" -> "_add"; case "-" -> "_sub"; case "*" -> "_mul";
            case "/" -> "_div"; case "%" -> "_mod";
            case "<" -> "_lt"; case ">" -> "_gt"; case "<=" -> "_le"; case ">=" -> "_ge";
            case "=" -> "_eq"; case "!=" -> "_ne";
            default -> {
                String s = name.replace("->", "_to_").replace('-', '_');
                s = s.replace("<", "_lt_").replace(">", "_gt_").replace("=", "_eq_");
                s = s.replace("!", "").replace("*", "_s_").replace("+", "_plus_");
                // Clean up multiple/trailing underscores
                while (s.contains("__")) s = s.replace("__", "_");
                if (s.endsWith("_")) s = s.substring(0, s.length()-1);
                if (s.startsWith("_") && s.length() > 1) s = s.substring(1);
                if (s.endsWith("?")) s = s.substring(0, s.length()-1) + "_p";
                if (s.startsWith("#")) s = s.substring(1);
                // Mangle Python builtins and reserved words
                switch (s) {
                    case "False","None","True","and","as","assert","async","await",
                         "break","class","continue","def","del","elif","else","except",
                         "finally","for","from","global","if","import","in","is",
                         "lambda","nonlocal","not","or","pass","raise","return",
                         "try","while","with","yield" -> s = "_" + s;
                    // never add more here - builtin mangling is in the yield block below
                    default -> {}
                }
                if (PY_BUILTINS.contains(s)) s = "_" + s;
                yield s;
            }
        };
    }

    private static final java.util.Set<String> PY_BUILTINS = java.util.Set.of(
        "abs","all","any","ascii","bin","bool","breakpoint","bytearray","bytes",
        "callable","chr","classmethod","compile","complex","copyright","credits",
        "delattr","dict","dir","divmod","enumerate","eval","exec","exit","filter",
        "float","format","frozenset","getattr","globals","hasattr","hash","help",
        "hex","id","input","int","isinstance","issubclass","iter","len","license",
        "list","locals","map","max","memoryview","min","next","object","oct","open",
        "ord","pow","print","property","quit","range","repr","reversed","round",
        "set","setattr","slice","sorted","staticmethod","str","sum","super","tuple",
        "type","vars","zip"
    );

    /** snake() for binding positions — also mangles Python builtins */
    static String snakeBind(String name) {
        String s = snake(name);
        if (PY_BUILTINS.contains(s)) s = "_" + s;
        return s;
    }

    static String pascal(String name) {
        name = name.replace("<","").replace(">","");
        StringBuilder sb = new StringBuilder();
        for (String p : name.split("[-_]")) if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        return sb.toString();
    }

    private List<SExpr> list(SExpr e) {
        if (e instanceof SExpr.Pair p) return p.toList();
        if (e instanceof SExpr.Nil) return List.of();
        return List.of(e);
    }

    private SExpr nth(SExpr e, int n) throws SchemeException {
        List<SExpr> l = list(e);
        if (n >= l.size()) throw new SchemeException("expected element " + n, e.line());
        return l.get(n);
    }

    private String sym(SExpr e) throws SchemeException {
        if (e instanceof SExpr.Sym s) return s.name();
        throw new SchemeException("expected symbol", e.line());
    }

    private String pyStrList(List<String> items) {
        return "['" + String.join("', '", items) + "']";
    }
}

