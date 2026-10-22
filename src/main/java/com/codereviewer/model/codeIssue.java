package com.codereviewer.model;

public class codeIssue {

    private final String rule;
    private final String severity;
    private final int line;
    private final String message;
    private final String suggestion;
    private final String category; // "security" | "quality" | "maintainability"

    public codeIssue(
            String rule,
            String severity,
            int line,
            String message,
            String suggestion) {

        this.rule = rule;
        this.severity = severity;
        this.line = line;
        this.message = message;
        this.suggestion = suggestion;
        this.category = resolveCategory(rule);
    }

    // =========================================================
    // CATEGORY RESOLUTION — maps analyzer rule codes to UI category
    // =========================================================

    private static String resolveCategory(String rule) {
        if (rule == null) return "quality";
        switch (rule) {
            // Security analyzers
            case "JAVA012": // HardcodedSecretAnalyzer
            case "JAVA013": // SqlInjectionAnalyzer
                return "security";

            // Quality analyzers
            case "EMPTY_CATCH":   // EmptyCatchAnalyzer
            case "GENERIC_CATCH": // GenericCatchAnalyzer
            case "SYSTEM OUT":    // SystemOutAnalyzer
                return "quality";

            // Maintainability analyzers
            case "JAVA007":       // DeepNestingAnalyzer
            case "MAGIC_NUMBER":  // MagicNumberAnalyzer
            case "LONG_METHOD":   // LongMethodAnalyzer
            case "LONG_CLASS":    // LongClassAnalyzer
            case "TOO_MANY_ARGS": // TooManyArgumentsAnalyzer
            case "EMPTY_METHOD":  // EmptyMethodAnalyzer
            case "TODO":          // TodoAnalyzer
            case "FIXME":         // TodoAnalyzer
            case "COMPLEXITY":    // ComplexityAnalyzer
                return "maintainability";

            default:
                return "quality";
        }
    }

    public String getRule() {
        return rule;
    }

    public String getSeverity() {
        return severity;
    }

    public int getLine() {
        return line;
    }

    public String getMessage() {
        return message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return "Rule: " + rule
                + "\nSeverity: " + severity
                + "\nCategory: " + category
                + "\nLine: " + line
                + "\nMessage: " + message
                + "\nSuggestion: " + suggestion;
    }
}