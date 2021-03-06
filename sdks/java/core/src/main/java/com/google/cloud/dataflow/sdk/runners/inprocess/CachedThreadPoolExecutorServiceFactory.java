/*
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
package com.google.cloud.dataflow.sdk.runners.inprocess;

import com.google.cloud.dataflow.sdk.options.DefaultValueFactory;
import com.google.cloud.dataflow.sdk.options.PipelineOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A {@link ExecutorServiceFactory} that produces cached thread pools via
 * {@link Executors#newCachedThreadPool()}.
 */
class CachedThreadPoolExecutorServiceFactory
    implements DefaultValueFactory<ExecutorServiceFactory>, ExecutorServiceFactory {
  private static final CachedThreadPoolExecutorServiceFactory INSTANCE =
      new CachedThreadPoolExecutorServiceFactory();

  @Override
  public ExecutorServiceFactory create(PipelineOptions options) {
    return INSTANCE;
  }

  @Override
  public ExecutorService create() {
    return Executors.newCachedThreadPool();
  }
}
