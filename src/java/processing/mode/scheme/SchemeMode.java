package processing.mode.scheme;

import processing.app.*;
import processing.app.ui.*;
import java.io.File;

public class SchemeMode extends Mode {

    public SchemeMode(Base base, File folder) {
        super(base, folder);
        // macOS quarantines everything that came out of a downloaded zip and
        // then refuses to start the bundled Python; clear it once at startup
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            new Thread(() -> {
                try { new ProcessBuilder("xattr", "-dr", "com.apple.quarantine", folder.getAbsolutePath()).start().waitFor(); }
                catch (Exception ignored) {}
            }, "schememode-xattr").start();
        }
        try { new java.io.FileWriter(System.getProperty("user.home") + "/scheme-debug.txt",true).append("SchemeMode constructor called\n").close(); } catch(Exception _e) { System.err.println("DEBUG WRITE FAILED: " + _e); }
    }

    @Override public String   getTitle()            { return "Scheme"; }
    @Override public String   getDefaultExtension() { return "scm"; }
    @Override public String[] getExtensions()       { return new String[]{"scm","sch"}; }
    @Override public String[] getIgnorable()        { return new String[]{"scm~","build","__pycache__"}; }

    @Override
    public File[] getExampleCategoryFolders() {
        return new File[]{
            new File(examplesFolder, "Basics"),
            new File(examplesFolder, "Topics"),
            new File(examplesFolder, "3D"),
        };
    }

    @Override
    public Editor createEditor(Base base, String path,
                               EditorState state) throws EditorException {
        return new SchemeEditor(base, path, state, this);
    }

    public SchemeRunner handleLaunch(Sketch sketch, RunnerListener listener, SchemeEditor editor) {
        SchemeBuild build = new SchemeBuild(sketch, this);
        if (!build.prepare(listener)) return null;
        SchemeRunner runner = new SchemeRunner(build, listener, editor);
        new Thread(runner::launch, "scheme-runner").start();
        return runner;
    }

    public void handleStop(SchemeRunner runner) {
        if (runner != null) runner.stop();
    }

    public File getModeJarFolder() { return new File(getFolder(), "mode"); }

    @Override
    protected processing.app.syntax.TokenMarker createTokenMarker() {
        return new TsSchemeTokenMarker();
    }

    @Override
    public File[] getKeywordFiles() {
        File f = new File(getFolder(), "resources/keywords.txt");
        return new File[]{ f };
    }

    /** The folder the zip's Python for this computer is in: python/linux-x86_64 and so on. */
    static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        boolean arm = System.getProperty("os.arch", "").matches("aarch64|arm64");
        if (os.contains("win")) return "windows-x86_64";
        if (os.contains("mac")) return arm ? "macos-aarch64" : "macos-x86_64";
        return arm ? "linux-aarch64" : "linux-x86_64";
    }

    /**
     * Python with mewnala: the one that comes in the zip first, so nothing has
     * to be installed. Then a venv in the mode folder (for development), then
     * python3 on PATH.
     */
    public String getPythonExecutable() {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        File bundled = new File(getFolder(), "python/" + platform() + (win ? "/python.exe" : "/bin/python3"));
        if (bundled.isFile()) {
            if (!win) bundled.setExecutable(true, false);
            return bundled.getAbsolutePath();
        }
        for (String rel : new String[]{"venv/bin/python3", ".venv/bin/python3"}) {
            File f = new File(getFolder(), rel);
            if (f.exists()) return f.getAbsolutePath();
        }
        return "python3";
    }

    public boolean isMewnalaAvailable() {
        try {
            Process p = new ProcessBuilder(getPythonExecutable(), "-c", "import mewnala")
                .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) { return false; }
    }
}
