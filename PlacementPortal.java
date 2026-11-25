import java.util.*;

/**
 * Placement Portal
 * ----------------
 *  Hash Table (chaining)  -> O(1) average lookup of students / companies by ID
 *  Binary Search Tree     -> students ordered by CGPA; range query for eligibility
 *  Priority Queue (heap)  -> ranks each company's applicants, best candidate first
 *  Sorting (merge/quick)  -> sorted reports
 *
 * Compile:  javac PlacementPortal.java
 * Run:      java PlacementPortal          (interactive menu)
 *           java PlacementPortal --demo   (scripted walkthrough)
 */
public class PlacementPortal {

    // ======================================================================
    // 1. HASH TABLE (separate chaining, auto-resize)
    // ======================================================================
    static class HashTable<K, V> {
        private static class Entry<K, V> {
            final K key; V value;
            Entry(K k, V v) { key = k; value = v; }
        }

        private List<Entry<K, V>>[] buckets;
        private int size;

        HashTable() { buckets = newBuckets(8); }

        @SuppressWarnings("unchecked")
        private List<Entry<K, V>>[] newBuckets(int n) {
            List<Entry<K, V>>[] b = (List<Entry<K, V>>[]) new List[n];
            for (int i = 0; i < n; i++) b[i] = new LinkedList<>();
            return b;
        }

        // djb2 over the key's string form
        private int index(K key, int cap) {
            long h = 5381;
            for (char c : key.toString().toCharArray()) h = ((h << 5) + h + c) & 0xFFFFFFFFL;
            return (int) (h % cap);
        }

        private void resize() {
            List<Entry<K, V>>[] old = buckets;
            buckets = newBuckets(old.length * 2);
            for (List<Entry<K, V>> bucket : old)
                for (Entry<K, V> e : bucket)
                    buckets[index(e.key, buckets.length)].add(e);
        }

        void put(K key, V value) {
            for (Entry<K, V> e : buckets[index(key, buckets.length)])
                if (e.key.equals(key)) { e.value = value; return; }
            buckets[index(key, buckets.length)].add(new Entry<>(key, value));
            if (++size > buckets.length * 0.75) resize();
        }

        V get(K key) {
            for (Entry<K, V> e : buckets[index(key, buckets.length)])
                if (e.key.equals(key)) return e.value;
            return null;
        }

        boolean contains(K key) { return get(key) != null; }

        boolean remove(K key) {
            boolean removed = buckets[index(key, buckets.length)].removeIf(e -> e.key.equals(key));
            if (removed) size--;
            return removed;
        }

        List<V> values() {
            List<V> out = new ArrayList<>();
            for (List<Entry<K, V>> b : buckets) for (Entry<K, V> e : b) out.add(e.value);
            return out;
        }

        int size() { return size; }
        int capacity() { return buckets.length; }
    }

    // ======================================================================
    // 2. BINARY SEARCH TREE (students ordered by CGPA, then ID)
    // ======================================================================
    static class StudentBST {
        private static class Node {
            Student val; Node left, right;
            Node(Student s) { val = s; }
        }

        private Node root;

        // order: CGPA ascending, ties broken by ID so keys are unique
        private static int cmp(Student a, Student b) {
            int c = Double.compare(a.cgpa, b.cgpa);
            return c != 0 ? c : a.id.compareTo(b.id);
        }

        void insert(Student s) { root = insert(root, s); }

        private Node insert(Node n, Student s) {
            if (n == null) return new Node(s);
            int c = cmp(s, n.val);
            if (c < 0) n.left = insert(n.left, s);
            else if (c > 0) n.right = insert(n.right, s);
            else n.val = s;
            return n;
        }

        void delete(Student s) { root = delete(root, s); }

        private Node delete(Node n, Student s) {
            if (n == null) return null;
            int c = cmp(s, n.val);
            if (c < 0) n.left = delete(n.left, s);
            else if (c > 0) n.right = delete(n.right, s);
            else {
                if (n.left == null) return n.right;
                if (n.right == null) return n.left;
                Node succ = n.right;
                while (succ.left != null) succ = succ.left;
                n.val = succ.val;
                n.right = delete(n.right, succ.val);
            }
            return n;
        }

        /** Ascending by CGPA. */
        List<Student> inorder() {
            List<Student> out = new ArrayList<>();
            inorder(root, out);
            return out;
        }

