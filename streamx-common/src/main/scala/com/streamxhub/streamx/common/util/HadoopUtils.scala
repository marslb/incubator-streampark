/*
 * Copyright (c) 2019 The StreamX Project
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.streamxhub.streamx.common.util


import com.google.common.io.Files
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{CommonConfigurationKeys, FileSystem, Path}
import org.apache.hadoop.ha.HAServiceProtocol
import org.apache.hadoop.yarn.api.records.ApplicationId
import org.apache.hadoop.yarn.client.RMHAServiceTarget
import org.apache.hadoop.yarn.client.api.YarnClient
import org.apache.hadoop.yarn.conf.YarnConfiguration

import java.io.{File, IOException}
import scala.collection.mutable.ArrayBuffer


object HadoopUtils extends Logger {

  val DEFAULT_YARN_RM_HTTP_ADDRESS = "0.0.0.0:8088"

  lazy val yarnClient = {
    val yarnClient = YarnClient.createYarnClient
    val yarnConf = new YarnConfiguration(HdfsUtils.conf)
    yarnClient.init(yarnConf)
    yarnClient.start()
    yarnClient
  }

  /**
   * 从yarn源码里抛出来的...
   */
  lazy val rmHttpAddress: String = {
    val yarnConf = new YarnConfiguration(HdfsUtils.conf)
    val ids = yarnConf.get("yarn.resourcemanager.ha.rm-ids")
    if (ids == null) DEFAULT_YARN_RM_HTTP_ADDRESS
    else {
      var address = new ArrayBuffer[String](1)
      ids.split(",").foreach(x => {
        if (address.isEmpty) {
          val conf = new YarnConfiguration(yarnConf)
          conf.set(YarnConfiguration.RM_HA_ID, x)
          val serviceTarget = new RMHAServiceTarget(conf)
          val rpcTimeoutForChecks = yarnConf.getInt(
            CommonConfigurationKeys.HA_FC_CLI_CHECK_TIMEOUT_KEY,
            CommonConfigurationKeys.HA_FC_CLI_CHECK_TIMEOUT_DEFAULT)
          val proto = serviceTarget.getProxy(yarnConf, rpcTimeoutForChecks)
          if (proto.getServiceStatus.getState == HAServiceProtocol.HAServiceState.ACTIVE) {
            address += yarnConf.get(s"yarn.resourcemanager.webapp.address.$x")
          }
        }
      })
      if (address.isEmpty) DEFAULT_YARN_RM_HTTP_ADDRESS else address.head
    }
  }

  def toApplicationId(appId: String): ApplicationId = {
    require(appId != null)
    val timestampAndId = appId.split("_")
    ApplicationId.newInstance(timestampAndId(1).toLong, timestampAndId.last.toInt)
  }

  def getYarnAppTrackingUrl(applicationId: ApplicationId): String = {
    yarnClient.getApplicationReport(applicationId).getTrackingUrl
  }

  @throws[IOException]
  def downloadJar(jarOnHdfs: String): String = {
    val tmpDir = Files.createTempDir
    val fs = FileSystem.get(new Configuration)
    val sourcePath = fs.makeQualified(new Path(jarOnHdfs))
    if (!fs.exists(sourcePath)) throw new IOException("jar file: " + jarOnHdfs + " doesn't exist.")
    val destPath = new Path(tmpDir.getAbsolutePath + "/" + sourcePath.getName)
    fs.copyToLocalFile(sourcePath, destPath)
    new File(destPath.toString).getAbsolutePath
  }
}
