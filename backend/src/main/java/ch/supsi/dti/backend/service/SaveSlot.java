package ch.supsi.dti.backend.service;

import java.nio.file.Path;

public enum SaveSlot {
    AUTO("autosave.json"),
    SLOT_1("slot1.json"),
    SLOT_2("slot2.json"),
    SLOT_3("slot3.json");

    private static final SaveSlot[] MANUAL = {SLOT_1, SLOT_2, SLOT_3};

    private final String filename;

    SaveSlot(String filename) {
        this.filename = filename;
    }

    public Path path() {
        return Path.of("frontend", "saved", filename);
    }

    public boolean isManual() {
        return this != AUTO;
    }

    public static SaveSlot[] manualSlots() {
        return MANUAL.clone();
    }
}
