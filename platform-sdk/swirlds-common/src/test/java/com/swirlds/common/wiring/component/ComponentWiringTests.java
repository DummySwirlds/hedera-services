/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.wiring.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ComponentWiringTests {

    private interface FooBarBaz {
        @NonNull
        Long handleFoo(@NonNull Integer foo);

        @InputWireLabel("bar")
        @NonNull
        Long handleBar(@NonNull Boolean bar);

        void handleBaz(@NonNull String baz);

        @InputWireLabel("data to be transformed")
        @SchedulerLabel("transformer")
        @NonNull
        default String transformer(@NonNull final Long baseOutput) {
            handleBar(true);
            return "" + baseOutput;
        }
    }

    private static class FooBarBazImpl implements FooBarBaz {
        private long runningValue = 0;

        @Override
        public Long handleFoo(@NonNull final Integer foo) {
            runningValue += foo;
            return runningValue;
        }

        @Override
        public Long handleBar(@NonNull final Boolean bar) {
            runningValue *= bar ? 1 : -1;
            return runningValue;
        }

        @Override
        public void handleBaz(@NonNull final String baz) {
            runningValue *= baz.hashCode();
        }

        public long getRunningValue() {
            return runningValue;
        }
    }

    private interface ComponentWithListOutput {
        @NonNull
        List<String> handleInputA(@NonNull String s);

        @NonNull
        List<String> handleInputB(@NonNull Long l);
    }

    private static class ComponentWithListOutputImpl implements ComponentWithListOutput {

        @NonNull
        @Override
        public List<String> handleInputA(@NonNull final String s) {
            return List.of(s.split(""));
        }

        @NonNull
        @Override
        public List<String> handleInputB(@NonNull final Long l) {
            final String s = l.toString();
            // return a list of characters
            return List.of(s.split(""));
        }
    }

    /**
     * The framework should not permit methods that aren't on the component to be wired.
     */
    @Test
    void methodNotOnComponentTest() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final WiringModel wiringModel =
                WiringModel.create(platformContext, platformContext.getTime(), ForkJoinPool.commonPool());

        final TaskScheduler<Long> scheduler = wiringModel
                .schedulerBuilder("test")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();

        final ComponentWiring<FooBarBaz, Long> fooBarBazWiring =
                new ComponentWiring<>(wiringModel, FooBarBaz.class, scheduler);

        assertThrows(IllegalArgumentException.class, () -> fooBarBazWiring.getInputWire((x, y) -> 0L));

        assertThrows(IllegalArgumentException.class, () -> fooBarBazWiring.getInputWire((x, y) -> {}));

        assertThrows(IllegalArgumentException.class, () -> fooBarBazWiring.getTransformedOutput((x, y) -> 0L));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void simpleComponentTest(final int bindLocation) {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final WiringModel wiringModel =
                WiringModel.create(platformContext, platformContext.getTime(), ForkJoinPool.commonPool());

        final TaskScheduler<Long> scheduler = wiringModel
                .schedulerBuilder("test")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();

        final ComponentWiring<FooBarBaz, Long> fooBarBazWiring =
                new ComponentWiring<>(wiringModel, FooBarBaz.class, scheduler);

        final FooBarBazImpl fooBarBazImpl = new FooBarBazImpl();

        if (bindLocation == 0) {
            fooBarBazWiring.bind(fooBarBazImpl);
        }

        final InputWire<Integer> fooInput = fooBarBazWiring.getInputWire(FooBarBaz::handleFoo);
        assertEquals("handleFoo", fooInput.getName());
        final InputWire<Boolean> barInput = fooBarBazWiring.getInputWire(FooBarBaz::handleBar);
        assertEquals("bar", barInput.getName());

        if (bindLocation == 1) {
            fooBarBazWiring.bind(fooBarBazImpl);
        }

        final InputWire<String> bazInput = fooBarBazWiring.getInputWire(FooBarBaz::handleBaz);
        assertEquals("handleBaz", bazInput.getName());
        final OutputWire<Long> output = fooBarBazWiring.getOutputWire();

        if (bindLocation == 2) {
            fooBarBazWiring.bind(fooBarBazImpl);
        }

        final AtomicLong outputValue = new AtomicLong();
        output.solderTo("outputHandler", "output", outputValue::set);

        // Getting the same input wire multiple times should yield the same instance
        assertSame(fooInput, fooBarBazWiring.getInputWire(FooBarBaz::handleFoo));
        assertSame(barInput, fooBarBazWiring.getInputWire(FooBarBaz::handleBar));
        assertSame(bazInput, fooBarBazWiring.getInputWire(FooBarBaz::handleBaz));

        // Getting the output wire multiple times should yield the same instance
        assertSame(output, fooBarBazWiring.getOutputWire());

        if (bindLocation == 3) {
            fooBarBazWiring.bind(fooBarBazImpl);
        }

        long expectedRunningValue = 0;
        for (int i = 0; i < 1000; i++) {
            if (i % 3 == 0) {
                expectedRunningValue += i;
                fooInput.put(i);
                assertEquals(expectedRunningValue, fooBarBazImpl.getRunningValue());
                assertEquals(expectedRunningValue, outputValue.get());
            } else if (i % 3 == 1) {
                final boolean choice = i % 7 == 0;
                expectedRunningValue *= choice ? 1 : -1;
                barInput.put(choice);
                assertEquals(expectedRunningValue, fooBarBazImpl.getRunningValue());
                assertEquals(expectedRunningValue, outputValue.get());
            } else {
                final String value = "value" + i;
                expectedRunningValue *= value.hashCode();
                bazInput.put(value);
                assertEquals(expectedRunningValue, fooBarBazImpl.getRunningValue());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void transformerTest(final int bindLocation) {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final WiringModel wiringModel =
                WiringModel.create(platformContext, platformContext.getTime(), ForkJoinPool.commonPool());

        final TaskScheduler<Long> scheduler = wiringModel
                .schedulerBuilder("test")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();

        final FooBarBazImpl fooBarBazImpl = new FooBarBazImpl();

        final ComponentWiring<FooBarBaz, Long> fooBarBazWiring =
                new ComponentWiring<>(wiringModel, FooBarBaz.class, scheduler);

        if (bindLocation == 0) {
            fooBarBazWiring.bind(fooBarBazImpl);
        }

        final InputWire<Integer> fooInput = fooBarBazWiring.getInputWire(FooBarBaz::handleFoo);
        final InputWire<Boolean> barInput = fooBarBazWiring.getInputWire(FooBarBaz::handleBar);
        final InputWire<String> bazInput = fooBarBazWiring.getInputWire(FooBarBaz::handleBaz);

        final OutputWire<String> output = fooBarBazWiring.getTransformedOutput(FooBarBaz::transformer);

        // Getting the same transformer multiple times should yield the same instance
        assertSame(output, fooBarBazWiring.getTransformedOutput(FooBarBaz::transformer));

        if (bindLocation == 1) {
            fooBarBazWiring.bind(fooBarBazImpl);
        }

        final AtomicReference<String> outputValue = new AtomicReference<>("0");
        output.solderTo("outputHandler", "output", outputValue::set);

        long expectedRunningValue = 0;
        for (int i = 0; i < 1000; i++) {
            if (i % 3 == 0) {
                expectedRunningValue += i;
                fooInput.put(i);
                assertEquals(expectedRunningValue, fooBarBazImpl.getRunningValue());
                assertEquals("" + expectedRunningValue, outputValue.get());
            } else if (i % 3 == 1) {
                final boolean choice = i % 7 == 0;
                expectedRunningValue *= choice ? 1 : -1;
                barInput.put(choice);
                assertEquals(expectedRunningValue, fooBarBazImpl.getRunningValue());
                assertEquals("" + expectedRunningValue, outputValue.get());
            } else {
                final String value = "value" + i;
                expectedRunningValue *= value.hashCode();
                bazInput.put(value);
                assertEquals(expectedRunningValue, fooBarBazImpl.getRunningValue());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void splitterTest(final int bindLocation) {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final WiringModel wiringModel =
                WiringModel.create(platformContext, platformContext.getTime(), ForkJoinPool.commonPool());

        final TaskScheduler<List<String>> scheduler = wiringModel
                .schedulerBuilder("test")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();

        final ComponentWiring<ComponentWithListOutput, List<String>> componentWiring =
                new ComponentWiring<>(wiringModel, ComponentWithListOutput.class, scheduler);

        if (bindLocation == 0) {
            componentWiring.bind(new ComponentWithListOutputImpl());
        }

        final OutputWire<String> splitOutput = componentWiring.getSplitOutput();
        assertSame(splitOutput, componentWiring.getSplitOutput());

        final List<String> outputData = new ArrayList<>();
        splitOutput.solderTo("addToOutputData", "split data", outputData::add);

        final List<String> expectedOutputData = new ArrayList<>();

        if (bindLocation == 1) {
            componentWiring.bind(new ComponentWithListOutputImpl());
        }

        componentWiring.getInputWire(ComponentWithListOutput::handleInputA).put("hello world");
        expectedOutputData.addAll(List.of("h", "e", "l", "l", "o", " ", "w", "o", "r", "l", "d"));

        componentWiring.getInputWire(ComponentWithListOutput::handleInputB).put(123L);
        expectedOutputData.addAll(List.of("1", "2", "3"));

        assertEquals(expectedOutputData, outputData);
    }
}