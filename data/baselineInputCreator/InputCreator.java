import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;


public class InputCreator {
    private static final Pattern SEPARATOR = Pattern.compile("[\t ]");
    private static Map<Integer, TemporalGraph> baseGraphs;
    private static final LinkedList<Long> loadAndApplyTimes = new LinkedList<>();
    private static final LinkedList<Long> gradoopGraphStoreTimes = new LinkedList<>();
    private static final LinkedList<Long> tinkGraphStoreTimes = new LinkedList<>();
    private static final int nWorkers = 8;

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: java baseline.tink.InputCreator <full-jar-path> <graph> " +
                    "<input-folder-directory> <gradoop-output-path> true to resume from last state else false");
            System.exit(1);
        }
        String baseInputGraphMutationsFilePath = args[0];
        String gradoopOutputPath = args[1] + "/gradoop";
        String tinkOutputPath = args[1] + "/tink";
        String logFilePath = args[1] + "/executionMeta";
        boolean resumeFromState = args.length == 3 && Boolean.parseBoolean(args[2]);

        System.out.println("Input graph mutations file path: " + baseInputGraphMutationsFilePath);

        File executionMetadataDirectory = new File(logFilePath);
        if (!executionMetadataDirectory.exists() && !executionMetadataDirectory.mkdirs()) {
            System.out.println("Log Output " + executionMetadataDirectory + " directory does not exist");
            System.exit(1);
        }
        BufferedWriter outputWriter = new BufferedWriter(new FileWriter(executionMetadataDirectory + "/output.txt",
                resumeFromState));

        File mutationsDirectory = new File(baseInputGraphMutationsFilePath);
        File[] timeStepMutationsDirectory = mutationsDirectory.listFiles(File::isDirectory);
        if (timeStepMutationsDirectory == null) {
            System.out.println("No mutations found in " + baseInputGraphMutationsFilePath);
            System.exit(1);
        }

        baseGraphs = new HashMap<>();
        for (int i = 0; i < nWorkers; i++) baseGraphs.put(i, new TemporalGraph(TemporalGraph.repeat("F", 24), "g1"));

        int nTimeStepMutationsDirectory = timeStepMutationsDirectory.length;

        int startFromIndex;
        if (resumeFromState) {
            startFromIndex = Integer.parseInt(args[0]);
            File checkpointedGraphs = new File(executionMetadataDirectory + "/time=" + (startFromIndex - 1));
            Files.walk(checkpointedGraphs.toPath())
                    .filter(Files::isRegularFile)
                    .parallel()
                    .forEach(path -> {
                        try {
                            loadBaseGraph(path);
                        } catch (IOException e) {
                            System.err.println("Error loading base graph from " + path + " " + e.getMessage());
                            System.exit(1);
                        }
                    });
        } else {
            startFromIndex = 0;
            outputWriter.write("Step\tLoad-Apply\tGradoop\tTink\n");
        }
        System.out.printf("Loading & applying %d mutations... \n", nTimeStepMutationsDirectory - startFromIndex);

        // load & apply mutations and store the updated graph
        File timeStepMutationDirectory;
        BufferedWriter graphWriter;
        for (int i = startFromIndex; i < nTimeStepMutationsDirectory; i++) {
            String timeStepName = String.format("/time=%d", i);

            timeStepMutationDirectory = new File(mutationsDirectory + timeStepName);

            long timeResults = Arrays.stream(Objects.requireNonNull(timeStepMutationDirectory.listFiles()))
                    .parallel()
                    .map(directoryPath -> {
                        try {
                            return applyMutation(directoryPath.toPath());
                        } catch (Exception e) {
                            System.err.println("ERROR applying mutations from " + directoryPath + " " + e.getMessage());
                            System.exit(1);
                        }
                        return null;
                    })
                    .max(Long::compareTo)
                    .get();

            loadAndApplyTimes.add(timeResults);

            // store the updated graph
            storeGraphs(gradoopOutputPath, tinkOutputPath, timeStepMutationDirectory.getName());
            outputWriter.write(i + "\t" + loadAndApplyTimes.getLast() + "\t" + gradoopGraphStoreTimes.getLast() + "\t"
                    + tinkGraphStoreTimes.getLast() + "\n");
            outputWriter.flush();
            System.out.println(timeStepName + " done");

            // remove first checkpoint
            if (i > 5) {
                File firstCheckpoint = new File(executionMetadataDirectory + "/time=" + (i - 5));
                File[] files = firstCheckpoint.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.delete()) {
                            System.out.println("Error deleting file " + file);
                            System.exit(1);
                        }
                    }
                }
                if (!firstCheckpoint.delete()) {
                    System.out.println("Error deleting directory " + firstCheckpoint);
                    System.exit(1);
                }
            }

            // create new checkpoint directory
            File checkpointedGraphs = new File(executionMetadataDirectory + "/time=" + i);
            if (!checkpointedGraphs.exists() && !checkpointedGraphs.mkdirs()) {
                System.out.println("Output " + checkpointedGraphs + " directory does not exist");
                System.exit(1);
            }
            for (int wid = 0; wid < nWorkers; wid++) {
                graphWriter = new BufferedWriter(new FileWriter(checkpointedGraphs + "/" + wid));
                graphWriter.write(baseGraphs.get(wid).toString());
                graphWriter.close();
            }

        }
        outputWriter.close();
        System.out.println("Gradoop Output Path: " + gradoopOutputPath);
        System.out.println("Tink Output Path: " + tinkOutputPath);
    }

    private static void storeGraphs(String gradoopInputDirPath, String tinkInputDirPath, String fileName) {
        File gradoopInputDir = new File(gradoopInputDirPath + "/" + fileName);
        if (!gradoopInputDir.exists() && !gradoopInputDir.mkdirs()) {
            System.out.println("Output " + fileName + " directory does not exist");
            System.exit(1);
        }

        File tinkInputDir = new File(tinkInputDirPath + "/" + fileName);
        if (!tinkInputDir.exists() && !tinkInputDir.mkdirs()) {
            System.out.println("Output " + tinkInputDir + " directory does not exist");
            System.exit(1);
        }

        long gradoopWriteTime = 0;
        long tinkWriteTime = 0;
        for (int workerId = 0; workerId < nWorkers; workerId++) {
            TemporalGraph baseGraph = baseGraphs.get(workerId);
            CompletableFuture<Long> tinkFuture = CompletableFuture.supplyAsync(() ->
                    baseGraph.writeToTinkFormat(tinkInputDir));
            CompletableFuture<Long> gradoopFuture = CompletableFuture.supplyAsync(() ->
                    baseGraph.writeToGradoopFormat(gradoopInputDir));

            CompletableFuture.allOf(tinkFuture, gradoopFuture).join();
            try {
                tinkWriteTime += tinkFuture.get();
                gradoopWriteTime += gradoopFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                System.out.println("Runtime Exception : " + e.getMessage());
            }
        }
        tinkGraphStoreTimes.add(tinkWriteTime);
        gradoopGraphStoreTimes.add(gradoopWriteTime);
    }

    private static long applyMutation(Path directoryPath) throws IOException {
        long ts1 = System.currentTimeMillis();
        int workerId = Integer.parseInt(directoryPath.getFileName().toString().substring(7));
        TemporalGraph baseGraph = baseGraphs.get(workerId);

        Files.walk(directoryPath)
                .filter(Files::isRegularFile)
                .filter(file -> !file.getFileName().toString().equals("_SUCCESS"))
                .flatMap(path -> {
                    try {
                        return Files.readAllLines(path).stream();
                    } catch (IOException e) {
                        System.err.println("Error reading file: " + path + " " + e.getMessage());
                    }
                    return null;
                })
                .map(str -> SEPARATOR.split(str.replace("inf", String.valueOf(Integer.MAX_VALUE))))
                .sorted(Comparator.comparingInt(tokens -> Integer.parseInt(tokens[1])))
                .forEach(tokens -> {
                    int timeStep = Integer.parseInt(tokens[0]);
                    int mutationType = Integer.parseInt(tokens[1]);
                    int i = 0;

                    try {
                        switch (mutationType) {
                            case 0:
                                // add vertex
                                for (i = 2; i < tokens.length; i++)
                                    baseGraph.addVertex(Integer.parseInt(tokens[i]), timeStep, Integer.MAX_VALUE);
                                break;
                            case 1:
                                // add edge
                                for (i = 2; i < tokens.length; i += 2) {
                                    baseGraph.addEdge(Integer.parseInt(tokens[i]), Integer.parseInt(tokens[i + 1]),
                                            timeStep, Integer.MAX_VALUE);
                                }
                                break;
                            case 2:
                                // del edge
                                for (i = 2; i < tokens.length; i += 2)
                                    baseGraph.removeEdge(Integer.parseInt(tokens[i]), Integer.parseInt(tokens[i + 1]), timeStep);
                                break;
                            case 3:
                                // del vertex
                                for (i = 2; i < tokens.length; i++)
                                    baseGraph.removeVertex(Integer.parseInt(tokens[i]), timeStep);
                                break;
                            default:
                                throw new RuntimeException("Invalid mutation type: " + mutationType);
                        }
                    } catch (NullPointerException e) {
                        throw new RuntimeException(String.format("Operation %s, VID %s\nMsg %s", tokens[1], tokens[i], e.getMessage()));
                    }
                });

        return System.currentTimeMillis() - ts1;
    }

    private static void loadBaseGraph(Path workerGraph) throws IOException {
        System.out.println("Resuming using graph from " + workerGraph);
        TemporalGraph baseGraph = baseGraphs.get(Integer.parseInt(workerGraph.getFileName().toString()));

        Files.readAllLines(workerGraph).forEach(str -> {
            String[] tokens = SEPARATOR.split(str);
            int sourceVertexId = Integer.parseInt(tokens[0]);
            baseGraph.addVertex(sourceVertexId, Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
            for (int i = 3; i < tokens.length; i += 3)
                baseGraph.addEdge(sourceVertexId, Integer.parseInt(tokens[i]), Integer.parseInt(tokens[i + 1]),
                        Integer.parseInt(tokens[i + 2]));
        });
    }
}