        private void inorder(Node n, List<Student> out) {
            if (n == null) return;
            inorder(n.left, out);
            out.add(n.val);
            inorder(n.right, out);
        }

        /** All students with CGPA >= low, skipping subtrees that cannot qualify. */
        List<Student> atLeast(double low) {
            List<Student> out = new ArrayList<>();
            atLeast(root, low, out);
            return out;
        }

        private void atLeast(Node n, double low, List<Student> out) {
            if (n == null) return;
            if (n.val.cgpa >= low) {
                atLeast(n.left, low, out);   // left may still hold equal-CGPA nodes
                out.add(n.val);
            }
            atLeast(n.right, low, out);      // right side is always >= this node
        }

        int height() { return height(root); }
        private int height(Node n) { return n == null ? 0 : 1 + Math.max(height(n.left), height(n.right)); }
    }

    // ======================================================================
    // 3. PRIORITY QUEUE (binary max-heap, FIFO on ties)
    // ======================================================================
    static class MaxHeap<T> {
        private static class Item<T> {
            final double priority; final long seq; final T value;
            Item(double p, long s, T v) { priority = p; seq = s; value = v; }
        }

        private final List<Item<T>> a = new ArrayList<>();
        private long counter = 0;

        private boolean higher(Item<T> x, Item<T> y) {
            if (x.priority != y.priority) return x.priority > y.priority;
            return x.seq < y.seq;            // earlier applicant wins a tie
        }

        void push(double priority, T value) {
            a.add(new Item<>(priority, counter++, value));
            int i = a.size() - 1;
            while (i > 0) {
                int p = (i - 1) / 2;
                if (!higher(a.get(i), a.get(p))) break;
                Collections.swap(a, i, p);
                i = p;
            }
        }

        /** Removes the best item; returns {priority, value} packed as a Pair. */
        Pair<T> pop() {
            if (a.isEmpty()) throw new NoSuchElementException("heap is empty");
            Item<T> top = a.get(0);
            Item<T> last = a.remove(a.size() - 1);
            if (!a.isEmpty()) {
                a.set(0, last);
                int i = 0, n = a.size();
                while (true) {
                    int l = 2 * i + 1, r = 2 * i + 2, best = i;
                    if (l < n && higher(a.get(l), a.get(best))) best = l;
                    if (r < n && higher(a.get(r), a.get(best))) best = r;
                    if (best == i) break;
                    Collections.swap(a, i, best);
                    i = best;
                }
            }
            return new Pair<>(top.priority, top.value);
        }

        /** Best k items without disturbing this heap. */
        List<Pair<T>> peekTop(int k) {
            MaxHeap<T> copy = new MaxHeap<>();
            copy.a.addAll(this.a);
            copy.counter = this.counter;
            List<Pair<T>> out = new ArrayList<>();
            while (!copy.isEmpty() && out.size() < k) out.add(copy.pop());
            return out;
        }

        boolean isEmpty() { return a.isEmpty(); }
        int size() { return a.size(); }
    }

    static class Pair<T> {
        final double score; final T item;
        Pair(double s, T i) { score = s; item = i; }
    }

    // ======================================================================
    // 4. SORTING
    // ======================================================================
    /** Stable O(n log n) merge sort. */
    static <T> List<T> mergeSort(List<T> list, Comparator<T> cmp) {
        if (list.size() <= 1) return new ArrayList<>(list);
        int mid = list.size() / 2;
        List<T> left = mergeSort(list.subList(0, mid), cmp);
        List<T> right = mergeSort(list.subList(mid, list.size()), cmp);
        List<T> out = new ArrayList<>(list.size());
        int i = 0, j = 0;
        while (i < left.size() && j < right.size())
            out.add(cmp.compare(left.get(i), right.get(j)) <= 0 ? left.get(i++) : right.get(j++));
        while (i < left.size()) out.add(left.get(i++));
        while (j < right.size()) out.add(right.get(j++));
        return out;
    }

    private static final Random RNG = new Random();

    /** Randomised-pivot quick sort with 3-way partition. */
    static <T> List<T> quickSort(List<T> list, Comparator<T> cmp) {
        if (list.size() <= 1) return new ArrayList<>(list);
        T pivot = list.get(RNG.nextInt(list.size()));
        List<T> less = new ArrayList<>(), equal = new ArrayList<>(), more = new ArrayList<>();
        for (T x : list) {
            int c = cmp.compare(x, pivot);
            if (c < 0) less.add(x); else if (c > 0) more.add(x); else equal.add(x);
        }
        List<T> out = quickSort(less, cmp);
        out.addAll(equal);
        out.addAll(quickSort(more, cmp));
        return out;
    }

