package processing.mode.scheme;

import processing.app.Problem;

public class SchemeProblem implements Problem {
    private final String message;
    private final int tabIndex;
    private final int lineNumber;
    private int startOffset;
    private int stopOffset;

    public SchemeProblem(String message, int tabIndex, int lineNumber) {
        this.message = message;
        this.tabIndex = tabIndex;
        this.lineNumber = lineNumber;
    }

    public void setStartOffset(int o) { startOffset = o; }
    public void setStopOffset(int o)  { stopOffset = o; }

    @Override public String getMessage()    { return message; }
    @Override public boolean isError()      { return true; }
    @Override public boolean isWarning()    { return false; }
    @Override public int getTabIndex()      { return tabIndex; }
    @Override public int getLineNumber()    { return lineNumber; }
    @Override public int getStartOffset()   { return startOffset; }
    @Override public int getStopOffset()    { return stopOffset; }
}