class TemporalGraph {
    private final Map<Integer, TemporalVertex> vertices;
    private final Map<Integer, Map<Integer, TemporalEdge>> edges;

    private final String graphID;
    private final String graphName;
    private final int lastTimeStep;

    public TemporalGraph(String graphID, String graphName) {
        this.graphID = graphID;
        this.graphName = graphName;
        this.vertices = new HashMap<>();
        this.edges = new HashMap<>();
        this.lastTimeStep = 40;
    }

    public void addVertex(int vertexId, int startTime, int endTime) {
        // Add vertex
        TemporalVertex vertex = new TemporalVertex(vertexId, startTime, endTime);
        this.vertices.put(vertex.getId(), vertex);
        this.edges.put(vertex.getId(), new HashMap<>());
    }

    public void addEdge(int source, int destination, int startTime, int endTime) {
        TemporalEdge edge = new TemporalEdge(source, destination, startTime, endTime);
        this.edges.get(source).put(destination, edge);
    }

    public void removeVertex(int vertexId, int endTime) {
        this.vertices.get(vertexId).setEndTime(endTime);
    }

    public void removeEdge(int source, int destination, int endTime) {
        this.edges.get(source).get(destination).setEndTime(endTime);
    }

    public long writeToGradoopFormat(File outputDirectory) {
        long ts1 = System.currentTimeMillis();

        try {
            File file = new File(outputDirectory + "/metadata.csv");
            if (!file.exists()) {
                BufferedWriter metadataWriter = new BufferedWriter(new FileWriter(file));
                metadataWriter.write("v;ver;starttime:long,stoptime:long\ne;edg;starttime:long,stoptime:long\ng;" + graphName + ";");
                metadataWriter.close();
            }

            file = new File(outputDirectory + "/graphs.csv");
            if (!file.exists()) {
                BufferedWriter graphWriter = new BufferedWriter(new FileWriter(file));
                graphWriter.write(graphID + ";" + graphName + ";;(0" + lastTimeStep + "),(0," + lastTimeStep + ")\n");
                graphWriter.close();
            }

            BufferedWriter vertexWriter = new BufferedWriter(new FileWriter(outputDirectory + "/vertices.csv", true));
            StringBuilder sb = new StringBuilder();
            for (TemporalVertex vertex : this.vertices.values()) {
                sb.append(toHex(Integer.toString(vertex.getId()))).append(';');
                sb.append('[').append(graphID).append("];");
                sb.append("ver;");
                sb.append(vertex.getStartTime()).append('|').append(vertex.getEndTime()).append(';');
                String timestamps = "(" + vertex.getStartTime() + "," + vertex.getEndTime() + ")";
                sb.append(timestamps).append(',');
                sb.append(timestamps).append('\n');
            }
            vertexWriter.write(sb.toString());
            vertexWriter.close();

            BufferedWriter edgesWriter = new BufferedWriter(new FileWriter(outputDirectory + "/edges.csv", true));
            for (Map<Integer, TemporalEdge> edgeMap : this.edges.values()) {
                sb = new StringBuilder();
                for (TemporalEdge edge : edgeMap.values()) {
                    String sourceAsString = Integer.toString(edge.getSource());
                    String destinationAsString = Integer.toString(edge.getDestination());
                    sb.append(toHex(sourceAsString, destinationAsString)).append(';');
                    sb.append('[').append(graphID).append("];");
                    sb.append(toHex(sourceAsString)).append(';');
                    sb.append(toHex(destinationAsString)).append(';');
                    sb.append("edg;");
                    sb.append(edge.getStartTime()).append('|').append(edge.getEndTime()).append(';');
                    String timestamps = "(" + edge.getStartTime() + "," + edge.getEndTime() + ")";
                    sb.append(timestamps).append(',');
                    sb.append(timestamps).append('\n');
                }
                edgesWriter.write(sb.toString());
            }
            edgesWriter.close();
        } catch (IOException e) {
            System.err.println("Error writing to Gradoop CSV Format: " + e.getMessage());
        }

        return System.currentTimeMillis() - ts1;
    }


