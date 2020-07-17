/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.parquet.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.ParquetReadOptions;
import org.apache.parquet.bytes.BytesInput;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Encoding;
import org.apache.parquet.column.page.DataPage;
import org.apache.parquet.column.page.DataPageV1;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.column.page.PageReader;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Types;
import org.junit.Rule;
import org.junit.Test;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import static org.apache.parquet.column.Encoding.BIT_PACKED;
import static org.apache.parquet.column.Encoding.PLAIN;
import static org.apache.parquet.format.converter.ParquetMetadataConverter.MAX_STATS_SIZE;
import static org.junit.Assert.*;

public class TestParquetFileReader {
  private static final MessageType SCHEMA = MessageTypeParser.parseMessageType("" +
    "message m {" +
    "  required group a {" +
    "    required binary b;" +
    "  }" +
    "  required group c {" +
    "    required int64 d;" +
    "  }" +
    "}");
  private static final String[] PATH1 = {"a", "b"};
  private static final ColumnDescriptor C1 = SCHEMA.getColumnDescription(PATH1);
  private static final String[] PATH2 = {"c", "d"};
  private static final ColumnDescriptor C2 = SCHEMA.getColumnDescription(PATH2);

  private static final byte[] BYTES1 = { 0, 1, 2, 3 };
  private static final byte[] BYTES2 = { 1, 2, 3, 4 };
  private static final byte[] BYTES3 = { 2, 3, 4, 5 };
  private static final byte[] BYTES4 = { 3, 4, 5, 6 };
  private static final CompressionCodecName CODEC = CompressionCodecName.UNCOMPRESSED;

  private static final org.apache.parquet.column.statistics.Statistics<?> EMPTY_STATS = org.apache.parquet.column.statistics.Statistics
    .getBuilderForReading(Types.required(PrimitiveType.PrimitiveTypeName.BINARY).named("test_binary")).build();

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private void validateContains(MessageType schema, PageReadStore pages, String[] path, int values, BytesInput bytes) throws IOException {
    PageReader pageReader = pages.getPageReader(schema.getColumnDescription(path));
    DataPage page = pageReader.readPage();
    assertEquals(values, page.getValueCount());
    assertArrayEquals(bytes.toByteArray(), ((DataPageV1)page).getBytes().toByteArray());
  }

