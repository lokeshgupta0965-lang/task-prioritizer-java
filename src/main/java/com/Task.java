package com;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;


public class Task {
    private final int id;
    private final String description;
    private final boolean isDone;
    private final String dueDate; // ISO format YYYY-MM-DD or null/empty
    private final Integer parentId; // -1 or null means no parent
    private final String assignee; // empty or null means unassigned
    private final boolean isHighPriority;
    private final long createdAt; // unix epoch seconds

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public Task(int id,
                String description,
                boolean isDone,
                String dueDate,
                Integer parentId,
                String assignee,
                boolean isHighPriority,
                long createdAt) {
        this.id = id;
        this.description = description == null ? "" : description.trim();
        this.isDone = isDone;
        this.dueDate = (dueDate == null || dueDate.isBlank()) ? null : dueDate.trim();
        this.parentId = (parentId == null || parentId == -1) ? null : parentId;
        this.assignee = (assignee == null || assignee.isBlank()) ? null : assignee.trim();
        this.isHighPriority = isHighPriority;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDone() {
        return isDone;
    }

    public Optional<String> getDueDate() {
        return Optional.ofNullable(dueDate);
    }

    public Integer getParentId() {
        return parentId;
    }

    public Optional<String> getAssignee() {
        return Optional.ofNullable(assignee);
    }

    public boolean isHighPriority() {
        return isHighPriority;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean hasParent() {
        return parentId != null;
    }

    public boolean isAssigned() {
        return assignee != null && !assignee.isBlank();
    }

    public boolean hasDueDate() {
        return dueDate != null;
    }

    /**
     * Parse due date into LocalDate if possible.
     */
    public Optional<LocalDate> getDueDateAsLocalDate() {
        if (dueDate == null) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(dueDate, DATE_FORMATTER));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Unique key used for deduplication: description (case-insensitive trimmed) + due date (null normalized)
     */
    public UniqueKey uniqueKey() {
        return new UniqueKey(description == null ? "" : description.trim().toLowerCase(), dueDate == null ? "" : dueDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task)) return false;
        Task task = (Task) o;
        return id == task.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Task{" +
            "id=" + id +
            ", description='" + description + '\'' +
            ", isDone=" + isDone +
            ", dueDate='" + dueDate + '\'' +
            ", parentId=" + parentId +
            ", assignee='" + assignee + '\'' +
            ", isHighPriority=" + isHighPriority +
            ", createdAt=" + createdAt +
            '}';
    }

    /**
     * Inner static class representing the deduplication key for a task.
     */
    public static class UniqueKey {
        private final String normalizedDescription;
        private final String normalizedDueDate; // empty string means no due date

        public UniqueKey(String normalizedDescription, String normalizedDueDate) {
            this.normalizedDescription = normalizedDescription == null ? "" : normalizedDescription.trim();
            this.normalizedDueDate = normalizedDueDate == null ? "" : normalizedDueDate;
        }

        public static UniqueKey from(Task t) {
            return t.uniqueKey();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UniqueKey)) return false;
            UniqueKey uniqueKey = (UniqueKey) o;
            return normalizedDescription.equals(uniqueKey.normalizedDescription) &&
                normalizedDueDate.equals(uniqueKey.normalizedDueDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(normalizedDescription, normalizedDueDate);
        }

        @Override
        public String toString() {
            return "UniqueKey{" + "desc='" + normalizedDescription + '\'' + ", due='" + normalizedDueDate + '\'' + '}';
        }
    }
}
