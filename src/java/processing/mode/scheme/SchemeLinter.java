package processing.mode.scheme;

import processing.app.Problem;
import processing.app.Sketch;

import javax.swing.SwingUtilities;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/**
 * Live checking for SchemeMode, structured like CppMode's linter.
 *
 * Phase one (fast): each tab is parsed on its own, so an unbalanced paren is reported on the
 * right tab and line. Phase two (a moment later, only when everything parses): all tabs are
 * transpiled together, which catches malformed special forms, and the generated Python is checked
 * for names nothing defines. Those are mapped back to their Scheme line through the scm:N markers.
 */
public class SchemeLinter {

    private static final int PARSE_DEBOUNCE_MS = 150;
    private static final int DEEP_DEBOUNCE_MS = 600;

    /** Lists names the sketch part of the generated program reads but nothing defines. */
    private static final String CHECKER = """
        import ast, builtins, sys
        src = open(sys.argv[1]).read()
        lines = src.split(chr(10))
        cut = next((i for i, l in enumerate(lines) if l.strip() == '# -- sketch --'), -1)
        defined = set(dir(builtins))
        if cut >= 0:
            env = {'__name__': 'scheme_lint'}
            try:
                exec(chr(10).join(lines[:cut]), env)
                defined |= set(env)
            except Exception:
                pass
        tree = ast.parse(src)
        for n in ast.walk(tree):
            if isinstance(n, (ast.FunctionDef, ast.ClassDef)): defined.add(n.name)
            elif isinstance(n, ast.arg): defined.add(n.arg)
            elif isinstance(n, ast.ExceptHandler) and n.name: defined.add(n.name)
            elif isinstance(n, ast.Name) and isinstance(n.ctx, ast.Store): defined.add(n.id)
            elif isinstance(n, ast.Import): defined.update((a.asname or a.name).split('.')[0] for a in n.names)
            elif isinstance(n, ast.ImportFrom): defined.update(a.asname or a.name for a in n.names if a.name != '*')
        marks = {}
        cur = None
        for i, l in enumerate(lines, 1):
            t = l.strip()
            if t.startswith('# scm:'):
                try: cur = int(t[6:])
                except ValueError: pass
            marks[i] = cur
        seen = set()
        for n in ast.walk(tree):
            if isinstance(n, ast.Name) and isinstance(n.ctx, ast.Load) and n.id not in defined and n.lineno - 1 > cut:
                k = (n.id, marks.get(n.lineno))
                if k[1] and k not in seen:
                    seen.add(k)
                    print(n.id + chr(9) + str(k[1]))
        """;

    private final SchemeEditor editor;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SchemeLinter");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingParse, pendingDeep;
    private volatile List<Problem> problems = new ArrayList<>();
    private volatile int generation = 0;
    private volatile int parseFailedGeneration = -1;
    private File checkerFile;

    public SchemeLinter(SchemeEditor editor) {
        this.editor = editor;
    }

