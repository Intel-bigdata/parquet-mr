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
 This feature means there exists cache logic when loading parquet data. Client here refers to plasma client, which is used to get/put cache with plasma server.
 We maintain plasma clients in org/apache/parquet/hadoop/ParquetFileReader.java. Cache granularity is column chunk.

 ## To use it with Spark
 Two jars and ```plasma-store-server``` are needed to run with Spark and  .

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
This jar can also be downloaded via [mvn-repo-arrow-plasma-0.17.0.jar](https://repo1.maven.org/maven2/com/intel/arrow/arrow-plasma/0.17.0/arrow-plasma-0.17.0.jar).

Put these two jars under a folder. For example ```home/spark-sql/extra-jars```.Add conf in spark-default.conf
```
spark.files                        file:///home/spark-sql/extra-jars/parquet-hadoop-1.10.1.jar,file:///home/spark-sql/extra-jars/arrow-plasma-0.17.0.jar
spark.executor.extraClassPath      ./parquet-hadoop-1.10.1.jar:./arrow-plasma-0.17.0.jar
spark.driver.extraClassPath        file:///home/spark-sql/extra-jars/parquet-hadoop-1.10.1.jar:file:///home/spark-sql/extra-jars/arrow-plasma-0.17.0.jar
```
3: ```plasma-store-server```
```
cd /tmp
git clone https://github.com/Intel-bigdata/arrow.git
cd arrow && git checkout oap-master
cd cpp
mkdir release
cd release
#build libarrow, libplasma, libplasma_java
cmake -DCMAKE_INSTALL_PREFIX=/usr/ -DCMAKE_BUILD_TYPE=Release -DARROW_BUILD_TESTS=on -DARROW_PLASMA_JAVA_CLIENT=on -DARROW_PLASMA=on -DARROW_DEPENDENCY_SOURCE=BUNDLED  ..
make -j$(nproc)
sudo make install -j$(nproc)
```
Start plasma server using ```plasma-store-server -m 40000000000 -s /tmp/plasmaStore -d /mnt/pmem0```.

-m  how much Bytes share memory plasma will use

-s  Unix Domain sockcet path

-d  directory path

