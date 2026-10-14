package com;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class TaskPriorityServiceComplexTest {

    @Test
    public void complexNestedHierarchyShouldProduceParentFirstContiguousSubtrees() {
        // Build >100 tasks with 4-level nesting
        List<Task> tasks = new ArrayList<>();
        long base = 1_600_000_000L;
        int idCounter = 1;

        int numRoots = 7;            // level 0
        int childrenL1 = 4;          // level 1 per root
        int childrenL2 = 3;          // level 2 per L1
        int childrenL3 = 2;          // level 3 (deepest) per L2

        List<Integer> rootIds = new ArrayList<>();

        for (int r = 0; r < numRoots; r++) {
            int rootId = idCounter++;
            rootIds.add(rootId);
            // Root description and due date vary to exercise sorting
            String rootDesc = String.format("Root-%d", r);
            String rootDue = (r % 3 == 0) ? "2026-10-01" : (r % 3 == 1 ? "2026-10-05" : null);
            boolean rootHigh = (r % 2 == 0);
            tasks.add(new Task(rootId, rootDesc, false, rootDue, -1, "", rootHigh, base + rootId));

            for (int l1 = 0; l1 < childrenL1; l1++) {
                int idL1 = idCounter++;
                String descL1 = String.format("%s-L1-%d", rootDesc, l1);
                // mix due dates and priority
                String dueL1 = (l1 % 2 == 0) ? "2026-10-03" : null;
                boolean prioL1 = (l1 % 3 == 0);
                tasks.add(new Task(idL1, descL1, false, dueL1, rootId, "", prioL1, base + idL1));

                for (int l2 = 0; l2 < childrenL2; l2++) {
                    int idL2 = idCounter++;
                    String descL2 = String.format("%s-L2-%d", descL1, l2);
                    String dueL2 = (l2 == 0) ? "2026-09-30" : null; // some earlier due to test ordering
                    boolean prioL2 = (l2 % 2 == 1);
                    tasks.add(new Task(idL2, descL2, false, dueL2, idL1, "", prioL2, base + idL2));

                    for (int l3 = 0; l3 < childrenL3; l3++) {
                        int idL3 = idCounter++;
                        String descL3 = String.format("%s-L3-%d", descL2, l3);
                        // alternate due dates among deepest level
                        String dueL3 = (l3 % 2 == 0) ? "2026-10-02" : null;
                        boolean prioL3 = (l3 % 2 == 1);
                        tasks.add(new Task(idL3, descL3, false, dueL3, idL2, "", prioL3, base + idL3));
                    }
                }
            }
        }

        // Add some ineligible and duplicate tasks to ensure filtering works
        tasks.add(new Task(idCounter++, "DupRoot-0", false, "2026-10-01", -1, "", false, base + 9999));
        // duplicate description+due should be dropped (same as Root-0 if same description used)
        tasks.add(new Task(idCounter++, "Root-0", false, "2026-10-01", -1, "", false, base + 10000));

        // Some done/assigned tasks that should be filtered out
        tasks.add(new Task(idCounter++, "Done-X", true, "2026-12-01", -1, "", false, base + 20000));
        tasks.add(new Task(idCounter++, "Assigned-X", false, "2026-12-02", -1, "userZ", false, base + 20001));

        Task[] arr = tasks.toArray(new Task[0]);
        TaskPriorityService svc = new TaskPriorityService(arr);

        List<String> output = svc.buildPrintableOutput();

        // Basic sanity: >100 tasks were eligible and printed
        // Count expected eligible tasks by summing sizes in childrenMap
        Map<Integer, NavigableSet<Task>> cmap = svc.getChildrenMap();
        int expectedCount = cmap.values().stream().mapToInt(Set::size).sum();

        assertTrue(expectedCount > 100, "Expected more than 100 eligible tasks in generated dataset");
        assertEquals(expectedCount, output.size(), "Output size must equal number of eligible tasks in children map");

        // Build id->index map by parsing output lines
        Pattern p = Pattern.compile("Task ID:\s*(\d+)");
        Map<Integer, Integer> idToIndex = new HashMap<>();
        for (int i = 0; i < output.size(); i++) {
            String line = output.get(i);
            Matcher m = p.matcher(line);
            assertTrue(m.find(), "Each output line must contain Task ID");
            int id = Integer.parseInt(m.group(1));
            idToIndex.put(id, i);
        }

        // For each parent in cmap (excluding root key -1), verify its subtree is contiguous and immediately after the parent
        int ROOT_KEY = -1;
        for (Map.Entry<Integer, NavigableSet<Task>> entry : cmap.entrySet()) {
            int parentKey = entry.getKey();
            if (parentKey == ROOT_KEY) continue; // skip root bucket

            // parent must be present in idToIndex (because only eligible tasks are in cmap)
            assertTrue(idToIndex.containsKey(parentKey), "Parent " + parentKey + " must be present in output");
            int parentIdx = idToIndex.get(parentKey);

            // compute all descendants for this parent using cmap (DFS)
            Set<Integer> descendants = new LinkedHashSet<>();
            Deque<Integer> stack = new ArrayDeque<>();
            NavigableSet<Task> immediate = cmap.get(parentKey);
            if (immediate != null) {
                for (Task t : immediate) descendants.add(t.getId());
                for (Task t : immediate) stack.push(t.getId());
            }

            while (!stack.isEmpty()) {
                int cur = stack.pop();
                NavigableSet<Task> kids = cmap.get(cur);
                if (kids != null) {
                    for (Task k : kids) {
                        if (!descendants.contains(k.getId())) {
                            descendants.add(k.getId());
                            stack.push(k.getId());
                        }
                    }
                }
            }

            // If no descendants, continue
            if (descendants.isEmpty()) continue;

            // get min and max index among descendants
            int minIdx = Integer.MAX_VALUE, maxIdx = Integer.MIN_VALUE;
            for (int d : descendants) {
                assertTrue(idToIndex.containsKey(d), "Descendant " + d + " must be in output");
                int idx = idToIndex.get(d);
                minIdx = Math.min(minIdx, idx);
                maxIdx = Math.max(maxIdx, idx);
            }

            // descendants must start immediately after parent
            assertEquals(parentIdx + 1, minIdx, "Descendants of parent " + parentKey + " must start immediately after parent");

            // block must be contiguous and size matches
            int blockSize = maxIdx - minIdx + 1;
            assertEquals(descendants.size(), blockSize, "Descendants of parent " + parentKey + " must form a contiguous block");

            // Finally, ensure the ordering of immediate children in output matches the comparator order (siblings order)
            List<Integer> immediateList = new ArrayList<>();
            if (immediate != null) {
                for (Task t : immediate) immediateList.add(t.getId());

                // Find their indices and ensure relative order
                List<Integer> indices = new ArrayList<>();
                for (int id : immediateList) indices.add(idToIndex.get(id));

                List<Integer> sortedIndices = new ArrayList<>(indices);
                Collections.sort(sortedIndices);
                assertEquals(sortedIndices, indices, "Immediate children order for parent " + parentKey + " must match comparator order in output");
            }
        }
    }
}