    /** Called on every edit; cancels pending checks and schedules both phases for this text. */
    public synchronized void scheduleCheck(Sketch sketch, int tabIndex, String source) {
        if (pendingParse != null) pendingParse.cancel(false);
        if (pendingDeep != null) pendingDeep.cancel(false);
        int gen = ++generation;
        List<String> tabs = new ArrayList<>();
        for (int i = 0; i < sketch.getCodeCount(); i++)
            tabs.add(i == tabIndex ? source : sketch.getCode(i).getProgram());
        String name = sketch.getName();
        pendingParse = scheduler.schedule(() -> phaseOne(gen, tabs), PARSE_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        pendingDeep = scheduler.schedule(() -> phaseTwo(gen, tabs, name), DEEP_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    // Phase one: parse each tab on its own.
    private void phaseOne(int gen, List<String> tabs) {
        List<Problem> found = new ArrayList<>();
        for (int i = 0; i < tabs.size(); i++) {
            try {
                Parser.read(tabs.get(i));
            } catch (SchemeException e) {
                found.add(problemIn(tabs.get(i), i, e.line > 0 ? e.line - 1 : -1, e.getMessage()));
            } catch (Exception ignored) {}
        }
        if (!found.isEmpty()) parseFailedGeneration = gen;
        deliver(gen, found);
    }

    // Phase two: transpile everything, then look for names nothing defines.
    private void phaseTwo(int gen, List<String> tabs, String sketchName) {
        if (gen != generation || parseFailedGeneration == gen) return;
        int[] starts = new int[tabs.size()];
        String all = combine(tabs, starts);
        String py;
        try {
            py = new Transpiler().transpile(Parser.read(all), sketchName);
        } catch (SchemeException e) {
            deliver(gen, List.of(problemAt(tabs, starts, e.line > 0 ? e.line - 1 : -1, e.getMessage())));
            return;
        } catch (Exception e) {
            return;
        }
        List<Problem> found = new ArrayList<>();
        for (String[] hit : undefinedNames(py)) {
            try {
                int line = Integer.parseInt(hit[1]) - 1;
                found.add(problemAt(tabs, starts, line, "Unknown name: " + schemeName(hit[0])));
            } catch (NumberFormatException ignored) {}
        }
        deliver(gen, found);
    }

    /** Runs the checker over the generated program; each hit is {pythonName, schemeLine}. */
    private List<String[]> undefinedNames(String py) {
        List<String[]> out = new ArrayList<>();
        File tmp = null;
        try {
            if (checkerFile == null || !checkerFile.exists()) {
                checkerFile = File.createTempFile("scheme-lint-check", ".py");
                checkerFile.deleteOnExit();
                Files.writeString(checkerFile.toPath(), CHECKER);
            }
            tmp = File.createTempFile("scheme-lint", ".py");
            Files.writeString(tmp.toPath(), py);
            Process p = new ProcessBuilder("python3", checkerFile.getAbsolutePath(), tmp.getAbsolutePath())
                .redirectErrorStream(false).start();
            p.getOutputStream().close();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroyForcibly(); return out; }
            for (String line : text.split("\n")) {
                String[] parts = line.split("\t");
                if (parts.length == 2) out.add(parts);
            }
        } catch (Exception ignored) {
        } finally {
            if (tmp != null) tmp.delete();
        }
        return out;
    }

    /** Joins the tabs, recording the first line (0-based) of each one in starts. */
    private static String combine(List<String> tabs, int[] starts) {
        StringBuilder sb = new StringBuilder();
        int line = 0;
        for (int i = 0; i < tabs.size(); i++) {
            String t = tabs.get(i);
            starts[i] = line;
            sb.append(t);
            if (!t.endsWith("\n")) sb.append('\n');
            line += t.split("\n", -1).length - (t.endsWith("\n") ? 1 : 0);
        }
        return sb.toString();
    }

    /** A problem at a 0-based line of the combined source, placed on its own tab. */
    private static SchemeProblem problemAt(List<String> tabs, int[] starts, int line, String msg) {
        int tab = 0;
        if (line >= 0) for (int i = 0; i < starts.length; i++) if (starts[i] <= line) tab = i;
        return problemIn(tabs.get(tab), tab, line < 0 ? -1 : line - starts[tab], msg);
    }

    /** A problem underlining one line of a tab; -1 means the end of the tab. */
    private static SchemeProblem problemIn(String text, int tab, int line, String msg) {
        String[] lines = text.split("\n", -1);
        if (line < 0 || line >= lines.length) line = lines.length - 1;
        while (line > 0 && lines[line].trim().isEmpty()) line--;
        SchemeProblem p = new SchemeProblem(msg, tab, Math.max(0, line));
        p.setStartOffset(0);
        p.setStopOffset(Math.max(1, line >= 0 && line < lines.length ? lines[line].length() : 1));
        return p;
    }

    /** The Scheme spelling of a generated name, for messages: in_range_p becomes in-range? */
    private static String schemeName(String py) {
        String s = py.endsWith("_p") ? py.substring(0, py.length() - 2) + "?" : py;
        return s.replace('_', '-');
    }

    private void deliver(int gen, List<Problem> found) {
        if (gen != generation) return;
        problems = found;
        SwingUtilities.invokeLater(() -> {
            if (gen == generation) editor.setProblemList(problems);
        });
    }

    public List<Problem> getProblems() { return problems; }
    public void shutdown() { scheduler.shutdownNow(); }
}
