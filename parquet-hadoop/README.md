<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one
  ~ or more contributor license agreements.  See the NOTICE file
  ~ distributed with this work for additional information
  ~ regarding copyright ownership.  The ASF licenses this file
  ~ to you under the Apache License, Version 2.0 (the
  ~ "License"); you may not use this file except in compliance
  ~ with the License.  You may obtain a copy of the License at
  ~
  ~   http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied.  See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
  -->

 ## Client cache
 We maintain plasma clients in org/apache/parquet/hadoop/ParquetFileReader.java.Cache granularity is column chunk.

 ## To use it with Spark
 Two jars is needed to run with Spark.

 1:parquet-hadoop-1.10.1.jar
 ```
git clone https://github.com/Intel-bigdata/parquet-mr.git
cd parquet-mr/parquet-hadoop
git checkout oap-1.10.x
mvn clean package -DskipTests
```
 2:arrow-plasma-0.17.0.jar
 ```
git clone https://github.com/Intel-bigdata/arrow.git
cd arrow/java
git checkout oap-master
mvn clean package -DskipTests
```
Put these two jars under a folder.For example ```home/spark-sql/extra-jars```.Add conf in spark-default.conf
```
spark.files                        file:///home/spark-sql/extra-jars/parquet-hadoop-1.10.1.jar,file:///home/spark-sql/extra-jars/arrow-plasma-0.17.0.jar
spark.executor.extraClassPath      ./parquet-hadoop-1.10.1.jar:./arrow-plasma-0.17.0.jar
spark.driver.extraClassPath        file:///home/spark-sql/extra-jars/parquet-hadoop-1.10.1.jar:file:///home/spark-sql/extra-jars/arrow-plasma-0.17.0.jar
```
