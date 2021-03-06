/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.contrib.fileformat.base64;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import org.apache.hadoop.hive.shims.ShimLoader;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.InputFormat;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobConfigurable;
import org.apache.hadoop.mapred.LineRecordReader;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.commons.codec.binary.Base64;


public class Base64TextInputFormat
  implements InputFormat<LongWritable, BytesWritable>, JobConfigurable {
  
  
  public static class Base64LineRecordReader
    implements RecordReader<LongWritable, BytesWritable>, JobConfigurable {

    LineRecordReader reader;
    Text text;

    public Base64LineRecordReader(LineRecordReader reader) {
      this.reader = reader;
      text = reader.createValue();
    }
    
    @Override
    public void close() throws IOException {
      reader.close();
    }

    @Override
    public LongWritable createKey() {
      return reader.createKey();
    }

    @Override
    public BytesWritable createValue() {
      return new BytesWritable();
    }

    @Override
    public long getPos() throws IOException {
      return reader.getPos();
    }

    @Override
    public float getProgress() throws IOException {
      return reader.getProgress();
    }

    @Override
    public boolean next(LongWritable key, BytesWritable value)
        throws IOException {
      while (reader.next(key, text)) {
        byte[] textBytes = text.getBytes();
        int length = text.getLength();
        
        if (length != textBytes.length) {
          textBytes = Arrays.copyOf(textBytes, length);
        }
        byte[] binaryData = base64.decode(textBytes);

        int i;
        for (i = 0; i < binaryData.length && i < signature.length &&
                        binaryData[i] == signature[i]; ++i);

        if (i == signature.length) {
          value.set(binaryData, signature.length,
                    binaryData.length - signature.length);
          return true;
        }
      }
      return false;
    }
    
    private byte[] signature;
    private Base64 base64 = new Base64();

    @Override
    public void configure(JobConf job) {
      try {
        String signatureString = job.get("base64.text.input.format.signature");
        if (signatureString != null) {
          signature = signatureString.getBytes("UTF-8");
        } else {
          signature = new byte[0];
        }
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }      
    }
  }
  
  TextInputFormat format;
  JobConf job;
  
  public Base64TextInputFormat() {
    format = new TextInputFormat();
  }
  
  @Override
  public void configure(JobConf job) {
    this.job = job;
    format.configure(job);
  }
  
  public RecordReader<LongWritable, BytesWritable> getRecordReader(
      InputSplit genericSplit, JobConf job,
      Reporter reporter) throws IOException {
    reporter.setStatus(genericSplit.toString());
    Base64LineRecordReader reader = new Base64LineRecordReader(
        new LineRecordReader(job, (FileSplit) genericSplit));
    reader.configure(job);
    return reader;
  }

  @Override
  public InputSplit[] getSplits(JobConf job, int numSplits) throws IOException {
    return format.getSplits(job, numSplits);
  }

  public void validateInput(JobConf job) throws IOException {
    ShimLoader.getHadoopShims().inputFormatValidateInput(format, job);
  }

}