    private static String toHex(String num) {
        long a = Long.parseLong(num);
        String hex = Long.toString(a, 16);
        int len = hex.length();
        int left = 24 - len;
        return repeat("0", Math.max(0, left)) + hex;
    }

    private static String toHex(String a, String b) {
        int len = a.length() + b.length();
        int left = 24 - len;
        StringBuilder pre = new StringBuilder();
        pre.append(repeat("e", Math.max(0, left)));
        pre = new StringBuilder(a + pre + b);
        return pre.toString();
    }

    public static String repeat(String ch, int times) {
        return String.join("", Collections.nCopies(times, ch));
    }

    public long writeToTinkFormat(File outputDirectory) {
        long ts1 = System.currentTimeMillis();

        try {
            StringBuilder sb = new StringBuilder();
            for (TemporalVertex vertex : this.vertices.values()) {
                sb.append(vertex.getId()).append(',');
                sb.append(vertex.getStartTime()).append(',');
                sb.append(vertex.getEndTime()).append('\n');
            }
            BufferedWriter vertexWriter = new BufferedWriter(new FileWriter(outputDirectory + "/vertices.csv", true));
            vertexWriter.write(sb.toString());
            vertexWriter.close();

            BufferedWriter edgesWriter = new BufferedWriter(new FileWriter(outputDirectory + "/edges.csv", true));
            sb = null;
            for (Map<Integer, TemporalEdge> edgeMap : this.edges.values()) {
                sb = new StringBuilder();
                for (TemporalEdge edge : edgeMap.values()) {
                    sb.append(edge.getSource()).append(',');
                    sb.append(edge.getDestination()).append(',');
                    sb.append(edge.getStartTime()).append(',');
                    sb.append(edge.getEndTime()).append('\n');
                }
                edgesWriter.write(sb.toString());
            }
            edgesWriter.close();
        } catch (IOException e) {
            System.err.println("Error writing to Tink Format: " + e.getMessage());
        }

        return System.currentTimeMillis() - ts1;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        this.vertices.forEach((vertexId, vertex) -> {
            sb.append(vertexId).append("\t").append(vertex.getStartTime()).append("\t").append(vertex.getEndTime());
            this.edges.get(vertexId).forEach((destination, edge) -> sb.append("\t").append(destination).append("\t")
                    .append(edge.getStartTime()).append("\t").append(edge.getEndTime()));
            sb.append("\n");
        });

        return sb.toString();
    }
}

abstract class TemporalProperty {
    private final int startTime;

    private int endTime;

    public TemporalProperty(int startTime, int endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getStartTime() {
        return startTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }

}

class TemporalEdge extends TemporalProperty {
    private final int source;
    private final int destination;

    public TemporalEdge(int source, int destination, int startTime, int endTime) {
        super(startTime, endTime);
        this.source = source;
        this.destination = destination;
    }

    public int getSource() {
        return this.source;
    }

    public int getDestination() {
        return this.destination;
    }
}

class TemporalVertex extends TemporalProperty {
    private final int id;

    public TemporalVertex(int id, int startTime, int endTime) {
        super(startTime, endTime);
        this.id = id;
    }

    public int getId() {
        return this.id;
    }
}