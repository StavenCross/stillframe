package dev.stillframe;

public final class PackMappingTest {
    public static void main(String[] args) {
        PackMapping mapping = new PackMapping();
        mapping.add("ComponentInfo{demo.app/.Mobile}", "mobile");
        mapping.add("ComponentInfo{demo.app/demo.app.Television}", "tv");
        mapping.add("ComponentInfo{demo.app/}", "package_default");
        mapping.add("ComponentInfo{a.b/.One}", "other");
        mapping.add("ComponentInfo{a.b/.One}", "");
        mapping.add("invalid component", "ignored");
        if (!"tv".equals(mapping.find("demo.app", "demo.app.Television")))
            throw new AssertionError("exact TV mapping must win");
        if (!"mobile".equals(mapping.find("demo.app", "demo.app.Mobile")))
            throw new AssertionError("relative activity names must normalize");
        if (!"package_default".equals(mapping.find("demo.app", "demo.app.Unknown")))
            throw new AssertionError("explicit package-level fallback must win");
        if (!"other".equals(mapping.find("a.b", "a.b.NewEntry")))
            throw new AssertionError("missing activity may reuse the package's valid mark");
        if (mapping.find("missing", ".TV") != null)
            throw new AssertionError("unmapped packages need a native fallback");
    }
}
