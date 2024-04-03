
from pyspark.sql import SparkSession
spark = SparkSession.builder\
         .appName("Colab")\
         .config('spark.ui.port', '4050')\
         .getOrCreate()

import pyspark
from pyspark.sql.functions import concat_ws
from pyspark.sql import functions as f
from pyspark import SparkContext
sc = SparkContext.getOrCreate()
inputfile = sys.argv[1]
outputfile = sys.argv[2]
p = sc.textFile(inputfile)

workers=sys.argv[3]

def func3(l):
  ans= []
  lis = l.split(" ")
  siz = len(lis)
  ans.append([int(lis[1]), int(int(lis[0])%workers),str(0) , str(lis[0])])
  ans.append([int(lis[2]), int(int(lis[0])%workers),str(3) , str(lis[0])])
  i=3
  while(i<siz):
    ans.append([int(lis[i+1]), int(int(lis[0])%workers),str(1),str(lis[0])+" "+str(lis[i])])
    ans.append([int(lis[i+2]), int(int(lis[0])%workers),str(2),str(lis[0])+" "+str(lis[i])])
    i=i+3
  return ans


pd = p.flatMap(lambda l : func3(l))
q = (pd.toDF(["time","worker","op","Vid"])).groupby("time","worker",'op')
a= q.agg((f.collect_list('vid').alias('vid')))
#s=a.where(a.time>30) 
t = a.select('worker',concat_ws(' ',a.time,a.op,a.vid).alias('Vid')).coalesce(1)
t.write.partitionBy("worker").mode("overwrite").text(outputfile)

