package fr.universalserverloader.diagnostics;

public final class JavaRuntime {
    private JavaRuntime() { }

    public static int majorVersion() {
        String value = System.getProperty("java.specification.version", "0");
        if (value.startsWith("1.")) value = value.substring(2);
        int dot = value.indexOf('.');
        if (dot >= 0) value = value.substring(0, dot);
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return 0; }
    }
}
