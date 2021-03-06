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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.util.Throwables;
import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.coders.CoderException;
import com.google.cloud.dataflow.sdk.runners.inprocess.InProcessPipelineRunner.CommittedBundle;
import com.google.cloud.dataflow.sdk.runners.inprocess.InProcessPipelineRunner.UncommittedBundle;
import com.google.cloud.dataflow.sdk.transforms.DoFn;
import com.google.cloud.dataflow.sdk.util.IllegalMutationException;
import com.google.cloud.dataflow.sdk.util.MutationDetector;
import com.google.cloud.dataflow.sdk.util.MutationDetectors;
import com.google.cloud.dataflow.sdk.util.SerializableUtils;
import com.google.cloud.dataflow.sdk.util.UserCodeException;
import com.google.cloud.dataflow.sdk.util.WindowedValue;
import com.google.cloud.dataflow.sdk.values.PCollection;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import org.joda.time.Instant;

/**
 * A {@link BundleFactory} that ensures that elements added to it are not mutated after being
 * output. Immutability checks are enforced at the time {@link UncommittedBundle#commit(Instant)} is
 * called, checking the value at that time against the value at the time the element was added. All
 * elements added to the bundle will be encoded by the {@link Coder} of the underlying
 * {@link PCollection}.
 *
 * <p>This catches errors during the execution of a {@link DoFn} caused by modifying an element
 * after it is added to an output {@link PCollection}.
 */
class ImmutabilityCheckingBundleFactory implements BundleFactory {
  /**
   * Create a new {@link ImmutabilityCheckingBundleFactory} that uses the underlying
   * {@link BundleFactory} to create the output bundle.
   */
  public static ImmutabilityCheckingBundleFactory create(BundleFactory underlying) {
    return new ImmutabilityCheckingBundleFactory(underlying);
  }

  private final BundleFactory underlying;

  private ImmutabilityCheckingBundleFactory(BundleFactory underlying) {
    this.underlying = checkNotNull(underlying);
  }

  @Override
  public <T> UncommittedBundle<T> createRootBundle(PCollection<T> output) {
    return new ImmutabilityEnforcingBundle<>(underlying.createRootBundle(output));
  }

  @Override
  public <T> UncommittedBundle<T> createBundle(CommittedBundle<?> input, PCollection<T> output) {
    return new ImmutabilityEnforcingBundle<>(underlying.createBundle(input, output));
  }

  @Override
  public <T> UncommittedBundle<T> createKeyedBundle(
      CommittedBundle<?> input, Object key, PCollection<T> output) {
    return new ImmutabilityEnforcingBundle<>(underlying.createKeyedBundle(input, key, output));
  }

  private static class ImmutabilityEnforcingBundle<T> implements UncommittedBundle<T> {
    private final UncommittedBundle<T> underlying;
    private final SetMultimap<WindowedValue<T>, MutationDetector> mutationDetectors;
    private Coder<T> coder;

    public ImmutabilityEnforcingBundle(UncommittedBundle<T> underlying) {
      this.underlying = underlying;
      mutationDetectors = HashMultimap.create();
      coder = SerializableUtils.clone(getPCollection().getCoder());
    }

    @Override
    public PCollection<T> getPCollection() {
      return underlying.getPCollection();
    }

    @Override
    public UncommittedBundle<T> add(WindowedValue<T> element) {
      try {
        mutationDetectors.put(
            element, MutationDetectors.forValueWithCoder(element.getValue(), coder));
      } catch (CoderException e) {
        throw Throwables.propagate(e);
      }
      underlying.add(element);
      return this;
    }

    @Override
    public CommittedBundle<T> commit(Instant synchronizedProcessingTime) {
      for (MutationDetector detector : mutationDetectors.values()) {
        try {
          detector.verifyUnmodified();
        } catch (IllegalMutationException exn) {
          throw UserCodeException.wrap(
              new IllegalMutationException(
                  String.format(
                      "PTransform %s mutated value %s after it was output (new value was %s)."
                          + " Values must not be mutated in any way after being output.",
                      underlying.getPCollection().getProducingTransformInternal().getFullName(),
                      exn.getSavedValue(),
                      exn.getNewValue()),
                  exn.getSavedValue(),
                  exn.getNewValue(),
                  exn));
        }
      }
      return underlying.commit(synchronizedProcessingTime);
    }
  }
}
