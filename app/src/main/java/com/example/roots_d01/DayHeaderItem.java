package com.example.roots_d01;

import java.util.Set;
import java.util.HashSet;
import java.util.Objects;

public class DayHeaderItem {
    public final String dateHeaderText;
    public final Set<String> transportModes; // Holds unique transport modes for the day

    public DayHeaderItem(String dateHeaderText, Set<String> transportModes) {
        this.dateHeaderText = dateHeaderText;
        this.transportModes = (transportModes != null) ? transportModes : new HashSet<>();
    }

    // It's good practice to override equals and hashCode if these objects are used as map keys,
    // especially for headerExpansionStates. We'll use dateHeaderText as the primary key.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DayHeaderItem that = (DayHeaderItem) o;
        return Objects.equals(dateHeaderText, that.dateHeaderText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dateHeaderText);
    }

    @Override
    public String toString() {
        // This is important if you use the object itself as a key in a Map
        // and rely on its string representation for that key.
        // However, for headerExpansionStates, we'll use dateHeaderText directly.
        return dateHeaderText;
    }
}