# TARIS : Scalable Incremental Processing of Temporal Algorithms on Streaming Graphs
#### Ruchi Bhoot, Suved Ghanmode and [Yogesh Simmhan](http://cds.iisc.ac.in/faculty/simmhan/)
#### *DREAM:Lab, Indian Institute of Science (IISc)*

Temporal graphs are ones where lifespans are present on vertices, edges and attributes. Large temporal graphs are common in logistics and transit networks, social and web graphs, and in COVID-19 contact graphs. Real world temporal graphs are dynamic and are continuously changing. In the **TARIS Platform** ***(paper currently under review)***, we propose techniques for incrementally processing monotonic, selection based temporal algorithms for such streaming temporal graphs. Doing incremental prcoessing does not affect the correctness of the algorithm and reduces latency by 2-3 orders of magnitude as compared to baselines platforms we compare against, Tink [[WWW2018](paper-url)] and Gradoop [[VLDB2022](paper-url)].

<img src="https://github.com/dream-lab/i-wicm/assets/15685916/57ead526-7ada-4be9-a257-46ceb2282d71" width="40%" height="30%">

## About this Repository
The  **TARIS** platform provided in this repository is built on top of WICM ([Eurosys 2022](https://dl.acm.org/doi/pdf/10.1145/3492321.3519588), [GitHub](https://github.com/dream-lab/wicm)), Graphite (which implements ICM) [[ICDE 2020](https://doi.org/10.1109/ICDE48307.2020.00102)], [Apache Giraph 1.3.0](https://giraph.apache.org/releases.html) (which implements Pregel VCM), and [Hadoop 3.1.1](https://hadoop.apache.org/release/3.1.1.html) with support for HDFS and YARN. We provide instructions for installing and running TARIS in a pseudo-distributed mode on a single machine from binaries.

These instructions help install TARIS and the baseline frameworks which are compared in the paper under review. **The goal is to ensure that the artifacts can be evaluated to be [Functional](https://www.acm.org/publications/policies/artifact-review-and-badging-current)**, i.e., *the artifacts associated with the research are found to be documented, consistent, complete, exercisable.* These are provided for a sample graph and a sample algorithm, with more graph and algorithms share in the near future.

We first install TARIS and then run the *Earliest Arrival Time (EAT)* algorithm. We provide scripts to run *EAT graph algorithm* used in the paper on a sample graph, using *all update strategies* proposed in the paper under review. We also provide scripts to run the *different streaming strategies*. We also provide the implementation of the EAT algorithm on the *baseline frameworks* that we used for comparison.  We also provide scripts to verify that the result of incremental processing using TARIS is equivalent to recomptuing the full algorithm from scratch on the graph using WICM.

---
## 1. Installing Pre-requisites and Dependencies

Pre-requisites:
 * A Linux Ubuntu-based system (VM or bare-metal) with >= 8GB RAM
 * Java JDK 8
 * Maven >= 3.6.0

 1. First setup Hadoop 3.1.1 on the system with HDFS and YARN. Instructions for this are in [HadoopSetup.md](https://github.com/dream-lab/TARIS/tree/main).

Hadoop services should start on successful Hadoop/HDFS/YARN setup. Please see [HadoopSetup.md](https://github.com/dream-lab/TARIS/tree/main) for details.


---
## 2. TARIS Binaries

We will use pre-built JARs for TARIS present as `TARIS-1.0-SNAPSHOT-jar-with-dependencies.jar`

We plan to release the source code for TARIS in the future.

---

## 3. Running a WICM job for EAT on a Fullly Materialized Sample Graph

This runs the WICM job with recompute for each timestep on a sample graph we use to validate the correctness of incremental processing in TARIS.
Follow instructions at the [WICM GitHub Repo](https://github.com/dream-lab/wicm) to build the `WICM jar`. We provide instructions below to run a WICM job on our sample graph and EAT algorithm.

With Graphite ICM and Hadoop deployed, you can run your temporal graph processing job. We will use the **Earliest Arrival Time (EAT)** algorithm for this example. The job reads an input file of an interval graph in one of the supported formats and computes the earliest arrival path from a provided source node. We will use `IntIntNullTextInputFormat` input format, which indicates that the vertex ID is of type `Int`, the time dimension is of type `Int`, with no (`Null`) edge properties, and `Text` implies that the input graph file is in text format.

A sample graph [sampleGraph.txt](https://github.com/dream-lab/TARIS/tree/main/data/graph) has been provided in `data/graph` with ~30k vertices, ~1M edges and 40 timesteps. The topology of the graph was generated using [`PaRMAT`](https://github.com/farkhor/PaRMAT). The start-time and end-time of interval edges are uniformly sampled from [0,40)]. The lifespan of the vertex is set to maintain referential integrity in the graph.
Each line is an adjacency list for one source vertex and all its sink vertices of the format `source_id source_startTime source_endTime dest1_id dest1_startTime dest1_endTime dest2_id dest2_startTime dest2_endTime ...`. 


Copy the sample graph file to HDFS:
```
hdfs dfs -copyFromLocal data/graph/sampleGraph.txt
```
And check if the input graph has been copied to HDFS:
```
hdfs dfs -ls sampleGraph.txt
```


To run the `EAT` algorithm, the Giraph job script `runEAT.sh` has been provided in [`build/scripts/giraph/wicm_luds`](https://github.com/dream-lab/wicm/tree/Eurosys2022/build/scripts/giraph/wicm_luds). 

```
runEAT.sh <source> <lowerE> <upperE> <windows> <perfFlag> <inputGraph> <outputDir>
```
The job script takes 4 arguments:

1. `source` : The source vertex ID from which the traversal algorithm will start (e.g., `0`)
2. `perfFlag` : Set to `true` to dump performance related log information, `false` otherwise (e.g., `false`)
3. `inputGraph` : HDFS path to the input graph (e.g., `sampleGraph.txt`)
4. `outputDir` : HDFS path to the output folder (e.g., `WICM_output`)
5. `lowerE` : Start time of the graph lifespan (e.g., `0`)
6. `upperE` : End time of the graph lifespan (e.g., `40`)
7. `windows` : Temporal partitioning of the graph's lifespan, specified as timepoint boundaries separated by semicolon (e.g., `0;10;20;30;40`)

The sample graph `sampleGraph.txt` has a lifespan of [0,40). We will ruin WICK with fixed window size of 10 timesteps, with the windows as [0,10), [10,20), [20,30) and [30,40). Later, in #6, we describe how streaming strategies can be used to dynamically choose window size during runtime.

To run the WICM job using this configuration and with the same source vertex ID `0` on the sample graph:
```
cd build
bash ./scripts/giraph/wicm_luds/runEAT.sh 0 0 40 "0;10;20;30;40" false sampleGraph.txt WICM_output
```

The `WICM_output` folder should be present under `build/` after successful finishing the job.


---

## 4. Running a TARIS job for EAT Incrementally on a Sample Graph

This evaluates our proposed TARIS framework to show incremental processing on streaming graph using artifacts in this repo.

For this example, we assume the initial input graph at timestep 0 to be an empty graph with 0 vertices and 0 edges. 
A sample initial graph [empty.txt](https://github.com/dream-lab/TARIS/blob/main/data/graph/empty.txt) has been provided in `data/graph`. Optionally, TARIS can also start with a materialized interval graph at some earlier timestep as the initial graph and apply updates to it for future timesteps.

To copy the initial graph to HDFS:
```
hdfs dfs -copyFromLocal data/graph/empty.txt
```

To keep the example simple, we *a priori* create update sets for the each timestep as files that are loaded by the worker as part of its parse. This avoids setting up a Kafka broker to receive the update sets using a subscriber.
The mutation files for each timestep of the sample graph and for each worker are provided in [data/graph/sampleGraphMutations](https://github.com/dream-lab/TARIS/tree/main/data/graph/sampleGraphMutations). Details of how to create these mutation files are given in #7.
Each mutation file is of the format :` [timestep] [no of vid] [vid1] [op1] [array of Vid]* [op2] 4 [vid2] ...` in a binary format; `*` indicates an optional field, while `4` is a special separator that indicates the end of the current vertex's mutations.

####  Each vertex can have 4 kinds of mutations operations. Enum [op]:
- **0 :** Add vertex
- **1 :** Add edges. followed by a list of destination vertex ids.
- **2 :** Delete edges. followed by a list of destination vertex ids.
- **3 :** Delete vertex.


TARIS is evaluated on the 5 update strategies proposed in the paper under review. We provide scripts for all and show an example of our best performing *SpillRead* strategy.
#### The `Strategy` enum represents different pipeline strategy :
- **1 :** JITM
- **2 :** AITM
- **3 :** DITM
- **4 :** DITM + AITM
- **5 :** spillRead

All related scripts are provided in ['scripts/runEAT.sh'](https://github.com/dream-lab/TARIS/tree/main/scripts). The scripts have additional arguments:

- `mpath` :  **_Absolute_** path for mutation files in data/graph/sampleGraphMutations/
- `ws` : window size (e.g., `10`)
- `s` : pipeline strategy enum  (e.g., `5` for spillRead)
- `ssends` : At which superstep each window ends, required for strategy 1-4. Can get by using the information when running WICM. (e.g., `""` for spillRead).
    We extracted ssends for the sampleGraph  from WICM run for source vertex 0 and ws 10 as `"0;9;13;17"`
```
runEAT.sh <source> <inputGraph> <outputDir> <lowerE> <upperE> <windows> <ws> <s> <mpath> <ssends>
```

To run the TARIS job on the sample graph with the same source vertex ID `0`:
```
bash ./scripts/runEAT.sh 0 empty.txt TARIS_output 0 40 "0;10" 10 5 "YOUR_ABSOLUTE_PATH_TO_data/graph/sampleGraphMutations/" "0;9;13;17"
```

**NOTE:** You need to provide the absolute path on your system for mutation files present in `data/sampleGraphMutations/`

The `TARIS_output` folder will be created after successful finishing of the job.

To validate correctness of result for TARIS, use the path of `WICM_output` created in section #3 and path to `TARIS_output` and compare the file contents:
```
diff  TARIS_output/sorted.txt WICM_output/sorted.txt | wc -l
```

---
## 5. Running TARIS with Streaming Strategies

TARIS was evaluated on 5 streaming strategies we propose. These dynamically choose window size during runtime and can adapt to a given input rate of mutations. We provide scripts for all.
#### The ` Streaming Strategy` enum represents different pipeline strategy :
- **6 :** Fixed window size (baseline)
- **7 :** Greedy
- **8 :** Threshiold 1 (minimum window size)
- **9 :** Threshiold 2 (minimum window size + maximum wait latency)
- **10 :** Dynamic


All relevant scripts are provided in [scripts/runEATStreaming.sh](https://github.com/dream-lab/TARIS/tree/main/scripts). The scripts have additional arguments:
12. tmpmin :  Input rate of timesteps in timesteps/minute (e.g., `100`)
13. minws : minimum window size threshold (e.g., `15`)
14. maxLat : maximum wait latency threshold (e.g., `10`)
    Here, `ws` represents size of the first window (e.g., `1`)
```
runEAT.sh <source> <inputGraph> <outputDir> <lowerE> <upperE> <windows> <ws> <s> <mpath> <tspmin> <minws> <maxLat>
```

To run the TARIS job on streaming graph with inpute rate as `100 timesteps per minute` on the same vertex source ID `0` of the sample graph.
```
bash ./scripts/runEATStreaming.sh 0 empty.txt TARIS_output 0 40 "0;1" 10 6 "YOUR_ABSOLUTE_PATH_TO_data/graph/sampleGraphMutations/" 100 15 10
```


---
## 6. Customisations for Running on Cluster

[//]: # (Our paper evaluates five graph traversal algorithms: Earliest Arrival TIme &#40;EAT&#41;, Single Source Shortest Path &#40;SSSP&#41;, Temporal Reachability &#40;TR&#41;, Temporal Minimum Spanning Tree &#40;TMST&#41; and Fastest travel Time &#40;FAST&#41;.)

[//]: # (We have provided the job scripts for all platform variants to run each of these 5 traversal algorithms: `runEAT.sh, runSSSP.sh, runTR.sh, runTMST.sh, runFAST.sh` under respective folders [WICM]&#40;https://github.com/dream-lab/wicm/tree/Eurosys2022/build/scripts/giraph/wicm&#41; and)

[//]: # ([TARIS]&#40;https://github.com/dream-lab/i-wicm/tree/taris/build/scripts/giraph/wicmi&#41;)

The scripts can be edited to specify the number of workers using the argument `-w <num_workers>`, the number of threads per worker using the argument `giraph.numComputeThreads <num_threads>`, and the size of heap memory using the argument `-yarnheap <size in MB>`. By default, we run on `1` worker and `1` thread per worker and `60GB` yarn memory.

The number of workers is the number of machines in the cluster. For Hadoop deployment in a distributed mode, please check [`Hadoop Cluster Setup`](https://hadoop.apache.org/docs/r3.1.1/hadoop-project-dist/hadoop-common/ClusterSetup.html). The current [`HadoopSetup.md`](https://github.com/dream-lab/wicm/blob/Eurosys2022/HadoopSetup.md) sets up Hadoop in a pseudo-distributed mode with 1 worker. 

---
## 7. Creating Mutation Files for Interval Graph

In this section we provide scripts to create the mutation files for any materialzed interval graph. 

Additional pre-requisites:
 * Apache Spark 3.1.2
 * Python >= 2.7

Instructions for setting up Apache Spark are present in [`SparkSetup.md`](https://github.com/dream-lab/wicm/blob/Eurosys2022/SparkSetup.md). Hadoop should have been setup before running Spark using the instructions from above.

The script takes as input materialized complete interval graph of lifespan say [0-10] and creates mutation files for each timestep in the range (0,10). This code is present in [scripts/createMutations.py](https://github.com/dream-lab/TARIS/tree/main/scripts). It uses the same input graph format as described above under `section #3`. By default we use Giraph and TARIS with Hash partition. Both of these are user customizable. The script takes 3 arguments:

 1. inputGraph : HDFS path to input graph (e.g., `sampleGraph.txt`)
 2. outputPath : HDFS path to output mutation files (e.g., `sampleGraphMutations/`)
 3. numWorkers : Number of workers in the cluster (e.g., `1`)

```
spark-submit --master yarn --num-executors 1 --executor-cores 1 --executor-memory 2G createMutations.py <inputGraph> <outputPath> <numWorkers>
```

To run this pyspark code on the input graph `sampleGraph.txt` for `1` worker and store the output in `sampleGraphMutations/` folder in hdfs , we run:
```
cd scripts
spark-submit --master yarn --num-executors 1 --executor-cores 1 --executor-memory 2G createMutations.py sampleGraph.txt sampleGraphMutations/ 1
```

---
## 8. Running Baselines Frameworks: *Tink* and *Gradoop*
To run the baselines, we provide the binaries for Tink ([Tink: A Temporal Graph Analytics Library for Apache Flink](https://doi.org/10.1145/3184558.3186934), [Tink](https://github.com/otherwise777/Tink)) and Gradoop ([Distributed temporal graph analytics with GRADOOP](https://dl.acm.org/doi/abs/10.1007/s00778-021-00667-4), [Gradoop Temporal Examples](https://github.com/dbs-leipzig/gradoop/tree/develop/gradoop-examples/gradoop-examples-temporal)).
The binaries we use are extended version of Tink and Gradoop source code which includes temporal graph algorithms implementation required for comparison. These are present in `jars/baseline`.


### 8.1. Create sample graph for baselines
To run the baselines, we need to convert a sample graph provided for the TARIS's input to gradoop/tink native format. 
The sample graph is created using the following command:
```
cd data/baselineInputCreator
sh create-baseline-inputs.sh
```

### 8.3. Running the Baselines

### 8.3.1. Setting up Flink
We first need to install flink since both frameworks use it. The instructions to install flink are present at [flink](https://nightlies.apache.org/flink/flink-docs-stable/docs/try-flink/local_installation/).
Before executing either of baseline one needs to start flink cluster. To start flink cluster, run the following command:
```
./{flink_home}/bin/start-cluster.sh
```

### 8.3.2 Running EAT on Baselines
We have provided the run scripts for both Tink and Gradoop for the EAT algorithm. The run scripts are present in the scripts folders and take the following arguments:

- `full-jar-path`: path to the full jar file
- `graph`: graph-name / path to input
- `algorithm`: algorithm to run
- `src vertex id`: source vertex id
- `start time step`: start time step
- `end time step`: end time step

Sample run command for Tink:
```
sh ./scripts/run-tink.sh ./jars/baseline/tink-1.0.1-jar-with-dependencies.jar <sample-graph-output-dir>/tink 0 1 40
```

Sample run command for Gradoop:
```
sh ./scripts/run-gradoop.sh ./jars/baseline/gradoop-0.6.1-jar-with-dependencies.jar <sample-graph-output-dir>/gradoop 0 1 40
```

Once the execution completes the output will be present in the output directory specified in the run script. We can compare the output of the baselines with the output of TARIS to verify the correctness of the results. Using the following command:
```
python3 scripts/baseline_output_comparator.py -g Sample -a EAT -f tink -t TARIS_output -o <compared-to-framework-output-dir> 
```

## 8.6. Stop the Flink Cluster
After executing the baseline, you can stop the flink cluster by running the following command:
```
./{flink_home}/bin/stop-cluster.sh #Stops flink cluster
```
---
## 9. Graphs Evaluated in the Paper

The paper evaluates six different graphs, which were downloaded from the following sources. 
 1. Reddit: https://www.cs.cornell.edu/~jhessel/projectPages/redditHRC.html
 2. Twitter_static: http://twitter.mpi-sws.org/
 3. LDBC_365: datagen-8_9-fb - https://graphalytics.org/datasets
 4. LDBC_static: datagen-8_9-fb - https://graphalytics.org/datasets

These original graphs were pre-processed before being used as input to frameworks in place of the `sampleGraph.txt`. The pre-processing converts these graphs to the expected formats and normalizes the lifespans. 

---
## Contact

For more information, please contact: **Ruchi Bhoot <ruchibhoot@iisc.ac.in>** or **Suved Ghanmode <suvedsanjay@iisc.ac.in>** from [DREAM:Lab](https://dream-lab.in/), Department of Computational and Data Sciences, Indian Institute of Science, Bangalore, India


---
## License
```
Copyright [2024] [DREAM:Lab, Indian Institure of Science]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