    // ======================================================================
    // DOMAIN MODELS
    // ======================================================================
    static class Student {
        final String id, name, branch;
        final double cgpa;
        final Set<String> skills;
        String placedAt;

        Student(String id, String name, String branch, double cgpa, String skillsCsv) {
            this.id = id; this.name = name; this.branch = branch; this.cgpa = cgpa;
            this.skills = parseSkills(skillsCsv);
        }

        @Override public String toString() {
            return String.format("%-6s %-15s %-4s CGPA %-5.2f [%s]", id, name, branch, cgpa,
                    placedAt == null ? "unplaced" : "PLACED @ " + placedAt);
        }
    }

    static class Company {
        final String id, name, role;
        final double packageLpa, minCgpa;
        final Set<String> reqSkills;
        final int openings;
        final MaxHeap<Student> applicants = new MaxHeap<>();
        final Set<String> appliedIds = new HashSet<>();
        final List<Pair<Student>> selected = new ArrayList<>();

        Company(String id, String name, String role, double pkg, double minCgpa, String skillsCsv, int openings) {
            this.id = id; this.name = name; this.role = role; this.packageLpa = pkg;
            this.minCgpa = minCgpa; this.reqSkills = parseSkills(skillsCsv); this.openings = openings;
        }

        @Override public String toString() {
            return String.format("%-4s %-10s %-15s %5.1f LPA  min CGPA %.1f  openings %d",
                    id, name, role, packageLpa, minCgpa, openings);
        }
    }

    static Set<String> parseSkills(String csv) {
        Set<String> s = new HashSet<>();
        for (String x : csv.split(",")) if (!x.trim().isEmpty()) s.add(x.trim().toLowerCase());
        return s;
    }

    // ======================================================================
    // PORTAL
    // ======================================================================
    private final HashTable<String, Student> students = new HashTable<>();
    private final HashTable<String, Company> companies = new HashTable<>();
    private final StudentBST cgpaTree = new StudentBST();

    Student addStudent(String id, String name, String branch, double cgpa, String skills) {
        if (students.contains(id)) throw new IllegalArgumentException("Student " + id + " already exists");
        if (cgpa < 0 || cgpa > 10) throw new IllegalArgumentException("CGPA must be 0-10");
        Student s = new Student(id, name, branch, cgpa, skills);
        students.put(id, s);
        cgpaTree.insert(s);
        return s;
    }

    void removeStudent(String id) {
        Student s = student(id);
        cgpaTree.delete(s);
        students.remove(id);
    }

    Company addCompany(String id, String name, String role, double pkg, double minCgpa, String skills, int openings) {
        if (companies.contains(id)) throw new IllegalArgumentException("Company " + id + " already exists");
        Company c = new Company(id, name, role, pkg, minCgpa, skills, openings);
        companies.put(id, c);
        return c;
    }

    /** BST range query: everyone meeting the company's CGPA cutoff. */
    List<Student> eligibleStudents(String companyId) {
        return cgpaTree.atLeast(company(companyId).minCgpa);
    }

    /** CGPA dominates; each matched skill adds a bonus. */
    static double score(Student s, Company c) {
        Set<String> matched = new HashSet<>(s.skills);
        matched.retainAll(c.reqSkills);
        return Math.round((s.cgpa * 10 + matched.size() * 5) * 100.0) / 100.0;
    }

    void apply(String studentId, String companyId) {
        Student s = student(studentId);
        Company c = company(companyId);
        if (s.placedAt != null) throw new IllegalStateException(s.name + " is already placed at " + s.placedAt);
        if (s.cgpa < c.minCgpa)
            throw new IllegalStateException(s.name + " (CGPA " + s.cgpa + ") is below " + c.name + "'s cutoff " + c.minCgpa);
        if (!c.appliedIds.add(studentId)) throw new IllegalStateException(s.name + " already applied to " + c.name);
        c.applicants.push(score(s, c), s);
    }

    List<Pair<Student>> topApplicants(String companyId, int k) {
        return company(companyId).applicants.peekTop(k);
    }

