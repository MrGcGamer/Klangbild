# Klangbild R8 rules. Keep this file small; most libraries ship their own rules.

# commons-suncalc annotates with SpotBugs' @Nullable (a compile-only dependency it does not ship).
-dontwarn edu.umd.cs.findbugs.annotations.**
