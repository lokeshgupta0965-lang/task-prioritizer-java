package com;

import com.taskmanager.model.Task;
import com.taskmanager.service.TaskPriorityService;

import java.util.List;

public class Solution {

    public static void main(String[] args) {
        // Build 100+ tasks with 4-level nesting
        int id = 1;
        long base = 1_600_000_000L;

        // configuration tuned to produce ~120 tasks
        int roots = 3;
        int l1PerRoot = 3;
        int l2PerL1 = 3;
        int l3PerL2 = 3;

        Task[] tasks = new Task[roots * (1 + l1PerRoot * (1 + l2PerL1 * (1 + l3PerL2))) + 20];
        int idx = 0;

        for (int r = 0; r < roots; r++) {
            int rootId = id++;
            String rootDesc = "Root-" + r;
            String rootDue = (r % 3 == 0) ? "2026-10-01" : (r % 3 == 1 ? "2026-10-05" : null);
            boolean rootHigh = (r % 2 == 0);
            tasks[idx++] = new Task(rootId, rootDesc, false, rootDue, -1, "", rootHigh, base + rootId);

            for (int l1 = 0; l1 < l1PerRoot; l1++) {
                int idL1 = id++;
                String descL1 = rootDesc + "-L1-" + l1;
                String dueL1 = (l1 % 2 == 0) ? "2026-10-03" : null;
                boolean prioL1 = (l1 % 3 == 0);
                tasks[idx++] = new Task(idL1, descL1, false, dueL1, rootId, "", prioL1, base + idL1);

                for (int l2 = 0; l2 < l2PerL1; l2++) {
                    int idL2 = id++;
                    String descL2 = descL1 + "-L2-" + l2;
                    String dueL2 = (l2 == 0) ? "2026-09-30" : null;
                    boolean prioL2 = (l2 % 2 == 1);
                    tasks[idx++] = new Task(idL2, descL2, false, dueL2, idL1, "", prioL2, base + idL2);

                    for (int l3 = 0; l3 < l3PerL2; l3++) {
                        int idL3 = id++;
                        String descL3 = descL2 + "-L3-" + l3;
                        String dueL3 = (l3 % 2 == 0) ? "2026-10-02" : null;
                        boolean prioL3 = (l3 % 2 == 1);
                        tasks[idx++] = new Task(idL3, descL3, false, dueL3, idL2, "", prioL3, base + idL3);
                    }
                }
            }
        }

        // add some extra roots and duplicates and ineligible to exercise filtering
        for (int k = 0; k < 5; k++) {
            tasks[idx++] = new Task(id++, "ExtraRoot-" + k, false, null, -1, "", false, base + id);
        }
        // duplicates
        tasks[idx++] = new Task(id++, "Root-0", false, "2026-10-01", -1, "", false, base + id);
        tasks[idx++] = new Task(id++, "Root-0-L1-0", false, "2026-10-03", 2, "", false, base + id);
        // ineligible
        tasks[idx++] = new Task(id++, "Done-Task", true, "2027-01-01", -1, "", false, base + id);
        tasks[idx++] = new Task(id++, "Assigned-Task", false, "2027-01-02", -1, "userA", false, base + id);

        // trim the array to actual size
        Task[] input = new Task[idx];
        System.arraycopy(tasks, 0, input, 0, idx);

        TaskPriorityService svc = new TaskPriorityService();
        for (Task t : input) {
            svc.addTask(t);
        }

        List<String> out = svc.buildPrintableOutput();
        System.out.println("Printed tasks: " + out.size());
        // print first 200 lines for inspection
        for (int i = 0; i < Math.min(out.size(), 200); i++) System.out.println(out.get(i));
    }
}
