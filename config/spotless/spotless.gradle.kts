# Spotless config: only runs `spotlessCheck` and `spotlessApply` when explicitly invoked.
# We do not auto-format on every build (per "no aggressive refactor" preference).

spotless {
    kotlin {
        target("src/*/kotlin/**/*.kt", "src/commonMain/kotlin/**/*.kt", "src/androidMain/kotlin/**/*.kt", "src/jvmMain/kotlin/**/*.kt")
        ktlint().editorConfigOverride(
            "*.{kt,kts}" to mapOf(
                "max_line_length" to "140",
                "ij_kotlin_imports_layout" to "*,java.**,javax.**,kotlin.**,^",
                "ktlint_code_style" to "intellij_idea"
            )
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}
