package com.taskmanager.service;

import com.taskmanager.model.Task;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service that maintains three stores:
 *  - allTasksById : Map<Integer, Task>  (all tasks)
 *  - filteredSortedTasks : NavigableSet<Task> (tasks eligible for display, kept sorted)
 *  - uniqueTaskKeys : Set<Task.UniqueKey> (keys used to deduplicate before adding to filtered store)
 *
 * Public operations keep all stores consistent. Methods are synchronized for thread-safety of compound operations.
 */
public class TaskPriorityService {

    private final Map<Integer, Task> allTasksById = new ConcurrentHashMap<>();
    private final NavigableSet<Task> filteredSortedTasks = new TreeSet<>(new TaskComparator());
    private final Set<Task.UniqueKey> uniqueTaskKeys = new HashSet<>();

    public TaskPriorityService() {
    }

    public enum AddResult {
        ADDED,
        SKIPPED_DONE,
        SKIPPED_ASSIGNED,
        SKIPPED_DUPLICATE
    }

    /**
     * Add a task to the system.
     * - Always adds to allTasksById (replaces if id exists).
     * - If task is eligible (not done, unassigned) and unique, adds to filteredSortedTasks and uniqueTaskKeys.
     * Returns AddResult explaining what happened.
     */
    public synchronized AddResult addTask(Task task) {
        Objects.requireNonNull(task, "task");
        allTasksById.put(task.getId(), task);

        if (task.isDone()) {
            // not eligible
            return AddResult.SKIPPED_DONE;
        }
        if (task.isAssigned()) {
            return AddResult.SKIPPED_ASSIGNED;
        }

        Task.UniqueKey key = task.uniqueKey();
        if (uniqueTaskKeys.contains(key)) {
            return AddResult.SKIPPED_DUPLICATE;
        }

        uniqueTaskKeys.add(key);
        filteredSortedTasks.add(task);
        return AddResult.ADDED;
    }

    /**
     * Remove a task by id from system; updates all stores.
     */
    public synchronized boolean removeTask(int taskId) {
        Task existing = allTasksById.remove(taskId);
        if (existing == null) return false;

        if (filteredSortedTasks.remove(existing)) {
            uniqueTaskKeys.remove(existing.uniqueKey());
        }
        return true;
    }

    /**
     * Update an existing task (replace). Keeps stores consistent:
     * - replaces in allTasksById
     * - removes old from filtered store and unique keys if necessary
     * - attempts to add new version to filtered store if eligible and unique
     */
    public synchronized AddResult updateTask(Task task) {
        Objects.requireNonNull(task, "task");
        Task prev = allTasksById.put(task.getId(), task);

        if (prev != null) {
            if (filteredSortedTasks.remove(prev)) {
                uniqueTaskKeys.remove(prev.uniqueKey());
            }
        }

        return addTask(task);
    }

    /**
     * Rebuild the filteredSortedTasks and uniqueTaskKeys from allTasksById.
     * Useful when bulk changes occurred or at startup.
     */
    public synchronized void rebuildFilteredStore() {
        filteredSortedTasks.clear();
        uniqueTaskKeys.clear();

        // Iterate deterministic order (by id) to decide which duplicate to keep: keep first encountered
        allTasksById.values().stream()
                .sorted(Comparator.comparingInt(Task::getId))
                .forEach(t -> {
                    if (isEligibleForDisplay(t)) {
                        Task.UniqueKey key = t.uniqueKey();
                        if (!uniqueTaskKeys.contains(key)) {
                            uniqueTaskKeys.add(key);
                            filteredSortedTasks.add(t);
                        }
                    }
                });
    }

    /**
     * Build printable output lines instead of printing to System.out; useful for testing.
     * Format: "Task ID: {id}, Description: {description}" + optional ", parent: {parent_description}"
     */
    public synchronized List<String> buildPrintableOutput() {
        List<String> out = new ArrayList<>();
        for (Task t : filteredSortedTasks) {
            StringBuilder sb = new StringBuilder();
            sb.append("Task ID: ").append(t.getId())
                    .append(", Description: ").append(t.getDescription());

            if (t.hasParent()) {
                Task parent = allTasksById.get(t.getParentId());
                if (parent != null && parent.getDescription() != null && !parent.getDescription().isBlank()) {
                    sb.append(", parent: ").append(parent.getDescription());
                }
            }
            out.add(sb.toString());
        }
        return out;
    }

    /**
     * Print the prioritized tasks in order to stdout. Uses buildPrintableOutput internally.
     */
    public synchronized void printPrioritizedTasks() {
        buildPrintableOutput().forEach(System.out::println);
    }

    /**
     * Helper to determine whether a task is eligible to be shown on the dashboard (filter rules).
     */
    private boolean isEligibleForDisplay(Task t) {
        if (t == null) return false;
        if (t.isDone()) return false;
        if (t.isAssigned()) return false;
        return true;
    }

    // Expose read-only views for tests or dashboard rendering if needed.
    public synchronized List<Task> getAllTasks() {
        return new ArrayList<>(allTasksById.values());
    }

    public synchronized List<Task> getFilteredAndSortedTasks() {
        return new ArrayList<>(filteredSortedTasks);
    }

    public synchronized Set<Task.UniqueKey> getUniqueTaskKeys() {
        return new HashSet<>(uniqueTaskKeys);
    }

    /**
     * Debug helper: dump internal state to a string for assertions / logs.
     */
    public synchronized String dumpInternalState() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== allTasksById IDs ===\n");
        allTasksById.keySet().stream().sorted().forEach(id -> sb.append("  id=").append(id).append("\n"));
        sb.append("=== filteredSortedTasks (in-order) ===\n");
        filteredSortedTasks.forEach(t -> sb.append("  id=").append(t.getId())
                .append(", desc='").append(t.getDescription()).append("', due=")
                .append(t.getDueDate().orElse("<none>")).append(", prio=")
                .append(t.isHighPriority()).append(", created=")
                .append(t.getCreatedAt()).append("\n"));
        sb.append("=== uniqueTaskKeys ===\n");
        uniqueTaskKeys.forEach(k -> sb.append("  ").append(k).append("\n"));
        return sb.toString();
    }

    /**
     * Comparator implementing the prioritization rules:
     * 1) earliest due date (if present) first (missing due date considered after any real date)
     * 2) if due dates equal/missing: high priority first
     * 3) if same priority: oldest task (createdAt ascending)
     * 4) tie-breaker: id
     */
    public static class TaskComparator implements Comparator<Task> {
        @Override
        public int compare(Task a, Task b) {
            if (a == b) return 0;
            // compare due dates (absent => treated as after any real date)
            Optional<LocalDate> da = a.getDueDateAsLocalDate();
            Optional<LocalDate> db = b.getDueDateAsLocalDate();

            if (da.isPresent() && db.isPresent()) {
                int cmp = da.get().compareTo(db.get());
                if (cmp != 0) return cmp;
            } else if (da.isPresent()) {
                // a has date, b doesn't -> a before b
                return -1;
            } else if (db.isPresent()) {
                return 1;
            }

            // due dates equal or both absent -> high priority first
            if (a.isHighPriority() && !b.isHighPriority()) return -1;
            if (!a.isHighPriority() && b.isHighPriority()) return 1;

            // same priority -> oldest first (createdAt ascending)
            int cmpCreated = Long.compare(a.getCreatedAt(), b.getCreatedAt());
            if (cmpCreated != 0) return cmpCreated;

            // final tie-breaker by id to ensure comparator is consistent with equals
            return Integer.compare(a.getId(), b.getId());
        }
    }
}
