package processing.mode.scheme;

import processing.app.Problem;
import processing.app.Sketch;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class SchemeLinter {

    private final SchemeEditor editor;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scheme-linter");
            t.setDaemon(true);
            return t;
        });

    private ScheduledFuture<?> pending;
    private volatile List<Problem> problems = new ArrayList<>();

    public SchemeLinter(SchemeEditor editor) {
        this.editor = editor;
    }

    public void scheduleCheck(Sketch sketch, int tabIndex, String source) {
        if (pending != null) pending.cancel(false);
        pending = scheduler.schedule(() -> check(sketch, tabIndex, source),
                                     300, TimeUnit.MILLISECONDS);
    }

    private void check(Sketch sketch, int tabIndex, String source) {
        List<Problem> found = new ArrayList<>();
        try {
            Parser.parse(source);
        } catch (SchemeException e) {
            // If error is at EOF, find last non-empty line
            int eLine = e.line > 0 ? e.line : source.split("\n", -1).length;
            int line = Math.max(0, eLine - 1);
            String[] sourceLines = source.split("\n", -1);
            while (line > 0 && sourceLines[line].trim().isEmpty()) line--;
            String errLine = line < sourceLines.length ? sourceLines[line] : "";
            int lineLen = errLine.length();
            SchemeProblem p = new SchemeProblem(e.getMessage(), tabIndex, line);
            p.setStartOffset(0);
            p.setStopOffset(Math.max(1, lineLen));
            found.add(p);
        } catch (Exception ignored) {}
        problems = found;
        SwingUtilities.invokeLater(() -> editor.setProblemList(problems));
    }

    public List<Problem> getProblems() { return problems; }
    public void shutdown() { scheduler.shutdownNow(); }
}
