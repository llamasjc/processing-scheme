package processing.mode.scheme;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import processing.app.*;
import processing.app.syntax.*;
import processing.app.ui.*;

public class SchemeEditor extends Editor {
    private TsSchemeTokenMarker tsMarker;

    private SchemeRunner currentRunner;
    private SchemeLinter  linter;

    public SchemeEditor(Base base, String path,
                        EditorState state, Mode mode) throws EditorException {
        super(base, path, state, mode);
        // ── Tree-sitter syntax highlighting
        javax.swing.SwingUtilities.invokeLater(() -> {
            getTextArea().addCaretListener(e -> refreshHighlights());
            refreshHighlights();
            // Go-to-definition: F3, or Ctrl+click on a name
            getTextArea().getInputHandler().addKeyBinding("F3",
                (java.awt.event.ActionListener) ev -> handleGoToDefinition());
            getTextArea().getPainter().addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseReleased(java.awt.event.MouseEvent ev) {
                    if (ev.isControlDown()) javax.swing.SwingUtilities.invokeLater(() -> handleGoToDefinition());
                }
            });
        });
    }

    // ── Text area with PdeTextArea for line numbers + gutter ─────────────────
    @Override
    protected JEditTextArea createTextArea() {
        PdeTextArea pta = new PdeTextArea(new PdeTextAreaDefaults(),
                               new SchemeInputHandler(this), this);
        EventQueue.invokeLater(() -> {
            linter = new SchemeLinter(SchemeEditor.this);
            getTextArea().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                void run() {
                    if (linter != null)
                        linter.scheduleCheck(sketch, sketch.getCurrentCodeIndex(), getText());
                }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { run(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { run(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { run(); }
            });
            errorOverlay = new ErrorOverlay(pta);
            pta.getPainter().setLayout(null);
            pta.getPainter().add(errorOverlay);
            pta.getPainter().addComponentListener(new java.awt.event.ComponentAdapter() {
                public void componentResized(java.awt.event.ComponentEvent e) {
                    errorOverlay.setBounds(0, 0,
                        pta.getPainter().getWidth(), pta.getPainter().getHeight());
                }
            });
        });
        return pta;
    }

    // ── Required abstract overrides (exact CppEditor pattern) ─────────────────
    @Override public EditorToolbar createToolbar()               { return new SchemeToolbar(this); }
    @Override public Formatter     createFormatter()             { return null; }
    @Override public String        getCommentPrefix()            { return ";"; }
    @Override public void          handleImportLibrary(String n) { }
    @Override public void          startIndeterminate()          { }
    @Override public void          stopIndeterminate()           { }
    @Override public void          statusHalt()                  { }
    @Override public boolean       isHalted()                    { return currentRunner == null; }
    @Override public void          deactivateRun()               { toolbar.deactivateRun(); }

    @Override
    public void internalCloseRunner() {
        if (currentRunner != null) { currentRunner.stop(); currentRunner = null; }
        setProblemList(new java.util.ArrayList<>());
    }

    // ── Menus ─────────────────────────────────────────────────────────────────
    @Override
    public JMenu buildFileMenu() {
        JMenuItem exportApp = new JMenuItem("Export Application...");
        exportApp.addActionListener(ev -> handleExportApplication());
        return buildFileMenu(new JMenuItem[]{ exportApp });
    }

    @Override
    public JMenu buildHelpMenu() {
        return new JMenu("Help");
    }

    @Override
    public JMenu buildSketchMenu() {
        JMenuItem run  = processing.app.ui.Toolkit.newJMenuItem("Run", 'R');
        JMenuItem stop = new JMenuItem("Stop");
        run.addActionListener(e  -> handleRun());
        stop.addActionListener(e -> handleStop());
        return buildSketchMenu(new JMenuItem[]{ run, stop });
    }

    // ── Run / Stop ────────────────────────────────────────────────────────────
    public void handleRun() {
        internalCloseRunner();
        clearErrorHighlight();
        SchemeMode mode = (SchemeMode) getMode();
        // Skip read-only check — we run from generated Python, not the source file
        if (!mode.isMewnalaAvailable()) {
            statusError("mewnala not found. Run: pip install mewnala");
            deactivateRun();
            return;
        }
        statusNotice("Running...");
        new Thread(() -> {
            currentRunner = mode.handleLaunch(sketch, makeListener(), this);
            if (currentRunner == null) {
                EventQueue.invokeLater(this::deactivateRun);
            }
        }, "scheme-runner").start();
    }

    public void handleStop() {
        internalCloseRunner();
        deactivateRun();
        statusNotice("Stopped.");
    }

    void onSketchStopped() {
        currentRunner = null;
        EventQueue.invokeLater(this::deactivateRun);
    }

    // ── Error reporting with gutter line numbers (CppEditor pattern) ──────────
    void reportError(SchemeError err) {
        EventQueue.invokeLater(() -> {
            if (err.line >= 1 && textarea instanceof PdeTextArea) {
                PdeTextArea pta = (PdeTextArea) textarea;
                try {
                    int docLine = err.line - 1;
                    if (docLine >= pta.getLineCount()) return;
                    pta.clearGutterText();
                    pta.setGutterText(docLine, processing.app.syntax.PdeTextArea.STEP_MARKER);
                    pta.getPainter().repaint();
                    pta.scrollTo(docLine, 0);
                    pta.select(
                        pta.getLineStartOffset(docLine),
                        pta.getLineStopOffset(docLine) - 1
                    );
                } catch (Exception ignored) {}
                statusError(err.message + "  (line " + err.line + ")");
            } else {
                statusError(err.message);
            }
        });
    }

    void clearErrorHighlight() {
        if (errorOverlay != null) errorOverlay.clear();
        if (textarea instanceof PdeTextArea) {
            ((PdeTextArea) textarea).clearGutterText();
        }
    }

    // ── Comment toggle (Scheme: use ; prefix) ─────────────────────────────────
    void toggleComment(JEditTextArea ta) {
        int selStart  = ta.getSelectionStart();
        int selEnd    = ta.getSelectionStop();
        int startLine = ta.getLineOfOffset(selStart);
        int endLine   = ta.getLineOfOffset(selEnd);
        if (endLine > startLine && ta.getLineStartOffset(endLine) == selEnd) endLine--;

        boolean allCommented = true;
        for (int i = startLine; i <= endLine; i++) {
            if (!getLineText(ta, i).stripLeading().startsWith(";")) {
                allCommented = false; break;
            }
        }
        for (int i = endLine; i >= startLine; i--) {
            String raw = getLineText(ta, i);
            if (allCommented)
                replaceLineText(ta, i, raw.replaceFirst("(\\s*);\\s?", "$1"));
            else {
                int indent = 0;
                while (indent < raw.length() &&
                       (raw.charAt(indent)==' ' || raw.charAt(indent)=='\t')) indent++;
                replaceLineText(ta, i,
                    raw.substring(0, indent) + "; " + raw.substring(indent));
            }
        }
    }

    private String getLineText(JEditTextArea ta, int line) {
        try {
            int start = ta.getLineStartOffset(line);
            int end   = ta.getLineStopOffset(line) - 1;
            return ta.getDocument().getText(start, Math.max(0, end - start));
        } catch (javax.swing.text.BadLocationException e) { return ""; }
    }

    private void replaceLineText(JEditTextArea ta, int line, String text) {
        try {
            int start = ta.getLineStartOffset(line);
            int end   = ta.getLineStopOffset(line) - 1;
            ta.getDocument().remove(start, Math.max(0, end - start));
            ta.getDocument().insertString(start, text, null);
        } catch (javax.swing.text.BadLocationException e) {}
    }

    // ── RunnerListener ────────────────────────────────────────────────────────
    private RunnerListener makeListener() {
        return new RunnerListener() {
            @Override public void statusError(String m)    { SchemeEditor.this.statusError(m); }
            @Override public void statusError(Exception e) { SchemeEditor.this.statusError(e.getMessage()); }
            @Override public void statusNotice(String m)   { SchemeEditor.this.statusNotice(m); }
            @Override public void statusHalt()             { SchemeEditor.this.onSketchStopped(); }
            @Override public boolean isHalted()            { return currentRunner == null; }
            @Override public void startIndeterminate()     { }
            @Override public void stopIndeterminate()      { }
        };
    }
        // ── Error overlay (draws directly on painter, no debugger required) ────
    private ErrorOverlay errorOverlay;

    private class ErrorOverlay extends javax.swing.JPanel {
        private int errorLine = -1;
        ErrorOverlay(processing.app.syntax.JEditTextArea ta) {
            setOpaque(false);
            setLayout(null);
        }
        void setErrorLine(int line) { this.errorLine = line; repaint(); }
        void clear() { this.errorLine = -1; repaint(); }
        @Override protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            if (errorLine < 0 || !(textarea instanceof processing.app.syntax.PdeTextArea)) return;
            processing.app.syntax.PdeTextArea pta = (processing.app.syntax.PdeTextArea) textarea;
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
            int y = pta.lineToY(errorLine);
            int h = pta.getPainter().getFontMetrics().getHeight();
            g2.setColor(new java.awt.Color(255, 0, 0, 40));
            g2.fillRect(processing.app.ui.Editor.LEFT_GUTTER, y, getWidth(), h);
            g2.setColor(new java.awt.Color(255, 60, 60));
            int ax = processing.app.ui.Editor.LEFT_GUTTER - 12;
            int ay = y + h/2;
            g2.fillPolygon(new int[]{ax, ax-6, ax-6}, new int[]{ay, ay-4, ay+4}, 3);
        }
    }


    /** Point the current document's marker at that document and rebuild. Covers typing and tab switches. */
    private void refreshHighlights() {
        // Processing 4.5 shares one TokenMarker per mode; documents only hold per-line state.
        // So the marker always reads whichever tab the text area is showing.
        if (getMode().getTokenMarker() instanceof TsSchemeTokenMarker m) {
            m.setSource(() -> getTextArea().getText());
            m.setOnUpdate(() -> getTextArea().getPainter().repaint());
            m.invalidate();
        }
    }

    /** Jump to the definition of the name at the caret; does nothing if it isn't defined in the sketch. */
    public void handleGoToDefinition() {
        String word = wordAtCaret();
        if (word == null || word.isBlank()) return;
        java.util.List<String> tabs = new java.util.ArrayList<>();
        int current = sketch.getCurrentCodeIndex();
        for (int i = 0; i < sketch.getCodeCount(); i++)
            tabs.add(i == current ? getText() : sketch.getCode(i).getProgram());
        SchemeGoToDefinition.Definition def = SchemeGoToDefinition.find(tabs, word);
        if (def == null) { statusNotice("No definition of " + word + " in this sketch"); return; }
        if (def.tabIndex() != current) sketch.setCurrentCode(def.tabIndex());
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                getTextArea().setCaretPosition(getTextArea().getLineStartOffset(def.row()) + def.col());
                getTextArea().scrollTo(def.row(), def.col());
            } catch (Exception ignored) {}
        });
    }

    /** The Scheme name under the caret, split at Scheme delimiters (so make-ball! and in-range? work). */
    private String wordAtCaret() {
        try {
            int pos = getTextArea().getCaretPosition();
            String src = getTextArea().getDocument().getText(0, getTextArea().getDocumentLength());
            if (pos < 0 || pos > src.length()) return null;
            int start = pos;
            while (start > 0 && !SchemeGoToDefinition.isDelimiter(src.charAt(start - 1))) start--;
            int end = pos;
            while (end < src.length() && !SchemeGoToDefinition.isDelimiter(src.charAt(end))) end++;
            return end > start ? src.substring(start, end) : null;
        } catch (Exception e) { return null; }
    }

    /**
     * Export Application: a folder with the generated program, its assets, the installed mewnala
     * package (bundled, so nothing needs installing), a run.sh launcher and a README.
     */
    /** Builds a self-contained Linux AppImage (scripts/export.py) in the background. */
    private void exportAppImage(java.io.File program, String name, String pythonExe) {
        java.io.File exporter = new java.io.File(((SchemeMode) mode).getFolder(), "scripts/export.py");
        if (!exporter.exists()) return;
        java.io.File sketchDir = sketch.getFolder();
        java.io.File outDir = new java.io.File(sketchDir, "export");
        Thread t = new Thread(() -> {
            String msg;
            boolean ok = false;
            try {
                ProcessBuilder pb = new ProcessBuilder(pythonExe, exporter.getPath(), sketchDir.getPath(),
                    "--out", outDir.getPath(), "--program", program.getPath(), "--name", name);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                String out = new String(proc.getInputStream().readAllBytes());
                ok = proc.waitFor() == 0;
                String[] lines = out.strip().split("\n");
                msg = lines.length > 0 ? lines[lines.length - 1] : "";
            } catch (Exception ex) {
                msg = ex.getMessage();
            }
            final boolean fine = ok;
            final String last = msg;
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (fine) statusNotice("Linux AppImage ready in " + outDir);
                else statusError("AppImage export failed: " + last);
            });
        }, "scheme-appimage-export");
        t.setDaemon(true);
        t.start();
    }

    public void handleExportApplication() {
        try {
            SchemeBuild build = new SchemeBuild(sketch, (SchemeMode) mode);
            if (!build.prepare(this)) return;
            java.io.File script = build.launch.pythonScript;
            String name = build.launch.sketchName;
            java.nio.file.Path out = sketch.getFolder().toPath().resolve("export").resolve("linux").resolve(name);
            deleteTree(out);
            java.nio.file.Files.createDirectories(out);
            java.nio.file.Files.copy(script.toPath(), out.resolve(name + ".py"));
            SchemeRunner.copyAssets(sketch.getFolder(), out.resolve("assets").toFile());

            String[] info = pythonInfo(build.launch.pythonExe);
            java.nio.file.Path pkg = java.nio.file.Path.of(info[0]);
            String pyVersion = info[1];
            copyTree(pkg, out.resolve("mewnala"));

            String run = "#!/bin/sh\n"
                + "# " + name + ": a SchemeMode sketch. See README.txt.\n"
                + "cd \"$(dirname \"$0\")\"\n"
                + "command -v python3 >/dev/null 2>&1 || { echo \"python3 is needed to run this sketch\"; exit 1; }\n"
                + "python3 -c 'import sys; sys.exit(0 if \"%d.%d\" % sys.version_info[:2] == \"" + pyVersion + "\" else 1)' || {\n"
                + "  echo \"This export was made for Python " + pyVersion + ". Found: $(python3 --version)\"; exit 1; }\n"
                + "export RUST_LOG=error BEVY_LOG=error WGPU_LOG=error MEWNALA_BACKEND=glfw-x11\n"
                + "if [ -n \"$SCHEME_RAW_LOG\" ]; then exec python3 -u \"" + name + ".py\" \"$@\"; fi\n"
                + "# mewnala ignores RUST_LOG, so drop its TRACE..WARN log lines here (ERROR lines stay)\n"
                + "ESC=$(printf '\\033')\n"
                + "python3 -u \"" + name + ".py\" \"$@\" 2>&1 | grep -av --line-buffered -E \"^(${ESC}\\\\[[0-9;]*m)*[0-9]{4}-[0-9]{2}-[0-9]{2}T[^ ]* +(${ESC}\\\\[[0-9;]*m)*(TRACE|DEBUG|INFO|WARN)\"\n";
            java.nio.file.Path runSh = out.resolve("run.sh");
            java.nio.file.Files.writeString(runSh, run);
            runSh.toFile().setExecutable(true, false);

            String readme = name + "\n\n"
                + "A Processing sketch written in Scheme, exported from SchemeMode.\n\n"
                + "To run it:  ./run.sh\n\n"
                + "It needs:\n"
                + "  - Python " + pyVersion + "\n"
                + "  - a Vulkan graphics driver and an X11 desktop\n\n"
                + "The mewnala graphics library it draws with is included in the mewnala folder,\n"
                + "so there is nothing to install beyond Python. Files the sketch loads are in assets.\n";
            java.nio.file.Files.writeString(out.resolve("README.txt"), readme);

            exportPortable(out.getParent().getParent(), name, script.toPath());
            exportAppImage(script, name, String.valueOf(build.launch.pythonExe));
            statusNotice("Exported for Linux, Windows and macOS to " + out.getParent().getParent());
            openFolder(out.toFile());
        } catch (Exception ex) {
            statusError("Export failed: " + ex.getMessage());
        }
    }

    /** Where the mewnala package is installed, and the Python version, from the Python that runs sketches. */
    private static String[] pythonInfo(String pythonExe) throws Exception {
        Process p = new ProcessBuilder(pythonExe, "-c",
            "import mewnala, os, sys; print(os.path.dirname(mewnala.__file__)); print('%d.%d' % sys.version_info[:2])")
            .redirectErrorStream(true).start();
        String outText = new String(p.getInputStream().readAllBytes()).trim();
        if (p.waitFor() != 0) throw new Exception("could not locate mewnala: " + outText);
        String[] lines = outText.split("\n");
        return new String[]{ lines[lines.length - 2].trim(), lines[lines.length - 1].trim() };
    }

    private static void copyTree(java.nio.file.Path src, java.nio.file.Path dst) throws java.io.IOException {
        try (var walk = java.nio.file.Files.walk(src)) {
            for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) walk::iterator) {
                if (f.toString().contains("__pycache__")) continue;
                java.nio.file.Path t = dst.resolve(src.relativize(f).toString());
                if (java.nio.file.Files.isDirectory(f)) java.nio.file.Files.createDirectories(t);
                else java.nio.file.Files.copy(f, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteTree(java.nio.file.Path p) throws java.io.IOException {
        if (!java.nio.file.Files.exists(p)) return;
        try (var walk = java.nio.file.Files.walk(p)) {
            java.util.List<java.nio.file.Path> all = new java.util.ArrayList<>();
            walk.forEach(all::add);
            java.util.Collections.reverse(all);
            for (java.nio.file.Path f : all) java.nio.file.Files.delete(f);
        }
    }

    /** Shows a folder in the file manager; failure to open it is harmless (the export is already done). */
    private static void openFolder(java.io.File dir) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(dir);
                return;
            }
        } catch (Exception ignored) {}
        try { new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start(); } catch (Exception ignored) {}
    }

    private static final String WIN_LAUNCHER =
        "@echo off\r\n"
        + "rem NAME: a SchemeMode sketch. See README.txt.\r\n"
        + "cd /d \"%~dp0\"\r\n"
        + "set PY=python\r\n"
        + "where py >nul 2>nul && set PY=py -3\r\n"
        + "%PY% -c \"import mewnala\" >nul 2>nul || %PY% -m pip install --user mewnala==0.0.8\r\n"
        + "%PY% -u \"NAME.py\" %*\r\n"
        + "pause\r\n";

    private static final String MAC_LAUNCHER =
        "#!/bin/sh\n"
        + "# NAME: a SchemeMode sketch. See README.txt.\n"
        + "cd \"$(dirname \"$0\")\"\n"
        + "command -v python3 >/dev/null 2>&1 || { echo \"Python 3 is needed: https://www.python.org/downloads/\"; exit 1; }\n"
        + "python3 -c 'import mewnala' 2>/dev/null || python3 -m pip install --user mewnala==0.0.8\n"
        + "exec python3 -u \"NAME.py\" \"$@\"\n";

    /** Windows and macOS exports: program, assets, a launcher that installs mewnala from PyPI on first run. */
    private void exportPortable(java.nio.file.Path exportDir, String name, java.nio.file.Path script)
            throws java.io.IOException {
        String[][] targets = {
            { "windows", "run.bat", WIN_LAUNCHER, "Windows", "double-click run.bat" },
            { "macos", "run.command", MAC_LAUNCHER, "macOS", "double-click run.command (or run ./run.command in Terminal)" },
        };
        for (String[] t : targets) {
            java.nio.file.Path dir = exportDir.resolve(t[0]).resolve(name);
            deleteTree(dir);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.copy(script, dir.resolve(name + ".py"));
            SchemeRunner.copyAssets(sketch.getFolder(), dir.resolve("assets").toFile());
            java.nio.file.Path launcher = dir.resolve(t[1]);
            java.nio.file.Files.writeString(launcher, t[2].replace("NAME", name));
            launcher.toFile().setExecutable(true, false);
            String readme = name + "\n\n"
                + "A Processing sketch written in Scheme, exported from SchemeMode for " + t[3] + ".\n\n"
                + "To run it: " + t[4] + ".\n\n"
                + "It needs Python 3 (https://www.python.org/downloads/). The first run installs the\n"
                + "mewnala graphics library with pip; after that it starts right away.\n\n"
                + "This version of mewnala comes from PyPI, so a few features from the Linux build are\n"
                + "missing here (ambient light, hiding the cursor). Sketches that use them still run and\n"
                + "print a short notice instead.\n";
            java.nio.file.Files.writeString(dir.resolve("README.txt"), readme);
        }
    }
}
