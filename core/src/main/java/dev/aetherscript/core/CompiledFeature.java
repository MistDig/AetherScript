package dev.aetherscript.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record CompiledFeature(Feature definition, String sourceHash, String irHash, @JsonIgnore Program program) {
    public CompiledFeature(Feature definition, String sourceHash, String irHash) {
        this(definition, sourceHash, irHash, null);
    }
    public interface Program {
        List<Feature.MessageAction> evaluate(List<String> arguments);
    }
}
