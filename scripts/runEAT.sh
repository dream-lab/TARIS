#!/bin/bash

source=$1
inputGraph="$2"
outputDir="$3"
lowerE=$4
upperE=$5
windows="$6"
mPath="${9}"
last=$5
windowsize=$7
strategy=$8
absMutationPath="${9}"
ssends="${10}"
tmp100s=${11}
wantedlatency=${12}
spillsize=${13}

##### restart hadoop - cold start
:<<'END'
echo "Restarting Hadoop..."
$HADOOP_HOME/sbin/stop-all.sh
sleep 10
$HADOOP_HOME/sbin/start-all.sh
sleep 10
echo "Hadoop restarted!"
sleep 40
END
echo "Starting TARIS job..."

hadoop jar TARIS-1.0-SNAPSHOT-jar-with-dependencies.jar \
org.apache.giraph.GiraphRunner in.dreamlab.wicm.algorithms.wicmi.Strategy"$strategy".EAT \
--yarnjars TARIS-1.0-SNAPSHOT-jar-with-dependencies.jar \
-yarnheap 60000 \
-vif in.dreamlab.wicm.io.mutations.formats.EATTextInputFormat -vip "$inputGraph" \
-vof in.dreamlab.graphite.io.formats.IntIntIdWithValueTextOutputFormat -op "$outputDir" -w 1 \
-ca giraph.vertexClass=in.dreamlab.graphite.graph.DefaultIntervalVertex \
-ca giraph.vertexValueClass=in.dreamlab.graphite.graphData.IntIntIntervalData \
-ca giraph.edgeValueClass=in.dreamlab.graphite.graphData.IntIntIntervalData \
-ca giraph.outgoingMessageValueClass=in.dreamlab.graphite.comm.messages.IntIntIntervalMessage \
-ca graphite.intervalClass=in.dreamlab.graphite.types.IntInterval \
-ca graphite.warpOperationClass=in.dreamlab.graphite.warpOperation.IntMin \
-ca giraph.masterComputeClass=in.dreamlab.wicm.graph.mutations.Master.WICMMutationsWindowMasterStrategy"$strategy" \
-ca giraph.workerContextClass=in.dreamlab.wicm.graph.mutations.WICMMutationsWorkerContext \
-ca giraph.vertexResolverClass=in.dreamlab.wicm.graph.mutations.resolver.EATVertexResolver \
-ca wicm.mutationReaderClass=in.dreamlab.wicm.io.mutations.mutationReaders.EATMutationFileReaderNew \
-ca giraph.numComputeThreads=1 \
-ca giraph.numInputThreads=1 \
-ca sourceId="$source" \
-ca lastSnapshot="$last" \
-ca lowerEndpoint="$lowerE" \
-ca upperEndpoint="$upperE" \
-ca windows="$windows" \
-ca wicm.localBufferSize=2000 \
-ca wicm.minMessages=20 \
-ca icm.blockWarp=true \
-ca wicm.mutationPath="$mPath" \
-ca wicm.resolverPath="$outputDir" \
-ca windowSize="$windowsize" \
-ca strategy="$strategy" \
-ca debugPerformance=true \
-ca timestampPer100sec="$tmp100s" \
-ca SSends="$ssends" \
-ca mutationPath="$absMutationPath"
##### dump output
hdfs dfs -copyToLocal "$outputDir" .
hdfs dfs -rm -r "$outputDir"

##### dump logs
#appID=$(yarn app -list -appStates FINISHED,KILLED | grep "EAT" | sort -k1 -n | tail -n 1 | awk '{print $1}')
#echo $appID
#yarn logs -applicationId $appID > "EAT_"$outputDir"_"$source"_debug.log"


echo "Sorting debug output..."
cat $outputDir/part* >> $outputDir/output.txt
rm $outputDir/part*
#cat $outputDir/dump* >> $outputDir/output.txt
#rm $outputDir/dump*
sort -k1 -n < $outputDir/output.txt > $outputDir/sorted.txt
rm $outputDir/output.txt
sed -i '1d' $outputDir/sorted.txt                                                                                                                                                                                                                                                                                                                                ~                                                                                                                                                                                                                                                                                                                                                                                           ~