  @Test
  public void testReadAll() throws IOException {

    File testFile = temp.newFile();
    testFile.delete();
    Path path = new Path(testFile.toURI());
    Configuration configuration = new Configuration();

    ParquetFileWriter w = new ParquetFileWriter(configuration, SCHEMA, path);
    w.start();
    w.startBlock(3);
    w.startColumn(C1, 5, CODEC);
    long c1Starts = w.getPos();
    w.writeDataPage(2, 4, BytesInput.from(BYTES1), EMPTY_STATS, BIT_PACKED, BIT_PACKED, PLAIN);
    w.writeDataPage(3, 4, BytesInput.from(BYTES1), EMPTY_STATS, BIT_PACKED, BIT_PACKED, PLAIN);
    w.endColumn();
    long c1Ends = w.getPos();
    w.startColumn(C2, 6, CODEC);
    long c2Starts = w.getPos();
    w.writeDataPage(2, 4, BytesInput.from(BYTES2), EMPTY_STATS, BIT_PACKED, BIT_PACKED, PLAIN);
    w.writeDataPage(3, 4, BytesInput.from(BYTES2), EMPTY_STATS, BIT_PACKED, BIT_PACKED, PLAIN);
    w.writeDataPage(1, 4, BytesInput.from(BYTES2), EMPTY_STATS, BIT_PACKED, BIT_PACKED, PLAIN);
    w.endColumn();
    long c2Ends = w.getPos();
    w.endBlock();
    w.startBlock(4);
    w.startColumn(C1, 7, CODEC);
    w.writeDataPage(7, 4, BytesInput.from(BYTES3), EMPTY_STATS, BIT_PACKED, BIT_PACKED, PLAIN);
    w.endColumn();
    w.startColumn(C2, 8, CODEC);
    w.writeDataPage(8, 4, BytesInput.from(BYTES4), EMPTY_STATS, BIT_PACKED, BIT_PACKED, PLAIN);
    w.endColumn();
    w.endBlock();
    w.end(new HashMap<String, String>());

    ParquetMetadata readFooter = ParquetFileReader.readFooter(configuration, path);
    assertEquals("footer: "+ readFooter, 2, readFooter.getBlocks().size());
    assertEquals(c1Ends - c1Starts, readFooter.getBlocks().get(0).getColumns().get(0).getTotalSize());
    assertEquals(c2Ends - c2Starts, readFooter.getBlocks().get(0).getColumns().get(1).getTotalSize());
    assertEquals(c2Ends - c1Starts, readFooter.getBlocks().get(0).getTotalByteSize());
    HashSet<Encoding> expectedEncoding=new HashSet<Encoding>();
    expectedEncoding.add(PLAIN);
    expectedEncoding.add(BIT_PACKED);
    assertEquals(expectedEncoding,readFooter.getBlocks().get(0).getColumns().get(0).getEncodings());

    ParquetFileReader r = new ParquetFileReader(configuration, readFooter.getFileMetaData(), path,
      readFooter.getBlocks(), Arrays.asList(SCHEMA.getColumnDescription(PATH1), SCHEMA.getColumnDescription(PATH2)));

    PageReadStore pages = r.readNextRowGroup();
    assertEquals(3, pages.getRowCount());
    r.close();

    ParquetFileReader r2 = new ParquetFileReader(configuration, readFooter.getFileMetaData(), path,
            readFooter.getBlocks(), Arrays.asList(SCHEMA.getColumnDescription(PATH1), SCHEMA.getColumnDescription(PATH2)));
    PageReadStore pages2 = r2.readNextRowGroup();
    assertEquals(3, pages2.getRowCount());
    r2.close();

    ParquetFileReader r3 = new ParquetFileReader(configuration, readFooter.getFileMetaData(), path,
            readFooter.getBlocks(), Arrays.asList(SCHEMA.getColumnDescription(PATH1), SCHEMA.getColumnDescription(PATH2)));
    PageReadStore pages3 = r3.readNextRowGroup();
    assertEquals(3, pages3.getRowCount());
    r3.close();
//    ParquetFileReader r1 = new ParquetFileReader(configuration, readFooter.getFileMetaData(), path,
//            readFooter.getBlocks(), Arrays.asList(SCHEMA.getColumnDescription(PATH1), SCHEMA.getColumnDescription(PATH2)));
//    PageReadStore pages1 = r1.readNextRowGroup();
//    assertEquals(3, pages1.getRowCount());
//
//    ParquetFileReader r2 = new ParquetFileReader(configuration, readFooter.getFileMetaData(), path,
//            readFooter.getBlocks(), Arrays.asList(SCHEMA.getColumnDescription(PATH1), SCHEMA.getColumnDescription(PATH2)));
//    PageReadStore pages2 = r2.readNextRowGroup();
//    assertEquals(3, pages2.getRowCount());
//
//    validateContains(SCHEMA, pages, PATH1, 2, BytesInput.from(BYTES1));
//    validateContains(SCHEMA, pages, PATH1, 3, BytesInput.from(BYTES1));

//    for(int i = 0 ;i < 3; i++){
//      PageReadStore pages = r.readNextRowGroup();
//      assertEquals(3, pages.getRowCount());
//      validateContains(SCHEMA, pages, PATH1, 2, BytesInput.from(BYTES1));
//      validateContains(SCHEMA, pages, PATH1, 3, BytesInput.from(BYTES1));
//    }
  }

  private org.apache.parquet.column.statistics.Statistics<?> statsC1(Binary... values) {
    org.apache.parquet.column.statistics.Statistics<?> stats = org.apache.parquet.column.statistics.Statistics
      .createStats(C1.getPrimitiveType());
    for (Binary value : values) {
      if (value == null) {
        stats.incrementNumNulls();
      } else {
        stats.updateStats(value);
      }
    }
    return stats;
  }

  private org.apache.parquet.column.statistics.Statistics<?> statsC2(Long... values) {
    org.apache.parquet.column.statistics.Statistics<?> stats = org.apache.parquet.column.statistics.Statistics
      .createStats(C2.getPrimitiveType());
    for (Long value : values) {
      if (value == null) {
        stats.incrementNumNulls();
      } else {
        stats.updateStats(value);
      }
    }
    return stats;
  }
}
