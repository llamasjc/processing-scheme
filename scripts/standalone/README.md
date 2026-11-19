# processing-scheme standalone

Processing sketches in Scheme, without the Processing IDE. Python and the
libprocessing engine for Linux (x86_64), macOS (Apple Silicon) and Windows
are in `python/`. You need Java 17 or newer, which turns the Scheme into
Python (Processing's own Java works).

    ./run.sh examples/Basics/HelloCircle/HelloCircle.scm     Linux, macOS
    run.bat examples\Basics\HelloCircle\HelloCircle.scm      Windows

`examples/` has the sketches that come with Scheme Mode. Files a sketch
loads go in a `data/` folder next to it. Each run writes the Python program
to `build/` next to the sketch.
