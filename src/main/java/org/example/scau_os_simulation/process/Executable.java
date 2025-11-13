package org.example.scau_os_simulation.process;

import java.util.ArrayList;
import java.util.List;

public class Executable {
    private final List<String> instructions = new ArrayList<>();

    public Executable(List<String> lines) {
        if (lines != null) instructions.addAll(lines);
    }

    public String fetch(int pc) {
        if (pc < 0 || pc >= instructions.size()) return "end";
        return instructions.get(pc);
    }

    public int length() {
        return instructions.size();
    }
}

