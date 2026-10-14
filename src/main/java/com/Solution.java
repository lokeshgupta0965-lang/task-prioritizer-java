package com;

import com.taskmanager.service.TaskPriorityService;

import java.util.List;

public class Solution {

    public static void main(String[] args) {

        Task[] tasks = new Task[20];
        long now = 1_600_000_000L; // base createdAt

        int i = 0;
        // ----- Eligible tasks (we intend these to appear in output in the order below) -----
        // 1) Id 2: earliest due date -> should be first
        tasks[i++] = new Task(2, "Should be 1st", false, "2026-10-01", -1, "", false, now + 100);

        // 2) Id 1: next earliest due date -> should be second (high priority)
        tasks[i++] = new Task(1, "Should be 2nd", false, "2026-10-02", -1, "", true, now + 200);

        // 3) Id 10: same due date as id=1, lower priority and later createdAt -> third
        tasks[i++] = new Task(10, "Should be 3rd", false, "2026-10-02", -1, "", false, now + 300);

        // 4) Id 3: no due date, high priority -> comes after dated tasks
        tasks[i++] = new Task(3, "Should be 4th", false, "", -1, "", true, now + 400);

        // 5) Id 4: no due date, low priority, older than id5 -> should be 5th
        tasks[i++] = new Task(4, "Should be 5th", false, null, -1, "", false, now + 500);

        // 6) Id 5: child of 4 -> should be 6th and include parent description
        tasks[i++] = new Task(5, "Should be 6th", false, null, 4, "", false, now + 600);

        // 7) Id 0: Zero parent (no parent), will appear later (older/later createdAt to set order)
        tasks[i++] = new Task(0, "Zero parent", false, null, -1, "", false, now + 700);

        // 8) Id 11: child of 0 -> should come after 0 and include parent mapping
        tasks[i++] = new Task(11, "Child of zero", false, null, 0, "", false, now + 800);

        // ----- Other tasks (12 tasks) are ineligible or duplicates so they won't appear -----
        // Duplicates or ineligible tasks (done/assigned) to reach 20 total
        tasks[i++] = new Task(6, "Done task", true, "2026-11-01", -1, "", false, now + 10);
        tasks[i++] = new Task(7, "Assigned task", false, "2026-11-02", -1, "userA", false, now + 20);
        // Duplicate of an eligible task (same desc + due) - should be skipped
        tasks[i++] = new Task(8, "Should be 1st", false, "2026-10-01", -1, "", false, now + 30);
        // Duplicate of t1 (same desc + due) - should be skipped
        tasks[i++] = new Task(9, "Should be 2nd", false, "2026-10-02", -1, "", true, now + 40);
        // More done/assigned tasks
        tasks[i++] = new Task(12, "Some done", true, "", -1, "", false, now + 50);
        tasks[i++] = new Task(13, "Some assigned", false, "", -1, "someone", false, now + 60);
        // Duplicates with blank due date for t3 - duplicate should be skipped if present earlier
        tasks[i++] = new Task(14, "Should be 4th", false, "", -1, "", true, now + 70);
        // More ineligible
        tasks[i++] = new Task(15, "Done again", true, "", -1, "", false, now + 80);
        tasks[i++] = new Task(16, "Assigned again", false, "2026-12-01", -1, "userB", true, now + 90);
        // Non-conflicting tasks but ineligible (assigned/done)
        tasks[i++] = new Task(17, "Done 4", true, "2027-01-01", -1, "", false, now + 91);
        tasks[i++] = new Task(18, "Assigned 4", false, "", -1, "userC", false, now + 92);
        // Another duplicate to ensure dedupe set works
        tasks[i++] = new Task(19, "Zero parent", false, "", -1, "", false, now + 93);

        // Build printable output

        // Expected ordering and lines (exact strings)
        List<String> expected = List.of(
                "Task ID: 2, Description: Should be 1st",
                "Task ID: 1, Description: Should be 2nd",
                "Task ID: 10, Description: Should be 3rd",
                "Task ID: 3, Description: Should be 4th",
                "Task ID: 4, Description: Should be 5th",
                "Task ID: 5, Description: Should be 6th, parent: Should be 5th",
                "Task ID: 0, Description: Zero parent",
                "Task ID: 11, Description: Child of zero, parent: Zero parent"
        );


        TaskPriorityService taskPriorityService = new TaskPriorityService();

        // add tasks one by one using service API
        for (Task t : tasks) {
            if (t == null) continue;
            taskPriorityService.addTask(t);
        }

        taskPriorityService.printPrioritizedTasks();
    }

}
