package io.github.gcross.hyperring.ringtone;

public enum SimTarget {
    IMPORT_ONLY("仅导入系统铃声库"),
    SYSTEM_DEFAULT("系统默认铃声"),
    SIM_1("SIM 1"),
    SIM_2("SIM 2"),
    BOTH("SIM 1 + SIM 2");

    private final String label;

    SimTarget(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
