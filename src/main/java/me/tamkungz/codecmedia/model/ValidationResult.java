package me.tamkungz.codecmedia.model;

import java.util.List;

public record ValidationResult(
        boolean valid,
        List<String> warnings,
        List<String> errors
) {
}