    /** Pops the best applicants until openings are filled. */
    List<Pair<Student>> runSelection(String companyId) {
        Company c = company(companyId);
        while (c.selected.size() < c.openings && !c.applicants.isEmpty()) {
            Pair<Student> p = c.applicants.pop();
            if (p.item.placedAt != null) continue;   // taken by another company already
            p.item.placedAt = c.name;
            c.selected.add(p);
        }
        return c.selected;
    }

    List<Student> studentsSorted(String by) {
        Comparator<Student> cmp;
        switch (by) {
            case "name":   cmp = Comparator.comparing(s -> s.name.toLowerCase()); break;
            case "branch": cmp = Comparator.<Student, String>comparing(s -> s.branch)
                                           .thenComparing((a, b) -> Double.compare(b.cgpa, a.cgpa)); break;
            default:       cmp = (a, b) -> Double.compare(b.cgpa, a.cgpa);   // cgpa, high to low
        }
        return mergeSort(students.values(), cmp);
    }

    List<Company> companiesSorted(String by) {
        Comparator<Company> cmp;
        switch (by) {
            case "name":   cmp = Comparator.comparing(c -> c.name.toLowerCase()); break;
            case "cutoff": cmp = Comparator.comparingDouble(c -> c.minCgpa); break;
            default:       cmp = (a, b) -> Double.compare(b.packageLpa, a.packageLpa);   // package, high to low
        }
        return quickSort(companies.values(), cmp);
    }

    String summary() {
        List<Student> all = students.values();
        int placed = 0;
        for (Student s : all) if (s.placedAt != null) placed++;
        double pct = all.isEmpty() ? 0 : 100.0 * placed / all.size();
        List<Student> un = new ArrayList<>();
        for (Student s : studentsSorted("cgpa")) if (s.placedAt == null) un.add(s);
        StringBuilder names = new StringBuilder();
        for (Student s : un) names.append(names.length() == 0 ? "" : ", ").append(s.name);
        return String.format("%d/%d placed (%.1f%%)%nStill unplaced: %s", placed, all.size(), pct,
                un.isEmpty() ? "none" : names);
    }

    Student student(String id) {
        Student s = students.get(id);
        if (s == null) throw new NoSuchElementException("No student with ID " + id);
        return s;
    }

    Company company(String id) {
        Company c = companies.get(id);
        if (c == null) throw new NoSuchElementException("No company with ID " + id);
        return c;
    }

    // ======================================================================
    // SAMPLE DATA, DEMO, MENU
    // ======================================================================
    void loadSample() {
        addStudent("S101", "Aarav Sharma", "CSE", 9.1, "python,dsa,sql");
        addStudent("S102", "Diya Patel",   "CSE", 8.4, "java,dsa");
        addStudent("S103", "Rohan Verma",  "IT",  7.6, "python,ml");
        addStudent("S104", "Ananya Iyer",  "ECE", 8.9, "c++,embedded,dsa");
        addStudent("S105", "Kabir Singh",  "CSE", 6.8, "html,css,js");
        addStudent("S106", "Meera Nair",   "IT",  9.4, "python,dsa,ml,sql");
        addStudent("S107", "Vikram Rao",   "ME",  7.2, "cad,python");
        addStudent("S108", "Sneha Joshi",  "CSE", 8.4, "java,sql,dsa");

        addCompany("C01", "TechNova",  "SDE",           18, 8.0, "python,dsa,sql", 2);
        addCompany("C02", "DataWorks", "Data Analyst",  12, 7.0, "python,sql,ml",  2);
        addCompany("C03", "WebCraft",  "Frontend Dev",   8, 6.5, "html,css,js",    1);
        addCompany("C04", "CoreSys",   "Embedded Eng.", 10, 7.5, "c++,embedded",   1);
    }

