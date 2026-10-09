package processing.mode.scheme;

import processing.app.*;
import java.io.*;

public class SchemeRunner {
    private final SchemeBuild.PreparedLaunch launch;
    private final LineMapper                 lineMapper;
    private final RunnerListener             listener;
    private final SchemeEditor               editor;
    private Process          process;
    private volatile boolean stopping;
    private final StringBuilder stderrBuf = new StringBuilder();

    public SchemeRunner(SchemeBuild build, RunnerListener listener, SchemeEditor editor) {
        this.launch = build.launch; this.lineMapper = build.lineMapper;
        this.listener = listener; this.editor = editor;
    }

    public void launch() {
        listener.statusNotice("Running " + launch.sketchName + "…");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                launch.pythonExe, "-u", launch.pythonScript.getAbsolutePath());
            pb.environment().put("RUST_LOG", "error");
            pb.environment().put("BEVY_LOG", "error");
            pb.environment().put("WGPU_LOG", "error");
            pb.environment().put("BEVY_ASSET_LOG", "error");
            pb.environment().put("RUST_LOG_STYLE", "never");
            pb.environment().put("DISPLAY", System.getenv().getOrDefault("DISPLAY", ":0"));
            pb.environment().put("GDK_BACKEND", "x11");
            pb.environment().put("QT_QPA_PLATFORM", "xcb");
            pb.environment().put("MEWNALA_BACKEND", "glfw-x11");
            pb.directory(launch.pythonScript.getParentFile());
            process = pb.start();
            pipeOut(process.getInputStream());
            pipeErr(process.getErrorStream());
            int code = process.waitFor();
            if (!stopping && code != 0 && stderrBuf.length() > 0) {
                SchemeError err = lineMapper.parseTraceback(
                    stderrBuf.toString(), launch.sketchFileName);
                if (editor != null) editor.reportError(err);
                else listener.statusError(err.toString());
            } else if (!stopping && code != 0) {
                listener.statusError("Sketch exited with code " + code);
            }
        } catch (IOException e) {
            listener.statusError("Cannot start python3: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            listener.statusHalt();
        }
    }

    public void stop() {
        stopping = true;
        if (process != null && process.isAlive()) {
            process.destroy();
            try { process.waitFor(); } catch (InterruptedException ignored) {}
        }
    }

    // Rust tracing lines from Bevy/wgpu/mewnala: "2026-10-09T02:24:38.06Z  WARN target: ...".
    // TRACE..WARN are dropped so the sketch's own output and error messages stay visible;
    // ERROR passes through. Set SCHEME_RAW_LOG=1 to see everything.
    private static final java.util.regex.Pattern LOG_LINE =
        java.util.regex.Pattern.compile("^\\d{4}-\\d\\d-\\d\\dT\\S+\\s+(TRACE|DEBUG|INFO|WARN)\\s");
    private static final boolean RAW_LOG = System.getenv("SCHEME_RAW_LOG") != null;

    private static boolean isLogNoise(String l) {
        return !RAW_LOG && LOG_LINE.matcher(l).find();
    }

    private void pipeOut(InputStream is) {
        new Thread(() -> {
            try (var r = new BufferedReader(new InputStreamReader(is))) {
                String l; while ((l = r.readLine()) != null) { if (!isLogNoise(l)) System.out.println(l); }
            } catch (IOException ignored) {}
        }, "scheme-out").start();
    }

    private void pipeErr(InputStream is) {
        new Thread(() -> {
            try (var r = new BufferedReader(new InputStreamReader(is))) {
                String l;
                while ((l = r.readLine()) != null) {
                    if (isLogNoise(l)) continue;
                    System.err.println(l);
                    synchronized (stderrBuf) { stderrBuf.append(l).append("\n"); }
                }
            } catch (IOException ignored) {}
        }, "scheme-err").start();
    }
}