    static void demo() {
        PlacementPortal p = new PlacementPortal();
        p.loadSample();

        System.out.println("== Hash table: O(1) lookup ==");
        System.out.println(p.student("S106"));
        System.out.println("(capacity " + p.students.capacity() + ", size " + p.students.size() + ")");

        System.out.println("\n== BST: students ascending by CGPA (in-order) ==");
        for (Student s : p.cgpaTree.inorder()) System.out.println("  " + s);
        System.out.println("  tree height = " + p.cgpaTree.height());

        System.out.println("\n== BST range query: eligible for TechNova (CGPA >= 8.0) ==");
        for (Student s : p.eligibleStudents("C01")) System.out.println("  " + s.name + " " + s.cgpa);

        System.out.println("\n== Applications -> priority queues ==");
        String[][] apps = {{"S101","C01"},{"S102","C01"},{"S104","C01"},{"S106","C01"},{"S108","C01"},
                           {"S103","C02"},{"S106","C02"},{"S107","C02"},{"S105","C03"},{"S103","C03"},{"S104","C04"}};
        for (String[] a : apps) p.apply(a[0], a[1]);
        try { p.apply("S105", "C01"); }
        catch (RuntimeException e) { System.out.println("  rejected: " + e.getMessage()); }
        for (Pair<Student> t : p.topApplicants("C01", 3))
            System.out.printf("  TechNova top: %-14s score %.1f%n", t.item.name, t.score);

        System.out.println("\n== Selection rounds (heap pops, highest-paying company first) ==");
        for (Company c : p.companiesSorted("package")) {
            StringBuilder sb = new StringBuilder();
            for (Pair<Student> sel : p.runSelection(c.id))
                sb.append(sb.length() == 0 ? "" : ", ").append(String.format("%s (%.1f)", sel.item.name, sel.score));
            System.out.printf("  %-10s %4.0f LPA -> %s%n", c.name, c.packageLpa, sb.length() == 0 ? "no one" : sb);
        }

        System.out.println("\n== Sorted student report (merge sort, CGPA high to low) ==");
        for (Student s : p.studentsSorted("cgpa")) System.out.println("  " + s);

        System.out.println("\n== Summary ==");
        System.out.println(p.summary());
    }

    static final String MENU =
        "\n================ PLACEMENT PORTAL ================\n" +
        " 1. Add student             6. Apply to company\n" +
        " 2. Add company             7. Top applicants (heap)\n" +
        " 3. Find student (hash)     8. Run selection\n" +
        " 4. Eligible students (BST) 9. Sorted reports\n" +
        " 5. List companies         10. Placement summary\n" +
        "11. Load sample data        0. Exit\n" +
        "===================================================";

    static void cli() {
        PlacementPortal p = new PlacementPortal();
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println(MENU);
            System.out.print("Choice: ");
            if (!in.hasNextLine()) return;
            String ch = in.nextLine().trim();
            try {
                switch (ch) {
                    case "0": return;
                    case "1":
                        p.addStudent(ask(in, "ID"), ask(in, "Name"), ask(in, "Branch"),
                                Double.parseDouble(ask(in, "CGPA")), ask(in, "Skills (comma separated)"));
                        System.out.println("Added."); break;
                    case "2":
                        p.addCompany(ask(in, "ID"), ask(in, "Company"), ask(in, "Role"),
                                Double.parseDouble(ask(in, "Package (LPA)")), Double.parseDouble(ask(in, "Min CGPA")),
                                ask(in, "Required skills"), Integer.parseInt(ask(in, "Openings")));
                        System.out.println("Added."); break;
                    case "3": System.out.println(p.student(ask(in, "Student ID"))); break;
                    case "4": for (Student s : p.eligibleStudents(ask(in, "Company ID"))) System.out.println("  " + s); break;
                    case "5": for (Company c : p.companiesSorted(ask(in, "Sort by (package/name/cutoff)"))) System.out.println("  " + c); break;
                    case "6": p.apply(ask(in, "Student ID"), ask(in, "Company ID")); System.out.println("Applied."); break;
                    case "7": for (Pair<Student> t : p.topApplicants(ask(in, "Company ID"), 5))
                                  System.out.printf("  %-15s %.1f%n", t.item.name, t.score); break;
                    case "8": for (Pair<Student> t : p.runSelection(ask(in, "Company ID")))
                                  System.out.printf("  Selected: %s (score %.1f)%n", t.item.name, t.score); break;
                    case "9": for (Student s : p.studentsSorted(ask(in, "Sort by (cgpa/name/branch)"))) System.out.println("  " + s); break;
                    case "10": System.out.println(p.summary()); break;
                    case "11": p.loadSample(); System.out.println("Sample data loaded."); break;
                    default: System.out.println("Invalid choice.");
                }
            } catch (RuntimeException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static String ask(Scanner in, String label) {
        System.out.print(label + ": ");
        return in.nextLine().trim();
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--demo")) demo(); else cli();
    }
}